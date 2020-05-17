package com.merman.celebrity.server.logging.info;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

public class LogInfo {
	private static ThreadLocal<DateFormat>		threadLocalDateFormat		= new ThreadLocal<DateFormat>() {

		@Override
		protected DateFormat initialValue() {
			SimpleDateFormat		dateFormat		= new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			return dateFormat;
		}
		
	};

	private String logString;
	private Date timeStamp = new Date();
	private Object[] args;
	
	public LogInfo(Object... aArgs) {
		args = aArgs;
	}
	
	public String formatArgs(Object... aArgs) {
		List<String> argStringList = Arrays.asList( aArgs ).stream().map(x -> format(x)).collect(Collectors.toList());
		return String.join(", ", argStringList);
	}
	
	private static String format(Object aObject) {
		if ( aObject == null ) {
			return "null";
		}
		else if ( aObject instanceof Throwable ) {
			Throwable throwable = (Throwable) aObject;
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			throwable.printStackTrace(new PrintStream( byteArrayOutputStream ));
			return throwable.toString() + "\n" + byteArrayOutputStream.toString();
		}
		else {
			return aObject.toString();
		}
	}
	
	@Override
	public String toString() {
		if (logString == null) {
			String timeStampString = threadLocalDateFormat.get().format(timeStamp);
			logString = String.format("%s %s", timeStampString, formatArgs(args));
		}
		
		return logString;
	}
}
