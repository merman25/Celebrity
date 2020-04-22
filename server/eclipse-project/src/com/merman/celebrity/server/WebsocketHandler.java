package com.merman.celebrity.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebsocketHandler {
	private static final byte            MESSAGE_START_BYTE                = (byte) 0x81;                         // -127
	private static final int             MESSAGE_START_BYTE_AS_INT         = 0x81;                                // 129

	private static final int             LENGTH_BYTE_SUBTRACTION_CONSTANT  = 128;
	private static final byte            LENGTH_MAGNITUDE_16_BIT_INDICATOR = 126;
	private static final byte            LENGTH_MAGNITUDE_64_BIT_INDICATOR = 127;
	
	private static final int 			 MAX_LENGTH_16_BITS				   = 65536;
	
	private static AtomicInteger threadCount = new AtomicInteger();
	
	private final Socket socket;
	private volatile boolean started;
	private volatile boolean listen;
	private volatile boolean handshakeCompleted;
	
	private BlockingQueue<String> outgoingQueue = new ArrayBlockingQueue<>(1000, true);
	private Session session;
	
	private final MyInputStreamRunnable inputStreamRunnable = new MyInputStreamRunnable();
	private final MyOutputStreamRunnable outputStreamRunnable = new MyOutputStreamRunnable();
	
	private class MyInputStreamRunnable
	implements Runnable {

		@Override
		public void run() {
			try {
				performHandshake();
				while ( listen ) {
					InputStream inputStream = socket.getInputStream();
					for ( int nextByte; ( nextByte = inputStream.read() ) != -1; ) {
						if ( nextByte == MESSAGE_START_BYTE_AS_INT ) {
							byte magicByte = (byte) nextByte;
							
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
								System.out.println( "Message length: " + messageLength );
								
								if ( messageLength < 0 ) {
									throw new RuntimeException("Negative message length: " + messageLength);
								}
								else {
									byte[] key = { (byte) inputStream.read(), (byte) inputStream.read(), (byte) inputStream.read(), (byte) inputStream.read() };
									
									// FIXME can't handle messages whose lengths don't fit into an int
									byte[] encodedMessage = new byte[(int) messageLength];
									
									// read everything into byte array.
									// FIXME loop without a body! Should prob add some timeouts here if poss
									for ( int totalBytesRead = 0; ( totalBytesRead += inputStream.read(encodedMessage, totalBytesRead, encodedMessage.length - totalBytesRead ) ) < encodedMessage.length; );
									byte[] decodedMessage = decode(key, encodedMessage);
									String message = new String( decodedMessage, "UTF-8" );
									System.out.println("Received message: " + message );
									System.out.println("Message length: " + message.length() );
									
//									if ( message.length() > 20000 ) {
//										int lineLength = 149;
//										for ( int charsPrinted = 0; charsPrinted < message.length(); ) {
//											int charsToPrint = Math.min(lineLength, message.length() - charsPrinted );
//											int oldCharsPrinted = charsPrinted;
//											charsPrinted += charsToPrint;
//
//											System.out.println(" + \"" + message.substring(oldCharsPrinted, charsPrinted) + "\"");
//										}
//									}

								}
							}
						}
						else {
							System.out.println("Unexpected byte: " + nextByte);
						}
					}
				}
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
						byte[] messageBytes = message.getBytes("UTF-8");
						byte[] lengthArray = toLengthArray(messageBytes.length);

						byte[] frame = new byte[messageBytes.length + lengthArray.length + 1];
						frame[0] = MESSAGE_START_BYTE;
						System.arraycopy(lengthArray, 0, frame, 1, lengthArray.length);
						System.arraycopy(messageBytes, 0, frame, lengthArray.length + 1, messageBytes.length);

						socket.getOutputStream().write(frame);
						System.out.println("wrote message: " + message);
					}
					catch (InterruptedException | IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	

	public WebsocketHandler(Socket aSocket) {
		socket = aSocket;
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
	}

	public synchronized void stop() throws IOException {
		listen = false;
		if ( socket != null ) {
			socket.close();
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
				System.out.println("handshake completed");
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
}
