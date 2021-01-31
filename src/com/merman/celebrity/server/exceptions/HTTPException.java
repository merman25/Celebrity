package com.merman.celebrity.server.exceptions;

public class HTTPException extends RuntimeException {

	public HTTPException() {
	}

	public HTTPException(String aMessage) {
		super(aMessage);
	}

	public HTTPException(Throwable aCause) {
		super(aCause);
	}

	public HTTPException(String aMessage, Throwable aCause) {
		super(aMessage, aCause);
	}

	public HTTPException(String aMessage, Throwable aCause, boolean aEnableSuppression, boolean aWritableStackTrace) {
		super(aMessage, aCause, aEnableSuppression, aWritableStackTrace);
	}
}
