package com.merman.celebrity.server;

import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;

public class HTTPExchange {
	private static final String HEADER_DELIMITER = "\r\n";
	private static final char	HEADER_NAME_VALUE_SEPARATOR = ':';
	
	private SocketChannel clientSocketChannel;
	
	private String firstLine; // Line saying GET/POST etc, giving the URL, HTTP version, etc
	private String method;
	private String path;
	private String httpVersion;
	
	private Map<String, List<String>> requestHeaders = new LinkedHashMap<>();
	private Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
	
	private boolean finishedReadingRequestHeaders;
	
	private String requestBody;
	
	/**
	 * In the end the response has to be a byte arr (could be binary data),
	 * but could be useful for debugging to initially set it as a string,
	 * when it is text data.
	 */
	private String responseBodyString;
	private byte[] responseBody;

	private int currentHeaderElementNameValueSeparatorIndex = -1;
	private int currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator = -1;
	private int currentHeaderElementBufferOffset = 0;
	private byte[] currentHeaderElementBuffer = new byte[1024];
	
	
	public HTTPExchange() {}

	public HTTPExchange(SocketChannel aClientSocketChannel) {
		clientSocketChannel = aClientSocketChannel;
	}

	public SocketChannel getClientSocketChannel() {
		return clientSocketChannel;
	}

	public void addBytes(byte[] aByteArr) {
		/* This method is complicated because we never know how many bytes of the HTTP request we'll receive
		 * (due to the non-blocking I/O), and we need to examine the bytes before we know we have a complete
		 * request (for example, to know when we reach the end of the header, or to parse the Content-Length
		 * field of the header so that we know how long of a body to expect).
		 * 
		 * Since we need to do some of that anyway, we parse the whole thing byte by byte, rather than using
		 * regex or similar.
		 */
		int offsetIntoNewByteArr = 0;
		if (! finishedReadingRequestHeaders) {
			byte prevByte = -1;
			if (currentHeaderElementBufferOffset > 0) {
				prevByte = currentHeaderElementBuffer[ currentHeaderElementBufferOffset - 1 ];
			}
			
			int nameValueSeparatorIndexWithinNewBytes = -1;
			int startOfValueIndexWithinNewBytes = -1;
			boolean lookingForStartOfValue = currentHeaderElementNameValueSeparatorIndex >= 0 && currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator == -1;
			int headerElementBufferOffsetBeforeReadingNewBytes = currentHeaderElementBufferOffset;
			
			for (int newByteIndex = 0; newByteIndex < aByteArr.length; newByteIndex++) {
				byte newByte = aByteArr[newByteIndex];
				currentHeaderElementBuffer[ currentHeaderElementBufferOffset++ ] = newByte; // TODO make sure we can't exceed size

				if (newByte == HEADER_DELIMITER.charAt(1)) {
					// Might have reached a delimiter, let's check
					if (( newByteIndex > 0
							|| currentHeaderElementBufferOffset > 0)
							&& prevByte == HEADER_DELIMITER.charAt(0)) {
						// Reached delimiter, process header line

						if (currentHeaderElementNameValueSeparatorIndex == -1
								&& nameValueSeparatorIndexWithinNewBytes != -1 ) {
							currentHeaderElementNameValueSeparatorIndex = headerElementBufferOffsetBeforeReadingNewBytes + nameValueSeparatorIndexWithinNewBytes - offsetIntoNewByteArr;
						}
						if (currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator == -1
								&& startOfValueIndexWithinNewBytes != -1 ) {
							currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator = headerElementBufferOffsetBeforeReadingNewBytes + startOfValueIndexWithinNewBytes - offsetIntoNewByteArr;
						}

						addRequestHeaderFromBytesReadSoFar(2);

						offsetIntoNewByteArr = newByteIndex + 1;
						nameValueSeparatorIndexWithinNewBytes = -1;
						startOfValueIndexWithinNewBytes = -1;
						headerElementBufferOffsetBeforeReadingNewBytes = 0; // TODO rename var

						if ( finishedReadingRequestHeaders ) {
							break;
						}
					}
				}
				else if (currentHeaderElementNameValueSeparatorIndex == -1
						&& nameValueSeparatorIndexWithinNewBytes == -1
						&& newByte == HEADER_NAME_VALUE_SEPARATOR ) {
					nameValueSeparatorIndexWithinNewBytes = newByteIndex;
					lookingForStartOfValue = true;
				}
				else if (lookingForStartOfValue
							&& ! Character.isWhitespace(newByte)) {
					startOfValueIndexWithinNewBytes = newByteIndex;
					lookingForStartOfValue = false;
				}

				prevByte = newByte;
			}
			
			if (currentHeaderElementNameValueSeparatorIndex == -1
					&& nameValueSeparatorIndexWithinNewBytes != -1 ) {
				currentHeaderElementNameValueSeparatorIndex = headerElementBufferOffsetBeforeReadingNewBytes + nameValueSeparatorIndexWithinNewBytes - offsetIntoNewByteArr;
			}
			if (currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator == -1
					&& startOfValueIndexWithinNewBytes != -1 ) {
				currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator = headerElementBufferOffsetBeforeReadingNewBytes + startOfValueIndexWithinNewBytes - offsetIntoNewByteArr;
			}
		}
	}
	
