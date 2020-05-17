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
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import com.merman.celebrity.game.Player;
import com.merman.celebrity.server.HTTPResponseConstants;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.annotations.HTTPRequest;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.info.LogInfo;
import com.merman.celebrity.server.parameter_parsers.ParameterParserRegistry;
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
			throw new IllegalArgumentException( "Request name must consist of at least one word character and/or a hypen, and no other characters" );
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
		Object[] argValues = new Object[methodArgs.size() + 1];
		argValues[0] = aSession;
		for (int i = 0; i < methodArgs.size(); i++) {
			MyMethodArg methodArg = methodArgs.get(i);
			String argName = methodArg.name;
			Class<?> parameterType = methodArg.clazz;
			Object value = aRequestBodyAsMap.get(argName);

			try {
				Object parameterValue;
				if ( methodArg.clazz == List.class ) {
					JSONArray			jsonArray				= (JSONArray) value;
					List<String>		parameterValueList		= new ArrayList<>();
					jsonArray.forEach(element -> parameterValueList.add((String) element));
					parameterValue = parameterValueList;
				}
				else if (value instanceof String
						&& methodArg.clazz != String.class ) {
					parameterValue = ParameterParserRegistry.parseParameter((String) value, parameterType);
				}
				else if (value != null) {
					parameterValue = value;
				}
				else {
					throw new IOException("Request " + getContextName() + " missing required parameter: " + argName);
				}
				argValues[i+1] = parameterValue;
			} catch (ParseException e) {
				throw new IOException(String.format("Cannot parse parameter %s [%s] as %s", argName, value, parameterType.getSimpleName()), e);
			}
			catch (RuntimeException e) {
				Player player = aSession == null ? null : aSession.getPlayer();
				Log.log(LogInfo.class, "Session", aSession, "Player", player, "Handler", getContextName(), "Request body", aRequestBodyAsMap, "Exception", e);
			}
		}
		
		try {
			Object responseObject = method.invoke(null, argValues);
			if ( responseObject == null ) {
//				sendResponse(aHttpExchange, HTTPResponseConstants.Found, "");
				
//				dumpRequest(aHttpExchange);
//				aHttpExchange.getResponseHeaders().set("Location", "http://192.168.1.17:8080/celebrity.html");
//				aHttpExchange.sendResponseHeaders(HTTPResponseConstants.Found, 0);
				
				aHttpExchange.sendResponseHeaders(HTTPResponseConstants.No_Content, -1);
				aHttpExchange.getResponseBody().close();
				HttpExchangeUtil.logBytesSent(aHttpExchange, 0);
				
//				aHttpExchange.sendResponseHeaders(HTTPResponseConstants.OK, -1);
//				aHttpExchange.getResponseBody().close();
			}
			else if ( responseObject instanceof String ) {
				sendResponse(aHttpExchange, HTTPResponseConstants.OK, (String) responseObject);
			}
			else if ( responseObject instanceof Map ) {
				String responseString = serialiseMap( (Map) responseObject );
				sendResponse(aHttpExchange, HTTPResponseConstants.OK, responseString);
			}
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	public String serialiseMap( Map<?, ?> aMap ) {
		JSONObject		jsonObject		= new JSONObject();
		aMap.entrySet().forEach( entry -> jsonObject.put(entry.getKey().toString(), entry.getValue() ) );
		
		return jsonObject.toString();
	}
	
	public String serialiseMapOld( Map<?, ?> aMap ) {
		StringBuilder builder = new StringBuilder();
		for ( Entry<?, ?> mapEntry : aMap.entrySet() ) {
			Object key = mapEntry.getKey();
			Object value = mapEntry.getValue();
			
			if ( builder.length() > 0 ) {
				builder.append('&');
			}
			builder.append(key);
			builder.append('=');
			builder.append(value);
		}
		
		return builder.toString();
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
