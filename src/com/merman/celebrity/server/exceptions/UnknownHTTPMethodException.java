package com.merman.celebrity.server.exceptions;

public class UnknownHTTPMethodException extends HTTPException {

	public UnknownHTTPMethodException() {
	}

	public UnknownHTTPMethodException(String aMessage) {
		super(aMessage);
	}

	public UnknownHTTPMethodException(Throwable aCause) {
		super(aCause);
	}

	public UnknownHTTPMethodException(String aMessage, Throwable aCause) {
		super(aMessage, aCause);
	}

	public UnknownHTTPMethodException(String aMessage, Throwable aCause, boolean aEnableSuppression, boolean aWritableStackTrace) {
		super(aMessage, aCause, aEnableSuppression, aWritableStackTrace);
	}

}
