package com.merman.celebrity.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

public class Game {
	private String                    ID;
	private Player                    host;
	private List<Team>                teamList                    = new ArrayList<>();
	private List<Player>              playersWithoutTeams         = new ArrayList<>();
	private Map<Player, Team>         mapPlayersToTeams           = new HashMap<Player, Team>();
	private GameState                 state                       = GameState.WAITING_FOR_PLAYERS;
	private Map<Player, List<String>> mapPlayersToNameLists       = new HashMap<Player, List<String>>();
	private List<String>              masterNameList			  = new ArrayList<>();
	private List<String>              shuffledNameList            = new ArrayList<>();
	private Turn                      currentTurn;
	private int                       previousNameIndex;
	private int                       currentNameIndex;
	private Map<Team, List<String>>   mapTeamsToAchievedNames     = new HashMap<Team, List<String>>();

	private int                       numRounds;
	private int                       roundDurationInSec;
	private int                       numNamesPerPlayer;

	private int                       nextTeamIndex               = -1;
	private Map<Team, Integer>        mapTeamsToNextPlayerIndices = new HashMap<Team, Integer>();
	private Map<Team, List<Integer>>  mapTeamsToScores            = new HashMap<Team, List<Integer>>();
	private Player                    currentPlayer;

	private int                       roundIndex;
	
	public Game(String aID, Player aHost) {
		ID = aID;
		host = aHost;
	}

	public synchronized GameState getState() {
		return state;
	}

	public synchronized void setState(GameState aState) {
		state = aState;
	}

	public synchronized List<Team> getTeamList() {
		return teamList;
	}

	public synchronized List<Player> getPlayersWithoutTeams() {
		return playersWithoutTeams;
	}

	public synchronized String getID() {
		return ID;
	}

	public synchronized Player getHost() {
		return host;
	}

	public synchronized void addPlayer(Player aPlayer) {
		playersWithoutTeams.add(aPlayer);
		aPlayer.setGame(this);
	}

	public synchronized int getNumRounds() {
		return numRounds;
	}

	public synchronized void setNumRounds(int aNumRounds) {
		numRounds = aNumRounds;
	}

	public synchronized int getRoundDurationInSec() {
		return roundDurationInSec;
	}

	public synchronized void setRoundDurationInSec(int aRoundDurationInSec) {
		roundDurationInSec = aRoundDurationInSec;
	}

	public synchronized int getNumNamesPerPlayer() {
		return numNamesPerPlayer;
	}

	public synchronized void setNumNamesPerPlayer(int aNumNamesPerPlayer) {
		numNamesPerPlayer = aNumNamesPerPlayer;
	}

	public synchronized void allocateTeams(boolean aAllocateTeamsAtRandom) {
		List<Player>		playerList = new ArrayList<>( playersWithoutTeams );
		for ( Team team : teamList ) {
			playerList.addAll(team.getPlayerList());
		}
		teamList.clear();
		mapPlayersToTeams.clear();
		if ( aAllocateTeamsAtRandom ) {
			Collections.shuffle(playerList);
		}
		
		Team team1 = new Team();
		team1.setTeamName("Team 1");
		Team team2 = new Team();
		team2.setTeamName("Team 2");

		int numPlayers = playerList.size();
		int limit = ( numPlayers + 1 ) / 2; // if there's an odd number of players, we'll have more players in team 1 (looks neater)
		
		if ( numPlayers == 1 ) {
			// special case: if only 1 player (used for testing only), put him in 1st team
			team1.addPlayer(playerList.get(0));
			mapPlayersToTeams.put(playerList.get(0), team1);
		}
		else {

			/* Start from top, and work down. This ensures that if there's an odd
			 * number of players, Team 1 always has more than Team 2. Most importantly,
			 * this means that if there's only 1 player (for testing only), he goes in
			 * Team 1. If the only player is in Team 2, the client gets confused and
			 * doesn't give him a turn.
			 */
			for (int i = 0; i < limit; i++) {
				Player player = playerList.get(i);
				team1.addPlayer(player);
				mapPlayersToTeams.put(player, team1);
			}

			for (int i = limit; i < numPlayers; i++) {
				Player player = playerList.get(i);
				team2.addPlayer(player);
				mapPlayersToTeams.put(player, team2);
			}
		}
		
		teamList.add(team1);
		if ( numPlayers > 1 ) {
			teamList.add(team2);
		}
		playersWithoutTeams.clear();
		
		nextTeamIndex = -1;
		incrementPlayer();
	}

