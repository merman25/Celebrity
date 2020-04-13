package com.merman.celebrity.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {
	private static Map<String, Session>		sessionsMap		= new HashMap<String, Session>();
	
	public static Session createSession() {
		String sessionID = UUID.randomUUID().toString();
		Session session = new Session(sessionID);
		sessionsMap.put(sessionID, session);
		
		return session;
	}

	public static Session getSession(String aSessionID) {
		return sessionsMap.get(aSessionID);
	}
}
