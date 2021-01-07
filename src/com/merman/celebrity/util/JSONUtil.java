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
}
