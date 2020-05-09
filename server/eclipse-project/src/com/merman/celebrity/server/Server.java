package com.merman.celebrity.server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

import org.json.JSONObject;

import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.server.handlers.AnnotatedHandlers;
import com.merman.celebrity.server.handlers.AnnotatedMethodBasedHttpHandler;
import com.merman.celebrity.server.handlers.ServeFileHandler;
import com.sun.net.httpserver.HttpServer;

public class Server {
	public static final Path CLIENT_FILE_DIRECTORY = new File( "../../client" ).toPath();
	private static final List<String> FILE_TO_ADD_WHITELIST = Arrays.asList(
			"celebrity.html",
			"styles.css",
			"js/preload.js",
			"js/script.js",
			"js/dom-manipulation.js",
			"js/api-calls.js",
			"icons/happy-emoji.svg",
			"icons/sad-emoji.svg",
			"icons/thinking-emoji.svg"
    );
	
	private int portNumber = 8080;
//	private int portNumber = 80;

	private List<File> gameFileList;
	
	public Server(List<File> aGameFileList) {
		gameFileList = aGameFileList;
	}

	public void start() throws IOException {
		if ( gameFileList != null ) {
			for ( File gameFile : gameFileList ) {
				if ( gameFile.exists() ) {
					byte[] fileAsByteArr = Files.readAllBytes( gameFile.toPath() );
					String	fileAsString = new String(fileAsByteArr);
					JSONObject jsonObject = new JSONObject(fileAsString);
					GameManager.restoreGame(jsonObject);
				}
			}
		}
		
		HttpServer server = HttpServer.create(new InetSocketAddress( portNumber ), 10);

//		Files.walk( CLIENT_FILE_DIRECTORY )
//		.forEach( path -> {
//			if ( Files.isRegularFile(path) ) {
//				String pathAsString = CLIENT_FILE_DIRECTORY.relativize(path).toString().replace('\\', '/');
//				if ( FILE_TO_ADD_WHITELIST.contains(pathAsString) ) {
//					System.out.println( "adding file: " + pathAsString );
//					server.createContext("/" + pathAsString, new ServeFileHandler(pathAsString));
//				}
//			}
//		} );
		for ( String fileRelativePath : FILE_TO_ADD_WHITELIST ) {
			if ( Files.exists( CLIENT_FILE_DIRECTORY.resolve( Paths.get(fileRelativePath) ))) {
				System.out.println( "adding file: " + fileRelativePath );
				server.createContext("/" + fileRelativePath, new ServeFileHandler(fileRelativePath));
			}
		}

		System.out.println("\n\n");

		List<AnnotatedMethodBasedHttpHandler> handlers = AnnotatedMethodBasedHttpHandler.createHandlers(AnnotatedHandlers.class);
		for ( AnnotatedMethodBasedHttpHandler handler : handlers ) {
			System.out.println("adding context " + handler.getContextName());
			server.createContext("/" + handler.getContextName(), handler);
		}
		
		server.setExecutor( Executors.newFixedThreadPool(10) );
		server.start();
		
		IncomingWebsocketListener websocketListener = new IncomingWebsocketListener(8001);
		websocketListener.start();
	}
}
