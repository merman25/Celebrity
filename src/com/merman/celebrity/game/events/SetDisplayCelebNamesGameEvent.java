package com.merman.celebrity.game.events;

import java.util.Map;

import com.merman.celebrity.game.Game;

public class SetDisplayCelebNamesGameEvent extends GameStateUpdateEvent {
	private boolean displayCelebNames;

	public SetDisplayCelebNamesGameEvent(Game aGame, boolean aDisplayCelebNames) {
		super(aGame);
		displayCelebNames = aDisplayCelebNames;
	}

	public boolean getDisplayCelebNames() {
		return displayCelebNames;
	}

	@Override
	public Map<String, String> toPropertyMap() {
		Map<String, String> propertyMap = super.toPropertyMap();
		propertyMap.put("DisplayCelebNames", String.valueOf(getDisplayCelebNames()));
		return propertyMap;
	}

}
