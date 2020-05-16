package com.merman.celebrity.server.cleanup;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CleanupHelper {
	public static int                                   defaultExpiryDurationInS = 3600;

	private static List<CleanupHelper>                  cleanupHelperList        = new ArrayList<>();
	private static Timer                                cleanupTimer;

	private Iterable<? extends ICanExpire>              canExpireIterable;
	private IExpiredEntityRemover<ICanExpire> 			expiredEntityRemover;
	
	private static class MyTimerTask
	extends TimerTask {
		@Override
		public void run() {
			synchronized (CleanupHelper.class) {
				boolean somethingRemoved = false;
				for ( CleanupHelper cleanupHelper : cleanupHelperList ) {
					for ( ICanExpire canExpire : cleanupHelper.getCanExpireIterable() ) {
						if (canExpire.isExpired()) {
							somethingRemoved = true;
							System.out.format("Removing %s %s\n", canExpire.getClass().getSimpleName(), canExpire);
							cleanupHelper.getExpiredEntityRemover().remove(canExpire);
						}
					}
				}
				
				if (somethingRemoved)
					System.gc();
			}
		}
	}
	
	private CleanupHelper(Iterable<? extends ICanExpire> aCanExpireIterable, IExpiredEntityRemover<? extends ICanExpire> aExpiredEntityRemover) {
		canExpireIterable    = aCanExpireIterable;
		expiredEntityRemover = (IExpiredEntityRemover<ICanExpire>) aExpiredEntityRemover;
	}
	
	public static synchronized void registerForRegularCleanup(Iterable<? extends ICanExpire> aCanExpireIterable, IExpiredEntityRemover<? extends ICanExpire> aExpiredEntityRemover) {
		CleanupHelper cleanupHelper	= new CleanupHelper(aCanExpireIterable, aExpiredEntityRemover);
		cleanupHelperList.add(cleanupHelper);
		if ( cleanupTimer == null ) {
			cleanupTimer = new Timer("Cleanup-Timer", true);
			cleanupTimer.schedule(new MyTimerTask(), 60000, 60000);
		}
	}

	private Iterable<? extends ICanExpire> getCanExpireIterable() {
		return canExpireIterable;
	}

	private IExpiredEntityRemover<ICanExpire> getExpiredEntityRemover() {
		return expiredEntityRemover;
	}
}
