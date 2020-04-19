package com.merman.celebrity.game;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerManager {
	private static AtomicInteger     maxPublicUniqueID = new AtomicInteger();
	private static SortedMap<Integer, Player> playerMap         = new TreeMap<>();

	public static synchronized Player createPlayer() {
		Player player = new Player();
		player.setPublicUniqueID(maxPublicUniqueID.incrementAndGet());
		playerMap.put(player.getPublicUniqueID(), player);
		
		return player;
	}
	
	public static synchronized Player getPlayer(int aPublicUniqueId) {
		return playerMap.get(aPublicUniqueId);
	}
}
