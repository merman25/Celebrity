package com.merman.celebrity.server.logging;

import java.util.function.Predicate;

import com.merman.celebrity.server.logging.info.LogInfo;

public interface ILogFilter extends Predicate<LogInfo> {
	public boolean shouldLog(LogInfo aLogInfo);

	@Override
	default boolean test(LogInfo aLogInfo) {
		return shouldLog(aLogInfo);
	}
}
