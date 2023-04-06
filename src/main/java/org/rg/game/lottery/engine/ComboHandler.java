package org.rg.game.lottery.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ComboHandler {

	private List<Integer> numbers;
	private int combinationCount;
	private AtomicInteger combosSize;

	public ComboHandler(List<Integer> numbers, int combinationCount) {
		this.numbers = new ArrayList<>(numbers);
		this.combinationCount = combinationCount;
	}

	public int getSize() {
		if (combosSize == null) {
			combosSize = new AtomicInteger(0);
			compute();
		}
		return combosSize.get();
	}

	private int compute() {
		compute(
			numbers,
			combosSize,
			new int[combinationCount],
			0,
			numbers.size() - 1,
			0
		);
		return combosSize.get();
	}

	private void compute(
		List<Integer> numbers,
		AtomicInteger combinationCounter,
		int indexes[],
		int start,
		int end,
		int index
	) {
	    if (index == indexes.length) {
	    	combinationCounter.incrementAndGet();
	    	/*if ((combinationCounter.get() % 1000000) == 0) {
	    		System.out.println("Iterated " + combinationCounter.get() + " of combinations");
    		}*/
	    } else if (start <= end) {
	        indexes[index] = start;
	        compute(numbers, combinationCounter, indexes, start + 1, end, index + 1);
	        compute(numbers, combinationCounter, indexes, start + 1, end, index);
	    }
	}

	public Map<Integer, List<Integer>> find(Collection<Integer> indexes, boolean useSameCollectionInstance) {
		Map<Integer, List<Integer>> result = new HashMap<>();
		if (indexes.isEmpty()) {
			return result;
		}
		Collection<Integer> indexesToBeFound = useSameCollectionInstance ? indexes : new HashSet<>(indexes);
		find(
			numbers,
			new AtomicInteger(0),
			new int[combinationCount],
			0,
			numbers.size() - 1,
			0,
			indexesToBeFound,
			result
		);
		if (!indexesToBeFound.isEmpty()) {
			throw new RuntimeException("Not all indexes have been found");
		}
		return result;
	}

	private Map<Integer, List<Integer>> find(
		List<Integer> numbers,
		AtomicInteger combinationCounter,
		int indexes[],
		int start,
		int end,
		int index,
		Collection<Integer> indexesToBeFound,
		Map<Integer, List<Integer>> collector
	) {
        if (indexesToBeFound.isEmpty()) {
        	return collector;
        }
	    if (index == indexes.length) {
	    	Integer currentIndex = combinationCounter.getAndIncrement();
	    	if (indexesToBeFound.remove(currentIndex)) {
	    		collector.put(
    				currentIndex,
    				Arrays.stream(indexes)
					.map(numbers::get)
					.boxed()
				    .collect(Collectors.toList())
	    		);
	    	}
	    	/*if ((combinationCounter.get() % 10_000_000) == 0) {
	    		System.out.println("Tested " + combinationCounter.get() + " of combinations");
    		}*/
	    } else if (start <= end) {
	        indexes[index] = start;
	        find(numbers, combinationCounter, indexes, start + 1, end, index + 1, indexesToBeFound, collector);
	        find(numbers, combinationCounter, indexes, start + 1, end, index, indexesToBeFound, collector);
	    }
	    return collector;
	}

}