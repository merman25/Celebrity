package com.merman.celebrity.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

/**
 * Wrapper class so that the request-handling code can transparently handle
 * either {@link HttpExchange} or {@link HTTPExchange}.
 */
public class HTTPExchangeWrapper {
	private final HttpExchange sunHTTPExchange;
	private final HTTPExchange mermanHTTPExchange;
	
	public HTTPExchangeWrapper(HttpExchange aSunHTTPExchange) {
		sunHTTPExchange = aSunHTTPExchange;
		mermanHTTPExchange = null;
	}
	public HTTPExchangeWrapper(HTTPExchange aMermanHTTPExchange) {
		mermanHTTPExchange = aMermanHTTPExchange;
		sunHTTPExchange = null;
	}
	
	public Object getDelegate() {
		if (sunHTTPExchange != null) {
			return sunHTTPExchange;
		}
		else {
			return mermanHTTPExchange;
		}
	}
	
	public InetSocketAddress getRemoteAddress() throws IOException {
		if (sunHTTPExchange != null) {
			return sunHTTPExchange.getRemoteAddress();
		}
		else {
			return mermanHTTPExchange.getRemoteAddress();
		}
	}
	
	public Map<String, List<String>> getRequestHeaders() {
		if (sunHTTPExchange != null) {
			return sunHTTPExchange.getRequestHeaders();
		}
		else {
			return mermanHTTPExchange.getRequestHeaders();
		}
	}
	
	public InputStream getRequestBody() {
		if (sunHTTPExchange != null) {
			return sunHTTPExchange.getRequestBody();
		}
		else {
			return null;
		}
	}
	
	public String getRequestBodyString() {
		if (sunHTTPExchange != null) {
			return null;
		}
		else {
			return mermanHTTPExchange.getRequestBody();
		}
	}
	public Map<String, List<String>> getResponseHeaders() {
		if (sunHTTPExchange != null) {
			return sunHTTPExchange.getResponseHeaders();
		}
		else {
			return mermanHTTPExchange.getResponseHeaders();
		}
	}
	
	public void sendResponseHeaders(HTTPResponseConstants aResponseConstant, int aBodyLength) throws IOException {
		if (sunHTTPExchange != null) {
			sunHTTPExchange.sendResponseHeaders(aResponseConstant.getCode(), aBodyLength);
		}
		else {
			mermanHTTPExchange.setResponseHeaders(aResponseConstant, aBodyLength);
		}
	}
	
	public OutputStream getResponseBody() {
		if (sunHTTPExchange != null) {
			return sunHTTPExchange.getResponseBody();
		}
		else {
			return null;
		}
	}
	
	public void setResponseBody(String aResponse, byte[] aResponseBytes) {
		if (sunHTTPExchange != null) {
			
		}
		else {
			mermanHTTPExchange.setResponseBody(aResponse, aResponseBytes);
		}
	}
	
	public void setResponseBody(byte[] aResponseBytes) {
		if (sunHTTPExchange != null) {
			
		}
		else {
			mermanHTTPExchange.setResponseBody(aResponseBytes);
		}
	}

	
	public void sendResponse() throws IOException {
		if (sunHTTPExchange != null) {
			
		}
		else {
			mermanHTTPExchange.sendResponse();
		}
	}
	
	public void close() {
		if (sunHTTPExchange != null) {
			
		}
		else {
			mermanHTTPExchange.close();
		}
	}
	
	public URI getRequestURI() {
		if (sunHTTPExchange != null) {
			return sunHTTPExchange.getRequestURI();
		}
		else {
			return mermanHTTPExchange.getRequestURI();
		}
	}
}
