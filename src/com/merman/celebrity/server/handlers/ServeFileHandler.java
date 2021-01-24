package com.merman.celebrity.server.handlers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import com.merman.celebrity.client.theme.ThemeManager;
import com.merman.celebrity.server.CelebrityMain;
import com.merman.celebrity.server.HTTPExchangeWrapper;
import com.merman.celebrity.server.HTTPResponseConstants;
import com.merman.celebrity.server.Server;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.WebsocketHandler;
import com.merman.celebrity.server.analytics.Browser;
import com.merman.celebrity.server.analytics.UserAgentUtil;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;

public class ServeFileHandler extends AHttpHandler {
	private String relativePath;

	public ServeFileHandler(String aRelativePath) {
		relativePath = aRelativePath;
	}

	@Override
	protected void _handle(Session aSession, Map<String, Object> aRequestBodyAsMap, HTTPExchangeWrapper aExchangeWrapper) throws IOException {
		
//		dumpRequest(aExchange);
		if (Server.MAIN_FILE_NAME.equals(relativePath)
				&& "/".equals( aExchangeWrapper.getRequestURI().toString() )) {
			if ( aSession == null
					|| aSession.getPlayer() == null
					|| aSession.getPlayer().getGame() == null
					|| aSession.getPlayer().getGame().isExpired() ) {
				Session session = SessionManager.createSession();
				HttpExchangeUtil.setCookieResponseHeader(session, aExchangeWrapper);
				aExchangeWrapper.getResponseHeaders().computeIfAbsent("Set-Cookie", s -> new ArrayList<>()).add( String.format( "theme=%s; Max-Age=7200", ThemeManager.getCurrentTheme().getName() ) );

				
				InetSocketAddress remoteAddress = aExchangeWrapper.getRemoteAddress();
				InetAddress address = remoteAddress == null ? null : remoteAddress.getAddress();
				session.setOriginalInetAddress(address);
				
				Browser browser = null;
				String operatingSystem = null;
				String userAgentString = HttpExchangeUtil.getHeaderValue("User-agent", aExchangeWrapper);
				if (userAgentString != null) {
					browser = UserAgentUtil.getBrowserFromUserAgent(userAgentString);
					operatingSystem = UserAgentUtil.getOperatingSystemFromUserAgent(userAgentString);
				}
				
				Log.log(LogMessageType.INFO, LogMessageSubject.SESSIONS, "New session", session, "IP", address, "Browser", browser, "OS", operatingSystem, "User-agent", userAgentString );
			}
			else {
				WebsocketHandler websocketHandler = SessionManager.getWebsocketHandler(aSession);
				if (websocketHandler != null) {
					websocketHandler.stop();
				}
				aExchangeWrapper.getResponseHeaders().computeIfAbsent("Set-Cookie", s -> new ArrayList<>()).add( String.format( "%s=%s; Max-Age=10", HttpExchangeUtil.COOKIE_RESTORE_KEY, HttpExchangeUtil.COOKIE_RESTORE_VALUE) );
			}
		}
		
		
		
		serveFileContent(relativePath, aExchangeWrapper);
	}

	@Override
	public String getContextName() {
		return "/" + relativePath;
	}
	
	public static void serveFileContent( String aRelativePath, HTTPExchangeWrapper aExchangeWrapper ) throws IOException {
		File file = new File( Server.CLIENT_FILE_DIRECTORY.toFile(), aRelativePath );
		byte[] responseBytes;
		if ( file.exists() ) {
			responseBytes = Files.readAllBytes(file.toPath());
		}
		else if ( "version".equals( aRelativePath ) ) {
			responseBytes = ( "Version: " + CelebrityMain.getVersion() ).getBytes(StandardCharsets.UTF_8);
		}
		else {
			responseBytes = ( "File does not exist: " + aRelativePath ).getBytes(StandardCharsets.UTF_8);
		}
		
		if ( aRelativePath.toLowerCase().endsWith(".css") ) {
			aExchangeWrapper.getResponseHeaders().computeIfAbsent("Content-type", s -> new ArrayList<>()).add("text/css");
		}
		else if ( aRelativePath.toLowerCase().endsWith( ".svg" ) ) {
			aExchangeWrapper.getResponseHeaders().computeIfAbsent("Content-type", s -> new ArrayList<>()).add( "image/svg+xml" );
		}
		else if ( aRelativePath.toLowerCase().endsWith( ".js") ) {
			aExchangeWrapper.getResponseHeaders().computeIfAbsent("Content-type", s -> new ArrayList<>()).add( "text/javascript");
		}
				
		
		int bodyLength = responseBytes.length;
		aExchangeWrapper.sendResponseHeaders(HTTPResponseConstants.OK_200, bodyLength);
		OutputStream os = aExchangeWrapper.getResponseBody();
		if (os != null) {
			os.write(responseBytes);
			os.close();
		}
		else {
			aExchangeWrapper.setResponseBody( responseBytes );
			aExchangeWrapper.sendResponse();
			aExchangeWrapper.close();
		}
		
		HttpExchangeUtil.logBytesSent(aExchangeWrapper, bodyLength);
	}

}
