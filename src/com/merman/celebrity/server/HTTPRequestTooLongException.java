package com.merman.celebrity.server;

public class HTTPRequestTooLongException extends RuntimeException {
	private String request;

	public HTTPRequestTooLongException(String aMessage, String aRequest) {
		super(aMessage);
		request = aRequest;
	}

	public String getRequest() {
		return request;
	}
}