	public synchronized void setNameList(Player aPlayer, List<String> aCelebNameList) {
		if ( mapPlayersToTeams.containsKey(aPlayer) ) {
			mapPlayersToNameLists.put(aPlayer, aCelebNameList);
		}
	}
	
	public synchronized void allowNextPlayerToStartNextTurn() {
//		System.out.format("Starting round %,d of %,d\n", roundIndex+1, numRounds);
		setState(GameState.READY_TO_START_NEXT_TURN);
	}
	
	public synchronized Player getCurrentPlayer() {
		return currentPlayer;
	}
	
	private void incrementPlayer() {
		nextTeamIndex++;
		nextTeamIndex %= teamList.size();
		
		Team team = teamList.get(nextTeamIndex);

		Integer nextPlayerIndex = mapTeamsToNextPlayerIndices.computeIfAbsent(team, t -> -1);

		if ( ! team.getPlayerList().isEmpty() ) {
			nextPlayerIndex++;
			nextPlayerIndex %= team.getPlayerList().size();
			mapTeamsToNextPlayerIndices.put(team, nextPlayerIndex);

		}
		updateCurrentPlayerFromIndicesAfterChangeToTeamStructure();
//			Player player = team.getPlayerList().get(nextPlayerIndex);
//			currentPlayer = player;
	}
	
	private Player updateCurrentPlayerFromIndicesAfterChangeToTeamStructure() {
		Player	player = null;
		if ( nextTeamIndex >= 0 ) {
			nextTeamIndex %= teamList.size(); // make sure it's still within bounds, even if a team is removed
			Team team = teamList.get(nextTeamIndex);
			Integer nextPlayerIndex = mapTeamsToNextPlayerIndices.computeIfAbsent(team, t->-1);
			if ( team.getPlayerList().isEmpty() ) {
				nextPlayerIndex = -1;
				mapTeamsToNextPlayerIndices.put(team, nextPlayerIndex);
			}
			else {
				if ( nextPlayerIndex < 0
						|| nextPlayerIndex >= team.getPlayerList().size() ) {
					// make sure it's still within bounds, even if a player is removed
					nextPlayerIndex = 0;
					mapTeamsToNextPlayerIndices.put(team, nextPlayerIndex);
				}
				player = team.getPlayerList().get(nextPlayerIndex);
			}

		}
		
		currentPlayer = player;
		return player;
	}

	public synchronized int getNumPlayersToWaitFor() {
		return mapPlayersToTeams.size() - mapPlayersToNameLists.size();
	}

	public synchronized void shuffleNames() {
		previousNameIndex = 0;
		currentNameIndex = 0;
		shuffledNameList.clear();
		shuffledNameList.addAll(masterNameList);
		Collections.shuffle(shuffledNameList);
	}
	
	public synchronized List<String> getShuffledNameList() {
		return shuffledNameList;
	}
	
	public synchronized void startRound() {
		mapTeamsToAchievedNames.clear();
		allowNextPlayerToStartNextTurn();
	}

	public synchronized void startTurn() {
		currentTurn = new Turn(this, roundDurationInSec);
		currentTurn.start();
		setState(GameState.PLAYING_A_TURN);
	}

	public synchronized Turn getCurrentTurn() {
		return currentTurn;
	}

	public synchronized int getCurrentNameIndex() {
		return currentNameIndex;
	}

	public synchronized int getPreviousNameIndex() {
		return previousNameIndex;
	}

