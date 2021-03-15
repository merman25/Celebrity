package com.merman.celebrity.server.handlers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.game.Player;
import com.merman.celebrity.server.HTTPExchangeWrapper;
import com.merman.celebrity.server.HTTPResponseConstants;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.exceptions.IllegalServerRequestException;
import com.merman.celebrity.server.exceptions.NullSessionException;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;

public class OpenWebsocketRequestHandler extends APredicateBasedHttpHandler {

	private static final String HTTP_HEADER_SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
	private static final String WEBSOCKET_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

	public OpenWebsocketRequestHandler() {
		super(httpExchange -> httpExchange.getRequestHeaders().get(HTTP_HEADER_SEC_WEBSOCKET_KEY) != null);
	}

	@Override
	public String getContextName() {
		return "Open Websocket";
	}

	@Override
	protected void _handle(Session aSession, Map<String, Object> aRequestBodyAsMap, HTTPExchangeWrapper aHttpExchangeWrapper) throws IOException {
		if (aSession == null) {
			throw new NullSessionException();
		}
		
		// TODO send the game state, if there is one, as we do with the old WebsocketHandler
		Player player = aSession.getPlayer();
		Game game = player == null ? null : player.getGame();
		if (game != null) {
			JSONObject jsonObject = GameManager.toJSONObject(game, aSession.getSessionID(), true);
			jsonObject.put("GameEventType", "Refresh WebSocket");
			String jsonString = jsonObject.toString();
//			enqueueMessage("JSON=" + jsonString);
//			game.addGameEventListener(new NotifyClientGameEventListener(WebsocketHandler.this));
		}

		try {
			List<String> l_valueList = aHttpExchangeWrapper.getRequestHeaders().get(HTTP_HEADER_SEC_WEBSOCKET_KEY);
			String websocketKeyAsString = l_valueList.get(l_valueList.size() - 1);
			String stringToHash = websocketKeyAsString + WEBSOCKET_MAGIC_STRING;
			byte[] bytesToHash = stringToHash.getBytes(StandardCharsets.UTF_8);
			
			MessageDigest sha1MessageDigest = MessageDigest.getInstance("SHA-1");
			byte[] sha1Hash = sha1MessageDigest.digest(bytesToHash);
			String sha1HashBase64 = Base64.getEncoder().encodeToString(sha1Hash);
			
			aHttpExchangeWrapper.getResponseHeaders().put("connection", Arrays.asList("Upgrade"));
			aHttpExchangeWrapper.getResponseHeaders().put("Upgrade", Arrays.asList("websocket"));
			aHttpExchangeWrapper.getResponseHeaders().put("Sec-WebSocket-Accept", Arrays.asList(sha1HashBase64));
			
			aHttpExchangeWrapper.sendResponseHeaders(HTTPResponseConstants.Switching_Protocols_101, -1);
			
		} catch (NoSuchAlgorithmException e) {
			Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "SHA-1 MessageDigest not found. Exception", e);
			throw new IllegalServerRequestException("SHA-1 MessageDigest not found", null);
		}
	}
}
