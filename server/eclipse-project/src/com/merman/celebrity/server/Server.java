package com.merman.celebrity.server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Executors;

import com.merman.celebrity.server.handlers.AHttpHandler;
import com.merman.celebrity.server.handlers.AnnotatedHandlers;
import com.merman.celebrity.server.handlers.AnnotatedMethodBasedHttpHandler;
import com.merman.celebrity.server.handlers.FormHandler;
import com.merman.celebrity.server.handlers.FormHandlerRegistry;
import com.merman.celebrity.server.handlers.ServeFileHandler;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class Server {
	public static final Path CLIENT_FILE_DIRECTORY = new File( "../../client" ).toPath();
	
	private int portNumber = 8080;
//	private int portNumber = 80;
	
	public void start() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress( portNumber ), 10);
		MyHttpsServer httpsServer = new MyHttpsServer();

		Files.walk( CLIENT_FILE_DIRECTORY )
		.forEach( path -> {
			if ( Files.isRegularFile(path) ) {
				String pathAsString = CLIENT_FILE_DIRECTORY.relativize(path).toString();
				System.out.println( "adding file: " + pathAsString );
				server.createContext("/" + pathAsString, new ServeFileHandler(pathAsString));
				httpsServer.createContext("/" + pathAsString, new ServeFileHandler(pathAsString));
			}
		} );

		System.out.println("\n\n");

		List<AnnotatedMethodBasedHttpHandler> handlers = AnnotatedMethodBasedHttpHandler.createHandlers(AnnotatedHandlers.class);
		for ( AnnotatedMethodBasedHttpHandler handler : handlers ) {
			if ( handler.getRequestType() == RequestType.GET_OR_POST ) {
//				System.out.println("adding context " + handler.getContextName());
				server.createContext("/" + handler.getContextName(), handler);
				httpsServer.createContext("/" + handler.getContextName(), handler);
			}
			else if ( handler.getRequestType() == RequestType.FORM ) {
//				System.out.println("adding form handler " + handler.getContextName());
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
		
		server.createContext("/tryit", new AHttpHandler() {
			
			@Override
			public void _handle(HttpExchange aExchange) throws IOException {
				System.out.println("in HTTP handler");
				//				dumpRequest(aExchange);

				boolean success = false;
				try {
					Headers requestHeaders = aExchange.getRequestHeaders();
					for ( Entry<String, List<String>> l_mapEntry : requestHeaders.entrySet() ) {
						if ( l_mapEntry.getKey().equals("Sec-websocket-key") ) {
							String key = l_mapEntry.getValue().get(0);
							byte[] response;
							String encodedString = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")));
							response = ("HTTP/1.1 101 Switching Protocols\r\n"
									+ "Connection: Upgrade\r\n"
									+ "Upgrade: websocket\r\n"
									+ "Sec-WebSocket-Accept: "
									+ encodedString
									+ "\r\n\r\n").getBytes("UTF-8");
							
							aExchange.getResponseHeaders().set("Connection", "Upgrade");
							aExchange.getResponseHeaders().set("Upgrade", "websocket");
							aExchange.getResponseHeaders().set("Sec-WebSocket-Accept", encodedString);
							
							aExchange.sendResponseHeaders(HTTPResponseConstants.Switching_Protocols, -1);
//							aExchange.getResponseBody().write(response, 0, response.length);
//							aExchange.getResponseBody().close();
							success = true;
							break;
						}
					}
				}
				catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				if ( success ) {
					System.out.println("did the thing");
				}
				else {
					System.out.println("failed to do the thing");
				}
			}

			@Override
			public String getHandlerName() {
				return "tryitHTTP";
			}
		});
		
//		server.createContext( "/", new TestHandler("") );
//		httpsServer.createContext( "/", new TestHandler("") );

		server.setExecutor( Executors.newFixedThreadPool(10) );
		httpsServer.setExecutor( Executors.newFixedThreadPool(10) );

		server.start();
//		httpsServer.start();
		
		IncomingWebsocketListener websocketListener = new IncomingWebsocketListener(8081);
		websocketListener.start();
	}
}
