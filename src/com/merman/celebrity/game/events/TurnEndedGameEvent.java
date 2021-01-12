package com.merman.celebrity.game.events;

import java.util.Map;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.GameStatus;
import com.merman.celebrity.game.Player;

public class TurnEndedGameEvent extends GameStateUpdateEvent {

	private GameStatus newStatus;
	private Player newPlayer;

	public TurnEndedGameEvent(Game aGame, GameStatus aNewStatus, Player aNewPlayer) {
		super(aGame);
		newStatus = aNewStatus;
		newPlayer = aNewPlayer;
	}

	@Override
	public Map<String, String> toPropertyMap() {
		Map<String, String> propertyMap = super.toPropertyMap();
		propertyMap.put("NewPlayer", getNewPlayer() == null ? null : getNewPlayer().toString());
		propertyMap.put("NewStatus", getNewStatus() == null ? null : getNewStatus().toString());
		return propertyMap;
	}

	public GameStatus getNewStatus() {
		return newStatus;
	}

	public Player getNewPlayer() {
		return newPlayer;
	}
}
