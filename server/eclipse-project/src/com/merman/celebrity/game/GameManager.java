package com.merman.celebrity.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GameManager {
	private static Map<String, Game>		gamesMap		= new HashMap<String, Game>();
	private static Map<Player, Game>		mapHostsToGames	= new HashMap<Player, Game>();
	
	public static Game createGame( Player aHost ) {
		String gameID;
		while ( gamesMap.containsKey( ( gameID = String.valueOf( 1000 + new Random().nextInt(9000) ) ) ) );
		Game game = new Game( gameID, aHost );
		game.addPlayer(aHost);
		gamesMap.put(gameID, game);
		mapHostsToGames.put(aHost, game);
		
		return game;
	}

	public static Game getGame(String aGameID) {
		return gamesMap.get(aGameID);
	}
	
	public static Game getGameHostedByPlayer(Player aHost) {
		return mapHostsToGames.get(aHost);
	}
	
	public static String serialise( Game aGame ) {
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
}
