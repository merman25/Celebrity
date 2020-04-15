package com.merman.celebrity.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

public class Game {
	private String ID;
	private Player host;
	private List<Team> teamList = new ArrayList<>();
	private List<Player> playersWithoutTeams = new ArrayList<>();
	private Map<Player, Team> mapPlayersToTeams		= new HashMap<Player, Team>();
	private GameState state = GameState.WAITING_FOR_PLAYERS;
	private Map<Player, List<String>>	mapPlayersToNameLists		= new HashMap<Player, List<String>>();
	private List<String> shuffledNameList = new ArrayList<>();
	private Turn currentTurn;
	private int previousNameIndex;
	private int currentNameIndex;
	private Map<Team, List<String>> mapTeamsToAchievedNames = new HashMap<Team, List<String>>();
	
	private int numRounds;
	private int roundDurationInSec;
	private int numNamesPerPlayer;
	
	
	private int nextTeamIndex = -1;
	private Map<Team, Integer>		mapTeamsToNextPlayerIndices		= new HashMap<Team, Integer>();
	private Player currentPlayer;
	
	private int roundIndex;
	
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
		
		for (int i = 0; i < limit; i++) {
			Player player = playerList.get(i);
			team1.addPlayer(player);
			mapPlayersToTeams.put(player, team1);
		}
		
		Team team2 = new Team();
		team2.setTeamName("Team 2");
		for (int i = limit; i < n; i++) {
			Player player = playerList.get(i);
			team2.addPlayer(player);
			mapPlayersToTeams.put(player, team2);
		}
		
		teamList.add(team1);
		teamList.add(team2);
		playersWithoutTeams.clear();
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
		if ( currentPlayer == null ) {
			incrementPlayer();
		}
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
		for ( Entry<Player, List<String>> mapEntry : mapPlayersToNameLists.entrySet() ) {
			shuffledNameList.addAll(mapEntry.getValue());
		}
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
		Team currentTeam = mapPlayersToTeams.get(currentPlayer);
		List<String>		namesAchieved		= new ArrayList<>(shuffledNameList.subList(previousNameIndex, currentNameIndex));
		mapTeamsToAchievedNames.computeIfAbsent(currentTeam, t -> new ArrayList<>()).addAll(namesAchieved);
		
		System.out.format("Team %s got %d names\n", currentTeam.getTeamName(), namesAchieved.size());
		
		previousNameIndex = currentNameIndex;
	}

	private void endRound() {
		roundIndex++;
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
			System.out.println( "Passing on name index " + aPassNameIndex + ", current list: " + shuffledNameList );
			List<String>		achievedNames		= new ArrayList<>(shuffledNameList.subList(0, aPassNameIndex));
			List<String>		remainingNames		= new ArrayList<>(shuffledNameList.subList(aPassNameIndex, shuffledNameList.size()));

			int					newIndex			= new Random().nextInt( remainingNames.size() - 1 ) + 1;
			String				oldName				= remainingNames.get(newIndex);
			remainingNames.set(newIndex, remainingNames.get(0));
			remainingNames.set(0, oldName);

			shuffledNameList.clear();
			shuffledNameList.addAll(achievedNames);
			shuffledNameList.addAll(remainingNames);

			System.out.println( "New list: " + shuffledNameList );
		}
	}

	public synchronized int getRoundIndex() {
		return roundIndex;
	}
}
