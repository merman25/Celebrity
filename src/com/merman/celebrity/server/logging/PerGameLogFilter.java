package com.merman.celebrity.server.logging;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.Player;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.logging.info.LogInfo;

public class PerGameLogFilter implements ILogFilter {
	private Game game;

	public PerGameLogFilter(Game aGame) {
		if (aGame == null) {
			throw new IllegalArgumentException("Game arg may not be null");
		}
		game = aGame;
	}

	@Override
	public boolean shouldLog(LogInfo aLogInfo) {
		for (Object arg : aLogInfo.getArgs()) {
			if (arg == game) {
				return true;
			}
			else if (arg instanceof Session
					&& ((Session) arg).getPlayer().getGame() == game) {
				return true;
			}
			else if (arg instanceof Player
					&& ((Player) arg).getGame() == game) {
				return true;
			}
		}
		return false;
	}
}
