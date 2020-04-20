package com.merman.celebrity.server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

import com.merman.celebrity.server.handlers.AnnotatedHandlers;
import com.merman.celebrity.server.handlers.AnnotatedMethodBasedHttpHandler;
import com.merman.celebrity.server.handlers.FormHandler;
import com.merman.celebrity.server.handlers.FormHandlerRegistry;
import com.merman.celebrity.server.handlers.ServeFileHandler;
import com.sun.net.httpserver.HttpServer;

public class Server {
	public static final Path CLIENT_FILE_DIRECTORY = new File( "../../client" ).toPath();
	
	private String IPAddress = "192.168.1.17";
	private int portNumber = 8080;
	
	private class ListenForNewConnectionsRunnable
	implements Runnable {

		@Override
		public void run() {
			while ( true ) {
				try {
					ServerSocket		socket		= new ServerSocket();
					socket.setSoTimeout(1000);
					socket.bind( new InetSocketAddress( InetAddress.getByName( IPAddress ), portNumber ));
				}
				catch ( Exception e ) {
					e.printStackTrace();
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				}
			}
		}
	}

	public void start() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress( portNumber ), 10);

		Files.walk( CLIENT_FILE_DIRECTORY )
		.forEach( path -> {
			if ( Files.isRegularFile(path) ) {
				String pathAsString = CLIENT_FILE_DIRECTORY.relativize(path).toString();
				System.out.println( "adding file: " + pathAsString );
				server.createContext("/" + pathAsString, new ServeFileHandler(pathAsString));
			}
		} );

		System.out.println("\n\n");

		List<AnnotatedMethodBasedHttpHandler> handlers = AnnotatedMethodBasedHttpHandler.createHandlers(AnnotatedHandlers.class);
		for ( AnnotatedMethodBasedHttpHandler handler : handlers ) {
			if ( handler.getRequestType() == RequestType.GET_OR_POST ) {
				server.createContext("/" + handler.getContextName(), handler);
			}
			else if ( handler.getRequestType() == RequestType.FORM ) {
				FormHandlerRegistry.addHandler(new FormHandler(handler.getContextName(), handler) {

					@Override
					public boolean hasResponded() {
						return true;
					}

				});
			}
			else {
				System.err.println("Unknown request type: " + handler.getRequestType());
			}
		}

		server.setExecutor( Executors.newFixedThreadPool(10) );
		server.start();
	}
}
