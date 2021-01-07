package com.merman.celebrity.server.handlers;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.merman.celebrity.client.theme.ThemeManager;
import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.Player;
import com.merman.celebrity.server.HTTPResponseConstants;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.annotations.HTTPRequest;
import com.merman.celebrity.server.exceptions.IllegalServerRequestException;
import com.merman.celebrity.server.exceptions.NullSessionException;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.merman.celebrity.server.parameter_parsers.ParameterParserRegistry;
import com.merman.celebrity.util.JSONUtil;
import com.sun.net.httpserver.HttpExchange;

public class AnnotatedMethodBasedHttpHandler extends AHttpHandler {
	private final String name;
	private final Method method;
	private final List<MyMethodArg> methodArgs;
	
	private static class MyMethodArg {
		private String name;
		private Class<?> clazz;

		public MyMethodArg(String aName, Class<?> aClass) {
			name  = aName;
			clazz = aClass;
		}
	}
	
	public AnnotatedMethodBasedHttpHandler(Method aMethod) {
		if ( ( aMethod.getModifiers()
				& ( Modifier.STATIC
					| Modifier.PUBLIC ) ) == 0 ) {
			throw new IllegalArgumentException("Method must be public and static");
		}
		
		HTTPRequest httpRequestAnnotation = aMethod.getAnnotation(HTTPRequest.class);
		if (httpRequestAnnotation == null) {
			throw new IllegalArgumentException("Method must have annotation " + HTTPRequest.class.getName());
		}
		String requestName = httpRequestAnnotation.requestName();
		if ( requestName == null
				|| ! requestName.matches("[\\w-]+") ) {
			throw new IllegalArgumentException( "Request name must consist of at least one word character and/or a hyphen, and no other characters" );
		}
		
		name = requestName;
		method = aMethod;
		methodArgs = new ArrayList<>();
		
		Parameter[] parameters = method.getParameters();
		String[] argNames = httpRequestAnnotation.argNames();
		if ( parameters.length == 0
				|| parameters[0].getType() != Session.class ) {
			throw new IllegalArgumentException("First method parameter must be of type " + Session.class.getSimpleName());
		}
		if ( argNames.length != parameters.length - 1) {
			throw new IllegalArgumentException("Length of argNames arr must be one less than the number of method parameters");
		}
		
		
		for (int paramIndex=1; paramIndex<parameters.length; paramIndex++) {
			Parameter parameter = parameters[paramIndex];
			if ( ! ParameterParserRegistry.formatDefinedForClass(parameter.getType())) {
				throw new IllegalArgumentException("No format defined for method argument type: " + parameter.getType().getSimpleName());
			}
			
			MyMethodArg methodArg = new MyMethodArg(argNames[paramIndex-1], parameter.getType());
			methodArgs.add(methodArg);
		}
	}

	@Override
	public String getContextName() {
		return name;
	}

	@Override
	protected void _handle(Session aSession, Map<String, Object> aRequestBodyAsMap, HttpExchange aHttpExchange) throws IOException {
		if (aSession == null) {
			throw new NullSessionException();
		}

		Object[] argValues = new Object[methodArgs.size() + 1];
		argValues[0] = aSession;
		for (int i = 0; i < methodArgs.size(); i++) {
			MyMethodArg methodArg = methodArgs.get(i);
			String argName = methodArg.name;
			Class<?> parameterType = methodArg.clazz;
			Object value = aRequestBodyAsMap.get(argName);

			try {
				Object parameterValue;
				if ( parameterType == List.class ) {
					JSONArray			jsonArray				= (JSONArray) value;
					List<String>		parameterValueList		= new ArrayList<>();
					jsonArray.forEach(element -> {
						if (element instanceof String) {
							parameterValueList.add((String) element);
						}
						else if (element == JSONObject.NULL) {
							parameterValueList.add(null);
						}
						else {
							throw new IllegalServerRequestException(String.format("Player [%s], session [%s], HTTPHandler [%s], argument [%s], list elements should be strings, but found [%s]", aSession.getPlayer(), aSession, getName(), argName, element == null ? null : element.getClass()), String.format("Error: value [%s] should be text", element) );
						}
					});
					parameterValue = parameterValueList;
				}
				else if (value instanceof String
						&& parameterType != String.class ) {
					parameterValue = ParameterParserRegistry.parseParameter((String) value, parameterType);
				}
				else if (value == JSONObject.NULL) {
					parameterValue = null;
				}
				else {
					parameterValue = value;
				}
				argValues[i+1] = parameterValue;
				
				
			} catch (ParseException e) {
				throw new IllegalServerRequestException(String.format("Player [%s], session [%s], HTTPHandler [%s], argument [%s] at index [%,d], cannot parse [%s] as %s", aSession.getPlayer(), aSession, getName(), argName, i, value, parameterType.getSimpleName()), String.format("Error: cannot parse \"%s\" as %s", value, parameterType.getSimpleName()));
			}
			catch (IllegalServerRequestException e) {
				throw e;
			}
			catch (RuntimeException e) {
				Player player = aSession == null ? null : aSession.getPlayer();
				Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "Session", aSession, "Player", player, "Handler", getContextName(), "Request body", aRequestBodyAsMap, "Exception", e);
			}
		}

		HttpExchangeUtil.setCookieResponseHeader(aSession, aHttpExchange);

		try {
			Object responseObject = method.invoke(null, argValues);
			
			Game game = aSession.getPlayer().getGame();
			if (game != null) {
				aHttpExchange.getResponseHeaders().add( "Set-Cookie", String.format( "theme=%s; Max-Age=7200", ThemeManager.getTheme(game).getName() ) );
			}

			if ( responseObject == null ) {
				aHttpExchange.sendResponseHeaders(HTTPResponseConstants.No_Content, -1);
				aHttpExchange.getResponseBody().close();
				HttpExchangeUtil.logBytesSent(aHttpExchange, 0);
			}
			else if ( responseObject instanceof String ) {
				sendResponse(aHttpExchange, HTTPResponseConstants.OK, (String) responseObject);
			}
			else if ( responseObject instanceof Map ) {
				String responseString = JSONUtil.serialiseMap( (Map) responseObject );
				sendResponse(aHttpExchange, HTTPResponseConstants.OK, responseString);
			}
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			else {
				e.printStackTrace();
			}
		} catch (IllegalAccessException | IllegalArgumentException e) {
			Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Session", aSession, "Player", aSession.getPlayer(), "HTTPHandler", getName(), "Exception", e);
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], HTTPHandler [%s], exception [%s]", aSession.getPlayer(), aSession, getName(), e), null);
		}
	}

	public static List<AnnotatedMethodBasedHttpHandler> createHandlers( Class aClass ) {
		List<AnnotatedMethodBasedHttpHandler> handlerList = new ArrayList<AnnotatedMethodBasedHttpHandler>();
		
		Method[] methods = aClass.getMethods();
		for ( Method method : methods ) {
			if ( ( method.getModifiers()
					& ( Modifier.STATIC
						| Modifier.PUBLIC ) ) != 0
				&& method.getAnnotation(HTTPRequest.class) != null ) {
				AnnotatedMethodBasedHttpHandler handler = new AnnotatedMethodBasedHttpHandler(method);
				handlerList.add(handler);
			}
		}
		
		return handlerList;
	}

	public String getName() {
		return name;
	}
}
