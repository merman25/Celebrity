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
		StringBuilder builder = new StringBuilder();
		synchronized (aGame) {
			builder.append("host=");
			builder.append(aGame.getHost().getName());
			builder.append("&");
			builder.append("state=");
			builder.append(aGame.getState().toString());
			if ( ! aGame.getPlayersWithoutTeams().isEmpty() ) {
				builder.append("&");
				builder.append("players=");
				
				boolean first = true;
				for ( Player player : aGame.getPlayersWithoutTeams() ) {
					if ( ! first ) {
						builder.append(",");
					}
					first = false;
					builder.append(player.getName());
				}
			}
			if ( ! aGame.getTeamList().isEmpty() ) {
				builder.append("&teams=");
				
				boolean first = true;
				for ( Team team : aGame.getTeamList() ) {
					if ( ! first ) {
						builder.append(",");
					}
					first = false;
					builder.append( team.getTeamName() );
					if ( ! team.getPlayerList().isEmpty() ) {
						for ( Player player : team.getPlayerList() ) {
							builder.append("|");
							builder.append(player.getName());
						}
					}
				}
				
				builder.append("&scores=");
				
				first = true;
				for ( Team team : aGame.getTeamList() ) {
					if ( ! first ) {
						builder.append(",");
					}
					first = false;
					builder.append( team.getTeamName() );
					
					List<Integer> scoreList = aGame.getMapTeamsToScores().get(team);
					if ( scoreList != null ) {
						for ( Integer score : scoreList ) {
							builder.append("|");
							builder.append(score);
						}
					}
				}
			}
			if ( aGame.getNumRounds() > 0 ) {
				builder.append("&rounds=");
				builder.append( aGame.getNumRounds() );
				builder.append("&roundIndex=");
				builder.append( aGame.getRoundIndex() );
			}
			if ( aGame.getRoundDurationInSec() > 0 ) {
				builder.append("&duration=");
				builder.append( aGame.getRoundDurationInSec() );
			}
			if ( aGame.getNumNamesPerPlayer() > 0 ) {
				builder.append("&numNames=");
				builder.append( aGame.getNumNamesPerPlayer() );
			}
			if ( aGame.getState() == GameState.READY_TO_START_NEXT_TURN
					|| aGame.getState() == GameState.PLAYING_A_TURN ) {
				Player currentPlayer = aGame.getCurrentPlayer();
				builder.append("&currentPlayerSession=");
				builder.append(currentPlayer.getSessionID());
				builder.append("&currentPlayer=");
				builder.append(currentPlayer.getName());
			}
			if ( aGame.getState() == GameState.WAITING_FOR_NAMES ) {
				int numPlayersToWaitFor = aGame.getNumPlayersToWaitFor();
				builder.append("&numPlayersToWaitFor=");
				builder.append(numPlayersToWaitFor);
			}
			if ( aGame.getState() == GameState.PLAYING_A_TURN
					&& aGame.getCurrentTurn() != null ) {
				Turn currentTurn = aGame.getCurrentTurn();
				if ( currentTurn.isStarted()
						&& ! currentTurn.isStopped() ) {
					builder.append("&secondsRemaining=");
					builder.append(currentTurn.getSecondsRemaining());
					builder.append("&previousNameIndex=");
					builder.append(aGame.getPreviousNameIndex());
					builder.append("&currentNameIndex=");
					builder.append(aGame.getCurrentNameIndex());
					builder.append("&nameList=");
					builder.append(String.join(",", aGame.getShuffledNameList()));
				}
			}
			
			if ( ! aGame.getMapTeamsToAchievedNames().isEmpty() ) {
				builder.append("&namesAchieved=");
				
				boolean first = true;
				for ( Team team : aGame.getTeamList() ) {
					if ( ! first ) {
						builder.append(",");
					}
					first = false;
					List<String> achievedNames = aGame.getMapTeamsToAchievedNames().get(team);
					builder.append(team.getTeamName());
					if ( achievedNames != null ) {
						builder.append("|");
						builder.append(String.join("|", achievedNames));
					}
				}
			}
		}
		
		return builder.toString();
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
