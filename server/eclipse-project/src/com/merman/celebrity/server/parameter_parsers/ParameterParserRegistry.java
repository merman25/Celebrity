package com.merman.celebrity.server.parameter_parsers;

import java.text.Format;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ParameterParserRegistry {
	private static Map<Class<?>, Format> mapClassesToFormats		= new HashMap<Class<?>, Format>();
	
	public static synchronized void putFormat( Class<?> aClass, Format aFormat ) {
		mapClassesToFormats.put(aClass, aFormat);
	}

	static {
		putFormat(Integer.class, new IntegerParameterFormat());
	}
	
	public static synchronized <T> T parseParameter(String aParameterAsString, Class<T> aClass) throws ParseException {
		Format format = mapClassesToFormats.get(aClass);
		if ( format == null ) {
			throw new ParseException("No format defined for class: " + aClass.getSimpleName(), 0);
		}
		return (T) format.parseObject(aParameterAsString);
	}
	
	public static synchronized boolean formatDefinedForClass( Class<?> aClass ) {
		if ( aClass == String.class
				|| aClass == List.class ) {
			return true;
		}
		return mapClassesToFormats.containsKey(aClass);
	}
}
