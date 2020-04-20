package com.merman.celebrity.server.parameter_parsers;

import java.text.ParsePosition;

public class ListParameterFormat extends AParameterFormat {

	@Override
	public Object parseObject(String String, ParsePosition aPos) {
		String.split(",");
		return null;
	}

}
