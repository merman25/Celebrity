package com.merman.celebrity.server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

import org.json.JSONObject;

import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.server.handlers.AnnotatedHandlers;
import com.merman.celebrity.server.handlers.AnnotatedMethodBasedHttpHandler;
import com.merman.celebrity.server.handlers.ServeFileHandler;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.info.LogInfo;
import com.sun.net.httpserver.HttpServer;

public class Server {
	public static final Path CLIENT_FILE_DIRECTORY = new File( "../../client" ).toPath();
	public static final String MAIN_FILE_NAME = "celebrity.html";
	private static final List<String> FILE_TO_ADD_WHITELIST = new ArrayList<>( Arrays.asList(
			MAIN_FILE_NAME,
			"styles.css",
			"js/preload.js",
			"js/script.js",
			"js/dom-manipulation.js",
			"js/api-calls.js",
			"icons/happy-emoji.svg",
			"icons/sad-emoji.svg",
			"icons/thinking-emoji.svg",
			"icons/exit-icon.svg"
    ) );
	
	public static final List<String> ICON_LIST = new ArrayList<>();
	
	private int portNumber = 8000;

	private List<File> gameFileList;

	public Server(int aPortNumber, List<File> aGameFileList) {
		portNumber = aPortNumber;
		gameFileList = aGameFileList;
	}
	
	public Server(List<File> aGameFileList) {
		gameFileList = aGameFileList;
	}

	public void start() throws IOException {
		if ( gameFileList != null ) {
			for ( File gameFile : gameFileList ) {
				if ( gameFile.exists() ) {
					byte[] fileAsByteArr = Files.readAllBytes( gameFile.toPath() );
					String	fileAsString = new String(fileAsByteArr, StandardCharsets.UTF_8);
					JSONObject jsonObject = new JSONObject(fileAsString);
					GameManager.restoreGame(jsonObject);
				}
			}
		}
		
		HttpServer server = HttpServer.create(new InetSocketAddress( portNumber ), 10);
		
		Files.list( CLIENT_FILE_DIRECTORY.resolve( Paths.get( "icons", "christmas" ) ) )
			.filter( path -> path.getFileName().toString().toLowerCase().endsWith(".svg" ) )
			.map( path -> CLIENT_FILE_DIRECTORY.relativize( path ).toString() )
			.map( pathString -> pathString.replace( File.separator, "/" ) )
			.forEach( path -> ICON_LIST.add( path.toString() ) );
		
		FILE_TO_ADD_WHITELIST.addAll( ICON_LIST );

		for ( String fileRelativePath : FILE_TO_ADD_WHITELIST ) {
			if ( Files.exists( CLIENT_FILE_DIRECTORY.resolve( Paths.get(fileRelativePath) ))) {
				Log.log(LogInfo.class, "adding file", fileRelativePath);
				String context = "/" + fileRelativePath;
				if ( MAIN_FILE_NAME.equals(fileRelativePath)) {
					context = "/";
				}
				server.createContext(context, new ServeFileHandler(fileRelativePath));
			}
		}

		List<AnnotatedMethodBasedHttpHandler> handlers = AnnotatedMethodBasedHttpHandler.createHandlers(AnnotatedHandlers.class);
		for ( AnnotatedMethodBasedHttpHandler handler : handlers ) {
			Log.log(LogInfo.class, "adding context", handler.getContextName());
			server.createContext("/" + handler.getContextName(), handler);
		}
		
		server.setExecutor( Executors.newFixedThreadPool(10) );
		server.start();
		Log.log(LogInfo.class, "Serving HTTP requests on port " + portNumber);
		
		/* The client computes the URL at which to open a WebSocket by looking at the URL of the current page.
		 * Having a listener at address localhost means that local testing can always be done at
		 * http://localhost:8000, and the request to open a WebSocket will still work.
		 * 
		 * If we have only the external WebSocket listener, local testing always needs to be done at the
		 * server's IP address, which changes depending on which machine is doing the testing.
		 * 
		 * The external WebSocket listener handles all requests from other machines (which obviously still
		 * need to know the server IP address or URL).
		 */
		IncomingWebsocketListener externalWebsocketListener = new IncomingWebsocketListener(8001, false);
		externalWebsocketListener.start();
		IncomingWebsocketListener localhostWebsocketListener = new IncomingWebsocketListener(8001, true);
		localhostWebsocketListener.start();
	}
}
