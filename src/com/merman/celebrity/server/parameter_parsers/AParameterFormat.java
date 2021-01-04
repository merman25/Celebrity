package com.merman.celebrity.server.parameter_parsers;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;

public abstract class AParameterFormat extends Format {

	@Override
	public StringBuffer format(Object aObj, StringBuffer aToAppendTo, FieldPosition aPos) {
		aToAppendTo.append(aObj == null ? "null" : aObj.toString());
		return aToAppendTo;
	}

	@Override
	public Object parseObject(String aString) throws ParseException {
		ParsePosition parsePosition = new ParsePosition(0);
		Object parsedObject = parseObject(aString, parsePosition);
		if ( parsePosition.getErrorIndex() >= 0 ) {
			throw new ParseException(aString, parsePosition.getErrorIndex());
		}
		return parsedObject;
	}
}
