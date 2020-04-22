package com.merman.celebrity.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {
	private static Map<String, Session>					sessionsMap		= new HashMap<>();
	private static Map<Session, WebsocketHandler>		socketsMap		= new HashMap<>();
	
	public static Session createSession() {
		String sessionID = UUID.randomUUID().toString();
		Session session = new Session(sessionID);
		sessionsMap.put(sessionID, session);
		
		return session;
	}

	public static Session getSession(String aSessionID) {
		return sessionsMap.get(aSessionID);
	}

	public static void putSocket(Session aSession, WebsocketHandler aWebsocketHandler) {
		socketsMap.put(aSession, aWebsocketHandler);
	}
	
	public static WebsocketHandler getWebsocketHandler(Session aSession) {
		return socketsMap.get(aSession);
	}
}
