package com.merman.celebrity.util;

import java.util.ArrayList;
import java.util.List;

public class GameUtil {
	
	/**
	 * Allocates the players in <code>playerList</code> to <code>numTeams</code> roughly equal-size teams.
	 * <p>
	 * Here "roughly equal-size" means we have at most 2 possible team sizes, and if we do have 2 different
	 * team sizes, they differ by only 1. In other words, for <code>T</code> teams and <code>P</code> players,
	 * we want to find <code>n</code>, <code>k</code>
	 * such that
	 * <pre>
	 * n(T - k) + (n + 1)k = P
	 * 
	 * 0 ≤ k &lt; T</pre>
	 * This simplifies to
	 * <pre>
	 * nT + k = P</pre>
	 * So we choose
	 * <pre>
	 * n = P / T (integer division)
	 * k = P % T</pre>
	 * This is a bit less restrictive than what we do client-side, in which we require that at most 1 team
	 * has a different size from the others. In other words, <code>P%T == 1</code> or <code>P%T == T-1</code>.
	 * Client-side, we want to keep it to team numbers that people are likely to choose, to keep it simple.
	 * Server-side, we can simplify the code by making it more generic. 
	 * @param <P> The type of Player.
	 * @param numTeams The number of teams in which to split the players. Must be ≥ 1.
	 * @param playerList The list of players.
	 * @return A list of lists of players, such that the first <code>playerList.size() % numTeams</code> lists contain <code>playerList.size() / numTeams + 1</code>
	 * players, and the remaining <code>numTeams - playerList.size() % numTeams</code> lists contain <code>playerList.size() / numTeams</code> players. Each
	 * player in <code>playerList</code> occurs exactly once in exactly one of the returned lists. 
	 */
	public static <P> List<List<P>> allocateTeams(int numTeams, List<P> playerList) {
		if (numTeams < 1) {
			throw new IllegalArgumentException("numTeams must be >= 1. Value: " + numTeams);
		}
		if (numTeams > playerList.size()) {
			throw new IllegalArgumentException(String.format("numTeams (%,d) cannot be greater than number of players (%,d)", numTeams, playerList.size()));
		}
		List<List<P>>		teamList			= new ArrayList<>();
		int 				numPlayers 			= playerList.size();
		int					playerTeamIntRatio	= numPlayers / numTeams;
		int					remainder			= numPlayers % numTeams;
		
		int					numTeamsOfLargerSize 	= remainder;
		int					numTeamsOfSmallerSize	= numTeams - numTeamsOfLargerSize;
		int					smallerTeamSize			= playerTeamIntRatio;
		int[]				teamNumbers				= { numTeamsOfLargerSize, numTeamsOfSmallerSize };
		int[]				teamSizes				= { smallerTeamSize + 1, smallerTeamSize };
		
		int playerListOffset = 0;
		for ( int teamSizeIndex = 0; teamSizeIndex < teamSizes.length; teamSizeIndex++ ) {
			int teamSize 				= teamSizes[teamSizeIndex];
			int numTeamsWithThisSize	= teamNumbers[teamSizeIndex];
			
			for ( int teamIndex = 0; teamIndex < numTeamsWithThisSize; teamIndex++ ) {
				List<P>		team		= new ArrayList<>();
				teamList.add(team);
				for ( int playerIndex = 0; playerIndex < teamSize; playerIndex++ ) {
					team.add(playerList.get(playerListOffset + playerIndex));
				}
				playerListOffset += teamSize;
			}
		}
		
		assert playerListOffset == playerList.size();
		
		return teamList;
	}
}
