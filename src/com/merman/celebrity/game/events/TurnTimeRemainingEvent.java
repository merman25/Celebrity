package com.merman.celebrity.game.events;

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

}
