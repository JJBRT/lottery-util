package org.rg.game.lottery.engine;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CombinationFilterFactory {
	public static final CombinationFilterFactory INSTANCE;

	static {
		INSTANCE = new CombinationFilterFactory();
	}

	protected SimpleDateFormat simpleDateFormatter = new SimpleDateFormat("dd/MM/yyyy");
	protected DecimalFormat decimalFormat = new DecimalFormat( "#,##0.##" );
	protected DecimalFormat integerFormat = new DecimalFormat( "#,##0" );

	private CombinationFilterFactory() {}

	public Predicate<List<Integer>> parse(String filterAsString) {
		if (filterAsString == null || filterAsString.replaceAll("\\s+","").isEmpty()) {
			return numbers -> true;
		}
		return parseComplexExpression(filterAsString);
	}

	private Predicate<List<Integer>> parseComplexExpression(String filterAsString) {
		if (filterAsString.contains("&") || filterAsString.contains("|")) {
			int andCharacterIndex = filterAsString.indexOf("&");
			int orCharacterIndex = filterAsString.indexOf("|");
			if ((andCharacterIndex > -1 && orCharacterIndex > -1) ?
					andCharacterIndex < orCharacterIndex :
					andCharacterIndex > -1) {
				return parseSimpleExpression(filterAsString.substring(0, andCharacterIndex))
					.and(parseComplexExpression(filterAsString.substring(andCharacterIndex +1, filterAsString.length())));
			} else {
				return parseSimpleExpression(filterAsString.substring(0, orCharacterIndex))
					.or(parseComplexExpression(filterAsString.substring(orCharacterIndex +1, filterAsString.length())));
			}
		}
		return parseSimpleExpression(filterAsString);
	}

	private Predicate<List<Integer>> parseSimpleExpression(String filterAsString) {
		if (filterAsString.contains("odd") || filterAsString.contains("even")) {
			return buildOddOrEvenFilter(filterAsString);
		} else if (filterAsString.contains("sameLastDigit")) {
			return buildSameLastDigitFilter(filterAsString);
		} else if (filterAsString.contains("consecutiveLastDigit")) {
			return buildConsecutiveLastDigitFilter(filterAsString);
		} else if (filterAsString.contains("consecutiveNumber")) {
			return buildConsecutiveNumberFilter(filterAsString);
		} else if (filterAsString.contains("radius")) {
			return buildRadiusFilter(filterAsString);
		} else if (filterAsString.contains("->")) {
			return buildNumberGroupFilter(filterAsString);
		}
		return null;
	}

	private Predicate<List<Integer>> buildRadiusFilter(String filterAsString) {
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("radius");
		String[] rangeOptions = operationOptions[0].split("->");
		Integer leftRangeBounds = rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[0]) : null;
		Integer rightRangeBounds =  rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[1]) : null;
		String[] options = operationOptions[1].split(":");
		String[] boundsAsString = options[1].split(",");
		int leftOffset = Integer.parseInt(options[0].split(",")[0]);
		int rightOffset = Integer.parseInt(options[0].split(",")[1]);
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		return combo -> {
			int maxNumbersInRange = 0;
			for (Integer number : new TreeSet<>(combo)) {
				if (rangeOptions.length > 1) {
					if (number > rightRangeBounds) {
						return true;
					} else if (number < leftRangeBounds) {
						continue;
					}
				}
				int numbersInRangeCounter = 0;
				int leftBound = number + leftOffset;
				int rightBound = number + rightOffset;
				for (Integer innNumber : combo) {
					if (number != innNumber && innNumber >= leftBound && innNumber <= rightBound) {
						if (numbersInRangeCounter == 0) {
							numbersInRangeCounter++;
						}
						if (++numbersInRangeCounter > bounds[1]) {
							return false;
						} else if (numbersInRangeCounter > maxNumbersInRange) {
							maxNumbersInRange = numbersInRangeCounter;
						}
					}
				}
				if (rangeOptions.length > 1 && numbersInRangeCounter < bounds[0]) {
					return false;
				}
			}
			return rangeOptions.length > 1 || maxNumbersInRange >= bounds[0];
		};
	}

	private Predicate<List<Integer>> buildConsecutiveNumberFilter(String filterAsString) {
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("consecutiveNumber");
		String[] rangeOptions = operationOptions[0].split("->");
		Integer leftRangeBounds = rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[0]) : null;
		Integer rightRangeBounds =  rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[1]) : null;
		String[] options = operationOptions[1].split(":");
		String[] boundsAsString = options[1].split(",");
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		return combo -> {
			int counter = 0;
			int maxConsecutiveNumberCounter = 0;
			Integer previousNumber = null;
			for (int number : new TreeSet<>(combo)) {
				if (rangeOptions.length > 1) {
					if (number > rightRangeBounds) {
						return true;
					} else if (number < leftRangeBounds) {
						continue;
					}
				}
				if (previousNumber != null && ((number != 0 && previousNumber == number -1) || (number == 0 && previousNumber == 9))) {
					if (counter == 0) {
						counter++;
					}
					if (++counter > maxConsecutiveNumberCounter) {
						maxConsecutiveNumberCounter = counter;
					}
				} else {
					counter = 0;
				}
				previousNumber = number;
			}
			return maxConsecutiveNumberCounter >= bounds[0] && maxConsecutiveNumberCounter <= bounds[1];
		};
	}

	private Predicate<List<Integer>> buildNumberGroupFilter(String filterAsString) {
		String[] expressions = filterAsString.split(";");
		int[][] bounds = new int[expressions.length][4];
		boolean allMinAreZeroTemp = true;
		for (int i = 0; i < expressions.length; i++) {
			String[] expression = expressions[i].replaceAll("\\s+","").split(":");
			String[] boundsAsString = expression[0].split("->");
			bounds[i][0] = Integer.parseInt(boundsAsString[0]);
			bounds[i][1] = Integer.parseInt(boundsAsString[1]);
			String[] values = expression[1].split(",");
			if ((bounds[i][2] = Integer.parseInt(values[0])) > 0) {
				allMinAreZeroTemp = false;
			}
			bounds[i][3] = Integer.parseInt(values[1]);
		}
		boolean allMinAreZero =  allMinAreZeroTemp;
		return combo -> {
			int[] checkCounter = new int[bounds.length];
			for (Integer number : combo) {
				for (int i = 0; i < bounds.length; i++) {
					if (number >= bounds[i][0] && number <= bounds[i][1] && ++checkCounter[i] > bounds[i][3]) {
						return false;
					}
				}
			}
			if (!allMinAreZero) {
				for (int i = 0; i < bounds.length; i++) {
					if (checkCounter[i] < bounds[i][2]) {
						return false;
					}
				}
			}
			return true;
		};
	}

	private Predicate<List<Integer>> buildOddOrEvenFilter(String filterAsString) {
		String[] boundsAsString = filterAsString.replaceAll("\\s+","").replace("odd", "").replace("even", "").split(":")[1].split(",");
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		IntPredicate evenOrOddTester = filterAsString.contains("even") ?
			number -> number % 2 == 0 :
			number -> number % 2 != 0;
		return combo -> {
			int evenOrOddCounter = 0;
			for (Integer number : combo) {
				if(evenOrOddTester.test(number) && ++evenOrOddCounter > bounds[1]) {
					return false;
				}
			}
			return evenOrOddCounter >= bounds[0];
		};
	}

	private Predicate<List<Integer>> buildSameLastDigitFilter(String filterAsString) {
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("sameLastDigit");
		String[] rangeOptions = operationOptions[0].split("->");
		Integer leftRangeBounds = rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[0]) : null;
		Integer rightRangeBounds =  rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[1]) : null;
		String[] options = operationOptions[1].split(":");
		String[] boundsAsString = options[1].split(",");
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		return combo -> {
			int[] counters = new int[10];
			for (Integer number : new TreeSet<>(combo)) {
				if (rangeOptions.length > 1) {
					if (number > rightRangeBounds) {
						break;
					} else if (number < leftRangeBounds) {
						continue;
					}
				}
				counters[(number % 10)]++;
			}
			int maxSameDigitCount = Arrays.stream(counters).summaryStatistics().getMax();
			return maxSameDigitCount >= bounds[0] && maxSameDigitCount <= bounds[1];
		};
	}

	private Predicate<List<Integer>> buildConsecutiveLastDigitFilter(String filterAsString) {
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("consecutiveLastDigit");
		String[] rangeOptions = operationOptions[0].split("->");
		Integer leftRangeBounds = rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[0]) : null;
		Integer rightRangeBounds =  rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[1]) : null;
		String[] options = operationOptions[1].split(":");
		String[] boundsAsString = options[1].split(",");
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		return combo -> {
			Set<Integer> lastDigits = new TreeSet<>();
			for (Integer number : new TreeSet<>(combo)) {
				if (rangeOptions.length > 1) {
					if (number > rightRangeBounds) {
						break;
					} else if (number < leftRangeBounds) {
						continue;
					}
				}
				lastDigits.add(number % 10);
			}
			if (lastDigits.size() >= bounds[0] && lastDigits.size() <= bounds[1]) {
				return true;
			}
			int counter = 0;
			int maxConsecutiveLastDigitCounter = 0;
			Integer previousNumber = null;
			for (int number : lastDigits) {
				if (previousNumber != null && ((number != 0 && previousNumber == number -1) || (number == 0 && previousNumber == 9))) {
					if (counter == 0) {
						counter++;
					}
					if (++counter > maxConsecutiveLastDigitCounter) {
						maxConsecutiveLastDigitCounter = counter;
					}
				} else {
					counter = 0;
				}
				previousNumber = number;
			}
			return maxConsecutiveLastDigitCounter >= bounds[0] && maxConsecutiveLastDigitCounter <= bounds[1];
		};
	}

	String toString(List<Integer> combo) {
		return String.join(
			"\t",
			combo.stream()
		    .map(Object::toString)
		    .collect(Collectors.toList())
		);
	}

}
