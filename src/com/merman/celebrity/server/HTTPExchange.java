package com.merman.celebrity.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import com.merman.celebrity.server.exceptions.HTTPException;
import com.merman.celebrity.server.exceptions.HTTPRequestTooLongException;
import com.merman.celebrity.server.exceptions.UnknownHTTPMethodException;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;

public class HTTPExchange {
	private static final String                        HEADER_DELIMITER                                              = "\r\n";
	private static final char                          HEADER_NAME_VALUE_SEPARATOR                                   = ':';
	public static final int                            DEFAULT_MAX_REQUEST_SIZE_IN_BYTES                             = 0x2000;                                                             // 8kb
	private static final String                        HTTP_PROTOCOL                                                 = "HTTP/1.1";
	private static final String                        DATE_FORMAT_STRING                                            = "EEE, dd MMM yyyy HH:mm:ss z";


	private static final ThreadLocal<SimpleDateFormat> THREAD_LOCAL_DATE_FORMAT                                      = new ThreadLocal<SimpleDateFormat>() {

																															@Override
																															protected SimpleDateFormat initialValue() {
																																return new SimpleDateFormat(DATE_FORMAT_STRING, Locale.US);
																															}
																														};
																														
	private static final int                           MAX_HTTP_METHOD_NAME_LENGTH									 = Arrays.asList(HTTPMethod.values()).stream()
																															.mapToInt(method -> method.toString().length())
																															.reduce(0, Math::max);
	
	private int                                        maxRequestSizeInBytes                                         = DEFAULT_MAX_REQUEST_SIZE_IN_BYTES;
	private SocketChannel                              clientSocketChannel;
	private HTTPChannelHandler                         channelHandler;
	private SelectionKey                               selectionKey;

	// Line saying GET/POST etc, giving the URL, HTTP version, etc
	private String                                     firstLine;
	private HTTPMethod                                 method;
	private URI                                        requestURI;
	private String                                     httpProtocolString;
	private int                                        indexOfFirstWhitespaceChar                                    = -1;
	private int                                        indexOfSecondWhitespaceChar                                   = -1;

	private StringBuilder                              completeRequest                                               = new StringBuilder(1024);
	private Map<String, List<String>>                  modifiableRequestHeaders                                      = new LinkedHashMap<>();
	private Map<String, List<String>>                  unmodifiableRequestHeaders;
	private Map<String, List<String>>                  responseHeaders                                               = new LinkedHashMap<>();
	private boolean                                    staleUnmodifiableRequestHeaders                               = true;

	private boolean                                    finishedReadingRequestHeaders;
	private boolean                                    finishedReadingRequestBody;
	private int                                        reportedRequestBodyLength                                     = -1;

	private StringBuilder                              requestBodyBuilder                                            = new StringBuilder();
	private String                                     requestBody;

	/**
	 * In the end the response has to be a byte arr (could be binary data), but
	 * could be useful for debugging to initially set it as a string, when it is
	 * text data.
	 */
	private String                                     responseBodyString;
	private byte[]                                     responseBody                                                  = new byte[0];

	// Buffer for reading a single header element, and indices into that buffer
	private int                                        currentHeaderElementNameValueSeparatorIndex                   = -1;
	private int                                        currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator = -1;
	private int                                        currentHeaderElementBufferOffset                              = 0;
	private byte[]                                     currentHeaderElementBuffer                                    = new byte[1024];

	private HTTPResponseConstants                      responseCode                                                  = HTTPResponseConstants.Not_Found_404;
	private int                                        responseContentLength                                         = -1;
	
	
	public HTTPExchange() {}

