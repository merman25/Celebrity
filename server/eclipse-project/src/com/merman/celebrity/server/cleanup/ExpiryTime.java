package com.merman.celebrity.server.cleanup;

public class ExpiryTime {
	private int durationOfInactivityBeforeExpiringInSec;
	private long lastResetTimeMillis;
	private boolean expired;

	public ExpiryTime(int aDurationInSeconds) {
		durationOfInactivityBeforeExpiringInSec = aDurationInSeconds;
		resetExpiryTime();
	}

	public void resetExpiryTime() {
		lastResetTimeMillis = System.currentTimeMillis();
	}

	public boolean isExpired() {
		if (!expired)
			expired = ( System.currentTimeMillis() - lastResetTimeMillis ) / 1000 >= durationOfInactivityBeforeExpiringInSec;
			
		return expired;
	}
	
	public int getDurationToExpirySeconds() {
		return Math.max(0, (int) ( durationOfInactivityBeforeExpiringInSec - ( System.currentTimeMillis() - lastResetTimeMillis ) / 1000 ) );
	}
}
