package com.merman.celebrity.server.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

public abstract class AHttpHandler implements IContextHandler {

	@Override
	public void handle(HttpExchange aHttpExchange) throws IOException {
		try {
			String sessionID = HttpExchangeUtil.getSessionID(aHttpExchange);
			Session session = null;
			if ( sessionID != null ) {
				session = SessionManager.getSession(sessionID);
			}
			Map<String,Object> requestBodyAsMap = HttpExchangeUtil.getRequestBodyAsMap(aHttpExchange);
			_handle( session, requestBodyAsMap, aHttpExchange );
		}
		catch (RuntimeException e) {
			e.printStackTrace();
		}
	}

	protected abstract void _handle(Session aSession, Map<String, Object> aRequestBodyAsMap, HttpExchange aHttpExchange) throws IOException;
	
	protected void sendResponse( HttpExchange aExchange, int aCode, String aResponse ) throws IOException {
		aExchange.sendResponseHeaders(aCode, aResponse.getBytes().length);
		OutputStream os = aExchange.getResponseBody();
		os.write(aResponse.getBytes());
		os.close();
	}
	
	protected void dumpRequest(HttpExchange aExchange) {
		Headers requestHeaders = aExchange.getRequestHeaders();
		System.out.println( "Received request: " + getContextName() );
		for ( Entry<String, List<String>> l_mapEntry : requestHeaders.entrySet() ) {
			System.out.format("%s:\t%s\n", l_mapEntry.getKey(), String.join( ",", l_mapEntry.getValue() ));
		}
		System.out.println("\n");
		System.out.println("Request body:");
		System.out.println( String.join("\n", HttpExchangeUtil.getRequestBody(aExchange) ) );
		System.out.println( "End request body\n\n" );
	}
}
