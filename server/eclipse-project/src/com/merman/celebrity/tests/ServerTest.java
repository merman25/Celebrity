package com.merman.celebrity.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.game.GameStatus;
import com.merman.celebrity.game.Player;
import com.merman.celebrity.game.Team;
import com.merman.celebrity.server.CelebrityMain;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.WebsocketHandler;
import com.merman.celebrity.server.handlers.AnnotatedHandlers;
import com.merman.celebrity.util.SharedRandom;

public class ServerTest {
	
	@Test
	public void testStandardGamesWithNoProblems() {
		SharedRandom.setSeed(314159);
		
		int limit = 1000;
		for( int gameIndex=0; gameIndex<limit; gameIndex++) {
			System.out.format("Testing game %,d of %,d\n", gameIndex+1, limit);
			playARandomGame();
		}
	}

	private void playARandomGame() {
		int numPlayers = 1 + SharedRandom.getRandom().nextInt(10);
		int numNamesPerPlayer = 1 + SharedRandom.getRandom().nextInt(10);
		int numRounds = 1 + SharedRandom.getRandom().nextInt(5);
		int roundDurationInSec = 60;
		int totalNumberOfNames = numNamesPerPlayer * numPlayers;
		int numTeams = numPlayers > 1 ? 2 : 1;

		System.out.format("Playing game with %,d players, %,d names per player, %,d rounds, %,d teams\n", numPlayers, numNamesPerPlayer, numRounds, numTeams);
		Game game = initGame(numPlayers, numNamesPerPlayer, numRounds, roundDurationInSec, numTeams, true);
		Session hostSession = SessionManager.getSession(game.getHost().getSessionID());
		Assert.assertNotNull(hostSession);

		List<List<Player>>		playerListPerTeam		= new ArrayList<>();
		for ( Team team : game.getTeamList() ) {
			List<Player>	playerList		= new ArrayList<>();
			playerList.addAll(team.getPlayerList());
			playerListPerTeam.add(playerList);
		}


		int expectedTeamIndex = -1;
		Map<Integer, Integer>		mapTeamIndicesToExpectedPlayerIndices		= new HashMap<>();

		for (int roundIndex = 0; roundIndex < numRounds; roundIndex++) {
			for (int numNamesAchieved = 0; numNamesAchieved < totalNumberOfNames; ) {
				Player currentPlayer = game.getCurrentPlayer();
				Assert.assertNotNull(currentPlayer);

				expectedTeamIndex++;
				expectedTeamIndex %= numTeams;
				int expectedPlayerIndex = mapTeamIndicesToExpectedPlayerIndices.computeIfAbsent(expectedTeamIndex, i->-1);
				expectedPlayerIndex++;
				expectedPlayerIndex %= playerListPerTeam.get(expectedTeamIndex).size();
				mapTeamIndicesToExpectedPlayerIndices.put(expectedTeamIndex, expectedPlayerIndex);

				Assert.assertEquals("Expected player is playing", playerListPerTeam.get(expectedTeamIndex).get(expectedPlayerIndex), currentPlayer);

				Session playerSession = SessionManager.getSession(currentPlayer.getSessionID());
				AnnotatedHandlers.startTurn(playerSession);

				Assert.assertEquals(GameStatus.PLAYING_A_TURN, game.getStatus());

				int namesRemaining					= totalNumberOfNames - numNamesAchieved;
				int maxNumberOfNamesToAchieve 		= SharedRandom.getRandom().nextInt(11);
				int numNamesToAchieveOnThisTurn 	= Math.min( namesRemaining, maxNumberOfNamesToAchieve );
				int numNamesAchievedAtEndOfTurn		= numNamesAchieved + numNamesToAchieveOnThisTurn;

				for (int nameIndex = numNamesAchieved; nameIndex < numNamesAchievedAtEndOfTurn; nameIndex++) {
					Assert.assertEquals(nameIndex, game.getCurrentNameIndex() );
					AnnotatedHandlers.setCurrentNameIndex(playerSession, nameIndex + 1);
				}
				numNamesAchieved = numNamesAchievedAtEndOfTurn;
				if ( game.getStatus() == GameStatus.PLAYING_A_TURN ) {
					AnnotatedHandlers.endTurn(playerSession);
					Assert.assertEquals(GameStatus.READY_TO_START_NEXT_TURN, game.getStatus());
				}
				else if ( numNamesAchieved < totalNumberOfNames ) {
					Assert.assertEquals(GameStatus.READY_TO_START_NEXT_TURN, game.getStatus());
				}
				else if ( roundIndex < numRounds - 1 ) {
					Assert.assertEquals(GameStatus.READY_TO_START_NEXT_ROUND, game.getStatus());
					AnnotatedHandlers.startNextRound(hostSession);
					Assert.assertEquals(GameStatus.READY_TO_START_NEXT_TURN, game.getStatus());
				}
			}
		}

		Assert.assertEquals(GameStatus.ENDED, game.getStatus());
	}

