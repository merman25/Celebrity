package com.merman.celebrity.server.handlers;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.merman.celebrity.server.HTTPResponseConstants;
import com.sun.net.httpserver.HttpExchange;

public class FormHandlerRegistry {
	private static final String FORM_NAME_KEYWORD = "form";
	private static Map<String, FormHandler>		mapFormNamesToHandlers		= new HashMap<>();
	
	public static void addHandler( FormHandler aHandler ) {
		mapFormNamesToHandlers.put( aHandler.getFormName(), aHandler );
	}

	public static void handleExchange(HttpExchange aExchange) throws IOException {
		FormHandler formHandler = null;
		
		LinkedHashMap<String, String> requestBody = HttpExchangeUtil.getRequestBodyAsMap(aExchange);
		String formName = requestBody.get(FORM_NAME_KEYWORD);
		if ( formName != null ) {
			formHandler = mapFormNamesToHandlers.get(formName);
			if ( formHandler != null ) {
				formHandler.handle(aExchange);
			}
			else {
				System.err.println( "No handler defined for form: " + formName );
			}
		}
		else {
			System.err.println( "Form submitted with no name" );
		}
		
		if ( formHandler == null
				|| ! formHandler.hasResponded() ) {
			aExchange.sendResponseHeaders(HTTPResponseConstants.No_Content, -1);
			aExchange.getResponseBody().close();
		}
	}
}
