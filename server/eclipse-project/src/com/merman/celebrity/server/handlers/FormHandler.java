package com.merman.celebrity.server.handlers;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class FormHandler extends AHttpHandler {
	private String formName;
	private HttpHandler handlerImplementation;
	

	public FormHandler(String aFormName) {
		formName = aFormName;
	}

	public FormHandler(String aFormName, HttpHandler aHandlerImplementation) {
		formName = aFormName;
		handlerImplementation = aHandlerImplementation;
	}

	@Override
	protected void _handle(HttpExchange aExchange) throws IOException {
		handlerImplementation.handle(aExchange);
	}
	
	@Override
	public String getHandlerName() {
		return "form - " + getFormName();
	}
	
	public String getFormName() {
		return formName;
	}
}
