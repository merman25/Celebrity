package com.merman.celebrity.server.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map.Entry;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public abstract class AHttpHandler implements HttpHandler {
	private List<String> requestBody;
	private boolean responded;

	@Override
	public void handle(HttpExchange aExchange) throws IOException {
		requestBody = null;
		_handle( aExchange );
	}
	
	protected abstract void _handle(HttpExchange aExchange) throws IOException;
	
	protected void dumpRequest(HttpExchange aExchange) {
		Headers requestHeaders = aExchange.getRequestHeaders();
		System.out.println( "Received request: " + getHandlerName() );
		for ( Entry<String, List<String>> l_mapEntry : requestHeaders.entrySet() ) {
			System.out.format("%s:\t%s\n", l_mapEntry.getKey(), String.join( ",", l_mapEntry.getValue() ));
		}
		System.out.println("\n");
		System.out.println("Request body:");
		System.out.println( String.join("\n", getRequestBody(aExchange) ) );
		System.out.println( "End request body\n\n" );
	}

	protected synchronized List<String> getRequestBody(HttpExchange aExchange) {
		if ( requestBody != null ) {
			return requestBody;
		}
		
		requestBody		= HttpExchangeUtil.getRequestBody(aExchange);
		return requestBody;
	}
	
	public abstract String getHandlerName();
	
	public void sendResponse( HttpExchange aExchange, int aCode, String aResponse ) throws IOException {
		aExchange.sendResponseHeaders(aCode, aResponse.getBytes().length);
		OutputStream os = aExchange.getResponseBody();
		os.write(aResponse.getBytes());
		os.close();
		setResponded(true);
	}

	public boolean hasResponded() {
		return responded;
	}

	public void setResponded(boolean aResponded) {
		responded = aResponded;
	}

	
}
