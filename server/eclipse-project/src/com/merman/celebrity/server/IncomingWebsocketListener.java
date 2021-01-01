package com.merman.celebrity.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.info.LogInfo;

public class IncomingWebsocketListener {
	private volatile boolean listen;
	private int portNumber = -1;
	private ServerSocket serverSocket;
	private boolean localHost;
	
	private class MyListenForConnectionsRunnable
	implements Runnable {

		@Override
		public void run() {
			try {
				while (listen) {
					try {
						Socket clientSocket = serverSocket.accept();
						WebsocketHandler handler = new WebsocketHandler(clientSocket);
						handler.start();
					} catch (IOException e) {
						Log.log(LogInfo.class, "Exception in websocket listener", e);
					}
				}
			}
			catch (RuntimeException e) {
				Log.log(LogInfo.class, "Exception in websocket listener", e);
			}
		}
		
	}

	public IncomingWebsocketListener(int aPortNumber, boolean aLocalHost) {
		setPortNumber(aPortNumber);
		localHost = aLocalHost;
	}
	
	public int getPortNumber() {
		return portNumber;
	}

	public void setPortNumber(int aPortNumber) {
		portNumber = aPortNumber;
	}
	
	public void start() throws IOException {
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
			System.err.println("Couldn't find valid inetAddress for websocket server");
			System.exit(-1);
		}
		else {
			int portNumber = getPortNumber();
			Thread thread = new Thread( new MyListenForConnectionsRunnable(), "IncomingWebsocketListener-" + portNumber + ( localHost ? " (localhost)" : "" ) );
			serverSocket = new ServerSocket();
			serverSocket.setReuseAddress(true);
			serverSocket.bind( inetSocketAddress );
			listen = true;
			thread.start();

			Log.log(LogInfo.class, String.format( "Listening for websockets at address %s on port %d", inetSocketAddress.getAddress(), portNumber ) );
		}
	}

	/**
	 * Will not stop until a new client connects, because no timeout set on server socket.
	 */
	public void stop() {
		listen = false;
	}

	public boolean isLocalHost() {
		return localHost;
	}
}
