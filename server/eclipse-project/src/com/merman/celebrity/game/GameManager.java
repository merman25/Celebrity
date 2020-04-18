package com.merman.celebrity.game;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.json.JSONObject;

public class GameManager {
	private static Map<String, Game>		gamesMap		= new HashMap<String, Game>();
	private static Map<Player, Game>		mapHostsToGames	= new HashMap<Player, Game>();
	private static List<String>             testStringList;
	
	public static synchronized Game createGame( Player aHost ) {
		String gameID = generateGameID();
		Game game = createGame(aHost, gameID);
		
		return game;
	}

	private static Game createGame(Player aHost, String aGameID) {
		Game game = new Game( aGameID, aHost );
		game.addPlayer(aHost);
		gamesMap.put(aGameID, game);
		mapHostsToGames.put(aHost, game);
		return game;
	}

	private static String generateGameID() {
		String gameID;
		while ( gamesMap.containsKey( ( gameID = String.valueOf( 1000 + new Random().nextInt(9000) ) ) ) );
		return gameID;
	}

	public static synchronized Game getGame(String aGameID) {
		return gamesMap.get(aGameID);
	}
	
	public static synchronized Game getGameHostedByPlayer(Player aHost) {
		return mapHostsToGames.get(aHost);
	}
	
	public static synchronized String serialise( Game aGame ) {
		JSONObject jsonObject = new JSONObject()
                .put( "host", aGame.getHost().getName() )
                .put( "state", aGame.getState() )
                .put( "players", aGame.getPlayersWithoutTeams().stream()
                				 .map( player -> player.getName() )
                				 .collect( Collectors.toList() ) )
                .put( "teams", aGame.getTeamList().stream()
                			   .map( team -> new JSONObject()
                				   .put( "name", team.getTeamName() )
                				   .put( "playerList", team.getPlayerList().stream()
                						   			   .map( player -> player.getName() )
                						   			   .collect( Collectors.toList() ) ) )
                			   .collect( Collectors.toList() ) )
                .put( "scores", aGame.getTeamList().stream()
                		        .map( team -> new JSONObject()
                		            .put( "name", team.getTeamName() )
                		            .put( "scores" , aGame.getMapTeamsToScores().get(team) ) )
                		        .collect( Collectors.toList() ) )
                .put( "rounds", aGame.getNumRounds() )
                .put( "roundIndex", aGame.getRoundIndex() )
                .put( "duration", aGame.getRoundDurationInSec() )
                .put( "numNames", aGame.getNumNamesPerPlayer() )
                .put( "currentPlayerSession", aGame.getCurrentPlayer() == null ? null : aGame.getCurrentPlayer().getSessionID() )
                .put( "currentPlayer", aGame.getCurrentPlayer() == null ? null : aGame.getCurrentPlayer().getName() )
                .put( "numPlayersToWaitFor", aGame.getNumPlayersToWaitFor() )
                .put( "namesAchieved", aGame.getTeamList().stream()
                		               .map( team -> new JSONObject()
                		            		   .put( "name", team.getTeamName() )
                		            		   .put( "namesAchieved", aGame.getMapTeamsToAchievedNames().get( team ) ) )
                		               .collect( Collectors.toList() ) )
                ;
		
		if ( aGame.getState() == GameState.PLAYING_A_TURN
				&& aGame.getCurrentTurn() != null ) {
			Turn currentTurn = aGame.getCurrentTurn();
			if ( currentTurn.isStarted()
					&& ! currentTurn.isStopped() ) {
				jsonObject.put( "secondsRemaining", currentTurn.getSecondsRemaining() )
				          .put( "previousNameIndex", aGame.getPreviousNameIndex() )
				          .put( "currentNameIndex", aGame.getCurrentNameIndex() )
				          .put( "nameList", aGame.getShuffledNameList() )
				          ;
			}
		}
		
		
		return jsonObject.toString();
	}
	
	public static synchronized void createTestGame(String aGameID, Player aPlayer) {
		Game game = null;
		
		int numOtherPlayers = 1 + (int) ( 9*Math.random() );
		
		
		for (int playerIndex = 0; playerIndex < numOtherPlayers; playerIndex++) {
			Player player = new Player();
			String randomName = createRandomName();
			try {
				randomName = URLEncoder.encode( randomName, "UTF-8" );
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			player.setName( randomName );
			
			if ( playerIndex == 0 ) {
				game = createGame(player, aGameID);
				game.addPlayer(aPlayer);
			}
			else {
				game.addPlayer(player);
			}
		}
		
		int numRounds = 3;
		int numNamesPerPlayer = 6;
		int roundDurationInSec = 60;
		
		game.setNumRounds(numRounds);
		game.setNumNamesPerPlayer(numNamesPerPlayer);
		game.setRoundDurationInSec(roundDurationInSec);
		game.allocateTeams();
		game.setState(GameState.WAITING_FOR_NAMES);
		
		final Game g = game;
		game.getTeamList().forEach(
				t -> t.getPlayerList().forEach(
			    p -> g.setNameList(p, createRandomNameList(numNamesPerPlayer) ) ) );
		
		for ( int roundIndex = 0; roundIndex < numRounds; roundIndex++ ) {
			System.out.println( "playing round " + roundIndex );
			game.shuffleNames();
			game.startRound();
			
			while ( game.getState() != GameState.READY_TO_START_NEXT_ROUND
					&& game.getState() != GameState.ENDED ) {
				game.setState(GameState.PLAYING_A_TURN);
				int		remainingNames 		= game.getShuffledNameList().size() - game.getCurrentNameIndex();
				int		numNamesAchieved	= Math.min( remainingNames, (int) ( 7 * Math.random() ) );
				int 	newNameIndex 		= game.getCurrentNameIndex() + numNamesAchieved;
				
				game.setCurrentNameIndex( newNameIndex );
				game.turnEnded();
			}
		}
		
		game.setState(GameState.ENDED);
	}

	private static List<String> createRandomNameList(int aNumNames) {
		List<String>		randomNameList		= new ArrayList<String>();
		for (int i = 0; i < aNumNames; i++) {
			String randomName = createRandomName();
			try {
				randomName = URLEncoder.encode( randomName, "UTF-8" );
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			randomNameList.add( randomName );
		}
		return randomNameList;
	}

	private static String createRandomName() {
		if ( testStringList == null ) {
			createTestStringList();
		}
		int numWords = 1 + (int) (4*Math.random());
		StringBuilder		nameBuilder		= new StringBuilder();
		
		for (int wordIndex = 0; wordIndex < numWords; wordIndex++) {
			if ( wordIndex > 0 ) {
				nameBuilder.append( " " );
			}
			nameBuilder.append( testStringList.get( (int) ( Math.random() * testStringList.size() ) ) );
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
}
