package com.merman.celebrity.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
		return new ArrayList( teamList );
	}

	public synchronized List<Player> getPlayersWithoutTeams() {
		return new ArrayList( playersWithoutTeams );
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

	public synchronized void allocateTeams() {
		List<Player>		playerList = new ArrayList<>( playersWithoutTeams );
		for ( Team team : teamList ) {
			playerList.addAll(team.getPlayerList());
		}
		teamList.clear();
		mapPlayersToTeams.clear();
		Collections.shuffle(playerList);
		
		Team team1 = new Team();
		team1.setTeamName("Team 1");
		int n = playerList.size();
		int limit = n / 2;
		
		/* Start from top, and work down. This ensures that if there's an odd
		 * number of players, Team 1 always has more than Team 2. Most importantly,
		 * this means that if there's only 1 player (for testing only), he goes in
		 * Team 1. If the only player is in Team 2, the client gets confused and
		 * doesn't give him a turn.
		 */
		for (int i = n-1; i >= limit; i--) {
			Player player = playerList.get(i);
			team1.addPlayer(player);
			mapPlayersToTeams.put(player, team1);
		}
		
		Team team2 = new Team();
		team2.setTeamName("Team 2");
		for (int i = limit-1; i >= 0; i--) {
			Player player = playerList.get(i);
			team2.addPlayer(player);
			mapPlayersToTeams.put(player, team2);
		}
		
		teamList.add(team1);
		teamList.add(team2);
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
		setState(GameState.READY_TO_START_NEXT_TURN);
	}
	
	public synchronized Player getCurrentPlayer() {
		return currentPlayer;
	}
	
	private void incrementPlayer() {
		if ( nextTeamIndex == -1 ) {
			nextTeamIndex = 0;
		}
		
		if ( nextTeamIndex < teamList.size() ) {
			Team team = teamList.get(nextTeamIndex);
			nextTeamIndex++;
			nextTeamIndex %= teamList.size();
			
			Integer nextPlayerIndex = mapTeamsToNextPlayerIndices.computeIfAbsent(team, t -> 0);
			
			if ( ! team.getPlayerList().isEmpty() ) {
				Player player = team.getPlayerList().get(nextPlayerIndex);
				nextPlayerIndex++;
				nextPlayerIndex %= team.getPlayerList().size();
				mapTeamsToNextPlayerIndices.put(team, nextPlayerIndex);
				
				currentPlayer = player;
			}
		}
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
		
		System.out.format("Team %s got %d names\n", currentTeam.getTeamName(), namesAchieved.size());
		
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
				teamList.get(aTeamIndex).addPlayer(player);
				playersWithoutTeams.remove(player);
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
			
			if ( currentPlayer == player ) {
				incrementPlayer();
			}
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
}
