package com.merman.celebrity.game;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.merman.celebrity.server.CelebrityMain;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.cleanup.CleanupHelper;
import com.merman.celebrity.server.cleanup.IExpiredEntityRemover;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.merman.celebrity.server.logging.Logger;
import com.merman.celebrity.server.logging.PerGameLogFilter;
import com.merman.celebrity.server.logging.outputters.FileOutputter;
import com.merman.celebrity.util.SharedRandom;

public class GameManager {
	private static Map<String, Game>       gamesMap          = new HashMap<String, Game>();
	private static Map<Player, Game>       mapHostsToGames   = new HashMap<Player, Game>();
	private static Map<Game, List<Logger>> mapGamesToLoggers = new HashMap<>();
	private static List<String>            testStringList;

	public static boolean                  deleteExisting;
	public static boolean                  createFiles;
	
	private static class MyExpiredGameRemover
	implements IExpiredEntityRemover<Game> {
		@Override
		public void remove(Game aGame) {
			synchronized (GameManager.class) {
				Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Removing game", aGame);
				gamesMap.remove(aGame.getID());
				if (aGame.getHost() != null)
					mapHostsToGames.remove(aGame.getHost());

				List<Logger> gameLoggerList = mapGamesToLoggers.get(aGame);
				if (gameLoggerList != null) {
					for (Logger gameLogger : gameLoggerList) {
						Log.removeLogger(LogMessageSubject.GENERAL, gameLogger);
						gameLogger.close();
					}
				}
			}
		}
	}
	
	private static class MyGameIterable
	implements Iterable<Game> {

		@Override
		public Iterator<Game> iterator() {
			return new ArrayList(gamesMap.values()).iterator();
		}
	}
	
	static {
		CleanupHelper.registerForRegularCleanup(new MyGameIterable(), new MyExpiredGameRemover());
	}
	
	public static synchronized Game createGame( Player aHost ) {
		String gameID = generateGameID();
		Game game = createGame(aHost, gameID);
		
		return game;
	}

	private static Game createGame(Player aHost, String aGameID) {
		Game game = new Game( aGameID );
		setPlayerAsHostOfGame(game, aHost);
		game.addPlayer(aHost);
		gamesMap.put(aGameID, game);
		
		if ( ( createFiles
				|| ! CelebrityMain.isSysOutLogging() )
			&& ! aGameID.toLowerCase().startsWith("test") ) {
			File file = new File(CelebrityMain.getDataDirectory().toString() + "/games/" + aGameID );
			if ( file.isDirectory() ) {
				if ( deleteExisting ) {
					for ( File subFile : file.listFiles() ) {
						subFile.delete();
					}
				}
			}
			else if ( ! file.exists() ) {
				file.mkdirs();
			}
			
			if ( ! CelebrityMain.isSysOutLogging() ) {
				PerGameLogFilter logFilter = new PerGameLogFilter(game);
				Logger highLevelLogger = new Logger(LogMessageType.INFO, logFilter, new FileOutputter(new File(file, "log.txt")));
				Logger detailLogger = new Logger(logFilter, new FileOutputter(new File(file, "detail_log.txt")));
				mapGamesToLoggers.put(game, Arrays.asList(highLevelLogger, detailLogger));
				Log.addLogger(LogMessageSubject.GENERAL, highLevelLogger);
				Log.addLogger(LogMessageSubject.GENERAL, detailLogger);
			}
		}
		
		return game;
	}
	
	public static synchronized void setPlayerAsHostOfGame(Game game, Player player) {
		// Make sure game isn't recorded as having a different host
		if (game.getHost() != null) {
			mapHostsToGames.remove(game.getHost());
		}
		
		if (player != null) {
			// Make sure player isn't recorded as hosting another game
			Game gamePreviouslyHostedByPlayer = mapHostsToGames.get(player);
			if (gamePreviouslyHostedByPlayer != null
					&& gamePreviouslyHostedByPlayer.getHost() == player ) {
				gamePreviouslyHostedByPlayer.setHost(null);
			}

			mapHostsToGames.put(player, game);
		}
		
		game.setHost(player);
	}

