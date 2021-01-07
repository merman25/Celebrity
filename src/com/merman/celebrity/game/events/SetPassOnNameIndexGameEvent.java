package com.merman.celebrity.game.events;

import java.util.Map;

import com.merman.celebrity.game.Game;

public class SetPassOnNameIndexGameEvent extends GameStateUpdateEvent {
	private int passNameIndex;

	public SetPassOnNameIndexGameEvent(Game aGame, int aPassNameIndex) {
		super(aGame);
		passNameIndex = aPassNameIndex;
	}

	public int getPassNameIndex() {
		return passNameIndex;
	}

	@Override
	public Map<String, String> toPropertyMap() {
		Map<String, String> propertyMap = super.toPropertyMap();
		propertyMap.put("PassNameIndex", String.valueOf(getPassNameIndex()));
		return propertyMap;
	}

	
}
