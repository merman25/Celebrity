package com.merman.celebrity.server.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import org.json.JSONObject;

import com.merman.celebrity.server.CelebrityMain;
import com.merman.celebrity.server.HTTPExchangeWrapper;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.sun.net.httpserver.Headers;

public class HttpExchangeUtil {
	public static final String COOKIE_RESTORE_KEY = "restore";
	public static final String COOKIE_RESTORE_VALUE = "true";
	
	private static WeakHashMap<HTTPExchangeWrapper, String> requestBodyCache		= new WeakHashMap<>();
	
	public static synchronized String getRequestBody(HTTPExchangeWrapper aExchangeWrapper) {
		String requestBody = requestBodyCache.get(aExchangeWrapper);
		if ( requestBody == null ) {
			int bufferSize = 1024 * 1024;
			String reportedContentLengthString = HttpExchangeUtil.getHeaderValue("Content-Length", aExchangeWrapper);
			if ( reportedContentLengthString != null ) {
				try {
					int reportedContentLength = Integer.parseInt(reportedContentLengthString);
					if (reportedContentLength > 0) {
						bufferSize = reportedContentLength;
					}
				}
				catch (NumberFormatException e) {
					Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "Content length cannot be parsed as int", reportedContentLengthString);
				}
			}
			try {
				InputStream inputStream = aExchangeWrapper.getRequestBody();
				if (inputStream != null) {
					int totalBytesRead = 0;
					byte[] buffer = new byte[bufferSize];
					for (int bytesRead = 0;
							( bytesRead = inputStream.read(buffer, totalBytesRead, buffer.length - totalBytesRead ) ) != -1; ) {
						totalBytesRead += bytesRead;
						if (totalBytesRead == buffer.length) {
							byte[] newBuffer = new byte[2 * buffer.length];
							System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
							buffer = newBuffer;
						}
					}
					requestBody = new String(buffer, 0, totalBytesRead, StandardCharsets.UTF_8);
				}
				else {
					requestBody = aExchangeWrapper.getRequestBodyString();
				}

			}
			catch ( IOException e ) {
				Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "IOException reading HTTP request body", e);
			}

