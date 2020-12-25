package com.merman.celebrity.server.exceptions;

public class IllegalServerRequestException extends RuntimeException {
	private final String endUserMessage;
	
	public IllegalServerRequestException(String aStackTraceMessage, String aEndUserMessage) {
		super(aStackTraceMessage);
		
		if ( aEndUserMessage == null
				|| aEndUserMessage.trim().isEmpty() ) {
			throw new IllegalArgumentException("EndUserMessage is mandatory");
		}
		
		endUserMessage = aEndUserMessage;
	}

	public String getEndUserMessage() {
		return endUserMessage;
	}
}
