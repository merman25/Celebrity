package com.merman.celebrity.util;

import java.util.Map;

import org.json.JSONObject;

public class JSONUtil {

	private JSONUtil() {
	}

	public static String serialiseMap( Map<?, ?> aMap ) {
		JSONObject jsonObject = toJSONObject(aMap);
		
		return jsonObject.toString();
	}

	public static JSONObject toJSONObject(Map<?, ?> aMap) {
		JSONObject		jsonObject		= new JSONObject();
		aMap.entrySet().forEach( entry -> jsonObject.put(entry.getKey().toString(), entry.getValue() ) );
		return jsonObject;
	}

	public static JSONObject combine(JSONObject aLeftJSONObject, JSONObject aRightJSONObject) {
		JSONObject combinedObject 		= new  JSONObject();
		String[] leftNames = JSONObject.getNames(aLeftJSONObject);
		for (String name : leftNames) {
			combinedObject.put(name, aLeftJSONObject.get(name));
		}
		
		String[] rightNames = JSONObject.getNames(aRightJSONObject);
		for (String name : rightNames) {
			combinedObject.put(name, aRightJSONObject.get(name));
		}
		
		return combinedObject;
	}
}
