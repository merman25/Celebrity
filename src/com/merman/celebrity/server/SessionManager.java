package com.merman.celebrity.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.merman.celebrity.server.cleanup.CleanupHelper;
import com.merman.celebrity.server.cleanup.IExpiredEntityRemover;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;

public class SessionManager {
	private static Map<String, Session>					sessionsMap		= new HashMap<>();
	private static Map<Session, WebsocketHandler>		socketsMap		= new HashMap<>();
	
	private static class MyExpiredSessionRemover
	implements IExpiredEntityRemover<Session> {
		@Override
		public void remove(Session aSession) {
			synchronized (SessionManager.class) {
				Log.log(LogMessageType.DEBUG, LogMessageSubject.SESSIONS, "Removing session", aSession);
				sessionsMap.remove(aSession.getSessionID());
				WebsocketHandler websocketHandler = socketsMap.get(aSession);
				if (websocketHandler != null) {
					socketsMap.remove(aSession);
					websocketHandler.stop();
				}
			}
		}
	}
	
	private static class MySessionIterable
	implements Iterable<Session> {

		@Override
		public Iterator<Session> iterator() {
			synchronized (SessionManager.class) {
				return new ArrayList(sessionsMap.values()).iterator();
			}
		}
	}
	
	static {
		CleanupHelper.registerForRegularCleanup(new MySessionIterable(), new MyExpiredSessionRemover());
	}
	
	public static synchronized Session createSession() {
		String sessionID = UUID.randomUUID().toString();
		Session session = new Session(sessionID);
		sessionsMap.put(sessionID, session);
		
		return session;
	}

	public static synchronized Session getSession(String aSessionID) {
		return sessionsMap.get(aSessionID);
	}

	public static synchronized void putSocket(Session aSession, WebsocketHandler aWebsocketHandler) {
		WebsocketHandler oldWebsocketHandler = socketsMap.get(aSession);
		if (oldWebsocketHandler != null) {
			oldWebsocketHandler.stop();
		}
		socketsMap.put(aSession, aWebsocketHandler);
	}
	
	public static synchronized WebsocketHandler getWebsocketHandler(Session aSession) {
		return socketsMap.get(aSession);
	}
	
	public static synchronized int getNumSessions() {
		return sessionsMap.size();
	}
}
