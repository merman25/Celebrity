package com.merman.celebrity.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

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
	
	private ByteBuffer readWriteBuffer = ByteBuffer.allocate(1024 * 1024); // 1 MB buffer. As of 16/1/21, total size of client directory is 312 kB, so 1 MB is loads.
	
	private Map<SelectionKey, SocketChannel>		mapSelectionKeysToClientChannels		= new HashMap<>();
	
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
						Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "IOException on selector", selector, "select method", e);
						
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

										clientSocketChannel.configureBlocking(false);
										SelectionKey clientKey = clientSocketChannel.register(selector, SelectionKey.OP_READ); // Not registered for writing. TBC if we can write regardless

										mapSelectionKeysToClientChannels.put(clientKey, clientSocketChannel);

										Log.log(LogMessageType.DEBUG, LogMessageSubject.HTTP_REQUESTS, "New connection accepted from address", clientSocketChannel.getRemoteAddress(), "Num connections", mapSelectionKeysToClientChannels.size());
									}
									catch (IOException e) {
										Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "IOException on serverSocketChannel.accept(). ClientSocketChannel", clientSocketChannel, "exception", e);
									}
								}

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
											key.cancel();
											mapSelectionKeysToClientChannels.remove(key);
										}
										else if (bytesRead > 0) {
											// We do sometimes read 0 bytes, for whatever reason
											byte[] byteArr = new byte[bytesRead];
											readWriteBuffer.flip();
											readWriteBuffer.get(byteArr);
											
											String string = new String(byteArr, StandardCharsets.US_ASCII);
											string = string.replaceAll("\r(?!\n)", "\\r\r")
															.replaceAll("(?<!\r)\n", "\\n\n")
															.replace("\r\n", "\\r\\n\r\n");
											
											System.out.println("=== BEGIN MESSAGE ===");
											System.out.print(string);
											System.out.println("=== END MESSAGE ===");
										}
										
									} catch (IOException e) {
										Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "IOException when reading clientChannel ", e); // TODO associate with Session and/or IP
									}
								}
							}
						}
					}
				}
			}
			catch (RuntimeException e) {
				Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "Exception in websocket listener", e);
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
		
		InetSocketAddress inetSocketAddress = null;
		if (isLocalHost()) {
			inetSocketAddress = new InetSocketAddress("localhost", portNumber);
		}
		else {
			InetAddress inetAddressToUse = null;
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			while ( networkInterfaces.hasMoreElements() ) {
				NetworkInterface networkInterface = networkInterfaces.nextElement();
				if ( networkInterface.isUp()
						&& ! networkInterface.isLoopback() ) {
					Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
					while ( inetAddresses.hasMoreElements() ) {
						InetAddress inetAddress = inetAddresses.nextElement();
						if ( inetAddress.getHostAddress() != null
								&& inetAddress.getHostAddress().matches("\\d{1,3}(\\.\\d{1,3}){3}" ) ) {
							inetAddressToUse = inetAddress;
						}
					}
				}
			}
			
			if (inetAddressToUse != null) {
				inetSocketAddress = new InetSocketAddress(inetAddressToUse, portNumber);
			}
		}
		
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

			Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Serving HTTP requests at address", inetSocketAddress.getAddress(), "on port", portNumber );
		}
	}

	/**
	 * Will not stop until a new client connects, because no timeout set on server socket.
	 */
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
	}

	public boolean isLocalHost() {
		return localHost;
	}

	public static void main(String[] args) throws IOException {
		Log.addLogger(LogMessageSubject.GENERAL, new Logger(null, new PrintStreamOutputter(System.out)));
		new HTTPServer(8000, true).start();
	}
}
