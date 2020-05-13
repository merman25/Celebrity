package com.merman.celebrity.server;

import com.merman.celebrity.game.Player;
import com.merman.celebrity.game.PlayerManager;
import com.merman.celebrity.server.cleanup.CleanupHelper;
import com.merman.celebrity.server.cleanup.ExpiryTime;
import com.merman.celebrity.server.cleanup.ICanExpire;

public class Session implements ICanExpire {
	private final String sessionID;
	private Player player;
	
	private boolean                   expired;
	private ExpiryTime                expiryTime 				  = new ExpiryTime(CleanupHelper.defaultExpiryDurationInS);
	
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

	@Override
	public boolean isExpired() {
		if (!expired)
			expired = (getPlayer() != null && getPlayer().isExpired()) || expiryTime.isExpired();
		return expired;
	}
	
	public void resetExpiryTime() {
		expiryTime.resetExpiryTime();
	}
}
