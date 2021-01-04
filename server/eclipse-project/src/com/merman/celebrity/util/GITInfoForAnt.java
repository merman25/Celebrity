package com.merman.celebrity.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes a system call to git as part of ant build script, and prints the
 * string we need for the build to <code>System.out</code>.
 * <p>
 * Quicker to do this than to look up how to integrate git with ant.
 *
 */
public class GITInfoForAnt {
	private static enum CommandLineArg {
		COMMIT("^commit (\\w+)") {
			@Override
			public String getOutput(String aGroup) {
				return aGroup;
			}
			
		},
		DATE("^Date: *(.*)") {
			@Override
			public String getOutput(String aGroup) {
				try {
					Date date = Date.from( ZonedDateTime.parse( aGroup ).toInstant() );
					SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HHmm");
					format.setTimeZone(TimeZone.getTimeZone("UTC"));
					return format.format(date);
				}
				catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}
		};

		public final String regex;
		public abstract String getOutput(String aGroup);
		
		private CommandLineArg(String aRegex) {
			regex = aRegex;
		}
	}

	public static void main(String[] aArgs) {
		if (aArgs.length != 1) {
			printUsageAndExit();
		}

		String arg = aArgs[0];
		boolean recognisedArg = false;
		for (int i = 0; i < CommandLineArg.values().length; i++) {
			CommandLineArg commandLineArg = CommandLineArg.values()[i];
			if (commandLineArg.toString().equals(arg)) {
				recognisedArg = true;
				outputProperty( commandLineArg );
				break;
			}
		}
		
		if (! recognisedArg) {
			printUsageAndExit();
		}
	}

	private static void printUsageAndExit() {
		System.err.println("Error: expect one of following args:");
		for (int i = 0; i < CommandLineArg.values().length; i++) {
			CommandLineArg commandLineArg = CommandLineArg.values()[i];
			System.out.println(commandLineArg);
		}
		System.err.println();
		System.exit(1);
	}

	private static String findTargetLineOutput(CommandLineArg aCommandLineArg) throws IOException {
		String         outputString   = null;

		ProcessBuilder processBuilder = new ProcessBuilder("git", "log", "-n1", "--date=iso-strict");
		processBuilder.redirectErrorStream(true);
		Process process = processBuilder.start();
		Pattern pattern = Pattern.compile(aCommandLineArg.regex);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;

			while ((line = reader.readLine()) != null) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					outputString = aCommandLineArg.getOutput(matcher.group(1));
					break;
				}
			}
		}
		return outputString;
	}

	private static void outputProperty(CommandLineArg aCommandLineArg) {
		try {
			String revisionNumberString = findTargetLineOutput(aCommandLineArg);
			if (revisionNumberString != null) {
				System.out.print(revisionNumberString);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
