package com.merman.celebrity.server;

/**
 * Class for recording and reporting how much a thread or other resource was in use
 * during the last period of some specified duration.
 */
public class ActivityMonitor {
	public static final int DEFAULT_MONITORING_PERIOD_MILLIS = 1000;
	private static final int MILLIS_TO_NANOS = 1_000_000;
	
	private long monitoringPeriodNanos = DEFAULT_MONITORING_PERIOD_MILLIS * MILLIS_TO_NANOS;

	private volatile boolean monitoring;
	
	// cyclic buffer
	private long[] timeStamps = new long[16];
	private int nextTimeStampIndex = 0;
	private int timeStampArrayStartIndex = 0;
	
	public void startMonitoring() {
		if (monitoring) {
			throw new IllegalStateException("Already monitoring");
		}
		
		monitoring = true;
		addTimeStamp(false);
	}
	
	public void endMonitoring() {
		if (! monitoring) {
			throw new IllegalStateException("Monitoring had not started");
		}
		
		monitoring = false;
		addTimeStamp(true);
	}
	
	public int getPercentageTimeActiveInLastPeriod() {
		long currentTimeStamp = currentTimeNanos();
		checkArrayForStaleTimestamps(currentTimeStamp);
		
		long totalTimeActive = 0;
		for (int timeStampIndex = timeStampArrayStartIndex; timeStampIndex != nextTimeStampIndex; ) {
			int subsequentTimeStampIndex = timeStampIndex + 1;
			if (subsequentTimeStampIndex == timeStamps.length) {
				subsequentTimeStampIndex = 0;
			}
			
			long startTime = timeStamps[timeStampIndex];
			long endTime = subsequentTimeStampIndex == nextTimeStampIndex ? currentTimeStamp : timeStamps[subsequentTimeStampIndex];
			
			long duration = endTime - startTime;
			totalTimeActive += duration;
			
			if (subsequentTimeStampIndex == nextTimeStampIndex) {
				break;
			}
			
			timeStampIndex = subsequentTimeStampIndex + 1;
			if (timeStampIndex == timeStamps.length) {
				timeStampIndex = 0;
			}
		}
		
		return (int) ( ( 100 * totalTimeActive ) / monitoringPeriodNanos );
	}

	private void addTimeStamp(boolean aCheckArrayForStaleTimestamps) {
		long timeStamp = currentTimeNanos();
		
		if (aCheckArrayForStaleTimestamps) {
			checkArrayForStaleTimestamps(timeStamp);
		}
		
		
		timeStamps[nextTimeStampIndex] = timeStamp;
		
		nextTimeStampIndex++;
		if (nextTimeStampIndex == timeStamps.length) {
			// Cyclic buffer, so cycle back to 0
			nextTimeStampIndex = 0;
		}
		
		if (nextTimeStampIndex == timeStampArrayStartIndex) {
			// We've come all the way around and filled the buffer, make it bigger
			growTimeStampArray();
		}
	}
	
	private void checkArrayForStaleTimestamps(long aCurrentTimeStamp) {
		int earliestNonStaleTimeStampIndex = timeStampArrayStartIndex;
		boolean timeStampIsAnActivtyStart = true;
		long startOfCurrentMonitoringPeriod = aCurrentTimeStamp - monitoringPeriodNanos;
		
		for (int timeStampIndex = timeStampArrayStartIndex; timeStampIndex != nextTimeStampIndex; ) {
			long oldTimeStamp = timeStamps[timeStampIndex];
			long durationSinceOldTimeStamp = aCurrentTimeStamp - oldTimeStamp;
			
			if (durationSinceOldTimeStamp > monitoringPeriodNanos) {
				// Timestamp is old, we will remove some or all of this activity
				if (timeStampIsAnActivtyStart) {
					/* It started before the current monitoring period, we're only interested in the monitoring since the period started.
					 * When we check the next timestamp (the end of this activity), we may remove this activity entirely.
					 */
					timeStamps[timeStampIndex] = startOfCurrentMonitoringPeriod;
					earliestNonStaleTimeStampIndex = timeStampIndex;
				}
				else {
					// It ended before the current monitoring period, remove it by changing the start index in the cyclic buffer
					earliestNonStaleTimeStampIndex = timeStampIndex + 1;
				}
			}
			else {
				// This timestamp is valid.
				if (timeStampIsAnActivtyStart) {
					// If it's an activity end, we already set the earliestNonStaleIndex in the block above, on the previous iteration.
					earliestNonStaleTimeStampIndex = timeStampIndex;
				}
				break;
			}
			
			timeStampIsAnActivtyStart = ! timeStampIsAnActivtyStart;
			timeStampIndex++;
			if (timeStampIndex == timeStamps.length) {
				timeStampIndex = 0;
			}
		}
		
		if (earliestNonStaleTimeStampIndex == timeStamps.length) {
			earliestNonStaleTimeStampIndex = 0;
		}
		timeStampArrayStartIndex = earliestNonStaleTimeStampIndex;
	}

	private void growTimeStampArray() {
		int oldArrayLength = timeStamps.length;
		long[] newArray = new long[2 * oldArrayLength];
		int numIndicesBetweenStartIndexAndEndOfArray = oldArrayLength - timeStampArrayStartIndex;
		System.arraycopy(timeStamps, timeStampArrayStartIndex, newArray, 0, numIndicesBetweenStartIndexAndEndOfArray);
		System.arraycopy(timeStamps, 0, newArray, numIndicesBetweenStartIndexAndEndOfArray, timeStampArrayStartIndex);
		timeStamps = newArray;
		timeStampArrayStartIndex = 0;
		nextTimeStampIndex = oldArrayLength;
	}

	protected long currentTimeNanos() {
		return System.nanoTime();
	}
	
	public void setMonitoringPeriodMillis(long aMonitoringPeriodMillis) {
		monitoringPeriodNanos = aMonitoringPeriodMillis * MILLIS_TO_NANOS;
	}
	
	public long getMonitoringPeriodMillis() {
		return monitoringPeriodNanos / MILLIS_TO_NANOS;
	}
}