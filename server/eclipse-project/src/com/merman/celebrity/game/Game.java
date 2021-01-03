package com.merman.celebrity.game;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.merman.celebrity.client.theme.IconType;
import com.merman.celebrity.client.theme.Theme;
import com.merman.celebrity.client.theme.ThemeManager;
import com.merman.celebrity.game.events.GameEvent;
import com.merman.celebrity.game.events.GameStateUpdateEvent;
import com.merman.celebrity.game.events.IGameEventListener;
import com.merman.celebrity.game.events.NotifyClientGameEventListener;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.WebsocketHandler;
import com.merman.celebrity.server.cleanup.CleanupHelper;
import com.merman.celebrity.server.cleanup.ExpiryTime;
import com.merman.celebrity.server.cleanup.ICanExpire;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.merman.celebrity.util.SharedRandom;

public class Game implements ICanExpire {
	private String                    ID;
	private Player                    host;
	private List<Team>                teamList                    = new ArrayList<>();
	private List<Player>              playersWithoutTeams         = new ArrayList<>();
	private Map<Player, Team>         mapPlayersToTeams           = new LinkedHashMap<Player, Team>();
	private GameStatus                status                      = GameStatus.WAITING_FOR_PLAYERS;
	private Map<Player, List<String>> mapPlayersToNameLists       = new LinkedHashMap<Player, List<String>>();
	private List<String>              masterNameList              = new ArrayList<>();
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

	private List<IGameEventListener>  eventListeners              = new ArrayList<>();
	private boolean                   fireEvents                  = true;
	
	private boolean                   expired;
	private ExpiryTime                expiryTime 				  = new ExpiryTime(CleanupHelper.defaultExpiryDurationInS);
	
	// Useful for E2E testing. Value is supplied to the testBots, so they can tell if they're taking their turn at the proper time.
	private int                       turnCount;
	
	public Game(String aID) {
		ID = aID;
	}

	public synchronized GameStatus getStatus() {
		return status;
	}

	public synchronized void setStatus(GameStatus aStatus) {
		status = aStatus;
		Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Game", this, String.format("Setting status to %s", aStatus) );
		fireGameEvent();
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

	public synchronized void setHost(Player aHost) {
		host = aHost;
		fireGameEvent();
	}

	public synchronized void addPlayer(Player aPlayer) {
		setPlayerIconIfNecessary( aPlayer );
		playersWithoutTeams.add(aPlayer);
		aPlayer.setGame(this);
		if ( host == null ) {
			Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Game", this, String.format("Game %s had no host, setting %s as the host", getID(), aPlayer) );
			GameManager.setPlayerAsHostOfGame(this, aPlayer);
			currentPlayer = aPlayer;
		}
		
		WebsocketHandler websocketHandler = SessionManager.getWebsocketHandler(SessionManager.getSession(aPlayer.getSessionID()));
		if ( websocketHandler != null ) {
			addGameEventListener(new NotifyClientGameEventListener( websocketHandler ) );
		}
		fireGameEvent();
	}

	private void setPlayerIconIfNecessary(Player aPlayer) {
		if ( aPlayer.getIcon() == null
				&& aPlayer.getEmoji() == null ) {
			Theme theme = ThemeManager.getTheme(this);
			List<String> iconList = theme.getIconList();
			

			Set<String> usedIcons = getAllReferencedPlayers()
					.stream()
					.map( theme.getIconType() == IconType.EMOJI ? Player::getEmoji : Player::getIcon )
					.filter(x -> x!=null)
					.collect(Collectors.toSet());

			List<String> possibleIcons = new ArrayList<>(iconList);
			if (usedIcons.size() < possibleIcons.size()) {
				possibleIcons.removeAll(usedIcons);
			}

			if (! possibleIcons.isEmpty()) {
				String icon = possibleIcons.get( (int) ( Math.random() * possibleIcons.size() ) );
				if (theme.getIconType() == IconType.EMOJI) {
					aPlayer.setEmoji(icon);
				}
				else {
					aPlayer.setIcon(icon);
				}
			}
		}
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
			Collections.shuffle(playerList, SharedRandom.getRandom());
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
		
		fireGameEvent();
	}

	public synchronized void setNameList(Player aPlayer, List<String> aCelebNameList) {
		if ( mapPlayersToTeams.containsKey(aPlayer) ) {
			mapPlayersToNameLists.put(aPlayer, aCelebNameList);
			fireGameEvent();
		}
	}
	
	public synchronized List<String> getNameList(Player aPlayer) {
		return mapPlayersToNameLists.get(aPlayer);
	}
	
	public synchronized void removeNameList(Player aPlayer) {
		mapPlayersToNameLists.remove(aPlayer);
		fireGameEvent();
	}
	
	public synchronized void allowNextPlayerToStartNextTurn() {
		setStatus(GameStatus.READY_TO_START_NEXT_TURN);
	}
	
	public synchronized Player getCurrentPlayer() {
		return currentPlayer;
	}
	
	public void incrementPlayer() {
		if ( teamList.size() > 0 ) {
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
		}
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
		fireGameEvent();
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
		Collections.shuffle(shuffledNameList, SharedRandom.getRandom());
	}
	
	public synchronized List<String> getShuffledNameList() {
		return shuffledNameList;
	}
	
	public synchronized void startRound() {
		Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Game", this, String.format("Starting round %d", roundIndex+1));
		mapTeamsToAchievedNames.clear();
		allowNextPlayerToStartNextTurn();
	}

	public synchronized void startTurn() {
		turnCount++;
		currentTurn = new Turn(this, roundDurationInSec);
		currentTurn.start();
		setStatus(GameStatus.PLAYING_A_TURN);
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
			allowNextPlayerToStartNextTurn();
		}

		if ( GameManager.createFiles ) {
			File gameDir = new File("games/" + getID());
			if ( gameDir.isDirectory() ) {
				int numFiles = gameDir.listFiles().length;
				File fileToCreate;
				while ( ( fileToCreate = new File(gameDir, "" + ++numFiles) ).exists() );

				try ( PrintWriter p = new PrintWriter(fileToCreate) ) {
					p.print(GameManager.serialise(this, null, false));
				}
				catch ( IOException e ) {
					e.printStackTrace();
				}
			}
		}
	}

