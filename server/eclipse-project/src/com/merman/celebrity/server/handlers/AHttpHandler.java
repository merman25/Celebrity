package com.merman.celebrity.server.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.merman.celebrity.game.Player;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.info.LogInfo;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

public abstract class AHttpHandler implements IContextHandler {

	@Override
	public void handle(HttpExchange aHttpExchange) throws IOException {
		Session session = null;
		try {
//			dumpRequest(aHttpExchange);
			
			HttpExchangeUtil.logBytesReceived(aHttpExchange);
			String sessionID = HttpExchangeUtil.getSessionID(aHttpExchange);
			session = null;
			if ( sessionID != null ) {
				session = SessionManager.getSession(sessionID);
				
				if (session != null) {
					session.resetExpiryTime();
					Player player = session.getPlayer();
					if (player != null)
						player.resetExpiryTime();
				}

			}
			Map<String,Object> requestBodyAsMap = HttpExchangeUtil.getRequestBodyAsMap(aHttpExchange);
			_handle( session, requestBodyAsMap, aHttpExchange );
		}
		catch (RuntimeException e) {
			Player player = session == null ? null : session.getPlayer();
			Log.log(LogInfo.class, "Session", session, "Player", player, "Handler", getContextName(), "Exception on HTTP request", e);
		}
	}

	protected abstract void _handle(Session aSession, Map<String, Object> aRequestBodyAsMap, HttpExchange aHttpExchange) throws IOException;
	
	protected void sendResponse( HttpExchange aExchange, int aCode, String aResponse ) throws IOException {
		int bodyLength = aResponse.getBytes().length;
		aExchange.sendResponseHeaders(aCode, bodyLength);
		OutputStream os = aExchange.getResponseBody();
		os.write(aResponse.getBytes());
		os.close();
		
		HttpExchangeUtil.logBytesSent(aExchange, bodyLength);
	}

	protected void dumpRequest(HttpExchange aExchange) {
		Headers requestHeaders = aExchange.getRequestHeaders();
		System.out.println( "Received request: " + getContextName() );
		System.out.format("Request URI: %s\n", aExchange.getRequestURI() );
		System.out.format("Path: %s\n", aExchange.getHttpContext().getPath() );
		System.out.format("From: %s\n", aExchange.getRemoteAddress().getAddress() );
		for ( Entry<String, List<String>> l_mapEntry : requestHeaders.entrySet() ) {
			System.out.format("%s:\t%s\n", l_mapEntry.getKey(), String.join( ",", l_mapEntry.getValue() ));
		}
		System.out.println("\n");
		System.out.println("Request body:");
		System.out.println( String.join("\n", HttpExchangeUtil.getRequestBody(aExchange) ) );
		System.out.println( "End request body\n\n" );
	}
}
