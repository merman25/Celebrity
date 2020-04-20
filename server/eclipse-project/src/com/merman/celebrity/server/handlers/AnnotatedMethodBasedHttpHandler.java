package com.merman.celebrity.server.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.merman.celebrity.server.HTTPResponseConstants;
import com.merman.celebrity.server.RequestType;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.annotations.HTTPRequest;
import com.merman.celebrity.server.parameter_parsers.ParameterParserRegistry;
import com.sun.net.httpserver.HttpExchange;

public class AnnotatedMethodBasedHttpHandler extends AHttpHandler2 {
	private final String name;
	private final Method method;
	private final List<MyMethodArg> methodArgs;
	private final RequestType requestType;
	
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
		requestType = httpRequestAnnotation.requestType();
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
	protected void _handle(Session aSession, Map<String, String> aRequestBodyAsMap, HttpExchange aHttpExchange) throws IOException {
		Object[] argValues = new Object[methodArgs.size() + 1];
		argValues[0] = aSession;
		for (int i = 0; i < methodArgs.size(); i++) {
			MyMethodArg methodArg = methodArgs.get(i);
			String argName = methodArg.name;
			Class<?> parameterType = methodArg.clazz;
			String valueString = aRequestBodyAsMap.get(argName);

			try {
				Object parameterValue;
				if ( methodArg.clazz == List.class ) {
					List<String>		parameterValueList		= new ArrayList<>();
					String parameterNameRoot = argName.replaceFirst("(?i)List$", "" );
					for ( int listIndex=1;; listIndex++ ) {
						String parameterValueListElement = aRequestBodyAsMap.get( parameterNameRoot + listIndex );
						if ( parameterValueListElement == null ) {
							break;
						}
						
						parameterValueList.add(parameterValueListElement);
					}
					
					parameterValue = parameterValueList;
				}
				else {
					if ( valueString == null ) {
						throw new IOException("Request " + getContextName() + " missing required parameter: " + argName);
					}
					parameterValue = ParameterParserRegistry.parseParameter(valueString, parameterType);
				}
				argValues[i+1] = parameterValue;
			} catch (ParseException e) {
				throw new IOException(String.format("Cannot parse parameter %s [%s] as %s", argName, valueString, parameterType.getSimpleName()), e);
			}
		}
		
		try {
			Object responseObject = method.invoke(null, argValues);
			if ( responseObject == null ) {
				aHttpExchange.sendResponseHeaders(HTTPResponseConstants.No_Content, -1);
				aHttpExchange.getResponseBody().close();
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

	public void sendResponse( HttpExchange aExchange, int aCode, String aResponse ) throws IOException {
		aExchange.sendResponseHeaders(aCode, aResponse.getBytes().length);
		OutputStream os = aExchange.getResponseBody();
		os.write(aResponse.getBytes());
		os.close();
	}
	
	public String serialiseMap( Map<?, ?> aMap ) {
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

	public synchronized RequestType getRequestType() {
		return requestType;
	}
}
