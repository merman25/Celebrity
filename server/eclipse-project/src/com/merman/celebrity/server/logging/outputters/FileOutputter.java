package com.merman.celebrity.server.logging.outputters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class FileOutputter extends PrintStreamOutputter {
	private File file;
	private PrintStream printStream;

	public FileOutputter(File aFile) {
		file = aFile;
	}

	@Override
	public synchronized PrintStream getPrintStream() {
		try {
			if (printStream == null) {
				printStream = new PrintStream(new FileOutputStream(file, true));
			}
			return printStream;
		}
		catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void close() {
		if (printStream != null) {
			printStream.close();
		}
	}
}