	private void addRequestHeaderFromBytesReadSoFar(int aNumCharsToIgnoreAtEnd) {
		if (firstLine == null) {
			firstLine = new String(currentHeaderElementBuffer, 0, currentHeaderElementBufferOffset - aNumCharsToIgnoreAtEnd, StandardCharsets.US_ASCII);
			processFirstLine(firstLine);
		}
		else if (currentHeaderElementNameValueSeparatorIndex > 0) {
			String headerName = new String(currentHeaderElementBuffer, 0, currentHeaderElementNameValueSeparatorIndex, StandardCharsets.US_ASCII);
			String headerValue = "";
			if (currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator > currentHeaderElementNameValueSeparatorIndex) {
				headerValue = new String(currentHeaderElementBuffer, currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator, currentHeaderElementBufferOffset - aNumCharsToIgnoreAtEnd - currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator, StandardCharsets.US_ASCII);
			}
			
			requestHeaders.computeIfAbsent(headerName, s -> new ArrayList<>()).add(headerValue);
			Log.log(LogMessageType.INFO, LogMessageSubject.HTTP_REQUESTS, "Put header name", headerName, "value", headerValue);
		}
		else {
			if (currentHeaderElementBufferOffset == aNumCharsToIgnoreAtEnd) {
				// Empty line, we've reached end of header
				finishedReadingRequestHeaders = true;
			}
			else {
				String completeString = new String(currentHeaderElementBuffer, 0, currentHeaderElementBufferOffset - aNumCharsToIgnoreAtEnd, StandardCharsets.US_ASCII);
				Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "Element of HTTP header with no name-value separator, ignoring", completeString);
			}
		}

		currentHeaderElementNameValueSeparatorIndex = -1;
		currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator = -1;
		currentHeaderElementBufferOffset = 0;
	}

	private void processFirstLine(String aFirstLine) {
		Log.log(LogMessageType.INFO, LogMessageSubject.HTTP_REQUESTS, "First Line", aFirstLine);
		String[] componentArray = aFirstLine.split(" "); // can't be bothered going byte by byte any more
		if ( componentArray.length < 3 ) {
			throw new IllegalArgumentException("Not a valid first line of an HTTP request: " + aFirstLine);
		}
		method = componentArray[0];
		path = componentArray[1];
		httpVersion = componentArray[2];
	}

	public boolean isFinishedReadingRequestHeaders() {
		return finishedReadingRequestHeaders;
	}

	public Map<String, List<String>> getRequestHeaders() {
		return requestHeaders;
	}

	public String getFirstLine() {
		return firstLine;
	}
}
