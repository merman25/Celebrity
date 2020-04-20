package com.merman.celebrity.server.handlers;

import com.sun.net.httpserver.HttpHandler;

public interface IContextHandler extends HttpHandler {
	public String getContextName();
}
