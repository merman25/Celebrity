package com.merman.celebrity.tests;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import com.merman.celebrity.server.HTTPExchange;
import com.merman.celebrity.server.HTTPRequestTooLongException;

public class HTTPExchangeTest {
	private static String getRequest = "GET / HTTP/1.1\r\n" +
			"Host: localhost:8000\r\n" +
			"Connection: keep-alive\r\n" +
			"DNT: 1\r\n" +
			"Upgrade-Insecure-Requests: 1\r\n" +
			"User-Agent: JUnit Test\r\n" +
			"Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\r\n" +
			"Sec-Fetch-Site: none\r\n" +
			"Sec-Fetch-Mode: navigate\r\n" +
			"Sec-Fetch-User: ?1\r\n" +
			"Sec-Fetch-Dest: document\r\n" +
			"Accept-Encoding: gzip, deflate, br\r\n" +
			"Accept-Language: en-GB,en-US;q=0.9,en;q=0.8\r\n" +
			"\r\n";
	
	@Test
	public void testAddBytesOneAtATime() {
		HTTPExchange httpExchange = new HTTPExchange();

		String[] lineArray = getRequest.split("(?<=\\r\\n)");
		Assert.assertEquals("14 lines in request", 14, lineArray.length);

		for (int lineIndex = 0; lineIndex < lineArray.length; lineIndex++) {

			String line = lineArray[lineIndex];

			for (int charIndex = 0; charIndex < line.length(); charIndex++) {
				switch (lineIndex) {
				// Most of these cases fall through, since we can test gradually more as we get through the request
				case 13:
					Assert.assertEquals( Arrays.asList( "en-GB,en-US;q=0.9,en;q=0.8" ), httpExchange.getRequestHeaders().get( "Accept-Language" ) );
				case 12:
					Assert.assertEquals( Arrays.asList( "gzip, deflate, br" ), httpExchange.getRequestHeaders().get( "Accept-Encoding" ) );
				case 11:
					Assert.assertEquals( Arrays.asList( "document" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Dest" ) );
				case 10:
					Assert.assertEquals( Arrays.asList( "?1" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-User" ) );
				case 9:
					Assert.assertEquals( Arrays.asList( "navigate" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Mode" ) );
				case 8:
					Assert.assertEquals( Arrays.asList( "none" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Site" ) );
				case 7:
					Assert.assertEquals( Arrays.asList( "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9" ), httpExchange.getRequestHeaders().get( "Accept" ) );
				case 6:
					Assert.assertEquals( Arrays.asList( "JUnit Test" ), httpExchange.getRequestHeaders().get( "User-Agent" ) );
				case 5:
					Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "Upgrade-Insecure-Requests" ) );
				case 4:
					Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "DNT" ) );
				case 3:
					Assert.assertEquals( Arrays.asList( "keep-alive" ), httpExchange.getRequestHeaders().get( "Connection" ) );
				case 2:
					Assert.assertEquals( Arrays.asList( "localhost:8000" ), httpExchange.getRequestHeaders().get( "Host" ) );
				case 1:
					Assert.assertNotNull("First line should have been read", httpExchange.getFirstLine());
					break;
				case 0:
					Assert.assertNull("First line should not yet have been read", httpExchange.getFirstLine());
					break;
				}

				httpExchange.addBytes( new byte[] { (byte) line.charAt(charIndex) } );
			}
		}
	}

	@Test
	public void testAddBytesLineByLine() {
		HTTPExchange httpExchange = new HTTPExchange();

		String[] lineArray = getRequest.split("(?<=\\r\\n)");
		Assert.assertEquals("14 lines in request", 14, lineArray.length);

		for (int lineIndex = 0; lineIndex < lineArray.length; lineIndex++) {

			String line = lineArray[lineIndex];

			switch (lineIndex) {
			// Most of these cases fall through, since we can test gradually more as we get through the request
			case 13:
				Assert.assertEquals( Arrays.asList( "en-GB,en-US;q=0.9,en;q=0.8" ), httpExchange.getRequestHeaders().get( "Accept-Language" ) );
			case 12:
				Assert.assertEquals( Arrays.asList( "gzip, deflate, br" ), httpExchange.getRequestHeaders().get( "Accept-Encoding" ) );
			case 11:
				Assert.assertEquals( Arrays.asList( "document" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Dest" ) );
			case 10:
				Assert.assertEquals( Arrays.asList( "?1" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-User" ) );
			case 9:
				Assert.assertEquals( Arrays.asList( "navigate" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Mode" ) );
			case 8:
				Assert.assertEquals( Arrays.asList( "none" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Site" ) );
			case 7:
				Assert.assertEquals( Arrays.asList( "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9" ), httpExchange.getRequestHeaders().get( "Accept" ) );
			case 6:
				Assert.assertEquals( Arrays.asList( "JUnit Test" ), httpExchange.getRequestHeaders().get( "User-Agent" ) );
			case 5:
				Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "Upgrade-Insecure-Requests" ) );
			case 4:
				Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "DNT" ) );
			case 3:
				Assert.assertEquals( Arrays.asList( "keep-alive" ), httpExchange.getRequestHeaders().get( "Connection" ) );
			case 2:
				Assert.assertEquals( Arrays.asList( "localhost:8000" ), httpExchange.getRequestHeaders().get( "Host" ) );
			case 1:
				Assert.assertNotNull("First line should have been read", httpExchange.getFirstLine());
				break;
			case 0:
				Assert.assertNull("First line should not yet have been read", httpExchange.getFirstLine());
				break;
			}

			httpExchange.addBytes( line.getBytes(StandardCharsets.US_ASCII) );
		}
	}

	@Test
	public void testAddBytesTwoLinesAtATime() {
		HTTPExchange httpExchange = new HTTPExchange();

		String[] lineArray = getRequest.split("(?<=\\r\\n)");
		Assert.assertEquals("14 lines in request", 14, lineArray.length);

		for (int lineIndex = 0; lineIndex < lineArray.length - 1; lineIndex += 2) {

			String line1 = lineArray[lineIndex];
			String line2 = lineArray[lineIndex + 1];

			switch (lineIndex) {
			// Most of these cases fall through, since we can test gradually more as we get through the request
			case 12:
				Assert.assertEquals( Arrays.asList( "gzip, deflate, br" ), httpExchange.getRequestHeaders().get( "Accept-Encoding" ) );
				Assert.assertEquals( Arrays.asList( "document" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Dest" ) );
			case 10:
				Assert.assertEquals( Arrays.asList( "?1" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-User" ) );
				Assert.assertEquals( Arrays.asList( "navigate" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Mode" ) );
			case 8:
				Assert.assertEquals( Arrays.asList( "none" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Site" ) );
				Assert.assertEquals( Arrays.asList( "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9" ), httpExchange.getRequestHeaders().get( "Accept" ) );
			case 6:
				Assert.assertEquals( Arrays.asList( "JUnit Test" ), httpExchange.getRequestHeaders().get( "User-Agent" ) );
				Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "Upgrade-Insecure-Requests" ) );
			case 4:
				Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "DNT" ) );
				Assert.assertEquals( Arrays.asList( "keep-alive" ), httpExchange.getRequestHeaders().get( "Connection" ) );
			case 2:
				Assert.assertEquals( Arrays.asList( "localhost:8000" ), httpExchange.getRequestHeaders().get( "Host" ) );
				Assert.assertNotNull("First line should have been read", httpExchange.getFirstLine());
				break;
			case 0:
				Assert.assertNull("First line should not yet have been read", httpExchange.getFirstLine());
				break;
			}

			httpExchange.addBytes( line1.getBytes(StandardCharsets.US_ASCII) );
			httpExchange.addBytes( line2.getBytes(StandardCharsets.US_ASCII) );
		}

		Assert.assertEquals( Arrays.asList( "en-GB,en-US;q=0.9,en;q=0.8" ), httpExchange.getRequestHeaders().get( "Accept-Language" ) );
	}

	@Test
	public void testReadAllBytesAtOnce() {
		HTTPExchange httpExchange = new HTTPExchange();

		Assert.assertNull("First line should not yet have been read", httpExchange.getFirstLine());

		httpExchange.addBytes( getRequest.getBytes(StandardCharsets.US_ASCII) );

		Assert.assertEquals( Arrays.asList( "en-GB,en-US;q=0.9,en;q=0.8" ), httpExchange.getRequestHeaders().get( "Accept-Language" ) );
		Assert.assertEquals( Arrays.asList( "gzip, deflate, br" ), httpExchange.getRequestHeaders().get( "Accept-Encoding" ) );
		Assert.assertEquals( Arrays.asList( "document" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Dest" ) );
		Assert.assertEquals( Arrays.asList( "?1" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-User" ) );
		Assert.assertEquals( Arrays.asList( "navigate" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Mode" ) );
		Assert.assertEquals( Arrays.asList( "none" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Site" ) );
		Assert.assertEquals( Arrays.asList( "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9" ), httpExchange.getRequestHeaders().get( "Accept" ) );
		Assert.assertEquals( Arrays.asList( "JUnit Test" ), httpExchange.getRequestHeaders().get( "User-Agent" ) );
		Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "Upgrade-Insecure-Requests" ) );
		Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "DNT" ) );
		Assert.assertEquals( Arrays.asList( "keep-alive" ), httpExchange.getRequestHeaders().get( "Connection" ) );
		Assert.assertEquals( Arrays.asList( "localhost:8000" ), httpExchange.getRequestHeaders().get( "Host" ) );
		Assert.assertNotNull("First line should have been read", httpExchange.getFirstLine());
	}
	
	@Test
	public void testReadRandomNumberOfRemainingBytesEachTime() {
		// Number of bytes random between 0 and numRemaining
		Random random = new Random(54321);
		byte[] bytes = getRequest.getBytes(StandardCharsets.US_ASCII);

		for (int i = 0; i < 100; i++) {
			HTTPExchange httpExchange = new HTTPExchange();
			Assert.assertNull("First line should not yet have been read", httpExchange.getFirstLine());

			for (int charOffset = 0; charOffset < getRequest.length() - 1;) {
				int numRemaining = getRequest.length() - charOffset;
				int numBytesToRead = random.nextInt(numRemaining);

				byte[] bytesToRead = new byte[numBytesToRead];
				System.arraycopy(bytes, charOffset, bytesToRead, 0, numBytesToRead);

				httpExchange.addBytes(bytesToRead);

				charOffset += numBytesToRead;
			}

			Assert.assertEquals( Arrays.asList( "en-GB,en-US;q=0.9,en;q=0.8" ), httpExchange.getRequestHeaders().get( "Accept-Language" ) );
			Assert.assertEquals( Arrays.asList( "gzip, deflate, br" ), httpExchange.getRequestHeaders().get( "Accept-Encoding" ) );
			Assert.assertEquals( Arrays.asList( "document" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Dest" ) );
			Assert.assertEquals( Arrays.asList( "?1" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-User" ) );
			Assert.assertEquals( Arrays.asList( "navigate" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Mode" ) );
			Assert.assertEquals( Arrays.asList( "none" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Site" ) );
			Assert.assertEquals( Arrays.asList( "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9" ), httpExchange.getRequestHeaders().get( "Accept" ) );
			Assert.assertEquals( Arrays.asList( "JUnit Test" ), httpExchange.getRequestHeaders().get( "User-Agent" ) );
			Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "Upgrade-Insecure-Requests" ) );
			Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "DNT" ) );
			Assert.assertEquals( Arrays.asList( "keep-alive" ), httpExchange.getRequestHeaders().get( "Connection" ) );
			Assert.assertEquals( Arrays.asList( "localhost:8000" ), httpExchange.getRequestHeaders().get( "Host" ) );
			Assert.assertNotNull("First line should have been read", httpExchange.getFirstLine());
		}
	}
	
	@Test
	public void testReadRandomSmallNumberOfBytesEachTime() {
		// Number of bytes random between 0 and 5
		Random random = new Random(2468);
		byte[] bytes = getRequest.getBytes(StandardCharsets.US_ASCII);

		for (int i = 0; i < 100; i++) {
			HTTPExchange httpExchange = new HTTPExchange();
			Assert.assertNull("First line should not yet have been read", httpExchange.getFirstLine());

			for (int charOffset = 0; charOffset < getRequest.length() - 1;) {
				int numRemaining = getRequest.length() - charOffset;
				int numBytesToRead = random.nextInt( Math.min(5, numRemaining ) );

				byte[] bytesToRead = new byte[numBytesToRead];
				System.arraycopy(bytes, charOffset, bytesToRead, 0, numBytesToRead);

				httpExchange.addBytes(bytesToRead);

				charOffset += numBytesToRead;
			}

			Assert.assertEquals( Arrays.asList( "en-GB,en-US;q=0.9,en;q=0.8" ), httpExchange.getRequestHeaders().get( "Accept-Language" ) );
			Assert.assertEquals( Arrays.asList( "gzip, deflate, br" ), httpExchange.getRequestHeaders().get( "Accept-Encoding" ) );
			Assert.assertEquals( Arrays.asList( "document" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Dest" ) );
			Assert.assertEquals( Arrays.asList( "?1" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-User" ) );
			Assert.assertEquals( Arrays.asList( "navigate" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Mode" ) );
			Assert.assertEquals( Arrays.asList( "none" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Site" ) );
			Assert.assertEquals( Arrays.asList( "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9" ), httpExchange.getRequestHeaders().get( "Accept" ) );
			Assert.assertEquals( Arrays.asList( "JUnit Test" ), httpExchange.getRequestHeaders().get( "User-Agent" ) );
			Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "Upgrade-Insecure-Requests" ) );
			Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "DNT" ) );
			Assert.assertEquals( Arrays.asList( "keep-alive" ), httpExchange.getRequestHeaders().get( "Connection" ) );
			Assert.assertEquals( Arrays.asList( "localhost:8000" ), httpExchange.getRequestHeaders().get( "Host" ) );
			Assert.assertNotNull("First line should have been read", httpExchange.getFirstLine());
		}
	}
	
	@Test
	public void testReadRandomLargeNumberOfBytesEachTime() {
		// Number of bytes random between 0 and 100
		Random random = new Random(0xBABE);
		byte[] bytes = getRequest.getBytes(StandardCharsets.US_ASCII);

		for (int i = 0; i < 100; i++) {
			HTTPExchange httpExchange = new HTTPExchange();
			Assert.assertNull("First line should not yet have been read", httpExchange.getFirstLine());

			for (int charOffset = 0; charOffset < getRequest.length() - 1;) {
				int numRemaining = getRequest.length() - charOffset;
				int numBytesToRead = random.nextInt( Math.min(100, numRemaining ) );

				byte[] bytesToRead = new byte[numBytesToRead];
				System.arraycopy(bytes, charOffset, bytesToRead, 0, numBytesToRead);

				httpExchange.addBytes(bytesToRead);

				charOffset += numBytesToRead;
			}

			Assert.assertEquals( Arrays.asList( "en-GB,en-US;q=0.9,en;q=0.8" ), httpExchange.getRequestHeaders().get( "Accept-Language" ) );
			Assert.assertEquals( Arrays.asList( "gzip, deflate, br" ), httpExchange.getRequestHeaders().get( "Accept-Encoding" ) );
			Assert.assertEquals( Arrays.asList( "document" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Dest" ) );
			Assert.assertEquals( Arrays.asList( "?1" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-User" ) );
			Assert.assertEquals( Arrays.asList( "navigate" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Mode" ) );
			Assert.assertEquals( Arrays.asList( "none" ), httpExchange.getRequestHeaders().get( "Sec-Fetch-Site" ) );
			Assert.assertEquals( Arrays.asList( "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9" ), httpExchange.getRequestHeaders().get( "Accept" ) );
			Assert.assertEquals( Arrays.asList( "JUnit Test" ), httpExchange.getRequestHeaders().get( "User-Agent" ) );
			Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "Upgrade-Insecure-Requests" ) );
			Assert.assertEquals( Arrays.asList( "1" ), httpExchange.getRequestHeaders().get( "DNT" ) );
			Assert.assertEquals( Arrays.asList( "keep-alive" ), httpExchange.getRequestHeaders().get( "Connection" ) );
			Assert.assertEquals( Arrays.asList( "localhost:8000" ), httpExchange.getRequestHeaders().get( "Host" ) );
			Assert.assertNotNull("First line should have been read", httpExchange.getFirstLine());
		}
	}
	
	@Test
	public void testMaxRequestSize() {
		int numBytes = 446;
		byte[] bytes = getRequest.getBytes(StandardCharsets.US_ASCII);
		Assert.assertEquals("Num bytes in request should be as expected", numBytes, bytes.length);

		byte[] almostCompleteRequest = new byte[numBytes - 1];
		System.arraycopy(bytes, 0, almostCompleteRequest, 0, numBytes - 1);

		HTTPExchange httpExchange = new HTTPExchange();
		httpExchange.setMaxRequestSizeInBytes(numBytes - 1);
		httpExchange.addBytes(almostCompleteRequest);

		byte[] strawThatBreaksTheCamelsBack = new byte[1];
		strawThatBreaksTheCamelsBack[0] = bytes[numBytes - 1];

		boolean exceptionThrown = false;
		try {
			httpExchange.addBytes(strawThatBreaksTheCamelsBack);
			Assert.assertFalse("Should never reach here due to exception being thrown", true);
		}
		catch (HTTPRequestTooLongException e) {
			exceptionThrown = true;
		}
		Assert.assertTrue("Exception thrown", exceptionThrown);
	}
}
