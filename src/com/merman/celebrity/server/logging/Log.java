package com.merman.celebrity.server.logging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.merman.celebrity.server.logging.info.LogInfo;

public class Log {
	private Log() {}

	private static final LinkedHashMap<LogMessageSubject, List<Logger>> mapSubjectsToLoggers		= new LinkedHashMap<>();
	
	public static void log(LogMessageType aType, LogMessageSubject aSubject, Object... aArgs) {
		LogInfo logInfo = new LogInfo(aType, aSubject, aArgs);
		log(logInfo);
	}
	
	public static void log(LogInfo aLogInfo) {
		Iterable<Logger>	loggerList 		= getLoggerList(aLogInfo);
		
		for (Logger logger : loggerList ) {
			if (logger.getLogFilter() == null
					|| logger.getLogFilter().shouldLog(aLogInfo)) {
				logger.getLogOutputter().output(aLogInfo.toString());
			}
		}
	}

	private static Iterable<Logger> getLoggerList(LogInfo aLogInfo) {
		Set<Logger>	loggerCollection = new LinkedHashSet<>();

		List<Logger> listForGeneral = mapSubjectsToLoggers.get(LogMessageSubject.GENERAL);
		if (listForGeneral != null) {
			for (Logger logger: listForGeneral) {
				if (logger.getMaxDetailLogType().ordinal() >= aLogInfo.getType().ordinal()) {
					loggerCollection.add(logger);
				}
			}
		}

		LogMessageSubject subject = aLogInfo.getSubject();
		List<Logger> listForSubject = mapSubjectsToLoggers.get(subject);
		if (listForSubject != null) {
			for (Logger logger: listForSubject) {
				if (logger.getMaxDetailLogType().ordinal() >= aLogInfo.getType().ordinal()) {
					loggerCollection.add(logger);
				}
			}
		}
		return loggerCollection;
	}
	
	public static void addLogger(LogMessageSubject aSubject, Logger aLogger) {
		mapSubjectsToLoggers.computeIfAbsent(aSubject, s -> new ArrayList<>()).add(aLogger);
	}
	
	public static void removeLogger(LogMessageSubject aSubject, Logger aLogger) {
		List<Logger> loggerList = mapSubjectsToLoggers.get(aSubject);
		if (loggerList != null) {
			loggerList.remove(aLogger);
		}
	}
}