	public synchronized void stopTurn() {
		if ( currentTurn != null ) {
			currentTurn.stop();
			currentTurn = null;
		}
	}

	public synchronized void turnEnded() {
		if ( currentTurn != null ) {
			currentTurn = null;
		}
		keepScore();
		incrementPlayer();
		if ( currentNameIndex >= shuffledNameList.size() ) {
			endRound();
		}
		else {
			setPassOnNameIndex(currentNameIndex);
			setState(GameState.READY_TO_START_NEXT_TURN);
		}
	}

	private void keepScore() {
		Team currentTeam = mapPlayersToTeams.get(getCurrentPlayer());
		List<String>		namesAchieved		= new ArrayList<>(shuffledNameList.subList(previousNameIndex, currentNameIndex));
		mapTeamsToAchievedNames.computeIfAbsent(currentTeam, t -> new ArrayList<>()).addAll(namesAchieved);
		
//		System.out.format("%s got %d names for %s\n", getCurrentPlayer(), namesAchieved.size(), currentTeam.getTeamName());
		
		previousNameIndex = currentNameIndex;
	}

	private void endRound() {
		roundIndex++;
		
		for ( Team team : teamList ) {
			List<String> achievedNames = mapTeamsToAchievedNames.get(team);
			int          teamScore     = achievedNames == null ? 0 : achievedNames.size();
			mapTeamsToScores.computeIfAbsent(team, t -> new ArrayList<>()).add(teamScore);
		}
		
		if ( roundIndex < numRounds ) {
			shuffleNames();
			setState(GameState.READY_TO_START_NEXT_ROUND);
		}
		else {
			setState(GameState.ENDED);
		}
	}

	public synchronized void setCurrentNameIndex(int aCurrentNameIndex) {
		if ( getState() == GameState.PLAYING_A_TURN ) {
			currentNameIndex = aCurrentNameIndex;
			if ( currentNameIndex >= shuffledNameList.size() ) {
				stopTurn();
			}
		}
	}

	public synchronized Map<Team, List<String>> getMapTeamsToAchievedNames() {
		return mapTeamsToAchievedNames;
	}

	public synchronized void setPassOnNameIndex(int aPassNameIndex) {
		if ( aPassNameIndex < shuffledNameList.size() - 1 ) {
			List<String>		achievedNames		= new ArrayList<>(shuffledNameList.subList(0, aPassNameIndex));
			List<String>		remainingNames		= new ArrayList<>(shuffledNameList.subList(aPassNameIndex, shuffledNameList.size()));

			int					newIndex			= new Random().nextInt( remainingNames.size() - 1 ) + 1;
			String				oldName				= remainingNames.get(newIndex);
			remainingNames.set(newIndex, remainingNames.get(0));
			remainingNames.set(0, oldName);

			shuffledNameList.clear();
			shuffledNameList.addAll(achievedNames);
			shuffledNameList.addAll(remainingNames);
		}
	}

	public synchronized int getRoundIndex() {
		return roundIndex;
	}

	public synchronized Map<Team, List<Integer>> getMapTeamsToScores() {
		return mapTeamsToScores;
	}

	public void putPlayerInTeam(int aPlayerPublicID, int aTeamIndex) {
		Player player = PlayerManager.getPlayer(aPlayerPublicID);
		if ( player != null ) {
			for ( Team team : teamList ) {
				removePlayerFromTeam(player, team);
			}
			
			if ( aTeamIndex >=0
					&& aTeamIndex < teamList.size() ) {
				Team team = teamList.get(aTeamIndex);
				team.addPlayer(player);
				mapPlayersToTeams.put(player, team);
				playersWithoutTeams.remove(player);
				updateCurrentPlayerFromIndicesAfterChangeToTeamStructure();
			}
		}
	}

