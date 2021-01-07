package com.merman.celebrity.game.events;

import java.util.Map;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.Player;

public class UpdateCurrentPlayerGameEvent extends GameStateUpdateEvent {
	private Player newCurrentNewCurrentPlayer;

	public UpdateCurrentPlayerGameEvent(Game aGame, Player aNewCurrentPlayer) {
		super(aGame);
		newCurrentNewCurrentPlayer = aNewCurrentPlayer;
	}

	public Player getNewCurrentPlayer() {
		return newCurrentNewCurrentPlayer;
	}
	
	@Override
	public Map<String, String> toPropertyMap() {
		Map<String, String> propertyMap = super.toPropertyMap();
		propertyMap.put("NewCurrentPlayer", getNewCurrentPlayer() == null ? null : getNewCurrentPlayer().toString());
		return propertyMap;
	}
}
