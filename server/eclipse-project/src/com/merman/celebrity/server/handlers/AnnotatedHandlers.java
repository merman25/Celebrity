package com.merman.celebrity.server.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.game.GameStatus;
import com.merman.celebrity.game.Player;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.annotations.HTTPRequest;

public class AnnotatedHandlers {

	@HTTPRequest(requestName = "username", argNames = {"username"})
	public static void setUsername(Session session, String username) {
		System.out.println( "Setting user name " + username + " to session " + session );
		session.getPlayer().setName(username);
	}
	
	@HTTPRequest(requestName = "hostNewGame")
	public static Map<String, String> hostNewGame(Session session) {
		System.out.println( "Session " + session + " [" + session.getPlayer().getName() + "] will host a game" );
		Game game = GameManager.createGame(session.getPlayer());
		
		HashMap<String, String>		responseMap		= new HashMap<>();
		responseMap.put("gameID", game.getID());
		return responseMap;
	}
	
	@HTTPRequest(requestName = "requestGameState", argNames = {"gameID"})
	public static String requestGameState(Session session, String gameID) {
		Game game = GameManager.getGame(gameID);
		if ( game != null ) {
			try {
				String serialisedGame = GameManager.serialise(game, session.getSessionID(), true);
				return serialisedGame;
			}
			catch ( RuntimeException e ) {
				e.printStackTrace();
			}
		}

		return null;
	}
	
	@HTTPRequest(requestName = "gameParams", argNames = {"numRounds", "roundDuration", "numNames"})
	public static void setGameParams(Session session, Integer numRounds, Integer roundDuration, Integer numNames ) {
		if ( numRounds <= 0 || numRounds > 10 ) {
			System.err.println( "numRounds " + numRounds + ", should be 1-10" );
			return;
		}
		if ( roundDuration <= 0 || roundDuration > 600 ) {
			System.err.println( "roundDuration " + roundDuration + ", should be 1-600" );
			return;
		}
		if ( numNames <= 0 || numNames > 10 ) {
			System.err.println( "numNames " + numNames + ", should be 1-10" );
			return;
		}

		
		Player player = session.getPlayer();
		Game game = GameManager.getGameHostedByPlayer(player);

		if ( game != null ) {
			game.setNumRounds(numRounds);
			game.setRoundDurationInSec(roundDuration);
			game.setNumNamesPerPlayer(numNames);
			game.fireGameEvent();
		}
		else {
			System.err.println( player + " is not hosting any game" );
		}
	}
	
	@HTTPRequest( requestName = "allocateTeams" )
	public static void allocateTeams(Session session) {
		Player player = session.getPlayer();
		Game game = GameManager.getGameHostedByPlayer(player);

		if ( game != null ) {
			System.out.println( "allocating teams" );
			game.allocateTeams(true);
		}
		else {
			System.err.println( player + " is not hosting any game" );
		}

	}
	
	@HTTPRequest(requestName = "askGameIDResponse", argNames = {"gameID"})
	public static Map<String, String> askGameIDResponse(Session session, String gameID) {
		Map<String, String>	responseMap		= new HashMap<String, String>();
		
		Game game = GameManager.getGame(gameID);
		if ( gameID.trim().toLowerCase().startsWith("test")) {
			GameManager.createTestGame(gameID, session.getPlayer());
			responseMap.put("GameResponse", "TestGameCreated");
			responseMap.put("GameID", gameID);
		}
		else if ( game != null ) {
			System.out.println( "Session " + session + " [" + session.getPlayer().getName() + "] wants to join game " + gameID );
			game.addPlayer(session.getPlayer());
			responseMap.put("GameResponse", "OK");
			responseMap.put("GameID", game.getID());
		}
		else {
			System.err.println("Game not found: " + gameID);
			responseMap.put("GameResponse", "NotFound");
		}

		return responseMap;
	}
	
	@HTTPRequest(requestName = "sendNameRequest")
	public static void sendNameRequest(Session session) {
		Game game = session.getPlayer().getGame();
		if ( game != null ) {
			game.setStatus(GameStatus.WAITING_FOR_NAMES);
		}
	}
	
	@HTTPRequest(requestName = "nameList", argNames = {"nameList"})
	public static void provideNames(Session session, List<String> nameList ) {
		Player player = session.getPlayer();
		Game game = player.getGame();
		if ( game != null
				&& game.getStatus() == GameStatus.WAITING_FOR_NAMES ) {
			game.setNameList(player, nameList);
		}
	}
	
	@HTTPRequest(requestName = "startGame")
	public static void startGame(Session session) {
		Player player = session.getPlayer();
		Game gameHostedByPlayer = GameManager.getGameHostedByPlayer(player);
		if ( gameHostedByPlayer != null
				&& gameHostedByPlayer.getStatus() == GameStatus.WAITING_FOR_NAMES ) {
			gameHostedByPlayer.freezeNameList();
			gameHostedByPlayer.shuffleNames();
			gameHostedByPlayer.allowNextPlayerToStartNextTurn();
		}
	}
	
	@HTTPRequest(requestName = "startTurn")
	public static void startTurn(Session session) {
		session.getPlayer().getGame().startTurn();
	}
	
	@HTTPRequest(requestName = "startNextRound")
	public static void startNextRound(Session session) {
		session.getPlayer().getGame().startRound();
	}
	
	@HTTPRequest(requestName = "setCurrentNameIndex", argNames = {"newNameIndex"})
	public static void setCurrentNameIndex(Session session, Integer newNameIndex ) {
		session.getPlayer().getGame().setCurrentNameIndex(newNameIndex);
	}
	
	@HTTPRequest(requestName = "pass", argNames = {"passNameIndex"})
	public static Map<String, String> pass(Session session, Integer passNameIndex) {
		Game game = session.getPlayer().getGame();
		game.setPassOnNameIndex(passNameIndex);
		
		Map<String, String>	responseMap = new HashMap<>();
		responseMap.put("nameList", String.join(",", game.getShuffledNameList()));
		return responseMap;
	}
	
	@HTTPRequest(requestName = "endTurn")
	public static void endTurn(Session session) {
		Player player = session.getPlayer();
		Game game = player.getGame();
		if ( game != null ) {
			synchronized ( game ) {
				if ( game.getCurrentTurn() != null ) {
					game.getCurrentTurn().stop();
				}
				else {
					game.turnEnded();
				}
			}
		}
	}
	
	@HTTPRequest(requestName = "putInTeam", argNames = {"playerID", "teamIndex"})
	public static void putPlayerInTeam(Session session, Integer playerID, Integer teamIndex) {
		session.getPlayer().getGame().putPlayerInTeam( playerID, teamIndex );
	}
	
	@HTTPRequest(requestName = "removeFromGame", argNames = {"playerID"})
	public static void removePlayerFromGame(Session session, Integer playerID) {
		session.getPlayer().getGame().removePlayer(playerID);
	}
	
	@HTTPRequest(requestName = "moveEarlier", argNames = {"playerID"})
	public static void movePlayerEarlier(Session session, Integer playerID) {
		session.getPlayer().getGame().movePlayerInTeamOrder(playerID, false );
	}

	@HTTPRequest(requestName = "moveLater", argNames = {"playerID"})
	public static void movePlayerLater(Session session, Integer playerID) {
		session.getPlayer().getGame().movePlayerInTeamOrder(playerID, true );
	}
	
	@HTTPRequest(requestName = "makeNextInTeam", argNames = {"playerID"})
	public static void makePlayerNextInTeam(Session session, Integer playerID) {
		session.getPlayer().getGame().makePlayerNextInTeam(playerID);
	}
}
