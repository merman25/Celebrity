package com.merman.celebrity.server.parameter_parsers;

import java.text.ParsePosition;

public class StringParameterFormat extends AParameterFormat {

	@Override
	public Object parseObject(String aSource, ParsePosition aPos) {
		return aSource;
	}
}
