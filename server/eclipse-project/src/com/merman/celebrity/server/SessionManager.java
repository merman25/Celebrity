package com.merman.celebrity.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.merman.celebrity.server.cleanup.CleanupHelper;
import com.merman.celebrity.server.cleanup.IExpiredEntityRemover;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.info.SessionLogInfo;

public class SessionManager {
	private static Map<String, Session>					sessionsMap		= new HashMap<>();
	private static Map<Session, WebsocketHandler>		socketsMap		= new HashMap<>();
	
	private static class MyExpiredSessionRemover
	implements IExpiredEntityRemover<Session> {
		@Override
		public void remove(Session aSession) {
			synchronized (SessionManager.class) {
				Log.log(SessionLogInfo.class, "Removing session", aSession);
				sessionsMap.remove(aSession.getSessionID());
				WebsocketHandler websocketHandler = socketsMap.get(aSession);
				if (websocketHandler != null) {
					socketsMap.remove(aSession);
					try {
						websocketHandler.stop();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	private static class MySessionIterable
	implements Iterable<Session> {

		@Override
		public Iterator<Session> iterator() {
			return new ArrayList(sessionsMap.values()).iterator();
		}
	}
	
	static {
		CleanupHelper.registerForRegularCleanup(new MySessionIterable(), new MyExpiredSessionRemover());
	}
	
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
	
	public static int getNumSessions() {
		return sessionsMap.size();
	}
}
