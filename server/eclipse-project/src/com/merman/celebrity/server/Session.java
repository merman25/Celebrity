package com.merman.celebrity.server;

import com.merman.celebrity.game.Player;
import com.merman.celebrity.game.PlayerManager;

public class Session {
	private final String sessionID;
	private Player player;
	
	Session(String aSessionID) {
		sessionID = aSessionID;
		player = PlayerManager.createPlayer();
		player.setSessionID( aSessionID );
	}

	public String getSessionID() {
		return sessionID;
	}

	@Override
	public String toString() {
		return sessionID;
	}

	public Player getPlayer() {
		return player;
	}
}
