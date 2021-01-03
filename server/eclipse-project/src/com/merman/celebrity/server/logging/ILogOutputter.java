package com.merman.celebrity.server.logging;

import java.util.function.Consumer;

public interface ILogOutputter extends Consumer<String> {
	public void output(String aLogString);

	@Override
	default void accept(String aString) {
		output(aString);
	}

	default public void close() {
		// Do nothing by default (e.g. not needed for a sysout logger)
	}
	
}