	private void keepScore() {
		Team currentTeam = mapPlayersToTeams.get(getCurrentPlayer());
		List<String>		namesAchieved		= new ArrayList<>(shuffledNameList.subList(previousNameIndex, currentNameIndex));
		mapTeamsToAchievedNames.computeIfAbsent(currentTeam, t -> new ArrayList<>()).addAll(namesAchieved);
		
		Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Game", this, String.format("%s got %d names for %s", getCurrentPlayer(), namesAchieved.size(), currentTeam.getTeamName()));
		
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
			setStatus(GameStatus.READY_TO_START_NEXT_ROUND);
		}
		else {
			setStatus(GameStatus.ENDED);
		}
	}

	public synchronized void setCurrentNameIndex(int aCurrentNameIndex) {
		if ( getStatus() == GameStatus.PLAYING_A_TURN ) {
			currentNameIndex = aCurrentNameIndex;
			if ( currentNameIndex >= shuffledNameList.size() ) {
				stopTurn();
			}
			else {
				fireGameEvent();
			}
		}
	}
	
	public void setCurrentNameIndexOnDeserialisation(int aCurrentNameIndex ) {
		currentNameIndex = aCurrentNameIndex;
	}

	public synchronized Map<Team, List<String>> getMapTeamsToAchievedNames() {
		return mapTeamsToAchievedNames;
	}

	public synchronized void setPassOnNameIndex(int aPassNameIndex) {
		if ( aPassNameIndex < shuffledNameList.size() - 1 ) {
			List<String>		achievedNames		= new ArrayList<>(shuffledNameList.subList(0, aPassNameIndex));
			List<String>		remainingNames		= new ArrayList<>(shuffledNameList.subList(aPassNameIndex, shuffledNameList.size()));

			int					newIndex			= SharedRandom.getRandom().nextInt( remainingNames.size() - 1 ) + 1;
			String				oldName				= remainingNames.get(newIndex);
			remainingNames.set(newIndex, remainingNames.get(0));
			remainingNames.set(0, oldName);

			shuffledNameList.clear();
			shuffledNameList.addAll(achievedNames);
			shuffledNameList.addAll(remainingNames);

			fireGameEvent();
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
		}
	}

	public void removePlayer(int aPlayerPublicID) {
		Player player = PlayerManager.getPlayer(aPlayerPublicID);
		if ( player != null ) {
			player.setGame(null);


			for ( Team team : teamList ) {
				removePlayerFromTeam(player, team);
			}

			playersWithoutTeams.remove(player);
			mapPlayersToTeams.remove(player);
			mapPlayersToNameLists.remove(player);
			fireGameEvent();
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
					int nextPlayerIndex = indexOfPlayer;
					if ( getTeamList().indexOf(team) != nextTeamIndex ) {
						/* Team is currently playing, so when play moves to this team,
						 * the team's player index will be incremented at the start of
						 * the turn. So, now we need to subtract 1 from it.
						 */
						nextPlayerIndex -= 1;
						if ( nextPlayerIndex < 0 ) {
							nextPlayerIndex += team.getPlayerList().size();
						}
					}
					mapTeamsToNextPlayerIndices.put(team, nextPlayerIndex);
					updateCurrentPlayerFromIndicesAfterChangeToTeamStructure();
				}
			}
		}
	}
	
	public synchronized void addGameEventListener( IGameEventListener aGameEventListener ) {
		eventListeners.add(aGameEventListener);
	}
	
	public synchronized void removeGameEventListener( IGameEventListener aGameEventListener ) {
		eventListeners.remove(aGameEventListener);
	}
	
	public synchronized void removeAllGameEventListeners( WebsocketHandler aWebsocketHandler ) {
		for ( Iterator<IGameEventListener> listenerIterator = eventListeners.iterator(); listenerIterator.hasNext(); ) {
			IGameEventListener eventListener = listenerIterator.next();
			if ( eventListener instanceof NotifyClientGameEventListener
					&& ((NotifyClientGameEventListener) eventListener).getWebsocketHandler() == aWebsocketHandler ) {
				listenerIterator.remove();
			}
		}
	}
	
	public void fireGameEvent() {
		fireGameEvent(new GameStateUpdateEvent(this));
	}
	
	public synchronized void fireGameEvent(GameEvent aGameEvent) {
		resetExpiryTime();
		if ( isFireEvents() ) {
			for ( IGameEventListener listener: eventListeners ) {
				try {
					listener.gameEvent(aGameEvent);
				}
				catch ( RuntimeException e ) {
					e.printStackTrace();
				}
			}
		}
	}

	public void setRoundIndex(int aRoundIndex) {
		roundIndex = aRoundIndex;
	}

	public List<String> getMasterNameList() {
		return masterNameList;
	}

	public void setPreviousNameIndex(int aPreviousNameIndex) {
		previousNameIndex = aPreviousNameIndex;
	}

	public void setFireEvents(boolean aFireEvents) {
		fireEvents  = aFireEvents;
	}

	public boolean isFireEvents() {
		return fireEvents;
	}

	public Map<Player, Team> getMapPlayersToTeams() {
		return mapPlayersToTeams;
	}

	public int getNextTeamIndex() {
		return nextTeamIndex;
	}

	public int getTurnCount() {
		return turnCount;
	}

	@Override
	public boolean isExpired() {
		if (!expired) {
			boolean allPlayersHaveExpired = ! getAllReferencedPlayers().stream()
												.filter(player -> ! player.isExpired())
												.findAny()
												.isPresent();
			expired = allPlayersHaveExpired
						&& ( getStatus() == GameStatus.ENDED
								|| expiryTime.isExpired() );
		}
		return expired;
	}
	
	public void resetExpiryTime() {
		expiryTime.resetExpiryTime();
	}

	@Override
	public String toString() {
		return getID();
	}
	
	public void setTeamIndex(int aIndex) {
		if (aIndex < 0 || aIndex >= teamList.size()) {
			throw new IndexOutOfBoundsException(aIndex + ": list size " + teamList.size());
		}
		nextTeamIndex = aIndex;
		updateCurrentPlayerFromIndicesAfterChangeToTeamStructure();
	}

	public void removeAllPlayers() {
		List<Player> allReferencedPlayers = getAllReferencedPlayers();
		for (Player player: allReferencedPlayers) {
			player.setGame(null);
		}
		for (Team team : teamList) {
			team.getPlayerList().clear();
		}
		
		host = null;
		currentPlayer = null;
		playersWithoutTeams.clear();
		mapPlayersToTeams.clear();
		mapPlayersToNameLists.clear();
		GameManager.setPlayerAsHostOfGame(this, null);
	}
}
