package com.merman.celebrity.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.Player;

public class WebsocketHandler {
	private static final byte            MESSAGE_START_BYTE                = (byte) 0x81;                         // -127
	private static final int             MESSAGE_START_BYTE_AS_INT         = 0x81;                                // 129

	private static final int             LENGTH_BYTE_SUBTRACTION_CONSTANT  = 128;
	private static final byte            LENGTH_MAGNITUDE_16_BIT_INDICATOR = 126;
	private static final byte            LENGTH_MAGNITUDE_64_BIT_INDICATOR = 127;
	
	private static final int 			 MAX_LENGTH_16_BITS				   = 65536;
	
	private static final int             CLOSE_CONNECTION_BYTE			   = 0x88;
	private static final int             PING_BYTE						   = 0x89;
	private static final int             PONG_BYTE						   = 0x8A;
	
	private static final String          STOP                              = "__STOP__";
	
	private static AtomicInteger threadCount = new AtomicInteger();
	
	private final Socket socket;
	private volatile boolean started;
	private volatile boolean listen;
	private volatile boolean handshakeCompleted;
	
	private BlockingQueue<String> outgoingQueue = new ArrayBlockingQueue<>(1000, true);
	private Session session;
	
	private Timer pingTimer;
	private final MyInputStreamRunnable inputStreamRunnable = new MyInputStreamRunnable();
	private final MyOutputStreamRunnable outputStreamRunnable = new MyOutputStreamRunnable();
	
	private volatile boolean stoppingSoon;
	
	private class MyInputStreamRunnable
	implements Runnable {

		@Override
		public void run() {
			try {
				performHandshake();
				InputStream inputStream = socket.getInputStream();
				for ( int nextByte; listen && ( nextByte = inputStream.read() ) != -1; ) {
					int firstByteOfMessage = nextByte;
					if ( firstByteOfMessage == MESSAGE_START_BYTE_AS_INT
							|| firstByteOfMessage == PONG_BYTE
							|| firstByteOfMessage == CLOSE_CONNECTION_BYTE ) {

						byte[] lengthByteArray = new byte[9];
						nextByte = inputStream.read();
						byte lengthMagnitudeIndicator = (byte) ( nextByte - LENGTH_BYTE_SUBTRACTION_CONSTANT );
						lengthByteArray[0] = lengthMagnitudeIndicator;
						if ( lengthMagnitudeIndicator < 0 ) {
							System.err.format( "Illegal magnitude indicator [%d] from byte [%d]\n", lengthMagnitudeIndicator, lengthByteArray[0] );
						}
						else {
							if ( lengthMagnitudeIndicator == LENGTH_MAGNITUDE_16_BIT_INDICATOR ) {
								inputStream.read(lengthByteArray, 1, 2);
							}
							else if ( lengthMagnitudeIndicator == LENGTH_MAGNITUDE_64_BIT_INDICATOR ) {
								inputStream.read(lengthByteArray, 1, 8);
							}

							long messageLength = toLength(lengthByteArray);

							if ( messageLength < 0 ) {
								throw new RuntimeException("Negative message length: " + messageLength);
							}
							else {
								byte[] key = { (byte) inputStream.read(), (byte) inputStream.read(), (byte) inputStream.read(), (byte) inputStream.read() };
								
								if ( messageLength == 0 ) {
									if ( firstByteOfMessage == PONG_BYTE ) {
//										System.out.format("Pong received from %s\n", getSession().getPlayer() );
									}
									else {
										System.out.println( "zero-length message received" );
									}
								}
								else {
									// FIXME can't handle messages whose lengths don't fit into an int
									byte[] encodedMessage = new byte[(int) messageLength];

									// read everything into byte array.
									// FIXME loop without a body! Should prob add some timeouts here if poss
									for ( int totalBytesRead = 0; ( totalBytesRead += inputStream.read(encodedMessage, totalBytesRead, encodedMessage.length - totalBytesRead ) ) < encodedMessage.length; );
									byte[] decodedMessage = decode(key, encodedMessage);
									String message;
									if ( firstByteOfMessage == CLOSE_CONNECTION_BYTE ) {
										message = bytesToHex(decodedMessage) + " (encoded as " + bytesToHex(encodedMessage) + ")";
									}
									else {
										message = new String( decodedMessage, "UTF-8" );
									}

									if ( getSession() == null
											&& message.startsWith("session=") ) {
										String sessionID = message.substring("session=".length());
										session = SessionManager.getSession(sessionID);
										if ( session != null ) {
											SessionManager.putSocket( session, WebsocketHandler.this );
											System.out.format( "Opened websocket with session %s [%s] from IP %s\n", sessionID, session.getPlayer(), socket.getRemoteSocketAddress() );
											enqueueMessage("gotcha");
										}
										else {
											System.err.println( "Unknown session ID: " + sessionID );
										}
									}
									else if ( message.equals("client-ping") ) {
//										System.out.println("Received client ping from " + getSession().getPlayer() );
										enqueueMessage("client-pong");
									}
									else {
										System.out.println("Message from socket: " + message );
									}
								}
							}
						}
					}
					else {
						stopSoon();
						System.out.println("Unexpected byte: " + bytesToHex(nextByte));
					}
				}
			}
			catch ( SocketException e ) {
				System.out.format("Handler for session [%s] (%s) no longer listening: %s\n", getSession(), getSession().getPlayer(), e.getMessage());
			}
			catch ( IOException e ) {
				e.printStackTrace();
			}
		}
		
	}
	
