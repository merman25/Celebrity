package com.merman.celebrity.game.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONObject;

import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.server.WebsocketHandler;
import com.merman.celebrity.util.JSONUtil;

public class NotifyClientGameEventListener implements IGameEventListener {
	private WebsocketHandler websocketHandler;

	public NotifyClientGameEventListener(WebsocketHandler aWebsocketHandler) {
		websocketHandler = aWebsocketHandler;
	}

	@Override
	public void gameEvent(GameEvent aEvent) {
		websocketHandler.enqueueMessage("JSON=" + toJSONString(aEvent));
	}

	public WebsocketHandler getWebsocketHandler() {
		return websocketHandler;
	}
	
	private String toJSONString(GameEvent aGameEvent) {
		Map<String, String> basePropertyMap = aGameEvent.toPropertyMap();
		Map<String, String>	propertyMap		= new HashMap<>();
		for (Entry<String, String> mapEntry : basePropertyMap.entrySet()) {
			propertyMap.put("GameEvent" + mapEntry.getKey(), mapEntry.getValue());
		}
		propertyMap.put("GameEventType", aGameEvent.getClass().getSimpleName().replaceAll("(Game)?Event", ""));
		
		JSONObject jsonObject = JSONUtil.toJSONObject(propertyMap);
		
		if ( ! ( aGameEvent instanceof TurnTimeRemainingEvent ) ) {
			JSONObject gameJSONObject = GameManager.toJSONObject(aGameEvent.getGame(), websocketHandler.getSession().getSessionID(), true);
			jsonObject = JSONUtil.combine( jsonObject, gameJSONObject );
			propertyMap.put("GameState", GameManager.serialise(aGameEvent.getGame(), websocketHandler.getSession().getSessionID(), true));
		}
		String jsonString = JSONUtil.serialiseMap(propertyMap);
		
		return jsonObject.toString();
	}
}
