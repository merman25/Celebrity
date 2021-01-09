package com.merman.celebrity.util;

import java.util.Random;

/**
 * Holds a shared <code>java.util.Random</code>, so that tests can be made deterministic.
 */
public class SharedRandom {
	
	private static Random random = new Random();
	private static boolean setRandomGeneratorForEachGameWithFixedSeed;

	private SharedRandom() {
	}

	public static synchronized void setSeed(long aSeed) {
		random.setSeed(aSeed);
	}
	
	public static synchronized Random getRandom() {
		return random;
	}

	public static boolean isSetRandomGeneratorForEachGameWithFixedSeed() {
		return setRandomGeneratorForEachGameWithFixedSeed;
	}

	public static void setSetRandomGeneratorForEachGameWithFixedSeed(boolean aSetRandomGeneratorForEachGameWithFixedSeed) {
		setRandomGeneratorForEachGameWithFixedSeed = aSetRandomGeneratorForEachGameWithFixedSeed;
	}
}