	private static String generateGameID() {
		String gameID;
		while ( gamesMap.containsKey( ( gameID = String.valueOf( 1000 + SharedRandom.getRandom().nextInt(9000) ) ) ) );
		return gameID;
	}

	public static synchronized Game getGame(String aGameID) {
		return gamesMap.get(aGameID);
	}
	
	public static synchronized Game getGameHostedByPlayer(Player aHost) {
		return mapHostsToGames.get(aHost);
	}
	
	public static String serialise( Game aGame, String aSessionIDOfRequester, boolean aForClient ) {
		Integer publicIDOfRequester = null;
		Player playerRequesting = null;
		Session sessionRequesting = null;
		if ( aSessionIDOfRequester != null ) {
			sessionRequesting = SessionManager.getSession(aSessionIDOfRequester);
			if ( sessionRequesting != null ) {
				playerRequesting = sessionRequesting.getPlayer();
				publicIDOfRequester = playerRequesting.getPublicUniqueID();
			}
		}
		
		// TODO should synchronize on aGame?
		
		JSONObject jsonObject = new JSONObject()
                .put( "gameID", aGame.getID() )
				.put( "status", aGame.getStatus() )
                .put( "teams", aGame.getTeamList().stream()
         			   .map( team -> new JSONObject()
         				   .put( "name", team.getTeamName() )
         				   .put( "playerList", aForClient ? team.getPlayerList().stream()
         						   			   				.map( player -> toJSON( player ) )
         						   			   				.collect( Collectors.toList() )
         						   			   			  : Collections.EMPTY_LIST ) )
         			   .collect( Collectors.toList() ) )
                .put( "rounds", aGame.getNumRounds() )
                .put( "roundIndex", aGame.getRoundIndex() )
                .put( "duration", aGame.getRoundDurationInSec() )
                .put( "numNames", aGame.getNumNamesPerPlayer() )
                .put( "scores", aGame.getTeamList().stream()
        		        .map( team -> new JSONObject()
        		            .put( "name", team.getTeamName() )
        		            .put( "scores" , aGame.getMapTeamsToScores().get(team) ) )
        		        .collect( Collectors.toList() ) )
                .put( "namesAchieved", aGame.getTeamList().stream()
 		               .map( team -> new JSONObject()
 		            		   .put( "name", team.getTeamName() )
 		            		   .put( "namesAchieved", aGame.getMapTeamsToAchievedNames().get( team ) ) )
 		               .collect( Collectors.toList() ) )
                ;
		if ( aForClient ) {
			jsonObject
            .put( "currentPlayer", toJSON( aGame.getCurrentPlayer() ) )
            .put( "yourName", playerRequesting.getName() )
            .put( "yourTeamIndex", aGame.getTeamList().indexOf( aGame.getMapPlayersToTeams().get(playerRequesting) ) )
            .put( "publicIDOfRecipient", publicIDOfRequester )
			.put( "host", toJSON( aGame.getHost() ) )
			.put( "players", aGame.getPlayersWithoutTeams().stream()
					.map( player -> toJSON( player ) )
					.collect( Collectors.toList() ) )
			.put( "numPlayersToWaitFor", aGame.getNumPlayersToWaitFor() )
			.put( "turnCount", aGame.getTurnCount() )
			;
			
			if (sessionRequesting != null) {
				jsonObject.put( "sessionMaxAge", sessionRequesting.getExpiryTime().getDurationToExpirySeconds());
			}
			
			if ( aGame.getCurrentPlayer() != null ) {
				jsonObject.put( "currentPlayerID", aGame.getCurrentPlayer().getPublicUniqueID() );
			}
			
			if ( aGame.getNextTeamIndex() >= 0 ) {
				jsonObject.put("nextTeamIndex", aGame.getNextTeamIndex());
			}
			
			List<String> nameList = aGame.getNameList(playerRequesting);
			if (nameList != null) {
				jsonObject.put("submittedNameList", nameList);
			}
		}
		else {
			jsonObject
	          .put( "currentNameIndex", aGame.getCurrentNameIndex() )
	          .put( "nameList", aGame.getShuffledNameList() )
	          ;
		}
		
		if ( aGame.getStatus() == GameStatus.PLAYING_A_TURN
				&& aGame.getCurrentTurn() != null
				&& aForClient ) {
			Turn currentTurn = aGame.getCurrentTurn();
			if ( currentTurn.isStarted()
					&& ! currentTurn.isStopped() ) {
				jsonObject.put( "secondsRemaining", currentTurn.getSecondsRemaining() )
				          .put( "previousNameIndex", aGame.getPreviousNameIndex() )
				          .put( "currentNameIndex", aGame.getCurrentNameIndex() )
				          .put( "totalNames", aGame.getShuffledNameList().size())
				          ;
				if (playerRequesting != null
						&& aGame.getCurrentPlayer() == playerRequesting) {
					int currentNameIndex = aGame.getCurrentNameIndex();
					if (currentNameIndex < aGame.getShuffledNameList().size()) {
						jsonObject.put("currentName", aGame.getShuffledNameList().get(currentNameIndex));
					}
				}
			}
		}
		else {
			jsonObject.put( "secondsRemaining", 0 );
	          
		}
		
		
		return jsonObject.toString();
	}
	
