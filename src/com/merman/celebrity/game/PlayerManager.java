package com.merman.celebrity.game;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import com.merman.celebrity.server.cleanup.CleanupHelper;
import com.merman.celebrity.server.cleanup.IExpiredEntityRemover;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.merman.celebrity.util.IntPool;

public class PlayerManager {
	private static IntPool        				sPlayerPublicIDPool		= new IntPool();
	private static SortedMap<Integer, Player> 	sPlayerMap         		= new TreeMap<>();
	
	private static class MyExpiredPlayerRemover
	implements IExpiredEntityRemover<Player> {
		@Override
		public void remove(Player aPlayer) {
			Log.log(LogMessageType.INFO, LogMessageSubject.SESSIONS, "Removing player", aPlayer);
			synchronized (PlayerManager.class) {
				sPlayerMap.remove(aPlayer.getPublicUniqueID());
				sPlayerPublicIDPool.push(aPlayer.getPublicUniqueID());
			}
		}
	}
	
	private static class MyPlayerIterable
	implements Iterable<Player> {

		@Override
		public Iterator<Player> iterator() {
			synchronized (PlayerManager.class) {
				return new ArrayList(sPlayerMap.values()).iterator();
			}
		}
	}
	
	static {
		CleanupHelper.registerForRegularCleanup(new MyPlayerIterable(), new MyExpiredPlayerRemover());
	}

	public static synchronized Player createPlayer() {
		Player player = new Player();
		player.setPublicUniqueID(sPlayerPublicIDPool.pop());
		sPlayerMap.put(player.getPublicUniqueID(), player);
		
		return player;
	}
	
	public static synchronized Player getPlayer(int aPublicUniqueId) {
		return sPlayerMap.get(aPublicUniqueId);
	}
	
	public static synchronized int getNumPlayers() {
		return sPlayerMap.size();
	}
}