	private Game initGame(int aNumPlayers, int aNumNamesPerPlayer, int aNumRounds, int aRoundDurationInSec, int aNumTeams, boolean aAllocateTeamsAtRandom) {
		GameManager.createFiles = false;
		CelebrityMain.setSysOutLogging(true);
		int numClientPlayers = aNumPlayers - 1;
		
		// Host and other players create their sessions
		Session hostSession = SessionManager.createSession();
		AnnotatedHandlers.setUsername(hostSession, "player0");
		
		Assert.assertEquals("player0", hostSession.getPlayer().getName());
		
		List<Session>	playerSessions		= new ArrayList<>();
		for (int i = 0; i < numClientPlayers; i++) {
			Session playerSession = SessionManager.createSession();
			String username = "player" + (i+1);
			AnnotatedHandlers.setUsername(playerSession, username);
			Assert.assertEquals(username, playerSession.getPlayer().getName());
			
			playerSessions.add(playerSession);
		}
		
		// Host starts a new game
		AnnotatedHandlers.hostNewGame(hostSession);
		Game game = GameManager.getGameHostedByPlayer(hostSession.getPlayer());
		Assert.assertNotNull(game);
		Assert.assertNotNull(game.getID());
		Assert.assertEquals(hostSession.getPlayer(), game.getHost());
		Assert.assertEquals(game, hostSession.getPlayer().getGame());
		Assert.assertTrue("Game's player list should contain player", game.getPlayersWithoutTeams().contains(hostSession.getPlayer()));
		
		// Players join game
		for ( Session playerSession : playerSessions ) {
			AnnotatedHandlers.askGameIDResponse(playerSession, game.getID());
			Assert.assertEquals(playerSession.getPlayer().getGame(), game);
			Assert.assertTrue("Game's player list should contain player", game.getPlayersWithoutTeams().contains(playerSession.getPlayer()));
		}
		
		// Host sets game parameters and allocates teams
		AnnotatedHandlers.setGameParams(hostSession, aNumRounds, aRoundDurationInSec, aNumNamesPerPlayer);
		Assert.assertEquals(aNumRounds, game.getNumRounds());
		Assert.assertEquals(60, game.getRoundDurationInSec());
		Assert.assertEquals(aNumNamesPerPlayer, game.getNumNamesPerPlayer());
		
		if ( aAllocateTeamsAtRandom ) {
			AnnotatedHandlers.allocateTeams(hostSession);
		}
		else {
			game.allocateTeams(false);
		}
		Assert.assertTrue("List of teamless players should be empty", game.getPlayersWithoutTeams().isEmpty());
		List<Team> teamList = game.getTeamList();
		Assert.assertEquals(aNumTeams, teamList.size());
		
		int expectedTeamSize = aNumPlayers / aNumTeams;
		Set<Player> playersInTeams = new HashSet<>();
		for ( Team team : teamList ) {
			int teamSize = team.getPlayerList().size();
			Assert.assertTrue( String.format( "Team size [%d] should be %d or %d", teamSize, expectedTeamSize, expectedTeamSize+1), teamSize == expectedTeamSize || teamSize == ( expectedTeamSize + 1) );
			for (Player player : team.getPlayerList()) {
				Assert.assertTrue( "No player should be in 2 teams", playersInTeams.add(player) );
			}
		}
		Assert.assertEquals("All players should be in teams", aNumPlayers, playersInTeams.size());
		Assert.assertEquals(aNumPlayers, game.getAllReferencedPlayers().size());

		// Host requests names from all players
		Assert.assertEquals( "Initial state", GameStatus.WAITING_FOR_PLAYERS, game.getStatus() );
		AnnotatedHandlers.sendNameRequest(hostSession);
		Assert.assertEquals(GameStatus.WAITING_FOR_NAMES, game.getStatus());
		
		int expectedNumberOfPlayersToWaitFor = aNumPlayers;
		Assert.assertEquals(expectedNumberOfPlayersToWaitFor, game.getNumPlayersToWaitFor());
		
		int incrementingValue = 0;
		List<Session>	allSessions		= new ArrayList<>();
		allSessions.add(hostSession);
		allSessions.addAll(playerSessions);

		// Everyone provides names
		for ( Session playerSession : allSessions ) {
			List<String>	nameList		= new ArrayList<>();
			for (int i = 0; i < aNumNamesPerPlayer; i++) {
				nameList.add(String.valueOf(incrementingValue++));
			}
			AnnotatedHandlers.provideNames(playerSession, nameList);
			expectedNumberOfPlayersToWaitFor--;
			Assert.assertEquals(expectedNumberOfPlayersToWaitFor, game.getNumPlayersToWaitFor());
		}
		
		Assert.assertEquals(0, game.getNumPlayersToWaitFor());
		
		// Host starts game
		AnnotatedHandlers.startGame(hostSession);
		
		Assert.assertEquals(GameStatus.READY_TO_START_NEXT_TURN, game.getStatus());
		Assert.assertEquals("Should have correct number of total names", aNumNamesPerPlayer * aNumPlayers, game.getShuffledNameList().size() );
		
		return game;
	}
	