	private static JSONObject toJSON( Player aPlayer ) {
		if ( aPlayer == null ) {
			return null;
		}
		
		return new JSONObject()
				.put("name", aPlayer.getName())
				.put("publicID", aPlayer.getPublicUniqueID())
				.put("icon", aPlayer.getIcon())
				.put("emoji", aPlayer.getEmoji())
				;
	}
	
	public static synchronized void createTestGame(String aGameID, Player aPlayer) {
		Game game = createGame(aPlayer, aGameID);
		game.setFireEvents(false);
		
		int numOtherPlayers = 1 + (int) ( 9*SharedRandom.getRandom().nextDouble() );
		
		
		for (int playerIndex = 0; playerIndex < numOtherPlayers; playerIndex++) {
			Player player = PlayerManager.createPlayer();
			String randomName = createRandomName();
			player.setName( randomName );
			
			game.addPlayer(player);
		}
		
		int numRounds = 3;
		int numNamesPerPlayer = 6;
		int roundDurationInSec = 60;
		
		game.setNumRounds(numRounds);
		game.setNumNamesPerPlayer(numNamesPerPlayer);
		game.setRoundDurationInSec(roundDurationInSec);
		game.allocateTeams(true);
		game.setStatus(GameStatus.WAITING_FOR_NAMES);
		
		final Game g = game;
		game.getTeamList().forEach(
				t -> t.getPlayerList().forEach(
			    p -> g.setNameList(p, createRandomNameList(numNamesPerPlayer) ) ) );
		
		game.freezeNameList();
		for ( int roundIndex = 0; roundIndex < numRounds; roundIndex++ ) {
			Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Game", game, "playing round " + roundIndex );
			game.shuffleNames();
			game.startRound();
			
			while ( game.getStatus() != GameStatus.READY_TO_START_NEXT_ROUND
					&& game.getStatus() != GameStatus.ENDED ) {
				game.setStatus(GameStatus.PLAYING_A_TURN);
				int		remainingNames 		= game.getShuffledNameList().size() - game.getCurrentNameIndex();
				int		numNamesAchieved	= Math.min( remainingNames, (int) ( 7 * SharedRandom.getRandom().nextDouble() ) );
				int 	newNameIndex 		= game.getCurrentNameIndex() + numNamesAchieved;
				
				game.setCurrentNameIndex( newNameIndex );
				game.turnEnded();
			}
		}
		
		game.setFireEvents(true);
		game.setStatus(GameStatus.ENDED);
	}

	private static List<String> createRandomNameList(int aNumNames) {
		List<String>		randomNameList		= new ArrayList<String>();
		for (int i = 0; i < aNumNames; i++) {
			String randomName = createRandomName();
			randomNameList.add( randomName );
		}
		return randomNameList;
	}

	private static String createRandomName() {
		if ( testStringList == null ) {
			createTestStringList();
		}
		int numWords = 1 + (int) (4*SharedRandom.getRandom().nextDouble());
		StringBuilder		nameBuilder		= new StringBuilder();
		
		for (int wordIndex = 0; wordIndex < numWords; wordIndex++) {
			if ( wordIndex > 0 ) {
				nameBuilder.append( " " );
			}
			nameBuilder.append( testStringList.get( (int) ( SharedRandom.getRandom().nextDouble() * testStringList.size() ) ) );
		}
		return nameBuilder.toString();
	}

