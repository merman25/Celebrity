package com.merman.celebrity.server.exceptions;

public class HTTPRequestTooLongException extends HTTPException {
	private String request;

	public HTTPRequestTooLongException(String aMessage, String aRequest) {
		super(aMessage);
		request = aRequest;
	}

	public String getRequest() {
		return request;
	}
}
