package com.merman.celebrity.game.events;

import java.util.Map;

import com.merman.celebrity.game.Game;

public class SetGameParamsGameEvent extends GameStateUpdateEvent {
	private int numRounds;
	private int roundDuration;
	private int numNames;

	public SetGameParamsGameEvent(Game aGame, int aNumRounds, int aRoundDuration, int aNumNames) {
		super(aGame);
		numRounds = aNumRounds;
		roundDuration = aRoundDuration;
		numNames = aNumNames;
	}

	public int getNumRounds() {
		return numRounds;
	}

	public int getRoundDuration() {
		return roundDuration;
	}

	public int getNumNames() {
		return numNames;
	}

	@Override
	public Map<String, String> toPropertyMap() {
		Map<String, String> propertyMap = super.toPropertyMap();
		propertyMap.put("NumRounds", String.valueOf(getNumRounds()));
		propertyMap.put("RoundDuration", String.valueOf(getRoundDuration()));
		propertyMap.put("NumNames", String.valueOf(getNumNames()));
		return propertyMap;
	}

}
