package com.merman.celebrity.tests;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import com.merman.celebrity.util.GameUtil;

public class GameUtilTest {
	@Test
	public void test1Team() {
		assertTeamSizesAre(1, 1,   1);
		assertTeamSizesAre(1, 2,   2);
		assertTeamSizesAre(1, 3,   3);
		assertTeamSizesAre(1, 4,   4);
	}
	
	@Test
	public void test2Teams() {
		assertTeamSizesAre(2, 4,   2, 2);
		assertTeamSizesAre(2, 5,   3, 2);
		assertTeamSizesAre(2, 6,   3, 3);
		assertTeamSizesAre(2, 7,   4, 3);
		assertTeamSizesAre(2, 8,   4, 4);
		assertTeamSizesAre(2, 9,   5, 4);
		assertTeamSizesAre(2, 10,  5, 5);
	}
	
	@Test
	public void test3Teams() {
		assertTeamSizesAre(3, 6,   2, 2, 2);
		assertTeamSizesAre(3, 7,   3, 2, 2);
		assertTeamSizesAre(3, 8,   3, 3, 2);
		assertTeamSizesAre(3, 9,   3, 3, 3);
		assertTeamSizesAre(3, 10,  4, 3, 3);
		assertTeamSizesAre(3, 11,  4, 4, 3);
		assertTeamSizesAre(3, 12,  4, 4, 4);
		assertTeamSizesAre(3, 13,  5, 4, 4);
		assertTeamSizesAre(3, 14,  5, 5, 4);
		assertTeamSizesAre(3, 15,  5, 5, 5);
	}
	
	@Test
	public void test4Teams() {
		assertTeamSizesAre(4, 8,   2, 2, 2, 2);
		assertTeamSizesAre(4, 9,   3, 2, 2, 2);
		assertTeamSizesAre(4, 10,  3, 3, 2, 2);
		assertTeamSizesAre(4, 11,  3, 3, 3, 2);
		assertTeamSizesAre(4, 12,  3, 3, 3, 3);
		assertTeamSizesAre(4, 13,  4, 3, 3, 3);
		assertTeamSizesAre(4, 14,  4, 4, 3, 3);
		assertTeamSizesAre(4, 15,  4, 4, 4, 3);
		assertTeamSizesAre(4, 16,  4, 4, 4, 4);
	}
	
	@Test
	public void test5Teams() {
		assertTeamSizesAre(5, 10,  2, 2, 2, 2, 2);
		assertTeamSizesAre(5, 11,  3, 2, 2, 2, 2);
		assertTeamSizesAre(5, 12,  3, 3, 2, 2, 2);
		assertTeamSizesAre(5, 13,  3, 3, 3, 2, 2);
		assertTeamSizesAre(5, 14,  3, 3, 3, 3, 2);
		assertTeamSizesAre(5, 15,  3, 3, 3, 3, 3);
		assertTeamSizesAre(5, 16,  4, 3, 3, 3, 3);
		assertTeamSizesAre(5, 17,  4, 4, 3, 3, 3);
		assertTeamSizesAre(5, 18,  4, 4, 4, 3, 3);
		assertTeamSizesAre(5, 19,  4, 4, 4, 4, 3);
		assertTeamSizesAre(5, 20,  4, 4, 4, 4, 4);
	}
	
	@Test
	public void test6Teams() {
		assertTeamSizesAre(6, 12,  2, 2, 2, 2, 2, 2);
		assertTeamSizesAre(6, 13,  3, 2, 2, 2, 2, 2);
		assertTeamSizesAre(6, 14,  3, 3, 2, 2, 2, 2);
		assertTeamSizesAre(6, 15,  3, 3, 3, 2, 2, 2);
		assertTeamSizesAre(6, 16,  3, 3, 3, 3, 2, 2);
		assertTeamSizesAre(6, 17,  3, 3, 3, 3, 3, 2);
		assertTeamSizesAre(6, 18,  3, 3, 3, 3, 3, 3);
		assertTeamSizesAre(6, 19,  4, 3, 3, 3, 3, 3);
		assertTeamSizesAre(6, 20,  4, 4, 3, 3, 3, 3);
	}
	
	@Test
	public void testLargeTeams() {
		assertTeamSizesAre(6, 100, 17, 17, 17, 17, 16, 16);
		assertTeamSizesAre(17, 993, 59, 59, 59, 59, 59, 59, 59, 58, 58, 58, 58, 58, 58, 58, 58, 58, 58 );
	}
	
	@Test
	public void testEachPlayerOccursExactlyOnceInExactlyOneTeam() {
		int numTeams = 6;
		int numPlayers = 20;
		List<Object> playerList = list(numPlayers);
		List<List<Object>> teamList = GameUtil.allocateTeams(numTeams, playerList);
		
		List<Object> listOfAllPlayersInTeams = teamList.stream().flatMap(team -> team.stream()).collect(Collectors.toList());
		Assert.assertEquals(playerList, listOfAllPlayersInTeams);
	}
	
	private static List<Object> list(int aSize) {
		return Stream.generate( () -> new Object() )
					.limit(aSize)
					.collect(Collectors.toList());
	}
	
	private static void assertTeamSizesAre(int aNumTeams, int aNumPlayers, Integer... aSizes) {
		List<List<Object>> teamList = GameUtil.allocateTeams(aNumTeams, list(aNumPlayers));
		List<Integer> sizeList = teamList.stream().map(l -> l.size()).collect(Collectors.toList());
		Assert.assertEquals(Arrays.asList(aSizes), sizeList);
	}
}
