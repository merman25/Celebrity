package com.merman.celebrity.game.events;

import java.util.Map;

import com.merman.celebrity.game.Game;

public class AllocateTeamsGameEvent extends GameStateUpdateEvent {
	private int numTeams;

	public AllocateTeamsGameEvent(Game aGame, int aNumTeams) {
		super(aGame);
		numTeams = aNumTeams;
	}

	public int getNumTeams() {
		return numTeams;
	}

	@Override
	public Map<String, String> toPropertyMap() {
		Map<String, String> propertyMap = super.toPropertyMap();
		propertyMap.put("NumTeams", String.valueOf(getNumTeams()));
		return propertyMap;

	}

}