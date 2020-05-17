package com.merman.celebrity.server.logging.outputters;

import java.io.PrintStream;

import com.merman.celebrity.server.logging.ILogOutputter;

public class PrintStreamOutputter implements ILogOutputter {
	private PrintStream printStream;
	
	protected PrintStreamOutputter() {
		
	}

	public PrintStreamOutputter(PrintStream aPrintStream) {
		printStream = aPrintStream;
	}
	
	@Override
	public void output(String aLogString) {
		getPrintStream().println(aLogString);
	}

	public PrintStream getPrintStream() {
		return printStream;
	}
	
	public void close() {
		PrintStream printStream = getPrintStream();
		if (printStream != null) {
			printStream.close();
		}
	}
}
