package com.merman.celebrity.game;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.merman.celebrity.client.theme.IconType;
import com.merman.celebrity.client.theme.Theme;
import com.merman.celebrity.client.theme.ThemeManager;
import com.merman.celebrity.game.events.AddPlayerGameEvent;
import com.merman.celebrity.game.events.AllocateTeamsGameEvent;
import com.merman.celebrity.game.events.GameEvent;
import com.merman.celebrity.game.events.IGameEventListener;
import com.merman.celebrity.game.events.NotifyClientGameEventListener;
import com.merman.celebrity.game.events.RemoveNameListGameEvent;
import com.merman.celebrity.game.events.RemovePlayerGameEvent;
import com.merman.celebrity.game.events.SetCurrentNameIndexGameEvent;
import com.merman.celebrity.game.events.SetDisplayCelebNamesGameEvent;
import com.merman.celebrity.game.events.SetHostGameEvent;
import com.merman.celebrity.game.events.SetNameListGameEvent;
import com.merman.celebrity.game.events.SetPassOnNameIndexGameEvent;
import com.merman.celebrity.game.events.SetStatusGameEvent;
import com.merman.celebrity.game.events.TurnEndedGameEvent;
import com.merman.celebrity.game.events.UpdateCurrentPlayerGameEvent;
import com.merman.celebrity.server.CelebrityMain;
import com.merman.celebrity.server.SessionManager;
import com.merman.celebrity.server.WebsocketHandler;
import com.merman.celebrity.server.cleanup.CleanupHelper;
import com.merman.celebrity.server.cleanup.ExpiryTime;
import com.merman.celebrity.server.cleanup.ICanExpire;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;
import com.merman.celebrity.util.GameUtil;
import com.merman.celebrity.util.SharedRandom;

public class Game implements ICanExpire {
	private String                    ID;
	private Player                    host;
	private List<Team>                teamList                    = new ArrayList<>();
	private List<Player>              playersWithoutTeams         = new ArrayList<>();
	private Map<Player, Team>         mapPlayersToTeams           = new LinkedHashMap<>();
	private GameStatus                status                      = GameStatus.WAITING_FOR_PLAYERS;
	private boolean                   displayCelebNames			  = true;
	
	/* Use a sorted Map, so that if we're using a fixed random seed, the shuffled name list will
	 * always be the same, so long as all player names are distinct (which is the case in our tests).
	 * 
	 * In case 2 player names are the same, we also then compare by session ID
	 */
	private Map<Player, List<String>> mapPlayersToNameLists       = new TreeMap<>(Comparator.comparing(Player::getName).thenComparing(Player::getSessionID));
	private List<String>              masterNameList              = new ArrayList<>();
	private List<String>              shuffledNameList            = new ArrayList<>();
	private Turn                      currentTurn;
	private int                       previousNameIndex;
	private int                       currentNameIndex;
	private Map<Team, List<String>>   mapTeamsToAchievedNames     = new HashMap<>();

	private int                       numRounds;
	private int                       roundDurationInSec;
	private int                       numNamesPerPlayer;

	private int                       nextTeamIndex               = -1;
	private Map<Team, Integer>        mapTeamsToNextPlayerIndices = new HashMap<>();
	private Map<Team, List<Integer>>  mapTeamsToScores            = new HashMap<>();
	private Player                    currentPlayer;

	private int                       roundIndex;

	private List<IGameEventListener>  eventListeners              = new ArrayList<>();
	private boolean                   fireEvents                  = true;
	
	private boolean                   expired;
	private ExpiryTime                expiryTime 				  = new ExpiryTime(CleanupHelper.defaultExpiryDurationInS);
	
	// Useful for E2E testing. Value is supplied to the testBots, so they can tell if they're taking their turn at the proper time.
	private int                       turnCount;
	
	// Set true by an HTTP request for which there is no visible button - only test bots set it to true
	private boolean                   testGame;
	
	private Random                    random						= SharedRandom.getRandom();
	
	public Game(String aID) {
		ID = aID;
	}

	public synchronized GameStatus getStatus() {
		return status;
	}