	private void removePlayerFromTeam(Player player, Team team) {
		int indexOfPlayer = team.indexOf(player);
		if ( indexOfPlayer >= 0 ) {
			Integer nextPlayerIndex = mapTeamsToNextPlayerIndices.get(team);
			if ( nextPlayerIndex != null
					&& nextPlayerIndex > indexOfPlayer ) {
				nextPlayerIndex--;
				mapTeamsToNextPlayerIndices.put(team, nextPlayerIndex);
			}
			team.removePlayer(player);
			updateCurrentPlayerFromIndicesAfterChangeToTeamStructure();
			
//			if ( currentPlayer == player ) {
//				Integer playerIndex = mapTeamsToNextPlayerIndices.get(team);
//				if ( playerIndex == team.getPlayerList().size() ) {
//					if ( playerIndex == 0 ) {
//						// team has run out of players. Set index to -1, so that when it has players again, it'll start at the beginning
//						playerIndex = -1;
//					}
//					else {
//						playerIndex = 0;
//					}
//					mapTeamsToNextPlayerIndices.put(team, playerIndex);
//				}
//
//				if ( ! team.getPlayerList().isEmpty() ) {
//					currentPlayer = team.getPlayerList().get(playerIndex);
//				}
//				else if ( nextTeamIndex >= 0 ) {
//					/* if nextTeamIndex is -1, we haven't started the 1st round yet.
//					 * If we're already removing players at this point, either we're in one of the unit tests,
//					 * or there's trouble. Either way, wait until play properly starts before we start the
//					 * teams taking turns.
//					 */
//					incrementPlayer();
//				}
//			}
		}
	}

	public void removePlayer(int aPlayerPublicID) {
		Player player = PlayerManager.getPlayer(aPlayerPublicID);
		if ( player != null
				&& player != host ) {
			player.setGame(null);


			for ( Team team : teamList ) {
				removePlayerFromTeam(player, team);
			}

			playersWithoutTeams.remove(player);
			mapPlayersToTeams.remove(player);
			mapPlayersToNameLists.remove(player);
		}
	}

	public synchronized void freezeNameList() {
		masterNameList.clear();
		for ( Entry<Player, List<String>> mapEntry : mapPlayersToNameLists.entrySet() ) {
			masterNameList.addAll(mapEntry.getValue());
		}
		mapPlayersToNameLists.clear();
	}
	
	/**
	 * For debugging, return list of all players that are referenced in any field
	 * @return
	 */
	public synchronized List<Player> getAllReferencedPlayers() {
		LinkedHashSet<Player>	allPlayerSet		= new LinkedHashSet<>();
		if ( host != null ) {
			allPlayerSet.add(host);
		}
		if ( currentPlayer != null ) {
			allPlayerSet.add(currentPlayer);
		}
		allPlayerSet.addAll(playersWithoutTeams);
		teamList.stream().forEach(t -> allPlayerSet.addAll(t.getPlayerList()));
		allPlayerSet.addAll(mapPlayersToTeams.keySet());
		allPlayerSet.addAll(mapPlayersToNameLists.keySet());
		
		return new ArrayList<>(allPlayerSet);
	}

	public void movePlayerInTeamOrder(int aPlayerID, boolean aMovePlayerLater) {
		Player player = PlayerManager.getPlayer(aPlayerID);
		if ( player != null ) {
			Team team = mapPlayersToTeams.get(player);
			if ( team != null ) {
				int indexOfPlayer = team.indexOf(player);
				if ( indexOfPlayer >= 0 ) {
					team.movePlayerAtIndex( indexOfPlayer, aMovePlayerLater );
					updateCurrentPlayerFromIndicesAfterChangeToTeamStructure();
				}
			}
		}
	}

	public void makePlayerNextInTeam(Integer aPlayerID) {
		Player player = PlayerManager.getPlayer(aPlayerID);
		if ( player != null ) {
			Team team = mapPlayersToTeams.get(player);
			if ( team != null ) {
				int indexOfPlayer = team.indexOf(player);
				if ( indexOfPlayer >= 0 ) {
					mapTeamsToNextPlayerIndices.put(team, indexOfPlayer);
					updateCurrentPlayerFromIndicesAfterChangeToTeamStructure();
				}
			}
		}
	}
}
