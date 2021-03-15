package com.merman.celebrity.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.merman.celebrity.server.exceptions.HTTPException;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.merman.celebrity.util.IntPool;

public class HTTPChannelHandler {
	private static final Object                     HASHMAP_VALUE                           = new Object();
	
	private static IntPool                          sThreadIndexPool						= new IntPool();
	
	private boolean stop = false;
	private int threadIndex;
	
	private Selector selector;
	private Map<SelectionKey, SocketChannel>		mapSelectionKeysToClientChannels		= new HashMap<>();
	private Map<SocketChannel, HTTPExchange>		mapSocketChannelsToHTTPExchanges		= new HashMap<>();
	private Set<SocketChannel>						websocketChannels						= new HashSet<>();
	private Map<SelectionKey, Object>               selectionKeysToRemove					= new ConcurrentHashMap<>();
	private ActivityMonitor                         activityMonitor							= new ActivityMonitor();
	private volatile long                           lastActivityTimeStampNanos;
	private Map<SelectionKey, Long>					mapSelectionKeysToLastInputTimes		= new HashMap<>();
	
	private ByteBuffer readWriteBuffer = ByteBuffer.allocate(1024 * 1024); // 1 MB buffer. As of 16/1/21, total size of client directory is 312 kB, so 1 MB is loads.

	private HTTPServer httpServer;
	
