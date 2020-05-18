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

	public IncomingWebsocketListener(int aPortNumber) {
		setPortNumber(aPortNumber);
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
		
		if ( inetAddressToUse == null ) {
			System.err.println("Couldn't find valid inetAddress for websocket server");
			System.exit(-1);
		}
		else {
			int portNumber = getPortNumber();
			Thread thread = new Thread( new MyListenForConnectionsRunnable(), "IncomingWebsocketListener-" + portNumber );
			serverSocket = new ServerSocket();
			serverSocket.setReuseAddress(true);
			serverSocket.bind( new InetSocketAddress( inetAddressToUse, portNumber ) );
			listen = true;
			thread.start();

			Log.log(LogInfo.class, String.format( "Listening for websockets at address %s on port %d", inetAddressToUse, portNumber ) );
		}
	}

	/**
	 * Will not stop until a new client connects, because no timeout set on server socket.
	 */
	public void stop() {
		listen = false;
	}
}
