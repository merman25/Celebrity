package com.merman.celebrity.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import com.merman.celebrity.server.handlers.AHttpHandler;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.merman.celebrity.server.logging.Logger;
import com.merman.celebrity.server.logging.outputters.PrintStreamOutputter;

public class HTTPServer {

	private volatile boolean listen;
	private int portNumber = -1;
	private boolean localHost;

	private ServerSocketChannel serverSocketChannel;
	private SelectionKey serverKey;
	private Selector selector;

	private static final Object                     HASHMAP_VALUE							= new Object();
	private Map<HTTPChannelHandler, Object>         availableChannelHandlers				= new ConcurrentHashMap<>();
	private Map<HTTPChannelHandler, Object>	     	busyChannelHandlers						= new ConcurrentHashMap<>();
	private Timer                                   removeUnusedChannelHandlersTimer;
	
	private Map<URI, AHttpHandler>               	handlerMap								= new HashMap<>();
	
	private class MyListenForConnectionsRunnable
	implements Runnable {

		@Override
		public void run() {
			try {
				while (listen) {
					try {
						selector.select();
					}
					catch (IOException e) {
						Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "IOException on selector", selector, "exception", e);
						
						// We're probably screwed at this point, but may as well try again. Put in a sleep to avoid blasting the log files with data
						try {
							Thread.sleep(3000);
						} catch (InterruptedException e1) {}
						
						continue;
					}
					
					synchronized ( HTTPServer.this ) {
						if (listen) {
							// if selector.select() was woken up by a real selection, and not by invocation of selector.close()
							for (SelectionKey key : selector.selectedKeys()) {
								if (key == serverKey) {
									SocketChannel clientSocketChannel = null;
									try {
										clientSocketChannel = serverSocketChannel.accept();
										if (clientSocketChannel == null) {
											/* Can happen, since serverSocketChannel is non-blocking.
											 * Strange that it happens even when the server's key has been selected, but whatever
											 */
											continue;
										}
										
										HTTPChannelHandler channelHandler = null;
										if (! availableChannelHandlers.isEmpty()) {
											channelHandler = availableChannelHandlers.keySet().iterator().next();
										}
										else if (! busyChannelHandlers.isEmpty()) {
											HTTPChannelHandler previouslyBusyChannelHandler = busyChannelHandlers.keySet().iterator().next();
											if (previouslyBusyChannelHandler.getPercentageTimeActiveInLastPeriod() == 0) {
												busyChannelHandlers.remove(previouslyBusyChannelHandler);
												availableChannelHandlers.put(previouslyBusyChannelHandler, HASHMAP_VALUE);
												channelHandler = previouslyBusyChannelHandler;
											}
										}
										
										if (channelHandler == null) {
											channelHandler = new HTTPChannelHandler(HTTPServer.this);
											availableChannelHandlers.put(channelHandler, HASHMAP_VALUE);
										}
										channelHandler.add(clientSocketChannel);
									}
									catch (IOException e) {
										Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "IOException on accepting new connection to ServerSocketChannel. ClientSocketChannel", clientSocketChannel, "exception", e);
									}
								}

							}
						}
					}
				}
			}
			catch (RuntimeException e) {
				Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "Exception in HTTP Server", e);
			}
		}
	}
	
	private class MyRemoveUnusedChannelHandlersTimerTask
	extends TimerTask {

		@Override
		public void run() {
			synchronized (HTTPServer.this) {
				List<HTTPChannelHandler> noLongerBusyChannelHandlers = new ArrayList<>();
				
				/* Some handlers may have reported themselves busy, but then had no subsequent activity,
				 * so are blocking on Selector.select(). Can stop counting them as busy.
				 */
				for (Iterator<HTTPChannelHandler> channelHandlerIterator = busyChannelHandlers.keySet().iterator(); channelHandlerIterator.hasNext();) {
					HTTPChannelHandler httpChannelHandler = channelHandlerIterator.next();
					if (httpChannelHandler.getPercentageTimeActiveInLastPeriod() == 0) {
						channelHandlerIterator.remove();
						noLongerBusyChannelHandlers.add(httpChannelHandler);
					}
				}
				
				for (HTTPChannelHandler channelHandler : noLongerBusyChannelHandlers) {
					availableChannelHandlers.put(channelHandler, HASHMAP_VALUE);
				}
				
				for (Iterator<HTTPChannelHandler> channelHandlerIterator = availableChannelHandlers.keySet().iterator(); channelHandlerIterator.hasNext();) {
					HTTPChannelHandler httpChannelHandler = channelHandlerIterator.next();

					if (httpChannelHandler.getNumActiveKeys() == 0
							&& httpChannelHandler.getDurationSinceLastActivityMillis() >= 10000 ) {
						httpChannelHandler.stop();
						channelHandlerIterator.remove();
					}
				}
			}
		}
	}

	public HTTPServer(int aPortNumber, boolean aLocalHost) {
		setPortNumber(aPortNumber);
		localHost = aLocalHost;
	}
	
	public int getPortNumber() {
		return portNumber;
	}

	public void setPortNumber(int aPortNumber) {
		portNumber = aPortNumber;
	}
	
	public synchronized void start() throws IOException {
		if ( listen ) {
			throw new IllegalStateException("Already started");
		}
		
		InetSocketAddress inetSocketAddress = new InetSocketAddress(getPortNumber());
		
		/* Turns out we don't need all this. Can just give only the port number, and
		 * then both localhost and external connections will work.
		 * 
		 * TODO make the same fix for websocket server
		 */
//		if (isLocalHost()) {
//			inetSocketAddress = new InetSocketAddress("localhost", portNumber);
//		}
//		else {
//			InetAddress inetAddressToUse = null;
//			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
//			while ( networkInterfaces.hasMoreElements() ) {
//				NetworkInterface networkInterface = networkInterfaces.nextElement();
//				if ( networkInterface.isUp()
//						&& ! networkInterface.isLoopback() ) {
//					Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
//					while ( inetAddresses.hasMoreElements() ) {
//						InetAddress inetAddress = inetAddresses.nextElement();
//						if ( inetAddress.getHostAddress() != null
//								&& inetAddress.getHostAddress().matches("\\d{1,3}(\\.\\d{1,3}){3}" ) ) {
//							inetAddressToUse = inetAddress;
//						}
//					}
//				}
//			}
//
//			if (inetAddressToUse != null) {
//				inetSocketAddress = new InetSocketAddress(inetAddressToUse, portNumber);
//			}
//		}
		
		if ( inetSocketAddress == null ) {
			System.err.println("Couldn't find valid inetAddress for HTTP server");
			System.exit(-1);
		}
		else {
			int portNumber = getPortNumber();
			Thread thread = new Thread( new MyListenForConnectionsRunnable(), "HTTPServer-" + portNumber + ( localHost ? " (localhost)" : "" ) );
			serverSocketChannel = ServerSocketChannel.open();
			serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			serverSocketChannel.configureBlocking(false);
			selector = Selector.open();
			serverKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			serverSocketChannel.bind( inetSocketAddress );
			listen = true;
			thread.start();
			
			removeUnusedChannelHandlersTimer = new Timer("Remove unused HTTP channel handlers", true);
			removeUnusedChannelHandlersTimer.schedule(new MyRemoveUnusedChannelHandlersTimerTask(), 10000, 10000);

			Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Serving HTTP requests at address", inetSocketAddress.getAddress(), "on port", portNumber );
		}
	}

	public synchronized void stop() {
		listen = false;
		if (selector != null) {
			try {
				selector.close(); // unblocks the selector.select() call in the other thread
			} catch (IOException e) {
				Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "IOException when closing HTTPServer Selector", e);
			}
			selector = null;
		}
		if (serverKey != null) {
			serverKey.cancel();
			serverKey = null;
		}
		if (serverSocketChannel != null) {
			try {
				serverSocketChannel.close();
			} catch (IOException e) {
				Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "IOException when closing HTTPServer ServerSocketChannel", e);
			}
			serverSocketChannel = null;
		}
		if (removeUnusedChannelHandlersTimer != null) {
			removeUnusedChannelHandlersTimer.cancel();
			removeUnusedChannelHandlersTimer = null;
		}
	}

	public boolean isLocalHost() {
		return localHost;
	}

	public static void main(String[] args) throws IOException {
		Log.addLogger(LogMessageSubject.GENERAL, new Logger(null, new PrintStreamOutputter(System.out)));
		new HTTPServer(8000, true).start();
	}
	
	public void addHandler(AHttpHandler aHandler) {
		addHandler(aHandler.getContextName(), aHandler);
	}
	
	public void addHandler(String aURI, AHttpHandler aHandler) {
		try {
			URI uri = new URI(aURI);
			handlerMap.put(uri, aHandler);
		} catch (URISyntaxException e) {
			throw new RuntimeException("URISyntaxException", e);
		}
	}

	public void handle(HTTPExchange aExchange) {
//		System.out.print(aExchange.getCompleteRequest());
		
		URI uri = aExchange.getRequestURI();
		AHttpHandler handler = handlerMap.get(uri);
		try {
			if (handler == null) {
				aExchange.setResponseHeaders(HTTPResponseConstants.Not_Found_404, 0);
				aExchange.sendResponse();
				aExchange.close();
			}
			else {
				handler.handleWrapper(new HTTPExchangeWrapper(aExchange));
			}
		}
		catch (IOException e) {
			Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "IOException handling exchange", aExchange, "URI", uri, "handler", handler, "exception", e);
		}
	}

	public void reportActivityLevel(HTTPChannelHandler aChannelHandler, int aPercentageTimeActiveInLastPeriod) {
		if (aPercentageTimeActiveInLastPeriod > 50) {
			if (! busyChannelHandlers.containsKey(aChannelHandler)) {
					availableChannelHandlers.remove(aChannelHandler);
					busyChannelHandlers.put(aChannelHandler, HASHMAP_VALUE);
			}
		}
		else if ( ! availableChannelHandlers.containsKey(aChannelHandler)) {
			busyChannelHandlers.remove(aChannelHandler);
			availableChannelHandlers.put(aChannelHandler, HASHMAP_VALUE);
		}
	}
}