	private class MyReadFromChannelRunnable
	implements Runnable {

		@Override
		public void run() {
			try {
				while (! stop) {
					try {
						selector.select();
					} catch (IOException e) {
						Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "IOException on selector", selector, "exception", e);

						// We're probably screwed at this point, but may as well try again. Put in a sleep to avoid blasting the log files with data
						try {
							Thread.sleep(3000);
						} catch (InterruptedException e1) {}

						continue;
					}
					
					if (! selectionKeysToRemove.isEmpty()) {
						for (SelectionKey key : selectionKeysToRemove.keySet()) {
							removeKeyNow(key);
						}
						
						selectionKeysToRemove.clear();
						continue;
					}
					
					synchronized (HTTPChannelHandler.this) {
						if (! stop) {
							// if selector.select() was woken up by a real selection, and not by invocation of selector.close()
							
							activityMonitor.startMonitoring();
							
							for (SelectionKey key : selector.selectedKeys()) {
								if (key.isReadable()) {
									SocketChannel clientChannel = mapSelectionKeysToClientChannels.get(key);
									if (clientChannel == null) {
										// Shouldn't ever happen, but I'm copying some example code
										Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "Readable key", key, "from this server's selector", selector, "could not find SocketChannel");
										continue;
									}
									
									try {
										int bytesRead = clientChannel.read(readWriteBuffer.clear());
										if (bytesRead < 0) {
											removeKeyNow(key);
										}
										else if (bytesRead > 0) {
											// We do sometimes read 0 bytes, for whatever reason
											
											mapSelectionKeysToLastInputTimes.put(key, System.nanoTime());
											byte[] byteArr = new byte[bytesRead]; // TODO don't allocate a new array each time
											readWriteBuffer.flip();
											readWriteBuffer.get(byteArr);
											
											if (websocketChannels.contains(clientChannel)) {
												// TODO handle with a WebsocketMessage
											}
											else {

												HTTPExchange exchange = mapSocketChannelsToHTTPExchanges.computeIfAbsent(clientChannel, sc -> new HTTPExchange(sc, HTTPChannelHandler.this, key));

												try {
													exchange.addBytes(byteArr);

													if (exchange.isFinishedReadingRequestBody()) {
														httpServer.handle(exchange);
														mapSocketChannelsToHTTPExchanges.remove(clientChannel); // if we get more bytes down the same channel, it's a new exchange
													}
												}
												catch (HTTPException e) {
													removeKeyNow(key);

													Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "Exception handling HTTP Request", e);
												}
											}
										}

									}
									catch (IOException e) {
										Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "IOException when reading clientChannel ", e); // TODO associate with Session and/or IP
									}
								}
							}
							
							activityMonitor.endMonitoring();
							int percentageTimeActiveInLastPeriod = activityMonitor.getPercentageTimeActiveInLastPeriod();
							
							httpServer.reportActivityLevel(HTTPChannelHandler.this, percentageTimeActiveInLastPeriod);
							lastActivityTimeStampNanos = System.nanoTime();
						}
					}
				}
			}
			catch (RuntimeException e) {
				Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "Exception in HTTP Channel Handler", e);
			}
		}
	}

	public HTTPChannelHandler(HTTPServer aHttpServer) {
		httpServer = aHttpServer;
		activityMonitor.setMonitoringPeriodMillis(100);
	}

	public void add(SocketChannel aClientSocketChannel) throws IOException {
		if (selector == null) {
			start();
		}
		
		aClientSocketChannel.configureBlocking(false);
		SelectionKey clientKey = aClientSocketChannel.register(selector, SelectionKey.OP_READ);
		mapSelectionKeysToClientChannels.put(clientKey, aClientSocketChannel);
		mapSelectionKeysToLastInputTimes.put(clientKey, System.nanoTime());
		selector.wakeup();
	}
	
	protected synchronized void start() throws IOException {
		if (stop)
			throw new IllegalStateException("Already stopped");
		if (selector == null) {
			threadIndex = sThreadIndexPool.pop();
			lastActivityTimeStampNanos = System.nanoTime();
			selector = Selector.open();
			new Thread(new MyReadFromChannelRunnable(), "HTTPChannelHandler-" + threadIndex).start();
		}
	}
	
	public synchronized void stop() {
		stop = true;
		if (selector != null) {
			try {
				for (SelectionKey key : mapSelectionKeysToClientChannels.keySet()) {
					removeKeyNow(key);
				}
				mapSelectionKeysToClientChannels.clear();
				mapSocketChannelsToHTTPExchanges.clear();
				mapSelectionKeysToLastInputTimes.clear();
				selectionKeysToRemove.clear();
				selector.close();
				selector = null;
				
				sThreadIndexPool.push(threadIndex);
			} catch (IOException e) {
				Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "IOException when closing HTTPChannelHandler Selector", e);
			}
		}
	}
	
	public void remove(SelectionKey... aSelectionKeys) {
		for (SelectionKey selectionKey : aSelectionKeys) {
			selectionKeysToRemove.put(selectionKey, HASHMAP_VALUE);
		}
		selector.wakeup();
	}

	private void removeKeyNow(SelectionKey aKey) {
		try {
			aKey.cancel();
			mapSelectionKeysToLastInputTimes.remove(aKey);
			SocketChannel clientChannel = mapSelectionKeysToClientChannels.remove(aKey);
			mapSocketChannelsToHTTPExchanges.remove(clientChannel);
			clientChannel.close();
		}
		catch (IOException e) {
			Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "IOException closing client channel", e);
		}
	}

	public long getDurationSinceLastActivityMillis() {
		long currentTimeStamp = System.nanoTime();
		long durationNanos = currentTimeStamp - lastActivityTimeStampNanos;
		long durationMillis = durationNanos / 1_000_000;
		return durationMillis;
	}
	
	public int getNumActiveKeys() {
		return mapSelectionKeysToClientChannels.size();
	}
	
	public synchronized int getPercentageTimeActiveInLastPeriod() {
		// This method is synchronized to avoid having to make the methods of ActivityMonitor synchronized
		return activityMonitor.getPercentageTimeActiveInLastPeriod();
	}
	
	synchronized void checkForExpiredChannels() {
		int inactivityDurationThresholdInS = 5;
		int inactivityDurationThresholdNanos = inactivityDurationThresholdInS * 1_000_000_000;
		long nanoTime = System.nanoTime();
		List<SelectionKey> keysToRemove = null;
		for (Entry<SelectionKey, Long> mapEntry : mapSelectionKeysToLastInputTimes.entrySet()) {
			long durationSinceLastInputNanos = nanoTime - mapEntry.getValue();
			if (durationSinceLastInputNanos > inactivityDurationThresholdNanos) {
				if (keysToRemove == null) {
					keysToRemove = new ArrayList<>();
				}
				keysToRemove.add(mapEntry.getKey());
			}
		}

		if (keysToRemove != null) {
			remove(keysToRemove.toArray(new SelectionKey[ keysToRemove.size() ]));
		}
	}

	public void addToWebsocketChannelSet(SocketChannel aClientSocketChannel) {
		websocketChannels.add(aClientSocketChannel);
	}
}
