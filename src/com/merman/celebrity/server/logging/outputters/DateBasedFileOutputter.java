package com.merman.celebrity.server.logging.outputters;

import java.io.File;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateBasedFileOutputter extends FileOutputter {
	private static final String DATE_FORMAT = "yy-MM-dd";
	private static final long MILLIS_IN_DAY = 86_400_000;
	
	private long fileGenerationTimeStamp = -1;
	private long fileGenerationDayStamp = -1;

	public DateBasedFileOutputter(File aFileWithBaseName) {
		File fileWithTimeStampInName = generateFileWithTimeStampInName(aFileWithBaseName);
		setFile(fileWithTimeStampInName);
	}

	private File generateFileWithTimeStampInName(File aFileWithBaseName) {
		fileGenerationTimeStamp = System.currentTimeMillis();
		fileGenerationDayStamp = fileGenerationTimeStamp / MILLIS_IN_DAY;
		SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		String dateString = dateFormat.format(new Date(fileGenerationTimeStamp));
		
		File fileWithTimeStamp = new File( aFileWithBaseName.getParentFile(), dateString + "_" + aFileWithBaseName.getName() );
		
		return fileWithTimeStamp;
	}

	@Override
	public synchronized PrintStream getPrintStream() {
		long currentTimeMillis = System.currentTimeMillis();
		long currentDay = currentTimeMillis / MILLIS_IN_DAY;
		if (currentDay != fileGenerationDayStamp) {
			File currentFile = getFile();
			int prefixLength = ( DATE_FORMAT + "_" ).length();
			File baseFile = new File(currentFile.getParentFile(), currentFile.getName().substring(prefixLength));
			
			File newFile = generateFileWithTimeStampInName(baseFile);
			setFile(newFile);
			setPrintStream(null);
		}
		
		return super.getPrintStream();
	}
}
