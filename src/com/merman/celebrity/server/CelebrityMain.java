package com.merman.celebrity.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.server.analytics.ServerAnalyticsLogger;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.merman.celebrity.server.logging.Logger;
import com.merman.celebrity.server.logging.outputters.DateBasedFileOutputter;
import com.merman.celebrity.server.logging.outputters.PrintStreamOutputter;
import com.merman.celebrity.util.SharedRandom;

public class CelebrityMain {
	private static final String DATA_DIRECTORY_ROOT          = "../.celebrity";
	private static final String GAME_LOG_DIRECTORY_NAME      = "games";
	private static final String TEST_GAME_LOG_DIRECTORY_NAME = "test_games";

	public static AtomicLong    bytesReceived                = new AtomicLong();
	public static AtomicLong    bytesSent                    = new AtomicLong();

	private static int          overridePort                 = -1;
	private static boolean      sysOutLogging;
	private static String       version                      = "NO_VERSION";
	private static Path         dataDirectory			     = Paths.get(DATA_DIRECTORY_ROOT);
	public static boolean       newServer;

	public static void main(String[] args) throws IOException {
		GameManager.deleteExisting = true;
		GameManager.createFiles = true;
		Locale.setDefault(Locale.US);
		
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

		readVersion();
		initLogging();
		
		/* Output this one line always to sysout, so that we see it even when live and when
		 * all other logging output is going to files.
		 */
		System.out.format("Starting Celebrity Server Version %s, server code [%s]\n", getVersion(), newServer ? "New" : "Old");
		Log.log(LogMessageType.INFO, LogMessageSubject.RESTARTS, "Starting Celebrity Server Version", getVersion(), "server code", newServer ? "New" : "Old" );
		
		Server server;
		if (overridePort >= 0 ) {
			server = new Server(overridePort, gameFileList);
		}
		else {
			server = new Server(gameFileList);
		}
		server.start();
		new ServerAnalyticsLogger(server).start();
	}

	private static void readVersion() {
		URL resource = CelebrityMain.class.getResource("/build_info.properties");
		if (resource != null) {
			Properties properties = new Properties();
			try {
				properties.load(new InputStreamReader(resource.openStream()));
				version = properties.getProperty("build_date");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static void initLogging() {
		System.setErr(System.out);
		if (sysOutLogging) {
			Log.addLogger(LogMessageSubject.GENERAL, new Logger(null, new PrintStreamOutputter(System.out)));
		}
		else {
			File logDir = new File(dataDirectory.toFile(), "/logs");
			if ( ! logDir.exists() ) {
				logDir.mkdir();
			}
			Log.addLogger(LogMessageSubject.GENERAL, new Logger(LogMessageType.INFO, null, new DateBasedFileOutputter(new File(logDir, "general.txt"))));
			Log.addLogger(LogMessageSubject.GENERAL, new Logger(null, new DateBasedFileOutputter(new File(logDir, "all.txt"))));
			Log.addLogger(LogMessageSubject.ANALYTICS, new Logger(null, new DateBasedFileOutputter(new File(logDir, "stats.txt"))));
			Log.addLogger(LogMessageSubject.HTTP_REQUESTS, new Logger(null, new DateBasedFileOutputter(new File(logDir, "httprequests.txt"))));
			Log.addLogger(LogMessageSubject.RESTARTS, new Logger(null, new DateBasedFileOutputter(new File(logDir, "restarts.txt"))));
			Log.addLogger(LogMessageSubject.SESSIONS, new Logger(null, new DateBasedFileOutputter(new File(logDir, "sessions.txt"))));
		}
	}

	private static void processArg(String aArgName, String aArgValue) {
		if ( aArgName.equals("seed") ) {
			try {
				long seed = Long.parseLong(aArgValue);
				SharedRandom.setSeed(seed);
				
				// Could have a separate option for this, but if we've set a fixed seed, it's because we want repeatability.
				// And if we want repeatability, each game needs its own Random (because several may be played at once)
				SharedRandom.setSetRandomGeneratorForEachGameWithFixedSeed(true);
			}
			catch (NumberFormatException e) {
				System.err.format("Error: cannot parse seed value %s\n", aArgValue);
				System.exit(2);
			}
		}
		else if ( aArgName.equals("create-files") ) {
			if ( ! Arrays.asList("true", "false").contains(aArgValue.toLowerCase() ) ) {
				System.err.format("Error: cannot parse boolean: %s\n", aArgValue);
				System.exit(4);
			}
			boolean createFiles = Boolean.parseBoolean(aArgValue);
			GameManager.createFiles = createFiles;
		}
		else if ( aArgName.equals("delete-existing") ) {
			if ( ! Arrays.asList("true", "false").contains(aArgValue.toLowerCase() ) ) {
				System.err.format("Error: cannot parse boolean: %s\n", aArgValue);
				System.exit(5);
			}
			boolean deleteExisting = Boolean.parseBoolean(aArgValue);
			GameManager.deleteExisting = deleteExisting;
		}
		else if ( aArgName.equals("port")) {
			try {
				overridePort = Integer.parseInt(aArgValue);
				if (overridePort < 0) {
					System.err.format("Error: negative port number %s\n", overridePort);
					System.exit(6);
				}
			}
			catch (NumberFormatException e) {
				System.err.format("Error: cannot parse port number %s\n", aArgValue);
				System.exit(6);
			}

		}
		else if ( aArgName.equals("logging")) {
			if ( ! "sysout".equals(aArgValue)) {
				System.err.println("Error: only possible value for arg \"logging\" is \"sysout\"");
				System.exit(7);
			}
			else {
				sysOutLogging = true;
			}
		}
		else if ( aArgName.equals("new-server") ) {

			if ( ! Arrays.asList("true", "false").contains(aArgValue.toLowerCase() ) ) {
				System.err.format("Error: cannot parse boolean: %s\n", aArgValue);
				System.exit(4);
			}
			newServer = Boolean.parseBoolean(aArgValue);
		}
		else {
			System.err.format("Error: unknown argument: %s\n", aArgName);
			System.exit(3);
		}
	}

	public static boolean isSysOutLogging() {
		return sysOutLogging;
	}

	public static void setSysOutLogging(boolean aSysOutLogging) {
		sysOutLogging = aSysOutLogging;
	}

	public static String getVersion() {
		return version;
	}

	public static Path getDataDirectory() {
		return dataDirectory;
	}
	
	public static Path getLoggingDirectory(Game aGame) {
		String gameDirectoryName = GAME_LOG_DIRECTORY_NAME;
		if ( aGame.isTestGame() ) {
			gameDirectoryName = TEST_GAME_LOG_DIRECTORY_NAME;
		}
		return getDataDirectory().resolve(gameDirectoryName).resolve( aGame.getID() );
	}
}
