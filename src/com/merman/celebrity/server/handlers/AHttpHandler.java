package com.merman.celebrity.server.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONException;

import com.merman.celebrity.game.Player;
import com.merman.celebrity.server.HTTPResponseConstants;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.exceptions.IllegalServerRequestException;
import com.merman.celebrity.server.exceptions.NullSessionException;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.merman.celebrity.util.JSONUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

public abstract class AHttpHandler implements IContextHandler {

	@Override
	public void handle(HttpExchange aHttpExchange) throws IOException {
		String sessionID = null;
		Session session = null;
		InetSocketAddress remoteAddress = aHttpExchange.getRemoteAddress();
		InetAddress address = remoteAddress == null ? null : remoteAddress.getAddress();

		try {
//			dumpRequest(aHttpExchange);
			
			HttpExchangeUtil.logBytesReceived(aHttpExchange);
			sessionID = HttpExchangeUtil.getSessionID(aHttpExchange);
			session = null;
			if ( sessionID != null ) {
				session = SessionManager.getSession(sessionID);
				
				if (session != null) {
					session.resetExpiryTime();
				}
			}
			
			Log.log(LogMessageType.DEBUG, LogMessageSubject.HTTP_REQUESTS, "context", getContextName(), "player", session == null ? null : session.getPlayer(), "session", session, "game", session == null ? null : session.getPlayer().getGame(), "request body", HttpExchangeUtil.getRequestBody(aHttpExchange));
			
			Map<String,Object> requestBodyAsMap = HttpExchangeUtil.getRequestBodyAsMap(aHttpExchange);
			_handle( session, requestBodyAsMap, aHttpExchange );
		}
		catch (NullSessionException e) {
			sendErrorResponse(aHttpExchange, ServerErrors.NO_SESSION);
		}
		catch (IllegalServerRequestException e) {
			Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, e.getClass().getName(), "==>", e.getMessage());
			sendErrorResponse(aHttpExchange, ServerErrors.ILLEGAL_REQUEST, e.getEndUserMessage());
		}
		catch (InvalidJSONException e) {
			Player player = session == null ? null : session.getPlayer();
			Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "Session", session, "Player", player, "Handler", getContextName(), "IP", address, e.getMessage());
		}
		catch (JSONException e) {
			Player player = session == null ? null : session.getPlayer();
			Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "Session", session, "Player", player, "Handler", getContextName(), "IP", address, "JSONException", e);
		}
		catch (RuntimeException e) {
			Player player = session == null ? null : session.getPlayer();
			Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "Session", session, "Player", player, "Handler", getContextName(), "Request body", HttpExchangeUtil.getRequestBody(aHttpExchange), "IP", address, "Exception on HTTP request", e);
		}
	}

	protected abstract void _handle(Session aSession, Map<String, Object> aRequestBodyAsMap, HttpExchange aHttpExchange) throws IOException;
	
	protected void sendResponse( HttpExchange aExchange, int aCode, String aResponse ) throws IOException {
		byte[] responseBytes = aResponse.getBytes(StandardCharsets.UTF_8);
		int bodyLength = responseBytes.length;
		if (aResponse != null ) {
			if ( aResponse.startsWith("{")
					&& aResponse.endsWith("}") ) {
				aExchange.getResponseHeaders().put("content-type", Arrays.asList( "application/json" ));
			}
			else {
				aExchange.getResponseHeaders().put("content-type", Arrays.asList( "text/html" ) );
			}
		}
		aExchange.sendResponseHeaders(aCode, bodyLength);
		OutputStream os = aExchange.getResponseBody();
		os.write(responseBytes);
		os.close();
		
		HttpExchangeUtil.logBytesSent(aExchange, bodyLength);
	}

	protected void dumpRequest(HttpExchange aExchange) {
		Headers requestHeaders = aExchange.getRequestHeaders();
		System.out.println( "Received request: " + getContextName() );
		System.out.format("Request Method: %s\n", aExchange.getRequestMethod() );
		System.out.format("Request URI: %s\n", aExchange.getRequestURI() );
		System.out.format("Path: %s\n", aExchange.getHttpContext().getPath() );
		System.out.format("From: %s\n", aExchange.getRemoteAddress().getAddress() );
		for ( Entry<String, List<String>> l_mapEntry : requestHeaders.entrySet() ) {
			System.out.format("%s:\t%s\n", l_mapEntry.getKey(), String.join( ",", l_mapEntry.getValue() ));
		}
		System.out.println("\n");
		System.out.println("Request body:");
		System.out.println( String.join("\n", HttpExchangeUtil.getRequestBody(aExchange) ) );
		System.out.println( "End request body\n\n" );
	}
	
	protected void sendErrorResponse(HttpExchange aHttpExchange, ServerErrors aServerError) throws IOException {
		sendErrorResponse(aHttpExchange, aServerError, null);
	}
	
	protected void sendErrorResponse(HttpExchange aHttpExchange, ServerErrors aServerError, String aErrorMessage) throws IOException {
		Map<String, String>	responseObject		= new HashMap<>();
		responseObject.put("Error", aServerError.toString());
		if ( aErrorMessage != null ) {
			responseObject.put("Message", aErrorMessage);
		}
		String responseString = JSONUtil.serialiseMap(responseObject);
		
		/* You would think we would send HTTPResponseConstants.Bad_Request (400) here.
		 * If we do that, we enter the 'catch' block of the 'fetch' request. However,
		 * there doesn't seem to be any way to access the response string from there.
		 * 
		 * So, we tell the browser the result was OK, but provide enough data to the
		 * client code for it to do the right thing.
		 * 
		 * Try using https://www.w3schools.com/howto/tryit.asp?filename=tryhow_css_modal_bottom
		 */
		sendResponse(aHttpExchange, HTTPResponseConstants.OK, responseString);
	}
}
