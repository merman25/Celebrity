package com.merman.celebrity.server.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.merman.celebrity.client.theme.ThemeManager;
import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.Player;
import com.merman.celebrity.server.HTTPExchangeWrapper;
import com.merman.celebrity.server.HTTPResponseConstants;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.analytics.Browser;
import com.merman.celebrity.server.analytics.UserAgentUtil;
import com.merman.celebrity.server.annotations.HTTPRequest;
import com.merman.celebrity.server.annotations.MaxLength;
import com.merman.celebrity.server.annotations.StartNewSession;
import com.merman.celebrity.server.exceptions.IllegalServerRequestException;
import com.merman.celebrity.server.exceptions.NullSessionException;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.merman.celebrity.server.parameter_parsers.ParameterParserRegistry;
import com.merman.celebrity.util.JSONUtil;

public class AnnotatedMethodBasedHttpHandler extends AHttpHandler {
	private final String name;
	private final Method method;
	private final List<MyMethodArg> methodArgs;
	private final boolean createSession;
	
	private static class MyMethodArg {
		private String name;
		private Class<?> clazz;
		private int maxLength = Integer.MAX_VALUE;

		public MyMethodArg(String aName, Class<?> aClass) {
			name  = aName;
			clazz = aClass;
		}

		public int getMaxLength() {
			return maxLength;
		}

		public void setMaxLength(int aMaxLength) {
			if (clazz != String.class
					&& clazz != List.class) {
				throw new IllegalArgumentException("Max length only supported for Strings and Lists. Class: " + clazz);
			}
			maxLength = aMaxLength;
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
		createSession = aMethod.getAnnotation(StartNewSession.class) != null;
		
		Parameter[] parameters = method.getParameters();
		String[] argNames = httpRequestAnnotation.argNames();
		if ( parameters.length == 0
				|| parameters[0].getType() != Session.class ) {
			throw new IllegalArgumentException("First method parameter must be of type " + Session.class.getSimpleName());
		}
		if ( argNames.length != parameters.length - 1) {
			throw new IllegalArgumentException("Length of argNames arr must be one less than the number of method parameters");
		}
		
		MaxLength maxLengthAnnotation = method.getAnnotation(MaxLength.class);

		for (int paramIndex=1; paramIndex<parameters.length; paramIndex++) {
			Parameter parameter = parameters[paramIndex];
			if ( ! ParameterParserRegistry.formatDefinedForClass(parameter.getType())) {
				throw new IllegalArgumentException("No format defined for method argument type: " + parameter.getType().getSimpleName());
			}
			
			MyMethodArg methodArg = new MyMethodArg(argNames[paramIndex-1], parameter.getType());
			if (maxLengthAnnotation != null) {
				methodArg.setMaxLength(maxLengthAnnotation.value());
			}
			methodArgs.add(methodArg);
		}
	}

	@Override
	public String getContextName() {
		return name;
	}

