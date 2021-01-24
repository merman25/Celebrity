package com.merman.celebrity.tests;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;

import com.merman.celebrity.server.ActivityMonitor;

public class ActivityMonitorTest {
	private static class MyActivityMonitorWithControlledClock
	extends ActivityMonitor {
		private long nextTimeStampMillis;

		public void setNextTimeStampMillis(long aNextTimeStampMillis) {
			nextTimeStampMillis = aNextTimeStampMillis;
		}

		@Override
		protected long currentTimeNanos() {
			return nextTimeStampMillis * 1_000_000;
		}
	}

	@Test
	public void testNeverActive() {
		ActivityMonitor activityMonitor = new ActivityMonitor();
		Assert.assertEquals("Activity should be 0 initially", 0, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		MyActivityMonitorWithControlledClock controlledActivityMonitor = new MyActivityMonitorWithControlledClock();
		controlledActivityMonitor.startMonitoring();
		Assert.assertEquals("Activity should be 0 if clock time never increases", 0, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		controlledActivityMonitor.endMonitoring();
		Assert.assertEquals("Activity should be 0 if clock time never increases", 0, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}
	
	@Test
	public void testAlwaysActive() {
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(10000);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(10000);
		activityMonitor.endMonitoring();
		
		Assert.assertEquals("100% active", 100, activityMonitor.getPercentageTimeActiveInLastPeriod());

		activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(10000);
		
		// 4 activity periods with no gap in between
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(2500);
		activityMonitor.endMonitoring();
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(5000);
		activityMonitor.endMonitoring();
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(7500);
		activityMonitor.endMonitoring();
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(10000);
		
		Assert.assertEquals("100% active after 4 activities covering entire period, one of which is still ongoing", 100, activityMonitor.getPercentageTimeActiveInLastPeriod());
		activityMonitor.endMonitoring();
		Assert.assertEquals("100% active after 4 activities covering entire period", 100, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}
	
	@Test
	public void testPartlyActive() {
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(100);

		activityMonitor.setNextTimeStampMillis(10);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(20);
		activityMonitor.endMonitoring();
		
		activityMonitor.setNextTimeStampMillis(40);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(50);
		activityMonitor.endMonitoring();
		
		activityMonitor.setNextTimeStampMillis(80);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(90);
		activityMonitor.endMonitoring();
		
		activityMonitor.setNextTimeStampMillis(100);
		
		Assert.assertEquals("30% active after 3 activities each covering 10%", 30, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}
	
	@Test
	public void testForgettingOldActivityBitByBit() {
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(100);

		// 10ms periods of active, inactive, active, etc
		for (int timeStampMillis = 0; timeStampMillis < 100; timeStampMillis += 20) {
			activityMonitor.setNextTimeStampMillis(timeStampMillis);
			activityMonitor.startMonitoring();
			activityMonitor.setNextTimeStampMillis(timeStampMillis + 10);
			activityMonitor.endMonitoring();
		}
		
		Assert.assertEquals("After 5 periods of 10ms activity, we have 50% activity. We always divide by the full monitoring period, even though we have only gone from t=0 to t=90ms", 50, activityMonitor.getPercentageTimeActiveInLastPeriod());
		activityMonitor.setNextTimeStampMillis(100);
		Assert.assertEquals("When the end of the first monitoring period is reached, we're still at 50%", 50, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		int initialActivityDuration = 50;
		// Move forward in 5ms increments until everything is forgotten
		for (int timeStampMillis = 100; timeStampMillis <= 200; timeStampMillis += 5) {
			activityMonitor.setNextTimeStampMillis(timeStampMillis);
			
			int numCompletePeriodsToForget = ( timeStampMillis - 100 ) / 20;
			int durationFromCurrentPeriodToForget = Math.min( 10, ( ( timeStampMillis - 100 ) % 20 ) );
			
			int durationToForget = 10 * numCompletePeriodsToForget + durationFromCurrentPeriodToForget;
			
			int activityDuration = initialActivityDuration - durationToForget;
			int percentageActivity = activityDuration; // percentage == duration since period is 100ms
			
			Assert.assertEquals(String.format("After %,dms of forgetting", (timeStampMillis - 100)), percentageActivity, activityMonitor.getPercentageTimeActiveInLastPeriod());
		}
		
		Assert.assertEquals("Sanity check, should be 0 at end", 0, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}
	
	@Test
	public void testForgettingOldActivityAllAtOnce() {
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(100);

		// 10ms periods of active, inactive, active, etc
		for (int timeStampMillis = 0; timeStampMillis < 100; timeStampMillis += 20) {
			activityMonitor.setNextTimeStampMillis(timeStampMillis);
			activityMonitor.startMonitoring();
			activityMonitor.setNextTimeStampMillis(timeStampMillis + 10);
			activityMonitor.endMonitoring();
		}
		
		Assert.assertEquals("After 5 periods of 10ms activity, we have 50% activity. We always divide by the full monitoring period, even though we have only gone from t=0 to t=90ms", 50, activityMonitor.getPercentageTimeActiveInLastPeriod());
		activityMonitor.setNextTimeStampMillis(100);
		Assert.assertEquals("When the end of the first monitoring period is reached, we're still at 50%", 50, activityMonitor.getPercentageTimeActiveInLastPeriod());

		activityMonitor.setNextTimeStampMillis(200);
		Assert.assertEquals("0% active in last period", 0, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(100);

		// 10ms periods of active, inactive, active, etc
		for (int timeStampMillis = 0; timeStampMillis < 100; timeStampMillis += 20) {
			activityMonitor.setNextTimeStampMillis(timeStampMillis);
			activityMonitor.startMonitoring();
			activityMonitor.setNextTimeStampMillis(timeStampMillis + 10);
			activityMonitor.endMonitoring();
		}
		
		Assert.assertEquals("After 5 periods of 10ms activity, we have 50% activity. We always divide by the full monitoring period, even though we have only gone from t=0 to t=90ms", 50, activityMonitor.getPercentageTimeActiveInLastPeriod());
		activityMonitor.setNextTimeStampMillis(100);
		Assert.assertEquals("When the end of the first monitoring period is reached, we're still at 50%", 50, activityMonitor.getPercentageTimeActiveInLastPeriod());

		// big time jump
		activityMonitor.setNextTimeStampMillis(5000);
		Assert.assertEquals("0% active in last period", 0, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}
	
	@Test
	public void testForgettingIntermediateAmountOfActivity() {
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(5000);

		IntStream.of(0, 250,
				750, 2000,
				2600, 3200,
				3300, 4100,
				4850, 5000)
		.forEach(new IntConsumer() {
			private boolean start = true;
			
			@Override
			public void accept(int aValue) {
				activityMonitor.setNextTimeStampMillis(aValue);
				if (start) {
					activityMonitor.startMonitoring();
				}
				else {
					activityMonitor.endMonitoring();
				}
				start = ! start;
			}
		});
		
		Assert.assertEquals(61, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor.setNextTimeStampMillis(5800);
		Assert.assertEquals(55, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor.setNextTimeStampMillis(8500);
		Assert.assertEquals(15, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor.setNextTimeStampMillis(10100);
		Assert.assertEquals(0, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}
	
	@Test
	public void testForgettingIntermediateAmountOfActivity2() {
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(5000);

		IntStream.of(0, 250,
				750, 2000,
				2600, 3200,
				3300, 4100,
				4850, 5000)
		.forEach(new IntConsumer() {
			private boolean start = true;
			
			@Override
			public void accept(int aValue) {
				activityMonitor.setNextTimeStampMillis(aValue);
				if (start) {
					activityMonitor.startMonitoring();
				}
				else {
					activityMonitor.endMonitoring();
				}
				start = ! start;
			}
		});
		
		Assert.assertEquals(61, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor.setNextTimeStampMillis(8500);
		Assert.assertEquals(15, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor.setNextTimeStampMillis(10100);
		Assert.assertEquals(0, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}

	@Test
	public void testForgettingIntermediateAmountOfActivity3() {
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(5000);

		IntStream.of(0, 250,
				750, 2000,
				2600, 3200,
				3300, 4100,
				4850, 5000)
		.forEach(new IntConsumer() {
			private boolean start = true;
			
			@Override
			public void accept(int aValue) {
				activityMonitor.setNextTimeStampMillis(aValue);
				if (start) {
					activityMonitor.startMonitoring();
				}
				else {
					activityMonitor.endMonitoring();
				}
				start = ! start;
			}
		});
		
		Assert.assertEquals(61, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor.setNextTimeStampMillis(7010);
		Assert.assertEquals(31, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor.setNextTimeStampMillis(8500);
		Assert.assertEquals(15, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor.setNextTimeStampMillis(10100);
		Assert.assertEquals(0, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}

	@Test
	public void testForgettingIntermediateAmountOfActivity4() {
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(5000);

		IntStream.of(0, 250,
				750, 2000,
				2600, 3200,
				3300, 4100,
				4850, 5000)
		.forEach(new IntConsumer() {
			private boolean start = true;
			
			@Override
			public void accept(int aValue) {
				activityMonitor.setNextTimeStampMillis(aValue);
				if (start) {
					activityMonitor.startMonitoring();
				}
				else {
					activityMonitor.endMonitoring();
				}
				start = ! start;
			}
		});
		
		Assert.assertEquals(61, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor.setNextTimeStampMillis(9200);
		Assert.assertEquals(3, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor.setNextTimeStampMillis(10100);
		Assert.assertEquals(0, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}
	
	@Test
	public void testForgettingOldActivityWhileAddingNewActivity() {
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(5000);

		IntStream.of(0, 250,
				750, 2000,
				2600, 3200,
				3300, 4100,
				4850, 5000)
		.forEach(new IntConsumer() {
			private boolean start = true;
			
			@Override
			public void accept(int aValue) {
				activityMonitor.setNextTimeStampMillis(aValue);
				if (start) {
					activityMonitor.startMonitoring();
				}
				else {
					activityMonitor.endMonitoring();
				}
				start = ! start;
			}
		});
		
		Assert.assertEquals(61, activityMonitor.getPercentageTimeActiveInLastPeriod());

		activityMonitor.setNextTimeStampMillis(5300);
		activityMonitor.startMonitoring();
		Assert.assertEquals(56, activityMonitor.getPercentageTimeActiveInLastPeriod());
		activityMonitor.setNextTimeStampMillis(5550);
		activityMonitor.endMonitoring();
		Assert.assertEquals(61, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		activityMonitor.setNextTimeStampMillis(5800);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(7200);
		activityMonitor.endMonitoring();
		Assert.assertEquals(64, activityMonitor.getPercentageTimeActiveInLastPeriod());

		activityMonitor.setNextTimeStampMillis(7300);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(8000);
		activityMonitor.endMonitoring();
		Assert.assertEquals(70, activityMonitor.getPercentageTimeActiveInLastPeriod());

		activityMonitor.setNextTimeStampMillis(8500);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(8900);
		activityMonitor.endMonitoring();
		Assert.assertEquals(62, activityMonitor.getPercentageTimeActiveInLastPeriod());


		activityMonitor.setNextTimeStampMillis(9400);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(9600);
		activityMonitor.endMonitoring();
		Assert.assertEquals(62, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}
	
	@Test
	public void testForgettingOldActivityWhileAddingNewActivity2() {
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(5000);

		IntStream.of(0, 250,
				750, 2000,
				2600, 3200,
				3300, 4100,
				4850, 5000)
		.forEach(new IntConsumer() {
			private boolean start = true;
			
			@Override
			public void accept(int aValue) {
				activityMonitor.setNextTimeStampMillis(aValue);
				if (start) {
					activityMonitor.startMonitoring();
				}
				else {
					activityMonitor.endMonitoring();
				}
				start = ! start;
			}
		});
		
		Assert.assertEquals(61, activityMonitor.getPercentageTimeActiveInLastPeriod());

		activityMonitor.setNextTimeStampMillis(7300);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(8000);
		activityMonitor.endMonitoring();
		Assert.assertEquals(37, activityMonitor.getPercentageTimeActiveInLastPeriod());

		activityMonitor.setNextTimeStampMillis(9400);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(9600);
		activityMonitor.endMonitoring();
		Assert.assertEquals(21, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}
	
	@Test
	public void testLargeNumberOfActivityPeriods() {
		// We start with an arr of size 16, make sure we grow it correctly
		
		// First, repeat part of previous test, so that internal index for start within cyclic buffer is not at 0
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		activityMonitor.setMonitoringPeriodMillis(5000);

		IntStream.of(0, 250,
				750, 2000,
				2600, 3200,
				3300, 4100,
				4850, 5000)
		.forEach(new IntConsumer() {
			private boolean start = true;
			
			@Override
			public void accept(int aValue) {
				activityMonitor.setNextTimeStampMillis(aValue);
				if (start) {
					activityMonitor.startMonitoring();
				}
				else {
					activityMonitor.endMonitoring();
				}
				start = ! start;
			}
		});
		

		activityMonitor.setNextTimeStampMillis(5300);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(5550);
		activityMonitor.endMonitoring();
		
		activityMonitor.setNextTimeStampMillis(5800);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(7200);
		activityMonitor.endMonitoring();

		activityMonitor.setNextTimeStampMillis(7300);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(8000);
		activityMonitor.endMonitoring();

		activityMonitor.setNextTimeStampMillis(8500);
		activityMonitor.startMonitoring();
		activityMonitor.setNextTimeStampMillis(8900);
		activityMonitor.endMonitoring();
		Assert.assertEquals(62, activityMonitor.getPercentageTimeActiveInLastPeriod());
		
		// Now add more than 16 new timestamps
		IntStream.of(9000, 9010,
				9020, 9030,
				9040, 9050,
				9060, 9070,
				9080, 9090,
				9100, 9110,
				9120, 9130,
				9140, 9150,
				9160, 9170,
				9180, 9190,
				9200, 9210 )
		.forEach(new IntConsumer() {
			private boolean start = true;
			
			@Override
			public void accept(int aValue) {
				activityMonitor.setNextTimeStampMillis(aValue);
				if (start) {
					activityMonitor.startMonitoring();
				}
				else {
					activityMonitor.endMonitoring();
				}
				start = ! start;
			}
		});
		
		Assert.assertEquals(60, activityMonitor.getPercentageTimeActiveInLastPeriod());
	}
	
	@Test
	public void testStabilityOverMultipleCycles() {
		MyActivityMonitorWithControlledClock activityMonitor = new MyActivityMonitorWithControlledClock();
		int monitoringPeriodMillis = 300;
		activityMonitor.setMonitoringPeriodMillis(monitoringPeriodMillis);
		
		for (int cycleIndex = 0; cycleIndex < 10; cycleIndex++) {
				activityMonitor.setNextTimeStampMillis(( monitoringPeriodMillis * cycleIndex + 0));
				activityMonitor.startMonitoring();
				activityMonitor.setNextTimeStampMillis(( monitoringPeriodMillis * cycleIndex + 10 ));
				activityMonitor.endMonitoring();
			
				activityMonitor.setNextTimeStampMillis(( monitoringPeriodMillis * cycleIndex + 80));
				activityMonitor.startMonitoring();
				activityMonitor.setNextTimeStampMillis(( monitoringPeriodMillis * cycleIndex + 90 ));
				activityMonitor.endMonitoring();
			
				activityMonitor.setNextTimeStampMillis(( monitoringPeriodMillis * cycleIndex + 120));
				activityMonitor.startMonitoring();
				activityMonitor.setNextTimeStampMillis(( monitoringPeriodMillis * cycleIndex + 130 ));
				activityMonitor.endMonitoring();
			
				activityMonitor.setNextTimeStampMillis(( monitoringPeriodMillis * cycleIndex + 190));
				activityMonitor.startMonitoring();
				activityMonitor.setNextTimeStampMillis(( monitoringPeriodMillis * cycleIndex + 200 ));
				activityMonitor.endMonitoring();
			
				activityMonitor.setNextTimeStampMillis(( monitoringPeriodMillis * cycleIndex + 265));
				activityMonitor.startMonitoring();
				activityMonitor.setNextTimeStampMillis(( monitoringPeriodMillis * cycleIndex + 275 ));
				activityMonitor.endMonitoring();
			
			Assert.assertEquals("50ms active over 5 periods in the last 300ms, 16%", 16, activityMonitor.getPercentageTimeActiveInLastPeriod());
		}
	}
}
