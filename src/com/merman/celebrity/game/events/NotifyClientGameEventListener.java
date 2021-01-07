package com.merman.celebrity.game.events;

import java.util.Map;

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
		Map<String, String> propertyMap = aGameEvent.toPropertyMap();
		propertyMap.put("Type", aGameEvent.getClass().getSimpleName().replaceAll("(Game)?Event", ""));
		if ( ! ( aGameEvent instanceof TurnTimeRemainingEvent ) ) {
			propertyMap.put("GameState", GameManager.serialise(aGameEvent.getGame(), websocketHandler.getSession().getSessionID(), true));
		}
		String jsonString = JSONUtil.serialiseMap(propertyMap);
		
		return jsonString;
	}
}
