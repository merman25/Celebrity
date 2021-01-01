package com.merman.celebrity.server.handlers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import com.merman.celebrity.client.theme.ThemeManager;
import com.merman.celebrity.server.HTTPResponseConstants;
import com.merman.celebrity.server.Server;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.WebsocketHandler;
import com.merman.celebrity.server.analytics.Browser;
import com.merman.celebrity.server.analytics.UserAgentUtil;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.info.SessionLogInfo;
import com.sun.net.httpserver.HttpExchange;

public class ServeFileHandler extends AHttpHandler {
	private String relativePath;

	public ServeFileHandler(String aRelativePath) {
		relativePath = aRelativePath;
	}

	@Override
	protected void _handle(Session aSession, Map<String, Object> aRequestBodyAsMap, HttpExchange aExchange) throws IOException {
		
//		dumpRequest(aExchange);
		if (Server.MAIN_FILE_NAME.equals(relativePath)
				&& "/".equals( aExchange.getRequestURI().toString() )) {
			if ( aSession == null
					|| aSession.getPlayer() == null
					|| aSession.getPlayer().getGame() == null
					|| aSession.getPlayer().getGame().isExpired() ) {
				Session session = SessionManager.createSession();
				HttpExchangeUtil.setCookieResponseHeader(session, aExchange);
				aExchange.getResponseHeaders().add( "Set-Cookie", String.format( "theme=%s; Max-Age=7200", ThemeManager.getCurrentTheme().getName() ) );

				
				InetSocketAddress remoteAddress = aExchange.getRemoteAddress();
				InetAddress address = remoteAddress == null ? null : remoteAddress.getAddress();
				session.setOriginalInetAddress(address);
				
				Browser browser = null;
				String operatingSystem = null;
				String userAgentString = HttpExchangeUtil.getHeaderValue("User-agent", aExchange);
				if (userAgentString != null) {
					browser = UserAgentUtil.getBrowserFromUserAgent(userAgentString);
					operatingSystem = UserAgentUtil.getOperatingSystemFromUserAgent(userAgentString);
				}
				
				Log.log(SessionLogInfo.class, "New session", session, "IP", address, "Browser", browser, "OS", operatingSystem, "User-agent", userAgentString );
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
		byte[] responseBytes;
		if ( file.exists() ) {
			responseBytes = Files.readAllBytes(file.toPath());
		}
		else {
			responseBytes = ( "File does not exist: " + aRelativePath ).getBytes(StandardCharsets.UTF_8);
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
				
		
		int bodyLength = responseBytes.length;
		aExchange.sendResponseHeaders(HTTPResponseConstants.OK, bodyLength);
		OutputStream os = aExchange.getResponseBody();
		os.write(responseBytes);
		os.close();
		
		HttpExchangeUtil.logBytesSent(aExchange, bodyLength);
	}

}
