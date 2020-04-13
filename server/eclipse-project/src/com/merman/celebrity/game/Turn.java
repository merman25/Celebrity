package com.merman.celebrity.game;

import java.util.Timer;
import java.util.TimerTask;

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
			timer = new Timer();
			timer.schedule(new TimerTask() {
				
				@Override
				public void run() {
					int secondsUsed = initialSeconds - (int) ( ( System.nanoTime() - startTime ) / 1_000_000_000L );
					secondsRemaining = secondsUsed;
					if ( secondsRemaining <= 0 ) {
						stop();
					}
				}
			}, 0, 500);
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
		timer.cancel();
		timer = null;
		game.turnEnded();
	}
}
