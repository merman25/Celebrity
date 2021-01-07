package com.merman.celebrity.game.events;

import java.util.Map;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.Player;

public class RemoveNameListGameEvent extends GameStateUpdateEvent {
	private Player player;

	public RemoveNameListGameEvent(Game aGame, Player aPlayer) {
		super(aGame);
		player = aPlayer;
	}

	public Player getPlayer() {
		return player;
	}
	
	@Override
	public Map<String, String> toPropertyMap() {
		Map<String, String> propertyMap = super.toPropertyMap();
		propertyMap.put("Player", getPlayer() == null ? null : getPlayer().toString());
		return propertyMap;
	}

}
