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
	public static final int DEFAULT_MAX_REQUEST_SIZE_IN_BYTES = 0x2000; // 8kb
	
	private int    maxRequestSizeInBytes = DEFAULT_MAX_REQUEST_SIZE_IN_BYTES;
	
	private SocketChannel clientSocketChannel;
	
	private String firstLine; // Line saying GET/POST etc, giving the URL, HTTP version, etc
	private String method;
	private String path;
	private String httpVersion;
	
	private StringBuilder		completeRequest		= new StringBuilder(1024);
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


	// Buffer for reading a single header element, and indices into that buffer
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
		
		// Check size
		int maxRequestSizeInBytes = getMaxRequestSizeInBytes();
		if ( completeRequest.length() + aByteArr.length > maxRequestSizeInBytes ) {
			completeRequest.append(new String(aByteArr, 0, maxRequestSizeInBytes - completeRequest.length(), StandardCharsets.US_ASCII));
			throw new HTTPRequestTooLongException(String.format("HTTP request exceeded max size [%,d B]", maxRequestSizeInBytes), completeRequest.toString());
		}
		
		// Check we can fit everything into buffer
		if ( currentHeaderElementBufferOffset + aByteArr.length >= currentHeaderElementBuffer.length ) {
			// Will never get too big, since we are checking max size of entire request
			byte[] newBuffer = new byte[ currentHeaderElementBuffer.length * 2 ];
			System.arraycopy(currentHeaderElementBuffer, 0, newBuffer, 0, currentHeaderElementBuffer.length);
			currentHeaderElementBuffer = newBuffer;
		}
		
		// Number of bytes already read into arg byte array, before starting the current header element or the body
		int offsetIntoNewByteArr = 0;
		if (! finishedReadingRequestHeaders) {
			byte prevByte = -1;
			if (currentHeaderElementBufferOffset > 0) {
				prevByte = currentHeaderElementBuffer[ currentHeaderElementBufferOffset - 1 ];
			}
			
			// Index of first occurrence of HEADER_NAME_VALUE_SEPARATOR, after offsetIntoNewByteArr, within arg byte array
			int nameValueSeparatorIndexWithinNewBytes = -1;
			
			// Index of first non-whitespace character after nameValueSeparatorIndexWithinNewBytes
			int startOfValueIndexWithinNewBytes = -1;
			
			// True if we have seen HEADER_NAME_VALUE_SEPARATOR within the current header element, and are waiting for the next non-whitespace character.
			boolean lookingForStartOfValue = currentHeaderElementNameValueSeparatorIndex >= 0 && currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator == -1;
			
			// Value of currentHeaderElementBufferOffset at the time we started reading the current header element
			int headerElementBufferOffsetBeforeStartingCurrentHeaderElementWithinNewBytes = currentHeaderElementBufferOffset;
			
			/* Process arg byte array one by one, putting header elements into currentHeaderElementBuffer
			 * and remembering the significant indices.
			 * 
			 * When we encounter HEADER_DELIMITER, convert the content of currentHeaderElementBuffer into two Strings,
			 * a name and a value, and continue.
			 */
			for (int newByteIndex = 0; newByteIndex < aByteArr.length; newByteIndex++) {
				byte newByte = aByteArr[newByteIndex];
				currentHeaderElementBuffer[ currentHeaderElementBufferOffset++ ] = newByte;
				completeRequest.append((char) newByte);

				if (newByte == HEADER_DELIMITER.charAt(1)) {
					// Might have reached a delimiter, let's check
					if (( newByteIndex > 0
							|| currentHeaderElementBufferOffset > 0)
							&& prevByte == HEADER_DELIMITER.charAt(0)) {
						// Reached delimiter, process header line

						if (currentHeaderElementNameValueSeparatorIndex == -1
								&& nameValueSeparatorIndexWithinNewBytes != -1 ) {
							// We found the index of HEADER_NAME_VALUE_SEPARATOR in the arg byte array, let's remember it
							currentHeaderElementNameValueSeparatorIndex = headerElementBufferOffsetBeforeStartingCurrentHeaderElementWithinNewBytes + nameValueSeparatorIndexWithinNewBytes - offsetIntoNewByteArr;
						}
						if (currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator == -1
								&& startOfValueIndexWithinNewBytes != -1 ) {
							// We found the index of the first non-whitespace character after the HEADER_NAME_VALUE_SEPARATOR in the arg byte array, let's remember it
							currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator = headerElementBufferOffsetBeforeStartingCurrentHeaderElementWithinNewBytes + startOfValueIndexWithinNewBytes - offsetIntoNewByteArr;
						}

						addRequestHeaderFromBytesReadSoFar(2);

						// Reset variables
						offsetIntoNewByteArr = newByteIndex + 1;
						nameValueSeparatorIndexWithinNewBytes = -1;
						startOfValueIndexWithinNewBytes = -1;
						headerElementBufferOffsetBeforeStartingCurrentHeaderElementWithinNewBytes = 0;

						if ( finishedReadingRequestHeaders ) {
							break;
						}
					}
				}
				else if (currentHeaderElementNameValueSeparatorIndex == -1
						&& nameValueSeparatorIndexWithinNewBytes == -1
						&& newByte == HEADER_NAME_VALUE_SEPARATOR ) {
					// Found HEADER_NAME_VALUE_SEPARATOR, let's remember its index, and remember that we're now looking for the next non-whitespace character.
					nameValueSeparatorIndexWithinNewBytes = newByteIndex;
					lookingForStartOfValue = true;
				}
				else if (lookingForStartOfValue
							&& ! Character.isWhitespace(newByte)) {
					// Found first non-whitespace character after HEADER_NAME_VALUE_SEPARATOR, let's remember its index
					startOfValueIndexWithinNewBytes = newByteIndex;
					lookingForStartOfValue = false;
				}

				prevByte = newByte;
			}
			
			if (currentHeaderElementNameValueSeparatorIndex == -1
					&& nameValueSeparatorIndexWithinNewBytes != -1 ) {
				// We found the index of HEADER_NAME_VALUE_SEPARATOR in the arg byte array, let's remember it
				currentHeaderElementNameValueSeparatorIndex = headerElementBufferOffsetBeforeStartingCurrentHeaderElementWithinNewBytes + nameValueSeparatorIndexWithinNewBytes - offsetIntoNewByteArr;
			}
			if (currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator == -1
					&& startOfValueIndexWithinNewBytes != -1 ) {
				// We found the index of the first non-whitespace character after the HEADER_NAME_VALUE_SEPARATOR in the arg byte array, let's remember it
				currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator = headerElementBufferOffsetBeforeStartingCurrentHeaderElementWithinNewBytes + startOfValueIndexWithinNewBytes - offsetIntoNewByteArr;
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

	public int getMaxRequestSizeInBytes() {
		return maxRequestSizeInBytes;
	}

	public void setMaxRequestSizeInBytes(int aMaxRequestSizeInBytes) {
		maxRequestSizeInBytes = aMaxRequestSizeInBytes;
	}
}
