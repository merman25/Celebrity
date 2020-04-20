package com.merman.celebrity.server.handlers;

import java.io.IOException;
import java.util.Map;

import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.sun.net.httpserver.HttpExchange;

public abstract class AHttpHandler2 implements IContextHandler {

	@Override
	public void handle(HttpExchange aHttpExchange) throws IOException {
		String sessionID = HttpExchangeUtil.getSessionID(aHttpExchange);
		if ( sessionID != null ) {
			Session session = SessionManager.getSession(sessionID);
			if ( session != null ) {
				Map<String,String> requestBodyAsMap = HttpExchangeUtil.getRequestBodyAsMap(aHttpExchange);
				_handle( session, requestBodyAsMap, aHttpExchange );
			}
		}
	}

	protected abstract void _handle(Session aSession, Map<String, String> aRequestBodyAsMap, HttpExchange aHttpExchange) throws IOException;
}
