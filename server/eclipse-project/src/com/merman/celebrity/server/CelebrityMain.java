package com.merman.celebrity.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.server.analytics.ServerAnalyticsLogger;
import com.merman.celebrity.util.SharedRandom;

public class CelebrityMain {
	public static AtomicLong bytesReceived = new AtomicLong();
	public static AtomicLong bytesSent = new AtomicLong();

	public static void main(String[] args) throws IOException {
		GameManager.deleteExisting = true;
		GameManager.createFiles = true;
		
		List<File> gameFileList = new ArrayList<>();

		boolean processingOptions = true;
		String optionString = "--";
		for (int argIndex = 0; argIndex < args.length; argIndex++) {
			String arg = args[argIndex];
			if ( ! arg.startsWith(optionString) ) {
				processingOptions = false;
			}
			else if ( arg.length() == optionString.length() ) {
				processingOptions = false;
			}
			else {
				String argName = arg.substring(optionString.length());
				if ( argIndex == args.length - 1 ) {
					System.err.format("Error: no value specified for argument %s\n", argName);
					System.exit(1);
				}
				else {
					argIndex++;
					String argValue = args[argIndex];
					processArg(argName, argValue);
				}
			}

			if ( ! processingOptions ) {
				gameFileList.add(new File(arg));
			}
		}
		GameManager.deleteExisting = GameManager.deleteExisting && ( gameFileList == null || gameFileList.isEmpty() || gameFileList.stream().noneMatch(file -> file.exists()) );

		
		Server server = new Server(gameFileList);
		server.start();
		new ServerAnalyticsLogger(server).start();
	}

	private static void processArg(String aArgName, String aArgValue) {
		if ( aArgName.equals("seed") ) {
			try {
				long seed = Long.parseLong(aArgValue);
				SharedRandom.setSeed(seed);
			}
			catch (NumberFormatException e) {
				System.err.format("Error: cannot parse seed value %s\n", aArgValue);
				System.exit(2);
			}
		}
		else if ( aArgName.equals("create-files") ) {
			if ( ! Arrays.asList("true", "false", "yes", "no", "y", "n").contains(aArgValue.toLowerCase() ) ) {
				System.err.format("Error: cannot parse boolean: %s\n", aArgValue);
				System.exit(4);
			}
			boolean createFiles = Boolean.parseBoolean(aArgValue);
			GameManager.createFiles = createFiles;
		}
		else if ( aArgName.equals("delete-existing") ) {
			if ( ! Arrays.asList("true", "false", "yes", "no", "y", "n").contains(aArgValue.toLowerCase() ) ) {
				System.err.format("Error: cannot parse boolean: %s\n", aArgValue);
				System.exit(4);
			}
			boolean deleteExisting = Boolean.parseBoolean(aArgValue);
			GameManager.deleteExisting = deleteExisting;
		}
		else {
			System.err.format("Error: unknown argument: %s\n", aArgName);
			System.exit(3);
		}
	}
}
