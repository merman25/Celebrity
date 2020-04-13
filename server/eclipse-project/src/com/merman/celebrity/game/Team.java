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
	
	public synchronized List<Player> getPlayerList() {
		return new ArrayList<>(playerList);
	}
	
	public String getTeamName() {
		return teamName;
	}
	public void setTeamName(String aTeamName) {
		teamName = aTeamName;
	}
}
