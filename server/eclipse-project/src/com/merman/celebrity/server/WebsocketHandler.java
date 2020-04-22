package com.merman.celebrity.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebsocketHandler {
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
						if ( nextByte == 129 ) {
							byte magicByte = (byte) nextByte;
							System.out.println("Magic byte as byte: " + magicByte);
							nextByte = inputStream.read();
							byte lengthByte = (byte) nextByte;
							System.out.println("Length byte as byte: " + lengthByte);
							int messageLength = nextByte - 128;
							if ( messageLength > 125 ) {
								throw new RuntimeException("Message too long: " + nextByte);
							}
							else if ( messageLength < 0 ) {
								throw new RuntimeException("Negative message length: " + messageLength);
							}
							else {
								byte[] key = { (byte) inputStream.read(), (byte) inputStream.read(), (byte) inputStream.read(), (byte) inputStream.read() };
								byte[] encodedMessage = new byte[messageLength];
								inputStream.read(encodedMessage, 0, encodedMessage.length);
								byte[] decodedMessage = decode(key, encodedMessage);
								System.out.println("New code has decoded: " + new String( decodedMessage, "UTF-8" ) );

								byte[] originalFrame = new byte[encodedMessage.length + 6];
								originalFrame[0] = magicByte;
								originalFrame[1] = lengthByte;
								System.arraycopy(key, 0, originalFrame, 2, 4);
								System.arraycopy(encodedMessage, 0, originalFrame, 6, encodedMessage.length);
								System.out.format("Original frame: %s\n", Arrays.toString(originalFrame));

								byte[] encodeAgain = decode(key, decodedMessage);
								System.out.format("Re-encoded message: %s\n", Arrays.toString(encodeAgain));
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
//				try {
//					Thread.sleep(2000);
//
//					String message = outgoingQueue.take();
//					byte[] messageBytes = message.getBytes("UTF-8");
//
//					if ( messageBytes.length > 125 ) {
//						System.err.format("Message too long (%,d bytes): %s\n", messageBytes.length, message );
//					}
//					else {
//						byte[] frame = new byte[messageBytes.length + 2];
//						frame[0] = -127;
//						frame[1] = (byte) (messageBytes.length);
//						System.arraycopy(messageBytes, 0, frame, 2, messageBytes.length);
//
//						socket.getOutputStream().write(frame, 0, frame.length);
//						System.out.println("sent my dodgy message");
//					}
//				}
//				catch ( Exception e ) {
//					e.printStackTrace();
//				}
				
				while ( listen ) {
					try {
						String message 		= outgoingQueue.take();
						byte[] messageBytes = message.getBytes("UTF-8");

						if ( messageBytes.length > 125 ) {
							System.err.format("Message too long (%,d bytes): %s\n", messageBytes.length, message );
						}
						else {
							byte[] frame = new byte[messageBytes.length + 2];
							frame[0] = -127;
							frame[1] = (byte) messageBytes.length;
							System.arraycopy(messageBytes, 0, frame, 2, messageBytes.length);

							socket.getOutputStream().write(frame);
							System.out.println("wrote message: " + message);
						}
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
