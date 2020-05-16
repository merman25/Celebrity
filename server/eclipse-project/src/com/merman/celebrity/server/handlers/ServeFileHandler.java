package com.merman.celebrity.server.handlers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import com.merman.celebrity.game.GameStatus;
import com.merman.celebrity.server.HTTPResponseConstants;
import com.merman.celebrity.server.Server;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.WebsocketHandler;
import com.merman.celebrity.server.cleanup.CleanupHelper;
import com.sun.net.httpserver.HttpExchange;

public class ServeFileHandler extends AHttpHandler {
	private String relativePath;

	public ServeFileHandler(String aRelativePath) {
		relativePath = aRelativePath;
	}

	@Override
	protected void _handle(Session aSession, Map<String, Object> aRequestBodyAsMap, HttpExchange aExchange) throws IOException {
//		dumpRequest(aExchange);
		
		if (Server.MAIN_FILE_NAME.equals(relativePath)) {
			if ( aSession == null
					|| aSession.getPlayer() == null
					|| aSession.getPlayer().getGame() == null
					|| aSession.getPlayer().getGame().getStatus() == GameStatus.ENDED ) {
				Session session = SessionManager.createSession();
				aExchange.getResponseHeaders().set("Set-Cookie", String.format("session=%s; Max-Age=%s", session.getSessionID(), CleanupHelper.defaultExpiryDurationInS));
			}
			else {
				WebsocketHandler websocketHandler = SessionManager.getWebsocketHandler(aSession);
				if (websocketHandler != null) {
					websocketHandler.stop();
				}
				aExchange.getResponseHeaders().add("Set-Cookie", String.format( "%s=%s; Max-Age=10", HttpExchangeUtil.COOKIE_RESTORE_KEY, HttpExchangeUtil.COOKIE_RESTORE_VALUE) );
			}
		}
		
		
		
		serveFileContent(relativePath, aExchange);
	}

	@Override
	public String getContextName() {
		return "serve file " + relativePath;
	}
	
	public static void serveFileContent( String aRelativePath, HttpExchange aExchange ) throws IOException {
		File file = new File( Server.CLIENT_FILE_DIRECTORY.toFile(), aRelativePath );
		String response;
		if ( file.exists() ) {
			byte[] bytes = Files.readAllBytes(file.toPath());
			response = new String( bytes, StandardCharsets.UTF_8 );
		}
		else {
			response = "File does not exist: " + file.getAbsolutePath();
		}
		
		if ( aRelativePath.toLowerCase().endsWith(".css") ) {
			aExchange.getResponseHeaders().set("content-type", "text/css");
		}
		else if ( aRelativePath.toLowerCase().endsWith( ".svg" ) ) {
			aExchange.getResponseHeaders().set("content-type", "image/svg+xml" );
		}
		else if ( aRelativePath.toLowerCase().endsWith( ".js") ) {
			aExchange.getResponseHeaders().set("content-type", "text/javascript");
		}
				
		
		int bodyLength = response.getBytes().length;
		aExchange.sendResponseHeaders(HTTPResponseConstants.OK, bodyLength);
		OutputStream os = aExchange.getResponseBody();
		os.write(response.getBytes());
		os.close();
		
		HttpExchangeUtil.logBytesSent(aExchange, bodyLength);
	}

}
