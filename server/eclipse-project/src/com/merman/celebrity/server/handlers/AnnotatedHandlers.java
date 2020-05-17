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
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.info.LogInfo;
import com.merman.celebrity.server.logging.info.PerGameLogInfo;
import com.merman.celebrity.server.logging.info.SessionLogInfo;

public class AnnotatedHandlers {

	@HTTPRequest(requestName = "username", argNames = {"username"})
	public static void setUsername(Session session, String username) {
		Log.log(SessionLogInfo.class, "Session", session, "Username", username);
		session.getPlayer().setName(username);
	}
	
	@HTTPRequest(requestName = "hostNewGame")
	public static Map<String, String> hostNewGame(Session session) {
		Game game = GameManager.createGame(session.getPlayer());
		Log.log(PerGameLogInfo.class, "Player", session.getPlayer(), "Session", session, "hosting game", game);
		
		HashMap<String, String>		responseMap		= new HashMap<>();
		responseMap.put("gameID", game.getID());
		return responseMap;
	}
	
	@HTTPRequest(requestName = "gameParams", argNames = {"numRounds", "roundDuration", "numNames"})
	public static void setGameParams(Session session, Integer numRounds, Integer roundDuration, Integer numNames ) {
		Player player = session.getPlayer();
		Game game = GameManager.getGameHostedByPlayer(player);
		
		Class<? extends LogInfo> logInfoClass = game == null ? LogInfo.class : PerGameLogInfo.class;
		
		if ( numRounds <= 0 || numRounds > 10 ) {
			Log.log(logInfoClass, "Game", game, "Error: numRounds " + numRounds + ", should be 1-10");
			return;
		}
		if ( roundDuration <= 0 || roundDuration > 600 ) {
			Log.log(logInfoClass, "Game", game, "roundDuration " + roundDuration + ", should be 1-600" );
			return;
		}
		if ( numNames <= 0 || numNames > 10 ) {
			Log.log(logInfoClass, "Game", game, "numNames " + numNames + ", should be 1-10" );
			return;
		}

		

		if ( game != null ) {
			game.setNumRounds(numRounds);
			game.setRoundDurationInSec(roundDuration);
			game.setNumNamesPerPlayer(numNames);
			game.fireGameEvent();
		}
		else {
			Log.log(logInfoClass, "Error: " + player + " is not hosting any game, so cannot set game params" );
		}
	}
	
	@HTTPRequest( requestName = "allocateTeams" )
	public static void allocateTeams(Session session) {
		Player player = session.getPlayer();
		Game game = GameManager.getGameHostedByPlayer(player);

		if ( game != null ) {
			Log.log(PerGameLogInfo.class, "Game", game, "allocating teams" );
			game.allocateTeams(true);
		}
		else {
			Log.log(LogInfo.class, "Error: " + player + " is not hosting any game, so can't allocate teams" );
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
			Log.log(PerGameLogInfo.class, "Adding player", session.getPlayer(), "session", session, "to game", game);
			game.addPlayer(session.getPlayer());
			responseMap.put("GameResponse", "OK");
			responseMap.put("GameID", game.getID());
		}
		else {
			Log.log(LogInfo.class, "Error: game not found: " + gameID);
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
			gameHostedByPlayer.startRound();
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
	public static Map<String, Object> pass(Session session, Integer passNameIndex) {
		Game game = session.getPlayer().getGame();
		game.setPassOnNameIndex(passNameIndex);
		
		Map<String, Object>	responseMap = new HashMap<>();
		responseMap.put("currentName", game.getShuffledNameList().get(game.getCurrentNameIndex()));
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
		Player 	player 	= session 	== null ? null : session.getPlayer();
		Game 	game 	= player 	== null ? null : player.getGame();
		if (game != null) {
			game.removePlayer(playerID);
		}
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
