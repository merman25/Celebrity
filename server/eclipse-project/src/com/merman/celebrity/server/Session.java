package com.merman.celebrity.server;

import java.net.InetAddress;
import java.util.Date;

import com.merman.celebrity.game.Player;
import com.merman.celebrity.game.PlayerManager;
import com.merman.celebrity.server.cleanup.CleanupHelper;
import com.merman.celebrity.server.cleanup.ExpiryTime;
import com.merman.celebrity.server.cleanup.ICanExpire;

public class Session implements ICanExpire {
	private final String sessionID;
	private Player player;
	private InetAddress originalInetAddress;
	private final Date startTimeStamp;
	
	private boolean                   expired;
	private ExpiryTime                expiryTime 				  = new ExpiryTime(CleanupHelper.defaultExpiryDurationInS);
	
	Session(String aSessionID) {
		sessionID = aSessionID;
		player = PlayerManager.createPlayer();
		player.setSessionID( aSessionID );
		startTimeStamp = new Date();
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

	public InetAddress getOriginalInetAddress() {
		return originalInetAddress;
	}

	public void setOriginalInetAddress(InetAddress aAddress) {
		originalInetAddress = aAddress;
	}

	public Date getStartTimeStamp() {
		return startTimeStamp;
	}
}
