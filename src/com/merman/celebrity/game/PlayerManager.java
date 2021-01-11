package com.merman.celebrity.game;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.merman.celebrity.server.cleanup.CleanupHelper;
import com.merman.celebrity.server.cleanup.IExpiredEntityRemover;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;

public class PlayerManager {
	private static AtomicInteger     maxPublicUniqueID = new AtomicInteger();
	private static SortedMap<Integer, Player> playerMap         = new TreeMap<>();
	
	private static class MyExpiredPlayerRemover
	implements IExpiredEntityRemover<Player> {
		@Override
		public void remove(Player aPlayer) {
			Log.log(LogMessageType.INFO, LogMessageSubject.SESSIONS, "Removing player", aPlayer);
			synchronized (PlayerManager.class) {
				playerMap.remove(aPlayer.getPublicUniqueID());
			}
		}
	}
	
	private static class MyPlayerIterable
	implements Iterable<Player> {

		@Override
		public Iterator<Player> iterator() {
			return new ArrayList(playerMap.values()).iterator();
		}
	}
	
	static {
		CleanupHelper.registerForRegularCleanup(new MyPlayerIterable(), new MyExpiredPlayerRemover());
	}

	public static synchronized Player createPlayer() {
		Player player = new Player();
		player.setPublicUniqueID(maxPublicUniqueID.incrementAndGet());
		playerMap.put(player.getPublicUniqueID(), player);
		
		return player;
	}
	
	public static synchronized Player getPlayer(int aPublicUniqueId) {
		return playerMap.get(aPublicUniqueId);
	}
	
	public static synchronized int getNumPlayers() {
		return playerMap.size();
	}
}
