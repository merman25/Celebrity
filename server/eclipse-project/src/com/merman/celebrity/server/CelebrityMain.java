package com.merman.celebrity.server;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CelebrityMain {

	public static void main(String[] args) throws IOException {
		new Server().start();
		
		boolean doIt = false;
		
		if ( doIt ) {

			ServerSocket socket = new ServerSocket(8081);
			System.out.println("waiting...");
			Socket clientSocket = socket.accept();
			System.out.println("received connection");

			InputStream in = clientSocket.getInputStream();

			byte[] buffer = new byte[1024];
			//
			//		for ( int bytesRead = 0; ( bytesRead = in.read(buffer) ) != -1; ) {
			//
			//			System.out.print( bytesToHex(buffer, bytesRead));
			//		}

			OutputStream out = clientSocket.getOutputStream();
			new Thread() {
				@Override
				public void run () {
					try {
						Thread.sleep(2000);

						String message = "message 7\n";
						byte[] combinationOnMyLuggage = {1, 2, 3, 4};
						byte[] messageToEncode = message.getBytes("UTF-8");
						byte[] encodedMessage = decode(combinationOnMyLuggage, messageToEncode);
						byte[] frame = new byte[ encodedMessage.length + 6 ];
						frame[0] = -127;
						frame[1] = (byte) (encodedMessage.length + 128);
						System.out.println("My length as byte: " + frame[1]);
						System.arraycopy(combinationOnMyLuggage, 0, frame, 2, combinationOnMyLuggage.length);
						System.arraycopy(encodedMessage, 0, frame, 6, encodedMessage.length);

						frame = new byte[messageToEncode.length + 2];
						frame[0] = -127;
						frame[1] = (byte) (messageToEncode.length);
						System.arraycopy(messageToEncode, 0, frame, 2, messageToEncode.length);

						out.write(frame, 0, frame.length);
						System.out.println("sent my dodgy message");

						//					byte[] hello = { (byte) 0x81, (byte) 0x06, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x6f };
						//					out.write(hello, 0, hello.length);
						//					System.out.println("sent my other dodgy message");
					}
					catch ( Exception e ) {
						e.printStackTrace();
					}
				}
			}.start();

			try ( Scanner s = new Scanner(in, "UTF-8") ) {
				String data = s.useDelimiter("\\r\\n\\r\\n").next();
				System.out.println("processing data: " + data);
				Matcher get = Pattern.compile("^GET").matcher(data);
				if (get.find()) {
					//				while ( s.hasNext() ) {
					//					data = s.next();
					System.out.println("processing data: " + data);
					Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
					match.find();
					byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
							+ "Connection: Upgrade\r\n"
							+ "Upgrade: websocket\r\n"
							+ "Sec-WebSocket-Accept: "
							+ Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")))
							+ "\r\n\r\n").getBytes("UTF-8");
					out.write(response, 0, response.length);
					System.out.println("wrote response");
					//				}

					//				for ( int nextByte; ( nextByte = in.read() ) != -1; ) {
					//					if ( nextByte == 129 ) {
					//						nextByte = in.read();
					//						if ( nextByte > 125 ) {
					//							throw new RuntimeException("Message too long");
					//						}
					//						else {
					//							int messageLength = nextByte;
					//							byte[] key = { (byte) in.read(), (byte) in.read(), (byte) in.read(), (byte) in.read() };
					//							byte[] encodedMessageBuffer 		= new byte[1024];
					//							int	   encodedMessageBytesReadSoFar = 0;
					//
					//							while ( encodedMessageBytesReadSoFar < messageLength ) {
					//								for ( int bytesRead = 0; ( bytesRead = in.read(buffer) ) != -1; ) {
					//									int remainingBytesNeeded = messageLength - encodedMessageBytesReadSoFar;
					//									int bytesToPutIntoBuffer = Math.min(bytesRead, remainingBytesNeeded);
					//
					//									while ( encodedMessageBytesReadSoFar + bytesToPutIntoBuffer > encodedMessageBuffer.length ) {
					//										encodedMessageBuffer = doubleArraySize(encodedMessageBuffer);
					//									}
					//									System.arraycopy(buffer, 0, encodedMessageBuffer, encodedMessageBytesReadSoFar, bytesToPutIntoBuffer);
					//									encodedMessageBytesReadSoFar += bytesToPutIntoBuffer;
					//								}
					//							}
					//						}
					//					}
					//				}

					BufferedInputStream bin = new BufferedInputStream(in);
					for ( int nextByte; ( nextByte = in.read() ) != -1; ) {
						if ( nextByte == 129 ) {
							byte magicByte = (byte) nextByte;
							System.out.println("Magic byte as byte: " + magicByte);
							nextByte = in.read();
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
								byte[] key = { (byte) in.read(), (byte) in.read(), (byte) in.read(), (byte) in.read() };
								byte[] encodedMessage = new byte[messageLength];
								bin.read(encodedMessage, 0, encodedMessage.length);
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

					for ( int bytesRead = 0; ( bytesRead = in.read(buffer) ) != -1; ) {
						//						System.out.write(buffer, 0, bytesRead);
						for (int byteIndex = 0; byteIndex < bytesRead; byteIndex++) {
							if ( byteIndex > 0 ) {
								System.out.print(", ");
							}
							System.out.print( buffer[byteIndex] );
						}
						System.out.println();

						if ( buffer[0] == -127 ) {
							System.out.println("Message length: " + (128 + buffer[1]));
							byte[] key = new byte[4];
							System.arraycopy(buffer, 2, key, 0, 4);
							byte[] encodedMessage = new byte[bytesRead - 6];
							System.arraycopy(buffer, 6, encodedMessage, 0, encodedMessage.length);

							String string = new String( decode(key, encodedMessage), "UTF-8" );
							System.out.println( string);
						}
					}
				}

			}
			catch ( Exception e ) {
				e.printStackTrace();
			}

			System.out.println("reached end of stream");
		}
	}

	private static byte[] decode(byte[] aKey, byte[] aEncodedMessage) {
		byte[] decodedMessage = new byte[aEncodedMessage.length];
		for (int i = 0; i < aEncodedMessage.length; i++) {
			decodedMessage[i] = (byte) (aEncodedMessage[i] ^ aKey[i & 0x3]);
		}
		return decodedMessage;
	}

	private static byte[] doubleArraySize(byte[] aArray) {
		byte[] newArray = new byte[2 * aArray.length];
		System.arraycopy(aArray, 0, newArray, 0, aArray.length);
		return newArray;
	}

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes, int len) {
	    char[] hexChars = new char[len * 2];
	    for (int j = 0; j < len; j++) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = HEX_ARRAY[v >>> 4];
	        hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
	    }
	    return new String(hexChars);
	}
}
