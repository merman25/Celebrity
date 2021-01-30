package com.merman.celebrity.util;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Maintains a pool of <code>int</code>s such that the value returned by {@link #pop()} is
 * the lowest value that has not yet been popped and has not been returned to the pool
 * by {@link #push(int)}.
 * <p>
 * Values start at {@link #startValue}.
 * <p>
 * Useful for indexing objects with a finite lifetime, for which you want a unique index
 * for each existing object, but are happy to re-use an index once it's no longer in use.
 * The alternative is to just use a value which increases forever, but this becomes
 * annoying to read in the logs when the values get too large.
 */
public class IntPool {
	
	private int startValue = 1;
	
	private SortedSet<Integer>               usedIndices						= new TreeSet<>(Comparator.reverseOrder());
	private SortedSet<Integer>               indicesToReUse						= new TreeSet<>();
	
	public synchronized int pop() {
		int index;
		if (! indicesToReUse.isEmpty()) {
			index = indicesToReUse.first();
			indicesToReUse.remove(index);
		}
		else if (usedIndices.isEmpty()) {
			index = getStartValue();
		}
		else {
			Integer highestIndex = usedIndices.first();
			index = highestIndex + 1;
		}
		
		usedIndices.add(index);
		
		return index;
	}

	public synchronized void push(int aNoLongerUsedIndex) {
		usedIndices.remove(aNoLongerUsedIndex);
		indicesToReUse.add(aNoLongerUsedIndex);
	}

	public int getStartValue() {
		return startValue;
	}

	public void setStartValue(int aStartValue) {
		startValue = aStartValue;
	}

}
