package com.merman.celebrity.game;

public class Player {
	private String name;
	private Game game;
	private String sessionID;
	private int publicUniqueID;
	
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
		return name;
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
}