	@Test
	public void testRemovalOfPlayersByHost() {
		Game game = initGame(6, 1, 1, 60, 2, true);
		List<Session>	allSessions		= game.getTeamList().stream()
											.map(t -> t.getPlayerList().stream() )
											.reduce(Stream::concat).get()
											.sorted(Comparator.comparing(p -> p != game.getHost()))
											.map(p -> SessionManager.getSession(p.getSessionID()))
											.collect(Collectors.toList());

		Session hostSession = allSessions.get(0);
		Assert.assertEquals(game.getHost(), hostSession.getPlayer());
		Assert.assertEquals(6, game.getTeamList().get(0).getPlayerList().size()
								+ game.getTeamList().get(1).getPlayerList().size() );
		
		Assert.assertEquals(6, game.getAllReferencedPlayers().size());
		for (int sessionIndex = 1; sessionIndex < allSessions.size(); sessionIndex++) {
			Player player = allSessions.get(sessionIndex).getPlayer();
			AnnotatedHandlers.removePlayerFromGame(hostSession, player.getPublicUniqueID());

			Assert.assertEquals(6 - sessionIndex, game.getTeamList().get(0).getPlayerList().size()
													+ game.getTeamList().get(1).getPlayerList().size() );
			Assert.assertNotEquals(player, game.getCurrentPlayer());
		}
		Assert.assertEquals(1, game.getAllReferencedPlayers().size());
	}
	
	@Test
	public void testRemovalAndReinsertionOfPlayers() {
		Game game = initGame(6, 6, 3, 60, 2, false);
		List<Session>	allSessions		= game.getTeamList().stream()
											.map(t -> t.getPlayerList().stream() )
											.reduce(Stream::concat).get()
											.sorted(Comparator.comparing(Player::getName))
											.map(p -> SessionManager.getSession(p.getSessionID()))
											.collect(Collectors.toList());

		Session hostSession = allSessions.get(0);
		Assert.assertEquals(game.getHost(), hostSession.getPlayer());
		
		Assert.assertEquals("[player0, player1, player2]", game.getTeamList().get(0).getPlayerList().toString() );
		Assert.assertEquals("[player3, player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );
		Assert.assertEquals(36, game.getShuffledNameList().size());
		
		Assert.assertEquals(game.getCurrentPlayer(), allSessions.get(0).getPlayer());
		
		// play a few turns
		Assert.assertEquals("player0", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 4) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player3", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 8) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player1", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 12) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		
		// It's now player4's turn
		Assert.assertEquals("player4", game.getCurrentPlayer().getName() );
		// player4 lost his connection, remove him.
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(4).getPlayer().getPublicUniqueID());
		Assert.assertFalse("player4 should no longer be referenced", game.getAllReferencedPlayers().contains(allSessions.get(4).getPlayer()));
		Assert.assertEquals(5, game.getAllReferencedPlayers().size());
		Assert.assertEquals("[player3, player5]", game.getTeamList().get(1).getPlayerList().toString() );
		Assert.assertEquals(36, game.getShuffledNameList().size());

		// play goes to the next player in the same team
		Assert.assertEquals("player5", game.getCurrentPlayer().getName() );
		
		// player 4 creates a new session, and re-joins the game
		allSessions.set(4, SessionManager.createSession());
		AnnotatedHandlers.setUsername(allSessions.get(4), "player4");
		AnnotatedHandlers.askGameIDResponse(allSessions.get(4), game.getID());
		
