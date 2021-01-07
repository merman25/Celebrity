package com.merman.celebrity.util;

import java.util.Map;

import org.json.JSONObject;

public class JSONUtil {

	private JSONUtil() {
	}

	public static String serialiseMap( Map<?, ?> aMap ) {
		JSONObject		jsonObject		= new JSONObject();
		aMap.entrySet().forEach( entry -> jsonObject.put(entry.getKey().toString(), entry.getValue() ) );
		
		return jsonObject.toString();
	}
	
//	public static String serialiseObjectViaReflectionOnGetters( Object aObject ) {
//		return serialiseObjectViaReflectionOnGetters(aObject, null);
//	}
//
//	public static String serialiseObjectViaReflectionOnGetters( Object aObject, Predicate<Method> aFilter ) {
//	}
//
//	public static String serialiseObjectViaReflectionOnGetters( Object aObject, Predicate<Method> aFilter ) {
//		if (aObject == null) {
//			return JSONObject.NULL.toString();
//		}
//		else {
//			JSONObject jsonObject = new JSONObject();
//
//			Class<?> clazz = aObject.getClass();
//
//			Method[] methods = clazz.getMethods();
//			for (Method method : methods) {
//				String methodName = method.getName();
//				if (methodName.length() > 3
//						&& methodName.startsWith("get")
//						&& ( method.getModifiers() & Modifier.PUBLIC ) > 0
//						&& ( method.getModifiers() & Modifier.STATIC ) == 0
//						&& method.getParameterCount() == 0
//						&& method.getReturnType() != void.class
//						&& ( aFilter == null
//								|| aFilter.test(method) ) ) {
//					try {
//						Object value 		= method.invoke(aObject);
//						String fieldName	= methodName.substring("get".length());
//						putObject( jsonObject, fieldName, value );
//					}
//					catch (Exception e) {
//						Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "Object", aObject, "class", clazz.getSimpleName(), "failed to invoke method", method, "exception", e);
//					}
//				}
//			}
//		}
//	}
//
//	public static void putObject(JSONObject aJSONObject, String aFieldName, Object aValue) {
//		putObject(aJSONObject, aFieldName, aValue, null);
//	}
//
//	private static void putObject(JSONObject aJSONObject, String aFieldName, Object aValue, Set<Collection<?>> aCollectionSetToAvoidLoops) {
//		if (aValue == null) {
//			aJSONObject.put(aFieldName, JSONObject.NULL);
//		}
//		else if (aValue instanceof Collection) {
//			Set<Collection<?>>	newCollectionSetToAvoidLoops = new HashSet<>();
//			if (aCollectionSetToAvoidLoops != null) {
//				newCollectionSetToAvoidLoops.addAll(aCollectionSetToAvoidLoops);
//			}
//			Collection<?>		collectionValue = (Collection<?>) aValue;
//			collectionValue.stream()
//			((Collection) aValue).forEach( element -> putObject(aJSONObject, aFieldName, , newCollectionSetToAvoidLoops));
//		}
//		else {
//			aJSONObject.put(aFieldName, aValue.toString());
//		}
//	}
}
