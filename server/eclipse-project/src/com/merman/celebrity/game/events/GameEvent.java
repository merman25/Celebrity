package com.merman.celebrity.game.events;

import com.merman.celebrity.game.Game;

public class GameEvent {
	private Game game;

	public GameEvent(Game aGame) {
		game = aGame;
	}

	public Game getGame() {
		return game;
	}
}