	public HTTPExchange(SocketChannel aClientSocketChannel, HTTPChannelHandler aHttpChannelHandler, SelectionKey aKey) {
		clientSocketChannel = aClientSocketChannel;
		channelHandler = aHttpChannelHandler;
		selectionKey = aKey;
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
				
				// Check for HTTP method
				if (indexOfFirstWhitespaceChar == -1) {
					if (currentHeaderElementBufferOffset > MAX_HTTP_METHOD_NAME_LENGTH + 1) {
						throw new UnknownHTTPMethodException("Unknown method (starts with [" + new String(currentHeaderElementBuffer, 0, currentHeaderElementBufferOffset - 1, StandardCharsets.US_ASCII) + "])");
					}
					else if (Character.isWhitespace(newByte)) {
						indexOfFirstWhitespaceChar = currentHeaderElementBufferOffset - 1;
						String methodName = new String(currentHeaderElementBuffer, 0, indexOfFirstWhitespaceChar, StandardCharsets.US_ASCII);
						try {
							method = HTTPMethod.valueOf(methodName);
						}
						catch (IllegalArgumentException e) {
							throw new UnknownHTTPMethodException("Unknown method [" + methodName + "]");
						}
					}
				}
				// Check for URI
				else if (indexOfSecondWhitespaceChar == -1) {
					if (Character.isWhitespace(newByte)) {
						indexOfSecondWhitespaceChar = currentHeaderElementBufferOffset - 1;
						String requestURIString = new String(currentHeaderElementBuffer, indexOfFirstWhitespaceChar + 1, indexOfSecondWhitespaceChar - indexOfFirstWhitespaceChar - 1, StandardCharsets.US_ASCII);
						try {
							requestURI = new URI(requestURIString);
						}
						catch (URISyntaxException e) {
							throw new HTTPException("URISyntax exception for string [" + requestURIString + "]", e);
						}
					}
				}

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

						addRequestHeaderFromBytesReadSoFar(HEADER_DELIMITER.length());

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
		
		if ( finishedReadingRequestHeaders ) {
			if ( getReportedRequestBodyLength() > 0) {
				for(int newByteIndex = offsetIntoNewByteArr; newByteIndex < aByteArr.length && requestBodyBuilder.length() < getReportedRequestBodyLength(); newByteIndex++ ) {
					requestBodyBuilder.append((char) aByteArr[newByteIndex]);
				}

				if ( requestBodyBuilder.length() >= getReportedRequestBodyLength() ) {
					requestBody = requestBodyBuilder.toString();
					finishedReadingRequestBody = true;
				}
			}
			else {
				requestBody = "";
				finishedReadingRequestBody = true;
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
			
			modifiableRequestHeaders.computeIfAbsent(headerName, s -> new ArrayList<>()).add(headerValue);
			
			if (headerName.equalsIgnoreCase("content-length")) {
				try {
					reportedRequestBodyLength = Integer.parseInt(headerValue);
				}
				catch (NumberFormatException e) {
					Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "Cannot parse content-length string", headerValue);
				}
			}
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

		staleUnmodifiableRequestHeaders = true;
		currentHeaderElementNameValueSeparatorIndex = -1;
		currentHeaderIndexOfFirstNonWhiteSpaceCharacterAfterSeparator = -1;
		currentHeaderElementBufferOffset = 0;
	}

	private void processFirstLine(String aFirstLine) {
		if (indexOfSecondWhitespaceChar >= 0) {
			httpProtocolString = new String(currentHeaderElementBuffer, indexOfSecondWhitespaceChar + 1, currentHeaderElementBufferOffset - indexOfSecondWhitespaceChar - HEADER_DELIMITER.length() - 1, StandardCharsets.US_ASCII);
			if (! HTTP_PROTOCOL.equals(httpProtocolString)) {
				throw new HTTPException("Unknown protocol [" + httpProtocolString + "]");
			}
		}
		else {
			throw new HTTPException("Invalid first line [" + aFirstLine + "]");
		}
	}

	public boolean isFinishedReadingRequestHeaders() {
		return finishedReadingRequestHeaders;
	}

	public Map<String, List<String>> getRequestHeaders() {
		// If this is called repeatedly before we've finished reading the headers, it will create
		// a lot of objects, which is a bit inefficient. But this only happens during the junit test.
		if (staleUnmodifiableRequestHeaders) {
			LinkedHashMap<String, List<String>> caseInsenitiveMap = new LinkedHashMap<String, List<String>>() {
				@Override
				public List<String> get(Object aKey) {
					return super.get(((String) aKey).toLowerCase(Locale.US));
				}

				@Override
				public List<String> put(String aKey, List<String> aValue) {
					return super.put(aKey.toLowerCase(Locale.US), aValue);
				}
				
			};
			
			for (Entry<String, List<String>> mapEntry : modifiableRequestHeaders.entrySet()) {
				// putAll does not use the overridden put method
				caseInsenitiveMap.put(mapEntry.getKey(), mapEntry.getValue());
			}
			unmodifiableRequestHeaders = Collections.unmodifiableMap(caseInsenitiveMap);
			
			staleUnmodifiableRequestHeaders = false;
		}
		return unmodifiableRequestHeaders;
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

	public int getReportedRequestBodyLength() {
		return reportedRequestBodyLength;
	}

	public boolean isFinishedReadingRequestBody() {
		return finishedReadingRequestBody;
	}

	public String getRequestBody() {
		return requestBody;
	}

	public InetSocketAddress getRemoteAddress() throws IOException {
		return (InetSocketAddress) clientSocketChannel.getRemoteAddress();
	}

	public Map<String, List<String>> getResponseHeaders() {
		return responseHeaders;
	}

	public void setResponseHeaders(HTTPResponseConstants aResponseConstant, int aContentLength) {
		responseCode = aResponseConstant;
		responseContentLength = aContentLength;
		
		if (aContentLength >= 0) {
			getResponseHeaders().put("content-length", Arrays.asList(String.valueOf(aContentLength)));
		}
	}

	public void setResponseBody(String aResponse, byte[] aResponseBytes) {
		responseBodyString = aResponse;
		responseBody = aResponseBytes;
	}
	
	public void setResponseBody(byte[] aResponseBytes) {
		responseBody = aResponseBytes;
	}
	
	public String getCompleteRequest() {
		return completeRequest.toString();
	}

	public URI getRequestURI() {
		return requestURI;
	}

	public void sendResponse() throws IOException {
		getResponseHeaders().put("date", Arrays.asList( THREAD_LOCAL_DATE_FORMAT.get().format(new Date()) ));
		
		int sizeAllocationPerHeader = 100; // should be enough, if not StringBuilder will just expand
		StringBuilder headerBuilder = new StringBuilder(sizeAllocationPerHeader * getResponseHeaders().size());
		
		headerBuilder.append(HTTP_PROTOCOL);
		headerBuilder.append(' ');
		headerBuilder.append(responseCode.getCodePlusTextResponse());
		headerBuilder.append(HEADER_DELIMITER);
		getResponseHeaders().entrySet().stream()
							.forEach( mapEntry -> {
								String headerName = mapEntry.getKey();
								mapEntry.getValue().stream()
										.forEach(headerValue -> {
											headerBuilder.append(headerName);
											headerBuilder.append(HEADER_NAME_VALUE_SEPARATOR);
											headerBuilder.append(' ');
											headerBuilder.append(headerValue);
											headerBuilder.append(HEADER_DELIMITER);
								});
							});
		
		headerBuilder.append(HEADER_DELIMITER);
		
		String header = headerBuilder.toString();
		byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
		
		byte[] responseBytes = new byte[headerBytes.length + responseBody.length];
		
		System.arraycopy(headerBytes, 0, responseBytes, 0, headerBytes.length);
		System.arraycopy(responseBody, 0, responseBytes, headerBytes.length, responseBody.length);
		
		ByteBuffer buffer = ByteBuffer.allocate(responseBytes.length); // TODO don't allocate new buffers all the time
		buffer.put(responseBytes);
		buffer.flip();
		clientSocketChannel.write(buffer);
	}

	public void close() {
		channelHandler.remove(selectionKey);
	}

	public HTTPMethod getMethod() {
		return method;
	}

	public String getHTTPProtocolString() {
		return httpProtocolString;
	}
}
