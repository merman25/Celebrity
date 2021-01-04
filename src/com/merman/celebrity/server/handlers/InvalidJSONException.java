package com.merman.celebrity.server.handlers;

public class InvalidJSONException extends RuntimeException {

	public InvalidJSONException() {
		super();
	}

	public InvalidJSONException(String aMessage, Throwable aCause) {
		super(aMessage, aCause);
	}

	public InvalidJSONException(String aMessage) {
		super(aMessage);
	}

	public InvalidJSONException(Throwable aCause) {
		super(aCause);
	}
}
