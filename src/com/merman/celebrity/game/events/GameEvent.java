package com.merman.celebrity.game.events;

import java.util.HashMap;
import java.util.Map;

import com.merman.celebrity.game.Game;

public class GameEvent {
	private Game game;

	public GameEvent(Game aGame) {
		game = aGame;
	}

	public Game getGame() {
		return game;
	}
	
	public Map<String, String> toPropertyMap() {
		return new HashMap<>();
	}
	
	@Override
	public String toString() {
		return String.format("%s [game %s] %s", getClass().getSimpleName(), getGame(), toPropertyMap());
	}
}
