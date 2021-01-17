package com.merman.celebrity.server.logging.outputters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class FileOutputter extends PrintStreamOutputter {
	private File file;
	private PrintStream printStream;

	public FileOutputter() {
	}
	
	public FileOutputter(File aFile) {
		setFile(aFile);
	}

	@Override
	public synchronized PrintStream getPrintStream() {
		try {
			if (printStream == null) {
				if (! file.getParentFile().exists()) {
					file.getParentFile().mkdirs();
				}
				printStream = new PrintStream(new FileOutputStream(file, true));
			}
			return printStream;
		}
		catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	protected void setPrintStream(PrintStream aPrintStream) {
		printStream = aPrintStream;
	}
	
	@Override
	public void close() {
		if (printStream != null) {
			printStream.close();
		}
	}

	protected File getFile() {
		return file;
	}

	protected void setFile(File aFile) {
		file = aFile;
	}
}
