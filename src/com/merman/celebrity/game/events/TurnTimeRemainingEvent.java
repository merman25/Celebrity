package com.merman.celebrity.game.events;

import java.util.Map;

import com.merman.celebrity.game.Game;

public class TurnTimeRemainingEvent extends GameEvent {
	private long millisRemaining;

	public TurnTimeRemainingEvent(Game aGame, long aMillisRemaining) {
		super(aGame);
		millisRemaining = aMillisRemaining;
	}

	public long getMillisRemaining() {
		return millisRemaining;
	}

	@Override
	public Map<String, String> toPropertyMap() {
		Map<String, String> propertyMap = super.toPropertyMap();
		propertyMap.put("MillisRemaining", String.valueOf(getMillisRemaining()));
		return propertyMap;
	}

}
