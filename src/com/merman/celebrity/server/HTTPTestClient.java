package com.merman.celebrity.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Simple HTTP client for playing around with things and manual testing.
 */
public class HTTPTestClient {
	private static String request = "GET / HTTP/1.1\r\n" +
			"Host: localhost:8000\r\n" +
			"Connection: keep-alive\r\n" +
			"DNT: 1\r\n" +
			"Upgrade-Insecure-Requests: 1\r\n" +
			"User-Agent: Simple HTTP Test Client\r\n" +
			"Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\r\n" +
			"Sec-Fetch-Site: none\r\n" +
			"Sec-Fetch-Mode: navigate\r\n" +
			"Sec-Fetch-User: ?1\r\n" +
			"Sec-Fetch-Dest: document\r\n" +
			"Accept-Encoding: gzip, deflate, br\r\n" +
			"Accept-Language: en-GB,en-US;q=0.9,en;q=0.8\r\n" +
			"\r\n";

	public static void main(String[] args) throws IOException, InterruptedException {
		Socket socket = sendHTTPRequest(0);
		printResponse(socket);
//		slowLorisAttack(1);

//		sendTooManyRequests();
	}

	private static void sendTooManyRequests() {
		for (int i = 0; i < 100; i++) {
			new Thread(() -> {
				for (int j = 0; j < 1000; j++) {
					sendHTTPRequest(0);
				}
			}).start();
		}
	}

	private static void printResponse(Socket socket) throws IOException {
		InputStream inputStream = socket.getInputStream();
		int totalBytesRead = 0;
		byte[] buffer = new byte[1024];
		for (int bytesRead = 0;
				( bytesRead = inputStream.read(buffer, totalBytesRead, buffer.length - totalBytesRead ) ) != -1; ) {
			totalBytesRead += bytesRead;
			if (totalBytesRead == buffer.length) {
				byte[] newBuffer = new byte[2 * buffer.length];
				System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
				buffer = newBuffer;
			}
		}
		String string = new String(buffer, 0, totalBytesRead, StandardCharsets.UTF_8);
		string = string.replaceAll("\\r(?!\\n)", "\\\\r\r")
		.replaceAll("(?<!\\r)\\n", "\\\\n\n")
		.replace("\r\n", "\\r\\n\r\n");

		System.out.print(string);
	}

	private static void slowLorisAttack(int aNumLorises) {
		for (int threadIndex = 0; threadIndex < aNumLorises; threadIndex++) {
			new Thread( () -> {
				sendHTTPRequest(10);
			}).start();
		}
	}

	private static Socket sendHTTPRequest(long aSleepTimeMillis) {
		Socket socket = null;
		try  {
			socket = new Socket("localhost", 8000);
			if (aSleepTimeMillis > 0) {
				for (int charIndex = 0; charIndex < request.length(); charIndex++) {
					socket.getOutputStream().write(request.charAt(charIndex));
					Thread.sleep(aSleepTimeMillis);
				}
			}
			else {
				socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return socket;
	}
}
