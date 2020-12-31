package com.merman.celebrity.game;

import com.merman.celebrity.server.cleanup.CleanupHelper;
import com.merman.celebrity.server.cleanup.ExpiryTime;
import com.merman.celebrity.server.cleanup.ICanExpire;

public class Player implements ICanExpire {
	private String name;
	private Game game;
	private String sessionID;
	private int publicUniqueID;
	private String icon;
	
	private boolean                   expired;
	private ExpiryTime                expiryTime 				  = new ExpiryTime(CleanupHelper.defaultExpiryDurationInS);

	Player() {
	}

	public String getName() {
		return name;
	}

	public void setName(String aName) {
		name = aName;
	}

	@Override
	public String toString() {
		return name == null ? "NO_NAME" : name;
	}

	public Game getGame() {
		return game;
	}

	public void setGame(Game aGame) {
		game = aGame;
	}

	public void setSessionID(String aSessionID) {
		sessionID = aSessionID;
	}

	public String getSessionID() {
		return sessionID;
	}

	public synchronized int getPublicUniqueID() {
		return publicUniqueID;
	}

	synchronized void setPublicUniqueID(int aPublicUniqueID) {
		publicUniqueID = aPublicUniqueID;
	}

	@Override
	public boolean isExpired() {
		if (!expired)
			expired = expiryTime.isExpired();
		return expired;
	}
	
	public void resetExpiryTime() {
		expiryTime.resetExpiryTime();
	}

	public String getIcon() {
		return icon;
	}

	public void setIcon(String aIcon) {
		icon = aIcon;
	}
}
