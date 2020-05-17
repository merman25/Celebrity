package com.merman.celebrity.server.logging;

public class Logger {
	private final ILogFilter logFilter;
	private final ILogOutputter logOutputter;
	
	public Logger(ILogFilter aLogFilter, ILogOutputter aLogOutputter) {
		logFilter        = aLogFilter;
		logOutputter     = aLogOutputter;
	}

	public ILogFilter getLogFilter() {
		return logFilter;
	}

	public ILogOutputter getLogOutputter() {
		return logOutputter;
	}
}
