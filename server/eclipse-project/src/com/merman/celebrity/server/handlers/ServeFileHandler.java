package com.merman.celebrity.server.handlers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Function;

import com.merman.celebrity.server.HTTPResponseConstants;
import com.merman.celebrity.server.Server;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

public class ServeFileHandler extends AHttpHandler {
	private String relativePath;

	public ServeFileHandler(String aRelativePath) {
		relativePath = aRelativePath;
	}

	@Override
	protected void _handle(HttpExchange aExchange) throws IOException {
		Headers requestHeaders = aExchange.getRequestHeaders();
		List<String> contentTypeList = requestHeaders.get( "Content-type" );
		if ( contentTypeList != null
				&& contentTypeList.contains( "application/x-www-form-urlencoded" ) ) {
			
			FormHandlerRegistry.handleExchange( aExchange );
			return;
		}
		
//		dumpRequest(aExchange);
		
		Function<String, String>		addCookieSetter		= response -> {
			int indexOfMarker = response.indexOf("<div id=\"dummy\"></div>");
			if ( indexOfMarker >= 0 ) {
				StringBuilder builder = new StringBuilder();
				Session session = SessionManager.createSession();
				builder.append(response.substring(0, indexOfMarker) );
				builder.append("<script>\n");
				builder.append("clearCookie(\"session\");\n");
				builder.append("var session=\"" + session.getSessionID() + "\"\n");
//				builder.append("document.getElementById(\"sessionField\").value=session;\n");
				builder.append("setCookie(\"session\", session, 1);\n" );
				builder.append("</script>\n");
				builder.append(response.substring(indexOfMarker));
				
				return builder.toString();
			}
			else {
				return response;
			}
		};
		
		serveFileContent(relativePath, aExchange, addCookieSetter);
	}

	@Override
	public String getHandlerName() {
		return "serve file " + relativePath;
	}
	
	public static void serveFileContent( String aRelativePath, HttpExchange aExchange ) throws IOException {
		serveFileContent(aRelativePath, aExchange, null);
	}
	
	public static void serveFileContent( String aRelativePath, HttpExchange aExchange, Function<String, String> aOptionalStringTransform ) throws IOException {
		File file = new File( Server.CLIENT_FILE_DIRECTORY.toFile(), aRelativePath );
		String response;
		if ( file.exists() ) {
			byte[] bytes = Files.readAllBytes(file.toPath());
			response = new String( bytes, StandardCharsets.UTF_8 );
		}
		else {
			response = "File does not exist: " + file.getAbsolutePath();
		}
		
		if ( aOptionalStringTransform != null ) {
			response = aOptionalStringTransform.apply(response);
		}
		
		if ( aRelativePath.toLowerCase().endsWith(".css") ) {
			aExchange.getResponseHeaders().set("content-type", "text/css");
		}
		else if ( aRelativePath.toLowerCase().endsWith( ".svg" ) ) {
			aExchange.getResponseHeaders().set("content-type", "image/svg+xml" );
		}
		else if ( aRelativePath.toLowerCase().endsWith( ".js") ) {
			aExchange.getResponseHeaders().set("content-type", "text/javascript");
		}
				
		
		aExchange.sendResponseHeaders(HTTPResponseConstants.OK, response.getBytes().length);
		OutputStream os = aExchange.getResponseBody();
		os.write(response.getBytes());
		os.close();
	}

}
