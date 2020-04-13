package com.merman.celebrity.server.handlers;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;

public class TestHandler extends AHttpHandler {
	private String requestName;

	public TestHandler(String aRequestName) {
		requestName = aRequestName;
	}

	@Override
	protected void _handle(HttpExchange aExchange) throws IOException {
		dumpRequest(aExchange);
		
		System.out.println( "inside test handler" );
	}

	@Override
	public String getHandlerName() {
		return requestName;
	}

}
