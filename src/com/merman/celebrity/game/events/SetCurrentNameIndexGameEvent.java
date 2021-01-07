package com.merman.celebrity.game.events;

import java.util.Map;

import com.merman.celebrity.game.Game;

public class SetCurrentNameIndexGameEvent extends GameStateUpdateEvent {
	private int currentNameIndex;

	public SetCurrentNameIndexGameEvent(Game aGame, int aCurrentNameIndex) {
		super(aGame);
		currentNameIndex = aCurrentNameIndex;
	}

	public int getCurrentNameIndex() {
		return currentNameIndex;
	}

	@Override
	public Map<String, String> toPropertyMap() {
		Map<String, String> propertyMap = super.toPropertyMap();
		propertyMap.put("CurrentNameIndex", String.valueOf(getCurrentNameIndex()));
		return propertyMap;
	}

	
}
