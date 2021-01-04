package com.merman.celebrity.server.parameter_parsers;

import java.text.ParsePosition;

public class IntegerParameterFormat extends AParameterFormat {

	@Override
	public Object parseObject(String aSource, ParsePosition aPos) {
		Integer parsedInteger = null;
		try {
			parsedInteger = Integer.parseInt(aSource);
			aPos.setIndex(aSource.length());
		}
		catch (NumberFormatException e) {
			aPos.setErrorIndex(0);
		}
		return parsedInteger;
	}
}
