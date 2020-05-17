package com.merman.celebrity.server.logging.info;

import com.merman.celebrity.game.Game;

public class PerGameLogInfo extends LogInfo {
	private Game game;

	public PerGameLogInfo(Object... aArgs) {
		super(aArgs);
		for (Object arg : aArgs) {
			if (arg instanceof Game) {
				if (game != null
						&& game != arg ) {
					throw new IllegalArgumentException("Multiple games specified: " +game + " and " + arg);
				}
				game = (Game) arg;
			}
		}
		
		if (game == null) {
			throw new IllegalArgumentException("No game specified");
		}
	}

	public Game getGame() {
		return game;
	}
}
