package com.merman.celebrity.tests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
		
		int limit = 1000;
		for( int gameIndex=0; gameIndex<limit; gameIndex++) {
			int numPlayers = 1 + random.nextInt(10);
			int numNamesPerPlayer = 1 + random.nextInt(10);
			int numRounds = 1 + random.nextInt(5);
			int roundDurationInSec = 60;
			int totalNumberOfNames = numNamesPerPlayer * numPlayers;
			int numTeams = 2;

			System.out.format("Testing game %,d of %,d: %,d players, %,d names per player, %,d rounds, %,d teams\n", gameIndex+1, limit, numPlayers, numNamesPerPlayer, numRounds, numTeams);
			Game game = initGame(numPlayers, numNamesPerPlayer, numRounds, roundDurationInSec, numTeams);
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
					int numNamesToAchieveOnThisTurn 	= Math.min( namesRemaining, random.nextInt(11) );
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

	private Game initGame(int numPlayers, int numNamesPerPlayer, int numRounds, int roundDurationInSec, int numTeams) {
		int numClientPlayers = numPlayers - 1;
		
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
		
		AnnotatedHandlers.setGameParams(hostSession, numRounds, roundDurationInSec, numNamesPerPlayer);
		Assert.assertEquals(numRounds, game.getNumRounds());
		Assert.assertEquals(60, game.getRoundDurationInSec());
		Assert.assertEquals(numNamesPerPlayer, game.getNumNamesPerPlayer());
		
		AnnotatedHandlers.allocateTeams(hostSession);
		Assert.assertTrue("List of teamless players should be empty", game.getPlayersWithoutTeams().isEmpty());
		List<Team> teamList = game.getTeamList();
		Assert.assertEquals(numTeams, teamList.size());
		
		int expectedTeamSize = numPlayers / numTeams;
		Set<Player> playersInTeams = new HashSet<>();
		for ( Team team : teamList ) {
			Assert.assertTrue( "Team size should be correct", team.getPlayerList().size() == expectedTeamSize || team.getPlayerList().size() == ( expectedTeamSize + 1) );
			for (Player player : team.getPlayerList()) {
				Assert.assertTrue( "No player should be in 2 teams", playersInTeams.add(player) );
			}
		}
		Assert.assertEquals("All players should be in teams", numPlayers, playersInTeams.size());
		
		Assert.assertEquals( "Initial state", GameState.WAITING_FOR_PLAYERS, game.getState() );
		AnnotatedHandlers.sendNameRequest(hostSession);
		Assert.assertEquals(GameState.WAITING_FOR_NAMES, game.getState());
		
		int expectedNumberOfPlayersToWaitFor = numPlayers;
		Assert.assertEquals(expectedNumberOfPlayersToWaitFor, game.getNumPlayersToWaitFor());
		
		int incrementingValue = 0;
		List<Session>	allSessions		= new ArrayList<>();
		allSessions.add(hostSession);
		allSessions.addAll(playerSessions);

		for ( Session playerSession : allSessions ) {
			List<String>	nameList		= new ArrayList<>();
			for (int i = 0; i < numNamesPerPlayer; i++) {
				nameList.add(String.valueOf(incrementingValue++));
			}
			AnnotatedHandlers.provideNames(playerSession, nameList);
			expectedNumberOfPlayersToWaitFor--;
			Assert.assertEquals(expectedNumberOfPlayersToWaitFor, game.getNumPlayersToWaitFor());
		}
		
		Assert.assertEquals(0, game.getNumPlayersToWaitFor());
		
		AnnotatedHandlers.startGame(hostSession);
		
		Assert.assertEquals(GameState.READY_TO_START_NEXT_TURN, game.getState());
		Assert.assertEquals("Should have correct number of total names", numNamesPerPlayer * numPlayers, game.getShuffledNameList().size() );
		
		return game;
	}
}
