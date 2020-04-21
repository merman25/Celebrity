package com.merman.celebrity.game;

import java.util.ArrayList;
import java.util.List;

public class Team {
	private String teamName;
	private List<Player> playerList = new ArrayList<>();
	
	public synchronized void addPlayer( Player player ) {
		playerList.add(player);
	}
	
	public synchronized void removePlayer( Player player ) {
		playerList.remove(player);
	}
	
	public synchronized int indexOf( Player player ) {
		return playerList.indexOf(player);
	}
	
	public synchronized List<Player> getPlayerList() {
		return playerList;
	}
	
	public String getTeamName() {
		return teamName;
	}
	public void setTeamName(String aTeamName) {
		teamName = aTeamName;
	}

	public synchronized void movePlayerAtIndex(int aIndexOfPlayer, boolean aMovePlayerLater) {
		if ( aIndexOfPlayer < 0
				|| aIndexOfPlayer >= playerList.size() ) {
			throw new IndexOutOfBoundsException("" + aIndexOfPlayer);
		}
		int newIndex = aIndexOfPlayer + ( aMovePlayerLater ? 1 : -1 );
		newIndex %= playerList.size();
		
		Player movedPlayer 	= playerList.get(aIndexOfPlayer);
		Player temp 		= playerList.get(newIndex);
		playerList.set(newIndex, movedPlayer);
		playerList.set(aIndexOfPlayer, temp);
	}
	
	@Override
	public String toString() {
		return getTeamName();
	}
}
