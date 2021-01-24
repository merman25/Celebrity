package com.merman.celebrity.server.handlers;

import java.io.IOException;

import com.merman.celebrity.server.HTTPExchangeWrapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public interface IContextHandler extends HttpHandler {
	public String getContextName();
	
	@Override
	default void handle(HttpExchange aHTTPExchange) throws IOException {
		HTTPExchangeWrapper wrapper = new HTTPExchangeWrapper(aHTTPExchange);
		handleWrapper(wrapper);
	}
	
	public abstract void handleWrapper(HTTPExchangeWrapper aExchangeWrapper) throws IOException;
}
