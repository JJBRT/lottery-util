package org.rg.game.lottery.engine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.rg.game.core.MathUtils;

public class ComboHandler {

	private List<Integer> numbers;
	private long combinationSize;
	private BigInteger size;

	public ComboHandler(List<Integer> numbers, long combinationSize) {
		this.numbers = new ArrayList<>(numbers);
		this.combinationSize = combinationSize;
	}

	public BigInteger getSize() {
		if (size == null) {
			size = sizeOf(numbers.size(), combinationSize);
		}
		return size;
	}

	public long getCombinationSize() {
		return combinationSize;
	}

	public static Long sizeOfAsLong(Number numbersCount, Number combinationCount) {
		return sizeOf(BigInteger.valueOf(numbersCount.longValue()), BigInteger.valueOf(combinationCount.longValue())).longValue();
	}

	public static BigInteger sizeOf(Number numbersCount, Number combinationCount) {
		return sizeOf(
			BigInteger.valueOf(numbersCount.longValue()),
			BigInteger.valueOf(combinationCount.longValue())
		);
	}

	public static BigInteger sizeOf(BigInteger numbersCount, BigInteger combinationCount) {
		return MathUtils.INSTANCE.factorial(
			numbersCount
		).divide(
			MathUtils.INSTANCE.factorial(combinationCount)
			.multiply(
				MathUtils.INSTANCE.factorial(numbersCount.subtract(combinationCount))
			)
		);
	}


	public int getSizeAsInt() {
		return getSize().intValue();
	}

	public long getSizeAsLong() {
		return getSize().longValue();
	}

	public Map<Long, List<Integer>> find(Collection<Long> indexes, boolean useSameCollectionInstance) {
		Map<Long, List<Integer>> result = new HashMap<>();
		if (indexes.isEmpty()) {
			return result;
		}
		Collection<Long> indexesToBeFound = useSameCollectionInstance ? indexes : new HashSet<>(indexes);
		find(
			numbers,
			new AtomicLong(0),
			new int[(int)combinationSize],
			0,
			numbers.size() - 1,
			0,
			indexesToBeFound,
			result
		);
		if (!indexesToBeFound.isEmpty()) {
			throw new NoSuchElementException("Not all indexes have been found");
		}
		return result;
	}

	private Map<Long, List<Integer>> find(
		List<Integer> numbers,
		AtomicLong combinationCounter,
		int indexes[],
		int start,
		int end,
		int index,
		Collection<Long> indexesToBeFound,
		Map<Long, List<Integer>> collector
	) {
        if (indexesToBeFound.isEmpty()) {
        	return collector;
        }
	    if (index == indexes.length) {
	    	Long currentIndex = combinationCounter.getAndIncrement();
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
	    		LogUtils.logInfo("Tested " + combinationCounter.get() + " of combinations");
    		}*/
	    } else if (start <= end) {
	        indexes[index] = start;
	        find(numbers, combinationCounter, indexes, start + 1, end, index + 1, indexesToBeFound, collector);
	        find(numbers, combinationCounter, indexes, start + 1, end, index, indexesToBeFound, collector);
	    }
	    return collector;
	}

	public void iterate(
		Consumer<List<Integer>> action
	) {
		iterate(
			this.numbers,
			new int[(int)combinationSize],
			0,
			numbers.size() - 1,
			0,
			action
		);
	}

	private void iterate(
		List<Integer> numbers,
		int indexes[],
		int start,
		int end,
		int index,
		Consumer<List<Integer>> action
	) {
	    if (index == indexes.length) {
	    	action.accept(
		    	Arrays.stream(indexes)
				.map(numbers::get)
				.boxed()
			    .collect(Collectors.toList())
			 );
	    } else if (start <= end) {
	        indexes[index] = start;
	        iterate(numbers, indexes, start + 1, end, index + 1, action);
	        iterate(numbers, indexes, start + 1, end, index, action);
	    }
	}

	public static String toExpression(Collection<Integer> numbers) {
		String expression = "";
		Integer previousNumber = null;
		List<Integer> comboSumList = new ArrayList<>(new TreeSet<>(numbers));
		for (int i = 0; i < comboSumList.size(); i++) {
			Integer sum = comboSumList.get(i);
			if (previousNumber == null) {
				expression += sum;
				previousNumber = sum;
			} else if (previousNumber == sum - 1) {
				if (!expression.endsWith("->")) {
					expression += "->";
				}
				if (i < comboSumList.size() - 1) {
					previousNumber = sum;
				} else {
					expression += sum;
				}
			} else if (expression.endsWith("->")) {
				expression += previousNumber + "," + sum;
				previousNumber = sum;
			} else {
				expression += "," +sum;
				previousNumber = sum;
			}
		}
		return expression;
	}

	public static String toString(List<Integer> combo) {
		return toString(combo, "\t");
	}

	public static String toString(List<Integer> combo, String separator) {
		return String.join(
			separator,
			combo.stream()
		    .map(Object::toString)
		    .collect(Collectors.toList())
		);
	}

	public static int sumValues(List<Integer> combo) {
		return combo.stream().collect(Collectors.summingInt(Integer::intValue)).intValue();
	}

	public static int sumMultiplicationOfEachValueByItselfReduced(List<Integer> combo, Integer reduction) {
		return processAndSum(combo, number -> number * (number - reduction));
	}

	public static int sumPowerOfValues(List<Integer> combo, Integer exponent) {
		return processAndSum(combo, number -> (int)Math.pow(number, exponent));
	}

	public static int processAndSum(List<Integer> combo, UnaryOperator<Integer> numberProcessor) {
		return combo.stream().map(numberProcessor).collect(Collectors.toList())
		.stream().collect(Collectors.summingInt(Integer::intValue)).intValue();
	}

}