	public synchronized void setStatus(GameStatus aStatus) {
		status = aStatus;
		Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Game", this, "Setting status to", aStatus );
		fireGameEvent(new SetStatusGameEvent(this, aStatus));
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
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "setHost", aHost);
		host = aHost;
		fireGameEvent(new SetHostGameEvent(this, aHost));
	}

	public synchronized void addPlayer(Player aPlayer) {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "addPlayer", aPlayer);
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
		fireGameEvent(new AddPlayerGameEvent(this, aPlayer));
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
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "setNumRounds", aNumRounds);
		numRounds = aNumRounds;
	}

	public synchronized int getRoundDurationInSec() {
		return roundDurationInSec;
	}

	public synchronized void setRoundDurationInSec(int aRoundDurationInSec) {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "setRoundDurationInSec", aRoundDurationInSec);
		roundDurationInSec = aRoundDurationInSec;
	}

	public synchronized int getNumNamesPerPlayer() {
		return numNamesPerPlayer;
	}

	public synchronized void setNumNamesPerPlayer(int aNumNamesPerPlayer) {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "setNumNamesPerPlayer", aNumNamesPerPlayer);
		numNamesPerPlayer = aNumNamesPerPlayer;
	}

	public synchronized void allocateTeams(int aNumTeams, boolean aAllocateTeamsAtRandom) {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "allocateTeams. Random", aAllocateTeamsAtRandom);
		List<Player>		playerList = new ArrayList<>( playersWithoutTeams );
		for ( Team team : teamList ) {
			playerList.addAll(team.getPlayerList());
		}
		teamList.clear();
		mapPlayersToTeams.clear();
		if ( aAllocateTeamsAtRandom ) {
			/* Sort before shuffling to get repeatable results when using fixed seed
			 * (players may not always be added to playersWithoutTeams in the same order, it depends
			 * on the network)
			 */
			Collections.sort(playerList, Comparator.comparing(Player::getName));
			Collections.shuffle(playerList, getRandom());
		}
		
		int numTeams = aNumTeams;
		if (playerList.size() == 1) {
			// special case: if only 1 player (used for testing only), have just 1 team
			numTeams = 1;
		}
		List<List<Player>> allocatedTeams = GameUtil.allocateTeams(numTeams, playerList);
		
		for (int teamIndex = 0; teamIndex < allocatedTeams.size(); teamIndex++) {
			List<Player> playerListForThisTeam = allocatedTeams.get(teamIndex);
			
			Team team = new Team();
			team.setTeamName("Team " + (teamIndex+1));
			teamList.add(team);
			
			for (int playerIndex = 0; playerIndex < playerListForThisTeam.size(); playerIndex++) {
				Player player = playerListForThisTeam.get(playerIndex);
				team.addPlayer(player);
				mapPlayersToTeams.put(player, team);
			}
		}
		
		playersWithoutTeams.clear();
		
		nextTeamIndex = -1;
		incrementPlayer();
		
		fireGameEvent(new AllocateTeamsGameEvent(this, aNumTeams));
	}

	public synchronized void setNameList(Player aPlayer, List<String> aCelebNameList) {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "setNameList", aPlayer, "List", aCelebNameList);
		if ( mapPlayersToTeams.containsKey(aPlayer) ) {
			mapPlayersToNameLists.put(aPlayer, aCelebNameList);
			fireGameEvent(new SetNameListGameEvent(this, aPlayer));
		}
	}
	
	public synchronized List<String> getNameList(Player aPlayer) {
		return mapPlayersToNameLists.get(aPlayer);
	}
	
	public synchronized void removeNameList(Player aPlayer) {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "removeNameList", aPlayer);
		mapPlayersToNameLists.remove(aPlayer);
		fireGameEvent(new RemoveNameListGameEvent(this, aPlayer));
	}
	
	public synchronized void allowNextPlayerToStartNextTurn() {
		setStatus(GameStatus.READY_TO_START_NEXT_TURN);
	}
	
	public synchronized Player getCurrentPlayer() {
		return currentPlayer;
	}
	
	public synchronized void incrementPlayer() {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "incrementPlayer");
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
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "updateCurrentPlayerFromIndicesAfterChangeToTeamStructure");
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
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "currentPlayer", currentPlayer);
		fireGameEvent( new UpdateCurrentPlayerGameEvent(this, player));
		return player;
	}

	public synchronized int getNumPlayersToWaitFor() {
		return mapPlayersToTeams.size() - mapPlayersToNameLists.size();
	}

	public synchronized void shuffleNames() {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "shuffleNames");
		previousNameIndex = 0;
		currentNameIndex = 0;
		shuffledNameList.clear();
		shuffledNameList.addAll(masterNameList);
		Collections.shuffle(shuffledNameList, getRandom());
	}
	
	public synchronized List<String> getShuffledNameList() {
		return shuffledNameList;
	}
	
	public synchronized void startRound() {
		Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Game", this, "Starting round", roundIndex+1);
		mapTeamsToAchievedNames.clear();
		allowNextPlayerToStartNextTurn();
	}

	public synchronized void startTurn() {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "startTurn");
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
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "stopTurn");
		if ( currentTurn != null ) {
			currentTurn.stop();
			currentTurn = null;
		}
	}

	public synchronized void turnEnded() {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "turnEnded");
		boolean oldFireEventsValue = isFireEvents();
		try {
			// Only fire the event after all processing has been completed. Otherwise sometimes the test bots see
			// the turn control buttons too early.
			setFireEvents(false);
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
			
			setFireEvents(oldFireEventsValue);
			fireGameEvent(new TurnEndedGameEvent(this, getStatus(), getCurrentPlayer()));
		}
		finally {
			// Make sure value is restored even if there's an exception, but don't fire the game event in that case
			setFireEvents(oldFireEventsValue);
		}

		if ( GameManager.createFiles ) {
			Path gameDir = CelebrityMain.getLoggingDirectory(this);
			if ( Files.isDirectory(gameDir)) {
				int numFiles = gameDir.toFile().listFiles().length;
				File fileToCreate;
				while ( ( fileToCreate = new File(gameDir.toFile(), "" + ++numFiles) ).exists() );

				try ( PrintWriter p = new PrintWriter(fileToCreate) ) {
					p.print(GameManager.serialise(this, null, false));
				}
				catch ( IOException e ) {
					Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "IOException when trying to save game", this, "at end of turn", getTurnCount(), "to file", fileToCreate, "exception", e);
				}
			}
		}
	}

	private void keepScore() {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "keepScore");
		Team currentTeam = mapPlayersToTeams.get(getCurrentPlayer());
		List<String>		namesAchieved		= new ArrayList<>(shuffledNameList.subList(previousNameIndex, currentNameIndex));
		mapTeamsToAchievedNames.computeIfAbsent(currentTeam, t -> new ArrayList<>()).addAll(namesAchieved);
		
		Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "Game", this, String.format("%s got %d names for %s", getCurrentPlayer(), namesAchieved.size(), currentTeam.getTeamName()));
		
		previousNameIndex = currentNameIndex;
	}

	private void endRound() {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "endRound");
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
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "setCurrentNameIndex", aCurrentNameIndex);
		if ( getStatus() == GameStatus.PLAYING_A_TURN ) {
			currentNameIndex = aCurrentNameIndex;
			if ( currentNameIndex >= shuffledNameList.size() ) {
				stopTurn();
			}
			else {
				fireGameEvent(new SetCurrentNameIndexGameEvent(this, aCurrentNameIndex));
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
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "setPassOnNameIndex", aPassNameIndex);
		if ( aPassNameIndex < shuffledNameList.size() - 1 ) {
			List<String>		achievedNames		= new ArrayList<>(shuffledNameList.subList(0, aPassNameIndex));
			List<String>		remainingNames		= new ArrayList<>(shuffledNameList.subList(aPassNameIndex, shuffledNameList.size()));

			int					newIndex			= getRandom().nextInt( remainingNames.size() - 1 ) + 1;
			String				oldName				= remainingNames.get(newIndex);
			remainingNames.set(newIndex, remainingNames.get(0));
			remainingNames.set(0, oldName);

			shuffledNameList.clear();
			shuffledNameList.addAll(achievedNames);
			shuffledNameList.addAll(remainingNames);

			fireGameEvent(new SetPassOnNameIndexGameEvent(this, aPassNameIndex));
		}
	}

	public synchronized int getRoundIndex() {
		return roundIndex;
	}

	public synchronized Map<Team, List<Integer>> getMapTeamsToScores() {
		return mapTeamsToScores;
	}

	public synchronized void putPlayerInTeam(int aPlayerPublicID, int aTeamIndex) {
		Player player = PlayerManager.getPlayer(aPlayerPublicID);
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "putPlayerInTeam", aPlayerPublicID, "player", player, "team index", aTeamIndex);
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
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "player", player, "team", team);
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

	public synchronized void removePlayer(int aPlayerPublicID) {
		Player player = PlayerManager.getPlayer(aPlayerPublicID);
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "removePlayer", aPlayerPublicID, "player", player);
		if ( player != null ) {
			player.setGame(null);


			for ( Team team : teamList ) {
				removePlayerFromTeam(player, team);
			}

			playersWithoutTeams.remove(player);
			mapPlayersToTeams.remove(player);
			mapPlayersToNameLists.remove(player);
			fireGameEvent(new RemovePlayerGameEvent(this, player));
		}
	}

	public synchronized void freezeNameList() {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "freezeNameList");
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

	public synchronized void movePlayerInTeamOrder(int aPlayerID, boolean aMovePlayerLater) {
		Player player = PlayerManager.getPlayer(aPlayerID);
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "movePlayerInTeamOrder", aPlayerID, "player", player, "later?", aMovePlayerLater);
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

	public synchronized void makePlayerNextInTeam(Integer aPlayerID) {
		Player player = PlayerManager.getPlayer(aPlayerID);
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "makePlayerNextInTeam", aPlayerID, "player", player);
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
	
	public synchronized void fireGameEvent(GameEvent aGameEvent) {
		resetExpiryTime();
		if ( isFireEvents() ) {
			for ( IGameEventListener listener: eventListeners ) {
				try {
					listener.gameEvent(aGameEvent);
				}
				catch ( RuntimeException e ) {
					Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "Game", this, "firing event", aGameEvent, "listener", listener, "exception", e);
				}
			}
		}
	}

	public synchronized void setRoundIndex(int aRoundIndex) {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "setRoundIndex", aRoundIndex);
		roundIndex = aRoundIndex;
	}

	public List<String> getMasterNameList() {
		return masterNameList;
	}

	public synchronized void setPreviousNameIndex(int aPreviousNameIndex) {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "setPreviousNameIndex", aPreviousNameIndex);
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
	
	public synchronized void setTeamIndex(int aIndex) {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "setTeamIndex", aIndex);
		if (aIndex < 0 || aIndex >= teamList.size()) {
			throw new IndexOutOfBoundsException(aIndex + ": list size " + teamList.size());
		}
		nextTeamIndex = aIndex;
		updateCurrentPlayerFromIndicesAfterChangeToTeamStructure();
	}

	public void removeAllPlayers() {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "removeAllPlayers");
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

	public boolean isTestGame() {
		return testGame;
	}

	public void setTestGame(boolean aTestGame) {
		testGame = aTestGame;
	}

	public Random getRandom() {
		return random;
	}

	public void setRandom(Random aRandom) {
		random = aRandom;
	}

	public boolean isDisplayCelebNames() {
		return displayCelebNames;
	}

	public synchronized void setDisplayCelebNames(boolean aDisplayCelebNames) {
		Log.log(LogMessageType.DEBUG, LogMessageSubject.GENERAL, "Game", this, "setDisplayCelebNames", aDisplayCelebNames);
		displayCelebNames = aDisplayCelebNames;
		fireGameEvent(new SetDisplayCelebNamesGameEvent(this, aDisplayCelebNames));
	}
}
