package com.merman.celebrity.server.exceptions;

public class NullSessionException extends RuntimeException {

	public NullSessionException() {
		super();
	}

	public NullSessionException(String aMessage, Throwable aCause) {
		super(aMessage, aCause);
	}

	public NullSessionException(String aMessage) {
		super(aMessage);
	}

	public NullSessionException(Throwable aCause) {
		super(aCause);
	}

}