	private class MyOutputStreamRunnable
	implements Runnable {

		@Override
		public void run() {
			if ( handshakeCompleted ) {
				while ( listen ) {
					try {
						String message 		= outgoingQueue.take();
						if ( STOP.equals(message) ) {
							continue;
						}
						sendMessage(MESSAGE_START_BYTE, message);

					}
					catch ( InterruptedException e ) {
						System.out.format("Output handler for session [%s] interrupted\n", getSession());
					}
					catch ( SocketException e ) {
						System.out.format("Handler for session [%s] (%s) can no longer write: %s\n", getSession(), getSession().getPlayer(), e.getMessage());
						try {
							stop();
						}
						catch ( IOException e2 ) {
							e2.printStackTrace();
						}
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		synchronized void sendMessage( byte aMessageStartByte, String aMessage ) throws IOException {
			byte[] messageBytes = aMessage.getBytes("UTF-8");
			byte[] lengthArray = toLengthArray(messageBytes.length);

			byte[] frame = new byte[messageBytes.length + lengthArray.length + 1];
			frame[0] = aMessageStartByte;
			System.arraycopy(lengthArray, 0, frame, 1, lengthArray.length);
			System.arraycopy(messageBytes, 0, frame, lengthArray.length + 1, messageBytes.length);

			socket.getOutputStream().write(frame);
		}
	}
	
	private class MyPingTimerTask
	extends TimerTask {

		@Override
		public void run() {
			if ( listen ) {
				try {
//					System.out.println("Pinging " + getSession().getPlayer());
					outputStreamRunnable.sendMessage((byte) PING_BYTE, "");
				}
				catch ( SocketException e ) {
					System.out.format("Handler for session [%s] (%s) can no longer write: %s\n", getSession(), getSession().getPlayer(), e.getMessage());
					try {
						stop();
					}
					catch ( IOException e2 ) {
						e2.printStackTrace();
					}
				}
				catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	

	public WebsocketHandler(Socket aSocket) {
		socket = aSocket;
	}
	
	private synchronized void stopSoon() {
		if ( ! stoppingSoon ) {
			stoppingSoon = true;
			new Thread("stopping a websocket handler") {

				@Override
				public void run() {
					try {
						Thread.sleep(2000);
						WebsocketHandler.this.stop();
					}
					catch ( Exception e ) {
						e.printStackTrace();
					}
				}
				
			}.start();
		}
	}

	private long toLength(byte[] aLengthByteArray) {
		long length = 0;
		int lengthMagnitudeIndicator = aLengthByteArray[0];
		if ( lengthMagnitudeIndicator < 0 ) {
			throw new IllegalArgumentException("Illegal magnitude indicator: " + aLengthByteArray[0]);
		}
		else if ( lengthMagnitudeIndicator < LENGTH_MAGNITUDE_16_BIT_INDICATOR ) {
			length = lengthMagnitudeIndicator;
		}
		else if ( lengthMagnitudeIndicator == LENGTH_MAGNITUDE_16_BIT_INDICATOR ) {
			int byteOneAsInt = aLengthByteArray[1];
			int byteTwoAsInt = aLengthByteArray[2];
			
			if ( byteOneAsInt < 0 ) {
				byteOneAsInt = 256 + byteOneAsInt;
			}
			if ( byteTwoAsInt < 0 ) {
				byteTwoAsInt = 256 + byteTwoAsInt;
			}
			length = ( ( byteOneAsInt << 8 ) | byteTwoAsInt );
		}
		else {
			assert aLengthByteArray[0] == LENGTH_MAGNITUDE_64_BIT_INDICATOR;
			
			for (int i = 1; i < 9; i++) {
				int shiftAmount = 8 * (8-i);
				long byteAsLong = aLengthByteArray[i];
				if ( byteAsLong < 0 ) {
					byteAsLong = 256 + byteAsLong;
				}
				length |= ( byteAsLong << shiftAmount );
			}
		}
		return length;
	}
	
	public static byte[] toLengthArray(long aLength) {
		byte[] lengthArray;
		if ( aLength < LENGTH_MAGNITUDE_16_BIT_INDICATOR ) {
			lengthArray = new byte[1];
			lengthArray[0] = (byte) aLength;
		}
		else if ( aLength < MAX_LENGTH_16_BITS ) {
			lengthArray = new byte[3];
			lengthArray[0] = LENGTH_MAGNITUDE_16_BIT_INDICATOR;
			lengthArray[1] = (byte) (aLength >> 8);
			lengthArray[2] = (byte) (aLength & 255);
		}
		else {
			lengthArray = new byte[9];
			lengthArray[0] = LENGTH_MAGNITUDE_64_BIT_INDICATOR;
			for (int i = 1; i < 9; i++) {
				int shiftAmount = 8 * (8-i);
				lengthArray[i] = (byte) (aLength >> shiftAmount);
			}
		}
		
		return lengthArray;
	}

	public synchronized void start() throws IOException {
		if ( started ) {
			throw new IllegalStateException("Already started");
		}
		started = true;
		listen = true;
		
		int threadNumber = threadCount.incrementAndGet();
		new Thread(inputStreamRunnable,  "Websocket-InputStream-"  + threadNumber).start();
		
		while ( ! handshakeCompleted ) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
			}
		}
		new Thread(outputStreamRunnable, "Websocket-OutputStream-" + threadNumber).start();
		
		pingTimer = new Timer("ping-timer-" + threadNumber);
		pingTimer.schedule(new MyPingTimerTask(), 5000, 10000);
	}

	public synchronized void stop() throws IOException {
		listen = false;
		if ( socket != null ) {
			socket.close();
		}
		if ( pingTimer != null ) {
			pingTimer.cancel();
			pingTimer = null;
		}
		enqueueMessage(STOP);
		
		Session session = getSession();
		Player  player  = session == null ? null : session.getPlayer();
		Game    game    = player == null ? null : player.getGame();
		if ( game != null ) {
			game.removeAllGameEventListeners(this);
		}
	}

	private void performHandshake() throws IOException {
		InputStream inputStream = socket.getInputStream();
		OutputStream outputStream = socket.getOutputStream();

		try {
			// Don't close this scanner, doing so will close ths socket
			Scanner scanner = new Scanner(inputStream, "UTF-8");
			String data = scanner.useDelimiter("\\r\\n\\r\\n").next();
			Matcher get = Pattern.compile("^GET").matcher(data);
			if (get.find()) {
				Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
				match.find();
				byte[] response;
				response = ("HTTP/1.1 101 Switching Protocols\r\n"
						+ "Connection: Upgrade\r\n"
						+ "Upgrade: websocket\r\n"
						+ "Sec-WebSocket-Accept: "
						+ Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")))
						+ "\r\n\r\n").getBytes("UTF-8");
				outputStream.write(response, 0, response.length);
				handshakeCompleted = true;
			}
		}
		catch ( Exception e ) {
			e.printStackTrace();
		}
	}
	
	private static byte[] decode(byte[] aKey, byte[] aEncodedMessage) {
		byte[] decodedMessage = new byte[aEncodedMessage.length];
		for (int i = 0; i < aEncodedMessage.length; i++) {
			decodedMessage[i] = (byte) (aEncodedMessage[i] ^ aKey[i & 0x3]);
		}
		return decodedMessage;
	}
	
	public synchronized void enqueueMessage(String aMessage) {
		try {
			outgoingQueue.put(aMessage);
		}
		catch ( InterruptedException e ) {
			e.printStackTrace();
		}
	}

	public Session getSession() {
		return session;
	}
	
	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for (int j = 0; j < bytes.length; j++) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = HEX_ARRAY[v >>> 4];
	        hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	
	public static String bytesToHex(int aByte) {
		return bytesToHex((byte) (aByte & 0xFF ));
	}
	
	public static String bytesToHex(byte aByte) {
		char[] hexChars = new char[2];
		int v = aByte & 0xFF;
		hexChars[0] = HEX_ARRAY[v >>> 4];
		hexChars[1] = HEX_ARRAY[v & 0x0F];
		return new String(hexChars);
	}
}
