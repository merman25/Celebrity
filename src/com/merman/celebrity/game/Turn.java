package com.merman.celebrity.game;

import java.util.Timer;
import java.util.TimerTask;

import com.merman.celebrity.game.events.TurnTimeRemainingEvent;

public class Turn {
	private int initialSeconds;
	private int secondsRemaining;
	private Game game;
	private boolean started;
	private boolean stopped;
	
	private Timer timer;
	private long startTime;

	public Turn(Game aGame, int aSecondsRemaining) {
		game = aGame;
		initialSeconds = secondsRemaining = aSecondsRemaining;
	}

	public synchronized void start() {
		if ( ! started ) {
			started = true;
			startTime = System.nanoTime();
			timer = new Timer("Turn Timer (game " + game.getID() + ")", false);
			timer.schedule(new TimerTask() {
				
				@Override
				public void run() {
					long millisRemaining 	= Math.max( 0, 1000 * initialSeconds - ( System.nanoTime() - startTime ) / 1000_000 );
					game.fireGameEvent(new TurnTimeRemainingEvent(game, millisRemaining));
					
					secondsRemaining 		= initialSeconds - (int) ( ( System.nanoTime() - startTime ) / 1_000_000_000L );
					if ( secondsRemaining <= 0 ) {
						stop();
					}
				}
			}, 0, 200);
		}
	}

	public int getSecondsRemaining() {
		return secondsRemaining;
	}

	public boolean isStarted() {
		return started;
	}

	public boolean isStopped() {
		return stopped;
	}

	public synchronized void stop() {
		secondsRemaining = 0;
		if (timer != null) {
			timer.cancel();
		}
		timer = null;
		game.turnEnded();
	}
}
