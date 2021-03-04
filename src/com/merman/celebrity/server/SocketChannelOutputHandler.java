package com.merman.celebrity.server;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.merman.celebrity.util.IntPool;

public class SocketChannelOutputHandler {
	
	private static IntPool               sThreadIndexPool  = new IntPool();

	private boolean                      stop              = false;
	private int                          threadIndex;

	private HTTPServer                   httpServer;
	private ActivityMonitor              activityMonitor   = new ActivityMonitor();
	private volatile long                lastActivityTimeStampNanos;

	// 1 MB buffer. As of 16/1/21, total size of client directory is 312 kB, so 1 MB is loads.
	private ByteBuffer                   writeBuffer       = ByteBuffer.allocate(1024 * 1024);

	private Thread                       processOutputQueueThread;
	private BlockingQueue<IOutputSender> outputSenderQueue = new LinkedBlockingQueue<>();
	
	private class MyProcessQueueRunnable
	implements Runnable {
		@Override
		public void run() {
			while (! stop) {
				IOutputSender outputSender = null;
				try {
					outputSender = outputSenderQueue.take();
				} catch (InterruptedException e) {
				}

				synchronized (SocketChannelOutputHandler.this) {
					if (! stop
							&& outputSender != null ) {
						activityMonitor.startMonitoring();
						try {
							outputSender.sendOutput(writeBuffer);
						}
						catch (Exception e) {
							Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "Exception when sending output", e);
						}
						
						activityMonitor.endMonitoring();

						int percentageTimeActiveInLastPeriod = activityMonitor.getPercentageTimeActiveInLastPeriod();
						httpServer.reportActivityLevel(SocketChannelOutputHandler.this, percentageTimeActiveInLastPeriod);

						lastActivityTimeStampNanos = System.nanoTime();
					}
				}
			}
		}
	}
	
	public SocketChannelOutputHandler(HTTPServer aHttpServer) {
		httpServer = aHttpServer;
		activityMonitor.setMonitoringPeriodMillis(100);
	}
	
	protected synchronized void start() {
		if (stop)
			throw new IllegalStateException("Already stopped");
		if (processOutputQueueThread == null) {
			threadIndex = sThreadIndexPool.pop();
			lastActivityTimeStampNanos = System.nanoTime();
			processOutputQueueThread = new Thread(new MyProcessQueueRunnable(), "SocketChannelOutputHandler-" + threadIndex);
			processOutputQueueThread.start();
		}
	}
	
	public synchronized void stop() {
		stop = true;
		if (processOutputQueueThread != null) {
			processOutputQueueThread.interrupt();
			outputSenderQueue.clear();
			sThreadIndexPool.push(threadIndex);
		}
	}
	
	public synchronized int getPercentageTimeActiveInLastPeriod() {
		// This method is synchronized to avoid having to make the methods of ActivityMonitor synchronized
		return activityMonitor.getPercentageTimeActiveInLastPeriod();
	}
	
	public long getDurationSinceLastActivityMillis() {
		long currentTimeStamp = System.nanoTime();
		long durationNanos = currentTimeStamp - lastActivityTimeStampNanos;
		long durationMillis = durationNanos / 1_000_000;
		return durationMillis;
	}

	public void add(IOutputSender aOutputSender) {
		outputSenderQueue.add(aOutputSender);
		
		if (processOutputQueueThread == null) {
			start();
		}
	}
	
	public int getQueueSize() {
		return outputSenderQueue.size();
	}
}