		// host puts him back in team 1
		AnnotatedHandlers.putPlayerInTeam(hostSession, allSessions.get(4).getPlayer().getPublicUniqueID(), 1);
		Assert.assertEquals("[player3, player5, player4]", game.getTeamList().get(1).getPlayerList().toString() );
		
		// and moves him to the right place
		AnnotatedHandlers.movePlayerEarlier(hostSession, allSessions.get(4).getPlayer().getPublicUniqueID());
		Assert.assertEquals("[player3, player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );
		Assert.assertEquals(36, game.getShuffledNameList().size());

		// play a few more turns
		Assert.assertEquals("player4", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 15) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player2", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 21) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player5", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player0", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 25) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		
		// player 3 lost connection! remove him
		Assert.assertEquals("player3", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID());
		Assert.assertFalse("player3 should no longer be referenced", game.getAllReferencedPlayers().contains(allSessions.get(3).getPlayer()));
		Assert.assertEquals(5, game.getAllReferencedPlayers().size());
		Assert.assertEquals("[player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );
		Assert.assertEquals(36, game.getShuffledNameList().size());

		// create a new session, add him back in
		allSessions.set(3, SessionManager.createSession());
		AnnotatedHandlers.setUsername(allSessions.get(3), "player3");
		AnnotatedHandlers.askGameIDResponse(allSessions.get(3), game.getID());
		AnnotatedHandlers.putPlayerInTeam(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID(), 1);
		Assert.assertEquals("[player4, player5, player3]", game.getTeamList().get(1).getPlayerList().toString() );
		
		// and moves him to the right place
		AnnotatedHandlers.movePlayerEarlier(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.movePlayerEarlier(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID());
		Assert.assertEquals("[player3, player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );
		Assert.assertEquals(36, game.getShuffledNameList().size());

		// play to the end of the round
		Assert.assertEquals("player3", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 31) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player1", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() != 0) {
			// currentNameIndex gets reset to 0 after reaching end of list so 'index != 0' is the condition to check to make sure we get every name
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		
		Assert.assertEquals(GameStatus.READY_TO_START_NEXT_ROUND, game.getStatus());
		Assert.assertEquals("player4", game.getCurrentPlayer().getName());
		
		// Check scores for the round are consistent
		Assert.assertEquals(36, game.getMapTeamsToAchievedNames().get(game.getTeamList().get(0)).size()
								+ game.getMapTeamsToAchievedNames().get(game.getTeamList().get(1)).size() );
		
		
		// players 1 and 5 both lose connection
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(1).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(5).getPlayer().getPublicUniqueID());
		Assert.assertEquals(4, game.getAllReferencedPlayers().size());
		Assert.assertEquals("[player0, player2]", game.getTeamList().get(0).getPlayerList().toString() );
		Assert.assertEquals("[player3, player4]", game.getTeamList().get(1).getPlayerList().toString() );
		Assert.assertEquals(36, game.getShuffledNameList().size());
		Assert.assertEquals("player4", game.getCurrentPlayer().getName());
		
		// not to worry, put them back in
		allSessions.set(1, SessionManager.createSession());
		allSessions.set(5, SessionManager.createSession());
		AnnotatedHandlers.setUsername(allSessions.get(1), "player1");
		AnnotatedHandlers.setUsername(allSessions.get(5), "player5");
		AnnotatedHandlers.askGameIDResponse(allSessions.get(1), game.getID());
		AnnotatedHandlers.askGameIDResponse(allSessions.get(5), game.getID());
		AnnotatedHandlers.putPlayerInTeam(hostSession, allSessions.get(1).getPlayer().getPublicUniqueID(), 0);
		AnnotatedHandlers.putPlayerInTeam(hostSession, allSessions.get(5).getPlayer().getPublicUniqueID(), 1);
		AnnotatedHandlers.movePlayerEarlier(hostSession, allSessions.get(1).getPlayer().getPublicUniqueID());
		Assert.assertEquals("[player0, player1, player2]", game.getTeamList().get(0).getPlayerList().toString() );
		Assert.assertEquals("[player3, player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );
		Assert.assertEquals(36, game.getShuffledNameList().size());
		
		// Start the next round and play a turn
		AnnotatedHandlers.startNextRound(hostSession);
		Assert.assertEquals("player4", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 8) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		Assert.assertEquals("player2", game.getCurrentPlayer().getName() );
		
		// Disaster! everyone but the host loses connection!
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(1).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(2).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(4).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(5).getPlayer().getPublicUniqueID());
		
		// They can all be put back, but the indices for who's next will now be messed up
		for (int i = 1; i <= 5; i++) {
			allSessions.set(i, SessionManager.createSession());
			AnnotatedHandlers.setUsername(allSessions.get(i), "player" + i);
			AnnotatedHandlers.askGameIDResponse(allSessions.get(i), game.getID());
			AnnotatedHandlers.putPlayerInTeam(hostSession, allSessions.get(i).getPlayer().getPublicUniqueID(), i / 3);
		}
		Assert.assertEquals("[player0, player1, player2]", game.getTeamList().get(0).getPlayerList().toString() );
		Assert.assertEquals("[player3, player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );
		Assert.assertEquals(36, game.getShuffledNameList().size());
		
		// As the only one who was left, player0 is the current player
		Assert.assertEquals("player0", game.getCurrentPlayer().getName() );
		
		// Let's return play to player2
		AnnotatedHandlers.makePlayerNextInTeam(hostSession, allSessions.get(2).getPlayer().getPublicUniqueID());
		Assert.assertEquals("player2", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 11) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		
		/* Team1's indices also got messed up. In fact they're still at index 1, didn't go all the way to zero
		 * because it's not Team1's turn
		 */
		Assert.assertEquals("player4", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.makePlayerNextInTeam(hostSession, allSessions.get(5).getPlayer().getPublicUniqueID());

		Assert.assertEquals("player5", game.getCurrentPlayer().getName() );
		
		// If Team1 now loses all its players and re-gains them, player3 will be the next player
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(4).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(5).getPlayer().getPublicUniqueID());
		for (int i = 3; i <= 5; i++) {
			allSessions.set(i, SessionManager.createSession());
			AnnotatedHandlers.setUsername(allSessions.get(i), "player" + i);
			AnnotatedHandlers.askGameIDResponse(allSessions.get(i), game.getID());
			AnnotatedHandlers.putPlayerInTeam(hostSession, allSessions.get(i).getPlayer().getPublicUniqueID(), 1);
		}
		Assert.assertEquals("[player3, player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );
		Assert.assertEquals(36, game.getShuffledNameList().size());
		Assert.assertEquals("player3", game.getCurrentPlayer().getName() );

		// Put player 5 back and play a turn
		AnnotatedHandlers.makePlayerNextInTeam(hostSession, allSessions.get(5).getPlayer().getPublicUniqueID());
		Assert.assertEquals("player5", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 17) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		
		// Let's have player 3 lose connection while player 0's in the middle of a turn
		Assert.assertEquals("player0", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 21) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		// Then player 4 would go next
		Assert.assertEquals("[player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );
		Assert.assertEquals("player4", game.getCurrentPlayer().getName() );

		// player 3 re-joins
		allSessions.set(3, SessionManager.createSession());
		AnnotatedHandlers.setUsername(allSessions.get(3), "player3");
		AnnotatedHandlers.askGameIDResponse(allSessions.get(3), game.getID());
		AnnotatedHandlers.putPlayerInTeam(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID(), 1);
		AnnotatedHandlers.movePlayerEarlier(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.movePlayerEarlier(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.makePlayerNextInTeam(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID());

		// All good again, play some more turns
		Assert.assertEquals("[player3, player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );

		Assert.assertEquals("player3", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 25) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		// Now let's have all of Team1 lose connection while Team0 are playing
		Assert.assertEquals("player1", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(4).getPlayer().getPublicUniqueID());
		while (game.getCurrentNameIndex() < 28) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(5).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		
		// It now wants Team1 to go, but there's no player
		Assert.assertEquals(3, game.getAllReferencedPlayers().size());
		Assert.assertNull( game.getCurrentPlayer() );
		
		// Put Team1 back
		for (int i = 3; i <= 5; i++) {
			allSessions.set(i, SessionManager.createSession());
			AnnotatedHandlers.setUsername(allSessions.get(i), "player" + i);
			AnnotatedHandlers.askGameIDResponse(allSessions.get(i), game.getID());
			AnnotatedHandlers.putPlayerInTeam(hostSession, allSessions.get(i).getPlayer().getPublicUniqueID(), 1);
		}
		Assert.assertEquals("[player3, player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );
		Assert.assertEquals(36, game.getShuffledNameList().size());
		
		// We're at the beginning of Team1's list, put it back to player4 as it should be and play some more turns
		Assert.assertEquals("player3", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.makePlayerNextInTeam(hostSession, allSessions.get(4).getPlayer().getPublicUniqueID());
	
		Assert.assertEquals("player4", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 30) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player2", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 32) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		
		// Player 1 drops out and stays out for a while (not his turn anyway).
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(1).getPlayer().getPublicUniqueID());
		Assert.assertEquals("player5", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 34) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		
		// player0 ends the round, player1 still missing
		Assert.assertEquals(GameStatus.READY_TO_START_NEXT_TURN, game.getStatus());
		Assert.assertEquals("player0", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() != 0) {
			// currentNameIndex gets reset to 0 after reaching end of list so 'index != 0' is the condition to check to make sure we get every name
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		
		Assert.assertEquals(GameStatus.READY_TO_START_NEXT_ROUND, game.getStatus());
		Assert.assertEquals("player3", game.getCurrentPlayer().getName() );
		Assert.assertEquals(5, game.getAllReferencedPlayers().size());
		Assert.assertEquals(36, game.getShuffledNameList().size());
		
		// Check scores for the round are consistent
		Assert.assertEquals(36, game.getMapTeamsToAchievedNames().get(game.getTeamList().get(0)).size()
								+ game.getMapTeamsToAchievedNames().get(game.getTeamList().get(1)).size() );

		// player3 plays a turn of the next round
		AnnotatedHandlers.startNextRound(hostSession);
		Assert.assertEquals("player3", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 3) {
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		
		// put back player1 where he should be
		allSessions.set(1, SessionManager.createSession());
		AnnotatedHandlers.setUsername(allSessions.get(1), "player1");
		AnnotatedHandlers.askGameIDResponse(allSessions.get(1), game.getID());
		AnnotatedHandlers.putPlayerInTeam(hostSession, allSessions.get(1).getPlayer().getPublicUniqueID(), 0);
		AnnotatedHandlers.movePlayerEarlier(hostSession, allSessions.get(1).getPlayer().getPublicUniqueID());
		AnnotatedHandlers.makePlayerNextInTeam(hostSession, allSessions.get(1).getPlayer().getPublicUniqueID());
		Assert.assertEquals("[player0, player1, player2]", game.getTeamList().get(0).getPlayerList().toString() );

		Assert.assertEquals("player1", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		while (game.getCurrentNameIndex() < 36) {
			// currentNameIndex does not get reset to 0 at end of game, so 'index < 36' is the condition to check rather than 'index != 0'.
			AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), game.getCurrentNameIndex() + 1 );
		}
		
		// Check scores for the round are consistent
		Assert.assertEquals(36, game.getMapTeamsToAchievedNames().get(game.getTeamList().get(0)).size()
								+ game.getMapTeamsToAchievedNames().get(game.getTeamList().get(1)).size() );

		// check total scores are consistent
		Map<Team, List<Integer>> mapTeamsToScores = game.getMapTeamsToScores();
		Assert.assertEquals(GameStatus.ENDED, game.getStatus());
		IntStream.of( 0, 1, 2 )
			.forEach( i -> {
				int totalScore = mapTeamsToScores.get(game.getTeamList().get(0)).get(i)
									+ mapTeamsToScores.get(game.getTeamList().get(1)).get(i);
				Assert.assertEquals(36, totalScore);
			});
	}
	
	@Test
	public void testToLengthArray() {
		Assert.assertEquals("[10]", Arrays.toString( WebsocketHandler.toLengthArray(10) ));
		Assert.assertEquals("[126, 3, -42]", Arrays.toString( WebsocketHandler.toLengthArray(982) ));
		Assert.assertEquals("[127, 0, 0, 0, 0, 0, 1, 90, 23]", Arrays.toString( WebsocketHandler.toLengthArray(88599) ));
	}
	
	@Test
	public void testSimultaneousGames() {
		SharedRandom.setSeed(271828);

		int numGames = 10;
		ExecutorService threadPool = Executors.newFixedThreadPool(numGames);

		List<Callable<Void>>		callableGameList		= new ArrayList<>();
		for (int gameIndex = 0; gameIndex < numGames; gameIndex++) {
			boolean add = callableGameList.add( new Callable<Void>() {

				@Override
				public Void call() throws Exception {
					playARandomGame();
					return null;
				}
			} );
		}
		try {
			threadPool.invokeAll(callableGameList);
		}
		catch ( InterruptedException e ) {
			e.printStackTrace();
			Assert.assertFalse("Threads interrupted", true);
		}
	}
}
