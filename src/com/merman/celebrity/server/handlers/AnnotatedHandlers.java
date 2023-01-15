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
import com.merman.celebrity.game.PlayerManager;
import com.merman.celebrity.game.events.SetGameParamsGameEvent;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.WebsocketHandler;
import com.merman.celebrity.server.annotations.HTTPRequest;
import com.merman.celebrity.server.annotations.StartNewSession;
import com.merman.celebrity.server.exceptions.IllegalServerRequestException;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;

public class AnnotatedHandlers {
	
	@StartNewSession
	@HTTPRequest(requestName = "username", argNames = {"username"})
	public static void setUsername(Session session, String username) {
		if (username == null
				|| username.trim().isEmpty() ) {
			throw new IllegalServerRequestException( String.format("Session [%s], illegal user name [%s]", session, username), null);
		}
		Log.log(LogMessageType.INFO, LogMessageSubject.SESSIONS, "Session", session, "IP", session.getOriginalInetAddress(), "Username", username);
		session.getPlayer().setName(username);
	}
	
	@HTTPRequest(requestName = "hostNewGame")
	public static Map<String, String> hostNewGame(Session session) {
		Game game = GameManager.createGame(session.getPlayer());
		Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Player", session.getPlayer(), "Session", session, "hosting game", game);
		
		HashMap<String, String>		responseMap		= new HashMap<>();
		responseMap.put("gameID", game.getID());
		return responseMap;
	}
	
	@HTTPRequest(requestName = "gameParams", argNames = {"numRounds", "roundDuration", "numNames"})
	public static void setGameParams(Session session, Integer numRounds, Integer roundDuration, Integer numNames ) {
		Player player = session.getPlayer();
		Game game = GameManager.getGameHostedByPlayer(player);
		
		if (game == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], tried to set game params when not hosting", session.getPlayer(), session ), "Error: you're not the host, you can't change the game settings" );
		}
		
