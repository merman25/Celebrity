package com.merman.celebrity.server.parameter_parsers;

import java.text.ParsePosition;

public class BooleanParameterFormat extends AParameterFormat {

	@Override
	public Object parseObject(String aSource, ParsePosition aPos) {
		Boolean parsedBoolean = Boolean.valueOf(aSource);
		aPos.setIndex(aSource.length());
		return parsedBoolean;
	}

}
