package com.merman.celebrity.server.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.game.GameStatus;
import com.merman.celebrity.game.Player;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.WebsocketHandler;
import com.merman.celebrity.server.annotations.HTTPRequest;
import com.merman.celebrity.server.exceptions.IllegalServerRequestException;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.info.LogInfo;
import com.merman.celebrity.server.logging.info.PerGameLogInfo;
import com.merman.celebrity.server.logging.info.SessionLogInfo;

public class AnnotatedHandlers {
/* If error, throw exception, ensure client knows about it and can inform the user.
 * Think about what kind of validation should be done in these methods vs in the Game object.
 */
	@HTTPRequest(requestName = "username", argNames = {"username"})
	public static void setUsername(Session session, String username) {
		if (username == null
				|| username.trim().isEmpty() ) {
			throw new IllegalServerRequestException( String.format("Session [%s], illegal user name [%s]", session, username), null);
		}
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
		if (gameID == null) {
			throw new IllegalServerRequestException( String.format("Session [%s], player [%s] provided null game ID", session, session.getPlayer()), null);
		}
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
		
		if (nameList == null) {
			throw new IllegalServerRequestException( String.format("Session [%s], player [%s], game [%s], provided null name list", session, player, game), null);
		}
		
		if (game == null) {
			throw new IllegalServerRequestException(String.format("Session [%s], player [%s], trying to provide names when game is null", session, player), "Error: you provided names, but you're not currently part of any game");
		}
		
		if (game.getStatus() != GameStatus.WAITING_FOR_NAMES) {
			throw new IllegalServerRequestException(String.format("Session [%s], player [%s], trying to provide names when game is in state", session, player, game.getStatus()), "Error: you provided names, but we don't need them from you at this time");
		}
		
		int numNamesPerPlayer = game.getNumNamesPerPlayer();
		if (nameList.size() != numNamesPerPlayer) {
			throw new IllegalServerRequestException(String.format("Session [%s], player [%s], game [%s], provided [%,d] names instead of [%,d]. Full list: %s", session, player, game, nameList.size(), numNamesPerPlayer, nameList), String.format( "Error: you gave %,d names, we need %,d", nameList.size(), numNamesPerPlayer ) );
		}
		
		for (String name : nameList) {
			if (name == null
					|| name.trim().isEmpty() ) {
				throw new IllegalServerRequestException(String.format("Session [%s], player [%s], game [%s], provided null or empty names. Full list: %s", session, player, game, nameList), "Error: some names had no text");
			}
		}
		
		
		game.setNameList(player, nameList);
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
	public static Map<String, String> startTurn(Session session) {
		Player player = session.getPlayer();
		Game game = player.getGame();
		if (game == null) {
			throw new IllegalServerRequestException(String.format("Session [%s], player [%s], not part of any game", session, player), "Error: you're not in any game at the moment");
		}
		else if (game.getStatus() != GameStatus.READY_TO_START_NEXT_TURN) {
			String endUserMessage = "Error: you can't start a turn right now";
			if (game.getStatus() == GameStatus.PLAYING_A_TURN) {
				endUserMessage = "Error: turn has already started";
			}
			throw new IllegalServerRequestException(String.format("Session [%s], player [%s], trying to start turn in game [%s], but its state is [%s]", session, player, game, game.getStatus()), endUserMessage);
		}
		else if (game.getCurrentPlayer() != player) {
			throw new IllegalServerRequestException(String.format("Session [%s], player [%s], trying to start turn in game [%s], but current player is [%s]", session, player, game, game.getCurrentPlayer()), String.format( "Error: it should be %s to take the next turn", game.getCurrentPlayer() ) );
		}
		
		game.startTurn();
		
		Map<String, String> responseMap = new HashMap<>();
		responseMap.put("status", "OK");
		
		return responseMap;
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
		if (game != null
				&& ( game.getHost() == player
						|| player.getPublicUniqueID() == playerID ) ) {
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
	
	@HTTPRequest(requestName = "setTeamIndex", argNames = "index")
	public static void setTeamIndex(Session session, Integer index) {
		Player player = session == null ? null : session.getPlayer();
		Game game = player == null ? null : GameManager.getGameHostedByPlayer(player);
		if (game != null
				&& index != null
				&& index >= 0
				&& index < game.getTeamList().size()) {
			game.setTeamIndex(index);
		}
		else {
			Log.log(LogInfo.class, "Error: cannot set team index. Session", session, "Player", player, "Game", game, "Index", index);
		}
	}
	
	@HTTPRequest(requestName = "getRestorableGameList")
	public static Map<String, List<String>> getRestorableGameList(Session session) {
		Map<String, List<String>>		responseMap		= new HashMap<>();
		List<Game> allNonExpiredGames = GameManager.getAllNonExpiredGames();
		List<String> gameIDList = allNonExpiredGames.stream()
													.map(game -> game.getID())
													.collect(Collectors.toList());
		
		Collections.sort(gameIDList);
		responseMap.put("gameList", gameIDList);
		
		return responseMap;
	}
	
	@HTTPRequest(requestName = "restoreGame", argNames = "gameID")
	public static Map<String, Object> restoreGame(Session session, String gameID) {
		Map<String, Object>		responseMap		= new HashMap<>();
		Game game = GameManager.getGame(gameID);
		if (game == null) {
			responseMap.put("result", "error");
			responseMap.put("message", "Unknown game ID: " + gameID);
		}
		else {
			List<Player> allReferencedPlayers = game.getAllReferencedPlayers();
			long currentTimeMillis = System.currentTimeMillis();
			Map<Player, Long>		mapActivePlayersToLastSeenDurations		= new LinkedHashMap<Player, Long>();
			for (Player player: allReferencedPlayers) {
				if ( ! player.isExpired() ) {
					Session playerSession = SessionManager.getSession(player.getSessionID());
					if (playerSession != null) {
						WebsocketHandler websocketHandler = SessionManager.getWebsocketHandler(playerSession);
						if (websocketHandler != null
								&& websocketHandler.isListening() ) {
							mapActivePlayersToLastSeenDurations.put(player, ( currentTimeMillis - websocketHandler.getLastSeenTimeMillis() ) / 1000);
						}
					}
				}
			}
			
			if ( ! mapActivePlayersToLastSeenDurations.isEmpty() ) {
				List<String> activePlayerNameList = mapActivePlayersToLastSeenDurations.keySet()
																				   .stream()
																				   .map(player -> player.getName())
																				   .collect(Collectors.toList());
				
				responseMap.put("result", "still_active");
				responseMap.put("activePlayers", activePlayerNameList);
				responseMap.put("lastSeenAgesInSeconds", new ArrayList<>(mapActivePlayersToLastSeenDurations.values()));
			}
			else {
				responseMap.put("result", "OK");
				game.removeAllPlayers();
				game.addPlayer(session.getPlayer()); // This player becomes the host
			}
		}
		
		return responseMap;
	}
}
