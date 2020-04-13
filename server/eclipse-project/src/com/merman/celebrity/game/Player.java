package com.merman.celebrity.game;

public class Player {
	private String name;
	private Game game;
	private String sessionID;

	public String getName() {
		return name;
	}

	public void setName(String aName) {
		name = aName;
	}

	@Override
	public String toString() {
		return "Player [name=" + name + "]";
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
}