			requestBodyCache.put(aExchangeWrapper, requestBody);
		}

		return requestBody;
	}
	
	public static LinkedHashMap<String, Object> getRequestBodyAsMap( HTTPExchangeWrapper aExchangeWrapper ) {
		String requestBody = getRequestBody(aExchangeWrapper);
		return toMap(requestBody);
	}
	
	public static LinkedHashMap<String, Object>	toMap( String aJSONString ) {
		LinkedHashMap<String, Object>		map		= new LinkedHashMap<>();

		String jsonString = aJSONString == null ? null : aJSONString.trim();
		if ( jsonString != null
				&& jsonString.startsWith("{")
				&& jsonString.endsWith("}" ) ) {
			JSONObject jsonObject = new JSONObject(aJSONString);
			String[] names = JSONObject.getNames(jsonObject);
			if (names != null) {
				// Can be null if jsonString is just "{}"
				for (String name : names) {
					map.put(name, jsonObject.get(name));
				}
			}
		}
		else if (aJSONString != null
				&& ! aJSONString.isEmpty() ) {
			throw new InvalidJSONException("Invalid JSON: " + aJSONString);
		}
		
		return map;
	}
		

	public static String getSessionID(HTTPExchangeWrapper aExchangeWrapper) {
		LinkedHashMap<String, String>		cookie		= getCookie( aExchangeWrapper );
		return cookie.get("session");
	}

	public static LinkedHashMap<String, String> getCookie(HTTPExchangeWrapper aExchangeWrapper) {
		LinkedHashMap<String, String>		cookie		= new LinkedHashMap<>();
		List<String> cookieElementList = aExchangeWrapper.getRequestHeaders().get("Cookie");
		if (cookieElementList != null) {
			for ( String cookieElement : cookieElementList ) {

				/* When testing with Cypress, it was the first time there were 2 cookies
				 * set instead of just 1 (the session, and also something set by Cypress).
				 * 
				 * For some reason, it came out as a list containing a string which was
				 * a semi-colon-separated list of cookies, instead of having each name-value
				 * pair as an element of the list. Something wrong there, but anyway we can
				 * still parse it.
				 */
				parseCookie(cookieElement, cookie);
			}
		}
		return cookie;
	}

	public static void parseCookie(String aCookieString, Map<String, String> aCookieSFCT) {
		List<String> cookieElementElementList = Arrays.asList( aCookieString.split("; *") );
		for ( String cookieElementElement: cookieElementElementList ) {
			int indexOfEquals = cookieElementElement.indexOf('=');
			if ( indexOfEquals >= 0
					&& indexOfEquals < cookieElementElement.length() - 1 ) {
				aCookieSFCT.put(cookieElementElement.substring(0, indexOfEquals), cookieElementElement.substring(indexOfEquals + 1));
			}
		}
	}
	
	public static void logBytesReceived(HTTPExchangeWrapper aExchangeWrapper) {
		int headerBytesReceived = 0;
		for ( Entry<String, List<String>> l_mapEntry : aExchangeWrapper.getRequestHeaders().entrySet() ) {
			headerBytesReceived += l_mapEntry.getKey().getBytes(StandardCharsets.UTF_8).length;
			List<String> l_headerValue = l_mapEntry.getValue();
			for (String string : l_headerValue) {
				headerBytesReceived += string.getBytes(StandardCharsets.UTF_8).length;
			}
		}

		String requestBody = getRequestBody(aExchangeWrapper);
		if (requestBody != null) {
			int bodyBytesReceived = requestBody.getBytes(StandardCharsets.UTF_8).length;

			CelebrityMain.bytesReceived.accumulateAndGet(headerBytesReceived + bodyBytesReceived, Long::sum);
		}
	}
	
	public static void logBytesSent(HTTPExchangeWrapper aExchangeWrapper, int aBodyLength) {
		long headerBytesSent = 0;
		for ( Entry<String, List<String>> l_mapEntry : aExchangeWrapper.getResponseHeaders().entrySet() ) {
			headerBytesSent += l_mapEntry.getKey().getBytes(StandardCharsets.UTF_8).length;
			List<String> l_headerValue = l_mapEntry.getValue();
			for (String string : l_headerValue) {
				headerBytesSent += string.getBytes(StandardCharsets.UTF_8).length;
			}
		}

		CelebrityMain.bytesSent.accumulateAndGet(headerBytesSent + aBodyLength, Long::sum);
	}

	public static void setCookieResponseHeader(Session aSession, HTTPExchangeWrapper aHttpExchangeWrapper) {
		String cookieString = String.format("session=%s; Max-Age=%s", aSession.getSessionID(), aSession.getExpiryTime().getDurationToExpirySeconds());
		aHttpExchangeWrapper.getResponseHeaders().computeIfAbsent("Set-Cookie", s -> new ArrayList<>()).add(cookieString);
	}

	/**
	 * Returns the first element of the list
	 * <pre>aExchange.getRequestHeaders().get( aHeaderName )</pre> if it exists.
	 * <p>
	 * {@link Headers#get(Object)} returns a <code>List&lt;String&gt;</code>, but in most common cases,
	 * there's only one element, containing the header value we want. So this is a convenience method
	 * to use in the typical case.
	 * @param aHeaderName The name of an HTTP header.
	 * @param aExchangeWrapper The current {@link HTTPExchangeWrapper}.
	 * @return The first element of the list returned by querying for this header name. If the list is null or empty, the method returns <code>null</code>.
	 */
	public static String getHeaderValue(String aHeaderName, HTTPExchangeWrapper aExchangeWrapper) {
		List<String> headerValueList = aExchangeWrapper.getRequestHeaders().get( aHeaderName );
		String headerValue = headerValueList != null && ! headerValueList.isEmpty() ? headerValueList.get( 0 ) : null;
		return headerValue;
	}
}
