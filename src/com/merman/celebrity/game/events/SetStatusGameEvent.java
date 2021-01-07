package com.merman.celebrity.game.events;

import java.util.Map;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.GameStatus;

public class SetStatusGameEvent extends GameStateUpdateEvent {
	private GameStatus status;

	public SetStatusGameEvent(Game aGame, GameStatus aStatus) {
		super(aGame);
		status = aStatus;
	}

	@Override
	public Map<String, String> toPropertyMap() {
		Map<String, String> propertyMap = super.toPropertyMap();
		propertyMap.put("Status", getStatus() == null ? null : getStatus().toString());
		return propertyMap;
	}

	public GameStatus getStatus() {
		return status;
	}
}
