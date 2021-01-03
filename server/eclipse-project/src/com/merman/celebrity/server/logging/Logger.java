package com.merman.celebrity.server.logging;

public class Logger {
	private final LogMessageType maxDetailLogType;
	private final ILogFilter logFilter;
	private final ILogOutputter logOutputter;

	public Logger(ILogFilter aLogFilter, ILogOutputter aLogOutputter) {
		// Log everything, if no message type specified
		this(LogMessageType.values()[ LogMessageType.values().length - 1 ], aLogFilter, aLogOutputter);
	}
	
	public Logger(LogMessageType aMaxDetailType, ILogFilter aLogFilter, ILogOutputter aLogOutputter) {
		maxDetailLogType = aMaxDetailType;
		logFilter        = aLogFilter;
		logOutputter     = aLogOutputter;
	}

	public ILogFilter getLogFilter() {
		return logFilter;
	}

	public ILogOutputter getLogOutputter() {
		return logOutputter;
	}

	public LogMessageType getMaxDetailLogType() {
		return maxDetailLogType;
	}

	public void close() {
		logOutputter.close();
	}
}
