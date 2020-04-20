package com.merman.celebrity.tests;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.game.GameState;
import com.merman.celebrity.game.Player;
import com.merman.celebrity.game.Team;
import com.merman.celebrity.server.Session;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.handlers.AnnotatedHandlers;

public class ServerTest {
	@Test
	public void testStandardGamesWithNoProblems() {
		Random random = new Random(31415);
		
		int limit = 5;
		for( int gameIndex=0; gameIndex<limit; gameIndex++) {
			int numPlayers = 1 + random.nextInt(10);
			int numNamesPerPlayer = 1 + random.nextInt(10);
			int numRounds = 1 + random.nextInt(5);
			int roundDurationInSec = 60;
			int totalNumberOfNames = numNamesPerPlayer * numPlayers;
			int numTeams = 2;

			System.out.format("Testing game %,d of %,d: %,d players, %,d names per player, %,d rounds, %,d teams\n", gameIndex+1, limit, numPlayers, numNamesPerPlayer, numRounds, numTeams);
			Game game = initGame(numPlayers, numNamesPerPlayer, numRounds, roundDurationInSec, numTeams, true);
			Session hostSession = SessionManager.getSession(game.getHost().getSessionID());
			Assert.assertNotNull(hostSession);

			List<List<Player>>		playerListPerTeam		= new ArrayList<>();
			for ( Team team : game.getTeamList() ) {
				List<Player>	playerList		= new ArrayList<>();
				playerList.addAll(team.getPlayerList());
				
				/* If a game only has one player (only used for testing), then we effectively only have one team, and shouldn't
				 * use the hard-coded value of 2 teams.
				 */
				if ( ! playerList.isEmpty() ) {
					playerListPerTeam.add(playerList);
				}
				else {
					numTeams--;
				}
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

					Assert.assertEquals(GameState.PLAYING_A_TURN, game.getState());

					int namesRemaining					= totalNumberOfNames - numNamesAchieved;
					int maxNumberOfNamesToAchieve 		= random.nextInt(11);
					int numNamesToAchieveOnThisTurn 	= Math.min( namesRemaining, maxNumberOfNamesToAchieve );
					int numNamesAchievedAtEndOfTurn		= numNamesAchieved + numNamesToAchieveOnThisTurn;

					for (int nameIndex = numNamesAchieved; nameIndex < numNamesAchievedAtEndOfTurn; nameIndex++) {
						Assert.assertEquals(nameIndex, game.getCurrentNameIndex() );
						AnnotatedHandlers.setCurrentNameIndex(playerSession, nameIndex + 1);
					}
					numNamesAchieved = numNamesAchievedAtEndOfTurn;
					if ( game.getState() == GameState.PLAYING_A_TURN ) {
						AnnotatedHandlers.endTurn(playerSession);
						Assert.assertEquals(GameState.READY_TO_START_NEXT_TURN, game.getState());
					}
					else if ( numNamesAchieved < totalNumberOfNames ) {
						Assert.assertEquals(GameState.READY_TO_START_NEXT_TURN, game.getState());
					}
					else if ( roundIndex < numRounds - 1 ) {
						Assert.assertEquals(GameState.READY_TO_START_NEXT_ROUND, game.getState());
						AnnotatedHandlers.startNextRound(hostSession);
						Assert.assertEquals(GameState.READY_TO_START_NEXT_TURN, game.getState());
					}
				}
			}

			Assert.assertEquals(GameState.ENDED, game.getState());
		}
	}

	private Game initGame(int aNumPlayers, int aNumNamesPerPlayer, int aNumRounds, int aRoundDurationInSec, int aNumTeams, boolean aAllocateTeamsAtRandom) {
		int numClientPlayers = aNumPlayers - 1;
		
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
		
		AnnotatedHandlers.hostNewGame(hostSession);
		Game game = GameManager.getGameHostedByPlayer(hostSession.getPlayer());
		Assert.assertNotNull(game);
		Assert.assertNotNull(game.getID());
		Assert.assertEquals(hostSession.getPlayer(), game.getHost());
		Assert.assertEquals(game, hostSession.getPlayer().getGame());
		Assert.assertTrue("Game's player list should contain player", game.getPlayersWithoutTeams().contains(hostSession.getPlayer()));
		
		for ( Session playerSession : playerSessions ) {
			AnnotatedHandlers.setGameID(playerSession, game.getID());
			Assert.assertEquals(playerSession.getPlayer().getGame(), game);
			Assert.assertTrue("Game's player list should contain player", game.getPlayersWithoutTeams().contains(playerSession.getPlayer()));
			
			Map<String, String> gameIDResponse = AnnotatedHandlers.askGameIDResponse(playerSession);
			Assert.assertEquals("OK", gameIDResponse.get("GameResponse"));
			Assert.assertEquals(game.getID(), gameIDResponse.get("GameID"));
		}
		
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

		Assert.assertEquals( "Initial state", GameState.WAITING_FOR_PLAYERS, game.getState() );
		AnnotatedHandlers.sendNameRequest(hostSession);
		Assert.assertEquals(GameState.WAITING_FOR_NAMES, game.getState());
		
		int expectedNumberOfPlayersToWaitFor = aNumPlayers;
		Assert.assertEquals(expectedNumberOfPlayersToWaitFor, game.getNumPlayersToWaitFor());
		
		int incrementingValue = 0;
		List<Session>	allSessions		= new ArrayList<>();
		allSessions.add(hostSession);
		allSessions.addAll(playerSessions);

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
		
		AnnotatedHandlers.startGame(hostSession);
		
		Assert.assertEquals(GameState.READY_TO_START_NEXT_TURN, game.getState());
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
		
		Assert.assertEquals(game.getCurrentPlayer(), allSessions.get(0).getPlayer());
		
		// play a few turns
		Assert.assertEquals("player0", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), 4	);
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player3", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), 8	);
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player1", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), 12	);
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		
		// It's now player4's turn
		Assert.assertEquals("player4", game.getCurrentPlayer().getName() );
		// player4 lost his connection, remove him.
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(4).getPlayer().getPublicUniqueID());
		Assert.assertFalse("player4 should no longer be referenced", game.getAllReferencedPlayers().contains(allSessions.get(4).getPlayer()));
		Assert.assertEquals(5, game.getAllReferencedPlayers().size());
		Assert.assertEquals("[player3, player5]", game.getTeamList().get(1).getPlayerList().toString() );

		// play goes to the next player in the same team
		Assert.assertEquals("player5", game.getCurrentPlayer().getName() );
		
		// player 4 creates a new session, and re-joins the game
		allSessions.set(4, SessionManager.createSession());
		AnnotatedHandlers.setUsername(allSessions.get(4), "player4");
		AnnotatedHandlers.setGameID(allSessions.get(4), game.getID());
		
		// host puts him back in team 1
		AnnotatedHandlers.putPlayerInTeam(hostSession, allSessions.get(4).getPlayer().getPublicUniqueID(), 1);
		Assert.assertEquals("[player3, player5, player4]", game.getTeamList().get(1).getPlayerList().toString() );
		
		// and moves him to the right place
		AnnotatedHandlers.movePlayerEarlier(hostSession, allSessions.get(4).getPlayer().getPublicUniqueID());
		Assert.assertEquals("[player3, player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );
		
		// play a few more turns
		Assert.assertEquals("player4", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), 15	);
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player2", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), 21	);
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player5", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);

		Assert.assertEquals("player0", game.getCurrentPlayer().getName() );
		AnnotatedHandlers.startTurn(			SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		AnnotatedHandlers.setCurrentNameIndex(	SessionManager.getSession(game.getCurrentPlayer().getSessionID()), 25	);
		AnnotatedHandlers.endTurn(				SessionManager.getSession(game.getCurrentPlayer().getSessionID())		);
		
		// player 3 lost connection! remove him
		AnnotatedHandlers.removePlayerFromGame(hostSession, allSessions.get(3).getPlayer().getPublicUniqueID());
		Assert.assertFalse("player3 should no longer be referenced", game.getAllReferencedPlayers().contains(allSessions.get(3).getPlayer()));
		Assert.assertEquals(5, game.getAllReferencedPlayers().size());
		Assert.assertEquals("[player4, player5]", game.getTeamList().get(1).getPlayerList().toString() );
	}
}
