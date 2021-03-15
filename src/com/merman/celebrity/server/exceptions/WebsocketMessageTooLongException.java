package com.merman.celebrity.server.exceptions;

public class WebsocketMessageTooLongException
extends WebsocketMessageException {

	public WebsocketMessageTooLongException() {
	}

	public WebsocketMessageTooLongException(String aMessage) {
		super(aMessage);
	}

}