	private static void createTestStringList() {
		try {
			byte[] fileAsByteArr = Files.readAllBytes( Paths.get("lorem-ipsum.txt") );
			String	fileAsString = new String(fileAsByteArr);
			testStringList = Arrays.asList( fileAsString.split( "\\s+" ) );
		} catch (IOException e) {
			e.printStackTrace();
			testStringList = Arrays.asList( "couldn't", "read", "file" );
		}
	}

	public static void restoreGame(JSONObject aJsonObject) {
		String gameID = aJsonObject.getString("gameID");
		Game game = new Game(gameID);
		game.setStatus(GameStatus.valueOf(aJsonObject.getString("status")));
		
		JSONArray teamsArray = aJsonObject.getJSONArray("teams");
		for ( Object object : teamsArray ) {
			JSONObject teamObject = (JSONObject) object;
			String teamName = teamObject.getString("name");
			Team team = new Team();
			team.setTeamName(teamName);
			game.getTeamList().add(team);
		}
		
		int numRounds = aJsonObject.getInt("rounds");
		game.setNumRounds(numRounds);
		int roundIndex = aJsonObject.getInt("roundIndex");
		game.setRoundIndex(roundIndex);
		int roundDuration = aJsonObject.getInt("duration");
		game.setRoundDurationInSec(roundDuration);
		int numNames = aJsonObject.getInt("numNames");
		game.setNumNamesPerPlayer(numNames);
		JSONArray scoresArray = aJsonObject.getJSONArray("scores");
		for ( Object object : scoresArray ) {
			JSONObject scoresObject = (JSONObject) object;
			String teamName = scoresObject.getString("name");
			Team teamWithScore = null;
			for ( Team team : game.getTeamList() ) {
				if ( team.getTeamName().equals(teamName)) {
					teamWithScore = team;
					break;
				}
			}
			if ( teamWithScore != null ) {
				JSONArray scoreListArray = scoresObject.getJSONArray("scores");
				List<Integer> scoreList		= new ArrayList<>();
				scoreListArray.forEach(score -> scoreList.add((Integer) score));
				
				game.getMapTeamsToScores().put(teamWithScore, scoreList);
			}
		}
		
		JSONArray namesAchievedArray = aJsonObject.getJSONArray("namesAchieved");
		for ( Object object : namesAchievedArray ) {
			JSONObject namesAchievedObject = (JSONObject) object;
			String teamName = namesAchievedObject.getString("name");
			Team teamWithNames = null;
			for ( Team team : game.getTeamList() ) {
				if ( team.getTeamName().equals(teamName)) {
					teamWithNames = team;
					break;
				}
			}
			if ( teamWithNames != null ) {
				JSONArray namesAchievedByThisTeamArray = namesAchievedObject.getJSONArray("namesAchieved");
				List<String> namesAchievedList = new ArrayList<>();
				namesAchievedByThisTeamArray.forEach(name -> namesAchievedList.add((String) name));
				game.getMapTeamsToAchievedNames().put(teamWithNames, namesAchievedList);
			}
		}
		
		int currentNameIndex = aJsonObject.getInt("currentNameIndex");
		game.setCurrentNameIndexOnDeserialisation(currentNameIndex);
		game.setPreviousNameIndex(currentNameIndex);
		JSONArray jsonArray = aJsonObject.getJSONArray("nameList");
		List<String> shuffledNameList = new ArrayList<>();
		jsonArray.forEach(name -> shuffledNameList.add((String) name));
		game.getShuffledNameList().addAll(shuffledNameList);
		game.getMasterNameList().addAll(shuffledNameList);
		
		game.incrementPlayer();
		
		gamesMap.put(gameID, game);
	}
	
	public static synchronized int getNumGames() {
		return gamesMap.size();
	}
	
	public static synchronized List<Game> getAllNonExpiredGames() {
		List<Game> gameList = new ArrayList<>();
		
		for (Game game : gamesMap.values()) {
			if (! game.isExpired()) {
				gameList.add(game);
			}
		}
		
		return gameList;
	}
}
