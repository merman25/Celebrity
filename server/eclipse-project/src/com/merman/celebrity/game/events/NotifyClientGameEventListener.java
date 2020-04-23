package com.merman.celebrity.game.events;

import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.server.WebsocketHandler;

public class NotifyClientGameEventListener implements IGameEventListener {
	private WebsocketHandler websocketHandler;

	public NotifyClientGameEventListener(WebsocketHandler aWebsocketHandler) {
		websocketHandler = aWebsocketHandler;
	}

	@Override
	public void gameEvent(GameEvent aEvent) {
		if ( aEvent instanceof GameStateUpdateEvent ) {
			websocketHandler.enqueueMessage("GameState=" + GameManager.serialise(aEvent.getGame(), websocketHandler.getSession().getSessionID(), true));
		}
		else if ( aEvent instanceof TurnTimeRemainingEvent ) {
			websocketHandler.enqueueMessage("MillisRemaining=" + ((TurnTimeRemainingEvent) aEvent).getMillisRemaining() );
		}
	}

	public WebsocketHandler getWebsocketHandler() {
		return websocketHandler;
	}
}