		if ( numRounds == null || numRounds <= 0 || numRounds > 10 ) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], numRounds [%,d], should be 1-10", session.getPlayer(), session, numRounds), "Error: the number of rounds should be 1-10");
		}
		if ( roundDuration == null || roundDuration <= 0 || roundDuration > 600 ) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], roundDuration [%,d], should be 1-10", session.getPlayer(), session, roundDuration), "Error: the round duration should be between 1 second and 10 minutes (600s)");
		}
		if ( numNames == null || numNames <= 0 || numNames > 10 ) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], numNames [%,d], should be 1-10", session.getPlayer(), session, numNames), "Error: the number of names per player should be 1-10");
		}

		if (game.getNumRounds() > 0
				|| game.getRoundDurationInSec() > 0
				|| game.getNumNamesPerPlayer() > 0 ) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], tried to set game params more than once", session.getPlayer(), session), "Error: settings have already been set, now they can't be changed for this game");
		}

		game.setNumRounds(numRounds);
		game.setRoundDurationInSec(roundDuration);
		game.setNumNamesPerPlayer(numNames);
		game.fireGameEvent(new SetGameParamsGameEvent(game, numRounds, roundDuration, numNames));
	}
	
	@HTTPRequest( requestName = "allocateTeams", argNames = {"numTeams"} )
	public static void allocateTeams(Session session, Integer aNumTeams) {
		Player player = session.getPlayer();
		Game game = GameManager.getGameHostedByPlayer(player);
		
		if (game == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to allocate teams, but is not the host", session.getPlayer(), session, session.getPlayer().getGame()), "Error: you're not the host, you can't allocate teams");
		}
		
		if (game.getStatus() != GameStatus.WAITING_FOR_PLAYERS) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to allocate teams when game is in state [%s]", session.getPlayer(), session, game, game.getStatus()), "Error: too late to reallocate teams");
		}

		Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Game", game, "allocating teams" );
		
		try {
			game.allocateTeams(aNumTeams, true);
		}
		catch (IllegalArgumentException e) {
			throw new IllegalServerRequestException(e.toString(), e.getMessage());
		}
	}
	
	@HTTPRequest(requestName = "askGameIDResponse", argNames = {"gameID"})
	public static Map<String, String> askGameIDResponse(Session session, String gameID) {
		if (gameID == null) {
			throw new IllegalServerRequestException( String.format("Session [%s], player [%s] provided null game ID", session, session.getPlayer()), null);
		}
		Map<String, String>	responseMap		= new HashMap<>();
		
		Game game = GameManager.getGame(gameID);
		if ( gameID.trim().toLowerCase().startsWith("test")) {
			GameManager.createTestGame(gameID, session.getPlayer());
			responseMap.put("GameResponse", "TestGameCreated");
			responseMap.put("GameID", gameID);
		}
		else if ( game != null ) {
			Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Adding player", session.getPlayer(), "session", session, "to game", game);
			game.addPlayer(session.getPlayer());
			responseMap.put("GameResponse", "OK");
			responseMap.put("GameID", game.getID());
		}
		else {
			Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Game not found", gameID);
			responseMap.put("GameResponse", "NotFound");
		}

		return responseMap;
	}
	
	@HTTPRequest(requestName = "sendNameRequest")
	public static void sendNameRequest(Session session) {
		Player player = session.getPlayer();
		Game game = GameManager.getGameHostedByPlayer(player);
		
		if (game == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to request names, but is not the host", session.getPlayer(), session, session.getPlayer().getGame()), "Error: you're not the host, you can't request names");
		}
		
		if (game.getStatus() != GameStatus.WAITING_FOR_PLAYERS) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to request names when game is in state [%s]", session.getPlayer(), session, game, game.getStatus()), "Error: too late to request names");
		}

		game.setStatus(GameStatus.WAITING_FOR_NAMES);
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
		
		if (gameHostedByPlayer == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to start game, but is not the host", session.getPlayer(), session, session.getPlayer().getGame()), "Error: you're not the host, you can't start the game");
		}
			
		if (gameHostedByPlayer.getStatus() != GameStatus.WAITING_FOR_NAMES) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to start game in state [%s]", session.getPlayer(), session, gameHostedByPlayer, gameHostedByPlayer.getStatus()), "Error: game cannot be started at this time");
		}
		
		if (gameHostedByPlayer.getNumPlayersToWaitFor() != 0) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to start game while still waiting for [%,d] players", session.getPlayer(), session, gameHostedByPlayer, gameHostedByPlayer.getNumPlayersToWaitFor()), String.format("Error: can't start game, still waiting for %,d players", gameHostedByPlayer.getNumPlayersToWaitFor()) );
		}

		gameHostedByPlayer.freezeNameList();
		gameHostedByPlayer.shuffleNames();
		gameHostedByPlayer.startRound();
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
		Player player = session.getPlayer();
		Game gameHostedByPlayer = GameManager.getGameHostedByPlayer(player);
		
		if (gameHostedByPlayer == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to start round, but is not the host", session.getPlayer(), session, session.getPlayer().getGame()), "Error: you're not the host, you can't start the round");
		}
			
		if (gameHostedByPlayer.getStatus() != GameStatus.READY_TO_START_NEXT_ROUND) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to start round in state [%s]", session.getPlayer(), session, gameHostedByPlayer, gameHostedByPlayer.getStatus()), "Error: round cannot be started at this time");
		}

		gameHostedByPlayer.startRound();
	}
	
	@HTTPRequest(requestName = "setCurrentNameIndex", argNames = {"newNameIndex"})
	public static void setCurrentNameIndex(Session session, Integer newNameIndex ) {
		Player player = session.getPlayer();
		Game game = player.getGame();
		
		if (newNameIndex == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to set a null currentNameIndex", player, session, game), null);
		}
		
		if (game == null) {
			throw new IllegalServerRequestException(String.format("Session [%s], player [%s], not part of any game", session, player), "Error: you're not in any game at the moment");
		}
		
		if (game.getStatus() != GameStatus.PLAYING_A_TURN) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to set name index in state [%s]", player, session, game, game.getStatus()), "Error: no turn is being played");
		}
		
		if (game.getCurrentPlayer() != player) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to set name index when player [%s] was playing", player, session, game, game.getCurrentPlayer()), "Error: it's not your turn");
		}
		
		if (newNameIndex != game.getCurrentNameIndex() + 1) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to change name index from %,d to %,d", player, session, game, game.getCurrentNameIndex(), newNameIndex), null);
		}
		
		game.setCurrentNameIndex(newNameIndex);
	}
	
	@HTTPRequest(requestName = "pass", argNames = {"passNameIndex"})
	public static Map<String, Object> pass(Session session, Integer passNameIndex) {
		Player player = session.getPlayer();
		Game game = player.getGame();

		if (passNameIndex == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to set a null passNameIndex", player, session, game), null);
		}
		
		if (game == null) {
			throw new IllegalServerRequestException(String.format("Session [%s], player [%s], not part of any game", session, player), "Error: you're not in any game at the moment");
		}
		
		if (game.getStatus() != GameStatus.PLAYING_A_TURN) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to pass on a game in state [%s]", player, session, game, game.getStatus()), "Error: no turn is being played");
		}
		
		if (game.getCurrentPlayer() != player) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to pass when player [%s] was playing", player, session, game, game.getCurrentPlayer()), "Error: it's not your turn");
		}
		
		if (passNameIndex != game.getCurrentNameIndex()) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to pass on name index %,d when current name index was %,d", player, session, game, passNameIndex, game.getCurrentNameIndex()), null);
		}

		game.setPassOnNameIndex(passNameIndex);
		
		Map<String, Object>	responseMap = new HashMap<>();
		responseMap.put("currentName", game.getShuffledNameList().get(game.getCurrentNameIndex()));
		return responseMap;
	}
	
	@HTTPRequest(requestName = "endTurn")
	public static void endTurn(Session session) {
		Player player = session.getPlayer();
		Game game = player.getGame();

		if (game == null) {
			throw new IllegalServerRequestException(String.format("Session [%s], player [%s], not part of any game", session, player), "Error: you're not in any game at the moment");
		}
		
		if (game.getStatus() != GameStatus.PLAYING_A_TURN) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to end the turn on a game in state [%s]", player, session, game, game.getStatus()), "Error: no turn is being played");
		}
		
		if (game.getCurrentPlayer() != player) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to end the turn when player [%s] was playing", player, session, game, game.getCurrentPlayer()), "Error: it's not your turn");
		}

		synchronized ( game ) {
			if ( game.getCurrentTurn() != null ) {
				game.getCurrentTurn().stop();
			}
			else {
				game.turnEnded();
			}
		}
	}
	
	@HTTPRequest(requestName = "putInTeam", argNames = {"playerID", "teamIndex"})
	public static void putPlayerInTeam(Session session, Integer playerID, Integer teamIndex) {
		Player player = session.getPlayer();
		
		if (playerID == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], provided null playerID", player, session), null);
		}
		if (teamIndex == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], provided null teamIndex", player, session), null);
		}
		
		Game gameHostedByPlayer = GameManager.getGameHostedByPlayer(player);
		Player playerToMove = PlayerManager.getPlayer(playerID);
		
		if (gameHostedByPlayer == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to move player [%s], ID [%,d], game [%s] to teamIndex [%,d], but is not the host", player, session, session.getPlayer().getGame(), playerToMove, playerID, playerToMove == null ? null : playerToMove.getGame(), teamIndex), "Error: you're not the host, you can't move players");
		}
		if (playerToMove == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to move player with ID [%,d] to teamIndex [%,d], but this player doesn't exist", player, session, gameHostedByPlayer, playerID, teamIndex), "Error: unkown player");
		}
		if (playerToMove.getGame() != gameHostedByPlayer) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to move player [%s], ID [%,d], to teamIndex [%,d], but that player is in game [%s]", player, session, gameHostedByPlayer, playerToMove, playerID, teamIndex, playerToMove.getGame()), "Error: that player is part of a different game");
		}
		if (teamIndex < 0
				|| teamIndex >= gameHostedByPlayer.getTeamList().size() ) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to move player [%s], ID [%,d], to non-existent teamIndex [%,d]", player, session, gameHostedByPlayer, playerToMove, playerID, teamIndex), "Error: team does not exist");
		}

		gameHostedByPlayer.putPlayerInTeam( playerID, teamIndex );
	}
	
	@HTTPRequest(requestName = "removeFromGame", argNames = {"playerID"})
	public static void removePlayerFromGame(Session session, Integer playerID) {
		Player player = session.getPlayer();
		
		if (playerID == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], provided null playerID", player, session), null);
		}
		
		Player playerToMove = PlayerManager.getPlayer(playerID);
		
		if (playerToMove == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to remove player with ID [%,d] from game, but this player doesn't exist", player, session, player.getGame(), playerID), "Error: unkown player");
		}
		if (playerToMove.getGame() == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to remove player [%s] with ID [%,d] from game, but this player is not part of any game", player, session, player.getGame(), playerToMove, playerID), "Error: player is not part of any game");
		}
		
		if (player != playerToMove
				&& playerToMove.getGame().getHost() != player) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to remove player [%s], ID [%,d], from game [%s], but is not the host", player, session, player.getGame(), playerToMove, playerID, playerToMove.getGame()), "Error: you're not the host, you can't remove any player other than yourself");
		}

		playerToMove.getGame().removePlayer(playerID);
	}
	
	@HTTPRequest(requestName = "moveEarlier", argNames = {"playerID"})
	public static void movePlayerEarlier(Session session, Integer playerID) {
		Player player = session.getPlayer();
		
		if (playerID == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], provided null playerID", player, session), null);
		}
		
		Game gameHostedByPlayer = GameManager.getGameHostedByPlayer(player);
		Player playerToMove = PlayerManager.getPlayer(playerID);
		
		if (gameHostedByPlayer == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to move player [%s], ID [%,d], game [%s] earlier in team, but is not the host", player, session, session.getPlayer().getGame(), playerToMove, playerID, playerToMove == null ? null : playerToMove.getGame()), "Error: you're not the host, you can't move players");
		}
		if (playerToMove == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to move player with ID [%,d] earlier in team, but this player doesn't exist", player, session, gameHostedByPlayer, playerID), "Error: unkown player");
		}
		if (playerToMove.getGame() != gameHostedByPlayer) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to move player [%s], ID [%,d], earlier in team, but that player is in game [%s]", player, session, gameHostedByPlayer, playerToMove, playerID, playerToMove.getGame()), "Error: that player is part of a different game");
		}

		gameHostedByPlayer.movePlayerInTeamOrder(playerID, false );
	}

	@HTTPRequest(requestName = "moveLater", argNames = {"playerID"})
	public static void movePlayerLater(Session session, Integer playerID) {
		Player player = session.getPlayer();
		
		if (playerID == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], provided null playerID", player, session), null);
		}
		
		Game gameHostedByPlayer = GameManager.getGameHostedByPlayer(player);
		Player playerToMove = PlayerManager.getPlayer(playerID);
		
		if (gameHostedByPlayer == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to move player [%s], ID [%,d], game [%s] later in team, but is not the host", player, session, session.getPlayer().getGame(), playerToMove, playerID, playerToMove == null ? null : playerToMove.getGame()), "Error: you're not the host, you can't move players");
		}
		if (playerToMove == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to move player with ID [%,d] later in team, but this player doesn't exist", player, session, gameHostedByPlayer, playerID), "Error: unkown player");
		}
		if (playerToMove.getGame() != gameHostedByPlayer) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to move player [%s], ID [%,d], later in team, but that player is in game [%s]", player, session, gameHostedByPlayer, playerToMove, playerID, playerToMove.getGame()), "Error: that player is part of a different game");
		}

		gameHostedByPlayer.movePlayerInTeamOrder(playerID, true );
	}
	
	@HTTPRequest(requestName = "makeNextInTeam", argNames = {"playerID"})
	public static void makePlayerNextInTeam(Session session, Integer playerID) {
		Player player = session.getPlayer();
		
		if (playerID == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], provided null playerID", player, session), null);
		}
		
		Game gameHostedByPlayer = GameManager.getGameHostedByPlayer(player);
		Player playerToMove = PlayerManager.getPlayer(playerID);
		
		if (gameHostedByPlayer == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to make player [%s], ID [%,d], game [%s] next in team, but is not the host", player, session, session.getPlayer().getGame(), playerToMove, playerID, playerToMove == null ? null : playerToMove.getGame()), "Error: you're not the host, you can't move players");
		}
		if (playerToMove == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to make player with ID [%,d] next in team, but this player doesn't exist", player, session, gameHostedByPlayer, playerID), "Error: unkown player");
		}
		if (playerToMove.getGame() != gameHostedByPlayer) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to make player [%s], ID [%,d], next in team, but that player is in game [%s]", player, session, gameHostedByPlayer, playerToMove, playerID, playerToMove.getGame()), "Error: that player is part of a different game");
		}

		gameHostedByPlayer.makePlayerNextInTeam(playerID);
	}
	
	@HTTPRequest(requestName = "setTeamIndex", argNames = "index")
	public static void setTeamIndex(Session session, Integer index) {
		Player player = session.getPlayer();
		
		if (index == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], provided null teamIndex", player, session), null);
		}
		
		Game gameHostedByPlayer = GameManager.getGameHostedByPlayer(player);
		
		if (gameHostedByPlayer == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to make set team index to [%,d], but is not the host", player, session, session.getPlayer().getGame(), index), "Error: you're not the host, you can't change whose turn it is");
		}
		
		if (index < 0
				|| index >= gameHostedByPlayer.getTeamList().size() ) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to make non-existent teamIndex [%,d] the next team", player, session, gameHostedByPlayer, index), "Error: team does not exist");
		}

		gameHostedByPlayer.setTeamIndex(index);
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
			Map<Player, Long>		mapActivePlayersToLastSeenDurations		= new LinkedHashMap<>();
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
	
	@HTTPRequest( requestName = "makePlayerHost", argNames = "playerID" )
	public static void makePlayerHost(Session aSession, Integer aPlayerPublicID) {
		Game game = GameManager.getGameHostedByPlayer(aSession.getPlayer());
		if (game == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], Session [%s], public ID of other player [%,d], Non-host tried to give hosting duties to other player", aSession.getPlayer(), aSession, aPlayerPublicID ), "Error: you can't make somebody the host when you're not the host yourself" );
		}
		if (aPlayerPublicID == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], Session [%s], public ID of other player was null", aSession.getPlayer(), aSession ), null );
		}
		Player player = PlayerManager.getPlayer(aPlayerPublicID);
		if (player == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], Session [%s], Unknown public ID of other player [%,d]", aSession.getPlayer(), aSession, aPlayerPublicID ), null );
		}
		
		if (player.getGame() != game) {
			throw new IllegalServerRequestException(String.format("Player [%s], Session [%s], other player [%s], is part of game [%s], can't be made host of game [%s]", aSession.getPlayer(), aSession, player, player.getGame(), game ), "Error: a player must be part of a game before they can be made the host" );
		}
		
		GameManager.setPlayerAsHostOfGame(game, player);
	}
	
	@HTTPRequest( requestName = "revokeSubmittedNames" )
	public static void revokeSubmittedNames(Session aSession) {
		Player player = aSession.getPlayer();
		Game game = player.getGame();
		
		if (game == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], tried to revoke submitted names when not part of any game", player, aSession), null);
		}
		
		if (game.getStatus() != GameStatus.WAITING_FOR_NAMES) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], game [%s], tried to revoke submitted names when in state [%s]", player, aSession, game, game.getStatus()), "Error: you can't change your names at this time");
		}
		
		game.removeNameList(player);
	}
	
	@HTTPRequest( requestName = "setTesting" )
	public static void setTesting(Session aSession) {
		aSession.setTestSession(true);
	}
	
	@HTTPRequest( requestName = "setDisplayCelebrityNames", argNames = "displayNames" )
	public static void setDisplayCelebrityNames(Session aSession, boolean aDisplayCelebrityNames) {
		Player player = aSession.getPlayer();
		Game game = player.getGame();
		
		if (game == null) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], tried to set the display of celebrity names when not part of any game", player, aSession), null);
		}
		
		if (game.getHost() != player) {
			throw new IllegalServerRequestException(String.format("Player [%s], session [%s], tried to set the display of celebrity names in game [%s] when the host is [%s]", player, aSession, game, game.getHost()), "Error: you are not the host, you can't change the game settings");
		}
		
		game.setDisplayCelebNames(aDisplayCelebrityNames);
	}
}
