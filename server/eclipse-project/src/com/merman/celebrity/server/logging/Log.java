package com.merman.celebrity.server.logging;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.merman.celebrity.server.logging.info.LogInfo;

public class Log {
	private Log() {}

	private static final LinkedHashMap<Class<? extends LogInfo>, List<Logger>> mapCategoriesToLoggers		= new LinkedHashMap<>();
	
	public static void log(Class<? extends LogInfo> aClass, Object... aArgs) {
		try {
			LogInfo logInfo = aClass
					.getConstructor(aArgs.getClass())
					.newInstance((Object) aArgs);
			log(logInfo);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void log(LogInfo aLogInfo) {
		
		List<Logger>	loggerList 		= getLoggerList(aLogInfo);
		
		for (Logger logger : loggerList ) {
			if (logger.getLogFilter() == null
					|| logger.getLogFilter().shouldLog(aLogInfo)) {
				logger.getLogOutputter().output(aLogInfo.toString());
			}
		}
	}

	private static List<Logger> getLoggerList(LogInfo aLogInfo) {
		List<Logger>	loggerList = new ArrayList<>();
		Class clazz = aLogInfo.getClass();
		do {
			List<Logger> listForClass = mapCategoriesToLoggers.get(clazz);
			if (listForClass != null) {
				loggerList.addAll(listForClass);
			}
		} while ( (clazz = clazz.getSuperclass()) != null);
		return loggerList;
	}
	
	public static void addLogger(Class<? extends LogInfo> aClass, Logger aLogger) {
		mapCategoriesToLoggers.computeIfAbsent(aClass, c -> new ArrayList<>()).add(aLogger);
	}
	
	public static void removeLogger(Class<? extends LogInfo> aClass, Logger aLogger) {
		List<Logger> loggerList = mapCategoriesToLoggers.get(aClass);
		if (loggerList != null) {
			loggerList.remove(aLogger);
		}
	}
}
