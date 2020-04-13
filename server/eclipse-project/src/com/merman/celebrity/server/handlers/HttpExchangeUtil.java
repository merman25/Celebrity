package com.merman.celebrity.server.handlers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.WeakHashMap;

import com.sun.net.httpserver.HttpExchange;

public class HttpExchangeUtil {
	private static WeakHashMap<HttpExchange, List<String>> requestBodyCache		= new WeakHashMap<>();
	
	public static synchronized List<String> getRequestBody(HttpExchange aExchange) {
		List<String> requestBody = requestBodyCache.get(aExchange);
		if ( requestBody == null ) {
			requestBody		= new ArrayList<>();

			try ( BufferedReader in = new BufferedReader( new InputStreamReader(aExchange.getRequestBody()))) {
				String line;
				while ( ( line = in.readLine() ) != null ) {
					requestBody.add( line );
				}
			}
			catch ( IOException e ) {
				e.printStackTrace();
			}
			
			requestBodyCache.put(aExchange, requestBody);
		}
		
		return requestBody;
	}
	
	public static LinkedHashMap<String, String> getRequestBodyAsMap( HttpExchange aExchange ) {
		String firstLine = "";
		List<String> requestBody = getRequestBody(aExchange);
		if ( ! requestBody.isEmpty() ) {
			firstLine = requestBody.get(0);
		}
		return toMap(firstLine);
	}
	
	public static LinkedHashMap<String, String>	toMap( String aKeyValuePairStringSeparatedByAmpersand ) {
		LinkedHashMap<String, String>		map		= new LinkedHashMap<>();

		String[] splitOnAmpersand = aKeyValuePairStringSeparatedByAmpersand.split("&");
		for (int i = 0; i < splitOnAmpersand.length; i++) {
			String element = splitOnAmpersand[i];
			int indexOfEquals = element.indexOf('=');
			if ( indexOfEquals > 0
					&& indexOfEquals < element.length() - 1 ) {
				map.put(element.substring(0, indexOfEquals), element.substring(indexOfEquals + 1 ) );
			}
		}
		
		return map;
	}
		

	public static String getSessionID(HttpExchange aExchange) {
		LinkedHashMap<String, String>		cookie		= getCookie( aExchange );
		return cookie.get("session");
	}

	public static LinkedHashMap<String, String> getCookie(HttpExchange aExchange) {
		LinkedHashMap<String, String>		cookie		= new LinkedHashMap<>();
		List<String> cookieElementList = aExchange.getRequestHeaders().get("Cookie");
		for ( String cookieElement : cookieElementList ) {
			int indexOfEquals = cookieElement.indexOf('=');
			if ( indexOfEquals >= 0
					&& indexOfEquals < cookieElement.length() - 1 ) {
				cookie.put(cookieElement.substring(0, indexOfEquals), cookieElement.substring(indexOfEquals + 1));
			}
		}
		return cookie;
	}
}