	@Override
	protected void _handle(Session aSession, Map<String, Object> aRequestBodyAsMap, HTTPExchangeWrapper aHttpExchangeWrapper) throws IOException {
		Session session;
		if (aSession != null) {
			session = aSession;
		}
		else if (! isCreateSession()) {
			throw new NullSessionException();
		}
		else {
			session = SessionManager.createSession();

			InetSocketAddress remoteAddress = aHttpExchangeWrapper.getRemoteAddress();
			InetAddress address = remoteAddress == null ? null : remoteAddress.getAddress();
			session.setOriginalInetAddress(address);

			Browser browser = null;
			String operatingSystem = null;
			String userAgentString = HttpExchangeUtil.getHeaderValue("User-agent", aHttpExchangeWrapper);
			if (userAgentString != null) {
				browser = UserAgentUtil.getBrowserFromUserAgent(userAgentString);
				operatingSystem = UserAgentUtil.getOperatingSystemFromUserAgent(userAgentString);
			}

			Log.log(LogMessageType.INFO, LogMessageSubject.SESSIONS, "New session", session, "IP", address, "Browser", browser, "OS", operatingSystem, "User-agent", userAgentString );
		}

		Object[] argValues = new Object[methodArgs.size() + 1];
		argValues[0] = session;
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
							String listElement = (String) element;
							if (listElement != null
									&& listElement.length() > methodArg.getMaxLength()) {
								throw new IllegalServerRequestException(String.format("Player [%s], session [%s], HTTPHandler [%s], argument [%s], length [%,d] greater than max [%,d]", session.getPlayer(), session, getName(), argName, listElement.length(), methodArg.getMaxLength()), String.format("Error: max string length %,d", methodArg.getMaxLength()));
							}
							parameterValueList.add(listElement);
						}
						else if (element == JSONObject.NULL) {
							parameterValueList.add(null);
						}
						else {
							throw new IllegalServerRequestException(String.format("Player [%s], session [%s], HTTPHandler [%s], argument [%s], list elements should be strings, but found [%s]", session.getPlayer(), session, getName(), argName, element == null ? null : element.getClass()), String.format("Error: value [%s] should be text", element) );
						}
					});
					parameterValue = parameterValueList;
				}
				else {
					if (value instanceof String) {
						String stringValue = (String) value;
						if (stringValue.length() > methodArg.getMaxLength()) {
							throw new IllegalServerRequestException(String.format("Player [%s], session [%s], HTTPHandler [%s], argument [%s], length [%,d] greater than max [%,d]", session.getPlayer(), session, getName(), argName, stringValue.length(), methodArg.getMaxLength()), String.format("Error: max string length %,d", methodArg.getMaxLength()));
						}
					}
					
					if (value instanceof String
							&& parameterType != String.class ) {
						parameterValue = ParameterParserRegistry.parseParameter((String) value, parameterType);
					}
					else if (value == JSONObject.NULL) {
						parameterValue = null;
					}
					else {
						parameterValue = value;
					}
				}
				argValues[i+1] = parameterValue;
				
				
			} catch (ParseException e) {
				throw new IllegalServerRequestException(String.format("Player [%s], session [%s], HTTPHandler [%s], argument [%s] at index [%,d], cannot parse [%s] as %s", session.getPlayer(), session, getName(), argName, i, value, parameterType.getSimpleName()), String.format("Error: cannot parse \"%s\" as %s", value, parameterType.getSimpleName()));
			}
			catch (IllegalServerRequestException e) {
				throw e;
			}
			catch (RuntimeException e) {
				Player player = session == null ? null : session.getPlayer();
				Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "Session", session, "Player", player, "Handler", getContextName(), "Request body", aRequestBodyAsMap, "Exception", e);
			}
		}

		HttpExchangeUtil.setCookieResponseHeader(session, aHttpExchangeWrapper);

		try {
			Object responseObject = method.invoke(null, argValues);
			
			Game game = session.getPlayer().getGame();
			if (game != null) {
				aHttpExchangeWrapper.getResponseHeaders().computeIfAbsent("set-cookie", s -> new ArrayList<>()).add( String.format( "theme=%s; Max-Age=7200", ThemeManager.getTheme(game).getName() ) );
			}

			if ( responseObject == null ) {
				aHttpExchangeWrapper.sendResponseHeaders(HTTPResponseConstants.No_Content_204, -1);
				OutputStream responseBody = aHttpExchangeWrapper.getResponseBody();
				if (responseBody != null) {
					responseBody.close();
				}
				HttpExchangeUtil.logBytesSent(aHttpExchangeWrapper, 0);
			}
			else if ( responseObject instanceof String ) {
				sendResponse(aHttpExchangeWrapper, HTTPResponseConstants.OK_200, (String) responseObject);
			}
			else if ( responseObject instanceof Map ) {
				String responseString = JSONUtil.serialiseMap( (Map) responseObject );
				sendResponse(aHttpExchangeWrapper, HTTPResponseConstants.OK_200, responseString);
			}
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			else {
				Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "Handler", getName(), "Session", session, "Player", session.getPlayer(), "Game", session.getPlayer().getGame(), "exception", e, "caused by", cause );
			}
		} catch (IllegalAccessException | IllegalArgumentException e) {
			Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Session", session, "Player", session.getPlayer(), "HTTPHandler", getName(), "Exception", e);
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], HTTPHandler [%s], exception [%s]", session.getPlayer(), session, getName(), e), null);
		}
	}

	public static List<AnnotatedMethodBasedHttpHandler> createHandlers( Class aClass ) {
		List<AnnotatedMethodBasedHttpHandler> handlerList = new ArrayList<>();
		
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

	public boolean isCreateSession() {
		return createSession;
	}
}
