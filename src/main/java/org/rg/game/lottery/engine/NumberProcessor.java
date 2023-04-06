package org.rg.game.lottery.engine;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class NumberProcessor {

	public static final String EXCLUSIVE_KEY = "excl";
	public static final String INCLUSIVE_KEY = "in";
	public static final String PREVIOUS_KEY = "prev";
	public static final String RANDOM_KEY = "rand";
	public static final String MOST_EXTRACTED_KEY = "mostExt";
	public static final String MOST_EXTRACTED_COUPLE_KEY = "mostExtCouple";
	public static final String PREVIOUS_SYSTEM_KEY = PREVIOUS_KEY + "Sys";
	public static final String SKIP_KEY = "skip";


	protected DateTimeFormatter simpleDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	public List<Integer> retrieveNumbersToBePlayed(
		Context context,
		String numbersAsString,
		LocalDate extractionDate,
		boolean sorted
	) {
		if (numbersAsString == null || numbersAsString.isEmpty()) {
			numbersAsString = "1 -> 90";
		}
		return retrieveNumbers(
			context,
			numbersAsString, extractionDate, new ArrayList<>(),
			numberToBeIncluded -> numberToBeExcluded -> number ->
				(numberToBeIncluded.isEmpty() || numberToBeIncluded.contains(number)) && !numberToBeExcluded.contains(number),
			sorted
		);
	}

	public List<Integer> retrieveNumbersToBeExcluded(
		Context context,
		String numbersAsString,
		LocalDate extractionDate,
		List<Integer> numbersToBePlayed,
		boolean sorted
	) {
		if (numbersAsString == null || numbersAsString.isEmpty()) {
			return new ArrayList<>();
		}
		List<Integer> numbersToBeExcluded =  retrieveNumbersToBePlayed(context, numbersAsString, extractionDate, sorted);
		for(Integer number : numbersToBeExcluded) {
			numbersToBePlayed.remove(number);
		}
		return numbersToBeExcluded;
	}

	private List<Integer> retrieveNumbers(
		Context context,
		String numbersAsString,
		LocalDate extractionDate,
		List<Integer> collectedNumbers,
		Function<List<Integer>, Function<List<Integer>, Predicate<Integer>>> numbersToBeIncludedPredicate,
		boolean sorted
	) {
		for (String numberAsString : numbersAsString.replaceAll("\\s+","").split(",")) {
			String[] rangeValues = numberAsString.split("->");
			if (rangeValues.length == 2) {
				String[] options = rangeValues[1].split(RANDOM_KEY + "|" + MOST_EXTRACTED_COUPLE_KEY + "|" + MOST_EXTRACTED_KEY);
				Integer leftBound = Integer.parseInt(rangeValues[0]);
				Integer rightBound = null;
				if (options.length == 2) {
					int numberGeneratorType =
						rangeValues[1].contains(RANDOM_KEY) ? 3 :
							rangeValues[1].contains(MOST_EXTRACTED_COUPLE_KEY) ? 2 :
								rangeValues[1].contains(MOST_EXTRACTED_KEY) ? 1 :
						-1;
					rangeValues[1] = options[0];
					rightBound = Integer.parseInt(rangeValues[1]);
					List<Integer> numberToBeIncluded = new ArrayList<>();
					Integer numbersToBeGenerated = null;
					if (options[1].contains(INCLUSIVE_KEY)) {
						String[] inclusionOptions = options[1].split(INCLUSIVE_KEY);
						numbersToBeGenerated = inclusionOptions[1].equalsIgnoreCase("all") ?
								(rightBound + 1) - leftBound :
								Integer.parseInt(inclusionOptions[0]);
						for (String numberToBeIncludedAsString : inclusionOptions[1].substring(0, inclusionOptions[1].indexOf(EXCLUSIVE_KEY) > -1?inclusionOptions[1].indexOf(EXCLUSIVE_KEY):inclusionOptions[1].length()).split("-")) {
							if (numberToBeIncludedAsString.contains(PREVIOUS_KEY)) {
								numberToBeIncluded.addAll(getPreviousNumbers(context,numberToBeIncludedAsString, collectedNumbers, extractionDate));
							} else {
								numberToBeIncluded.add(Integer.parseInt(numberToBeIncludedAsString));
							}
						}
					}
					List<Integer> numberToBeExcluded = new ArrayList<>();
					if (options[1].contains(EXCLUSIVE_KEY)) {
						String[] exclusionsOptions = options[1].split(EXCLUSIVE_KEY);
						if (numbersToBeGenerated == null) {
							numbersToBeGenerated = exclusionsOptions[0].equalsIgnoreCase("all") ?
									(rightBound + 1) - leftBound :
									Integer.parseInt(exclusionsOptions[0]);
						}
						for (String numberToBeExludedAsString : exclusionsOptions[1].split("-")) {
							if (numberToBeExludedAsString.contains(PREVIOUS_KEY)) {
								List<Integer> prevChosenNumbers = getPreviousNumbers(context, numberToBeExludedAsString, collectedNumbers, extractionDate);
								numberToBeExcluded.addAll(prevChosenNumbers);
								if (exclusionsOptions[0].equalsIgnoreCase("all")) {
									numbersToBeGenerated = numbersToBeGenerated - countNumbersInRange(prevChosenNumbers, leftBound, rightBound);
								}
							} else {
								numberToBeExcluded.add(Integer.parseInt(numberToBeExludedAsString));
								if (exclusionsOptions[0].equalsIgnoreCase("all")) {
									numbersToBeGenerated--;
								}
							}
						}
					}
					int skip = 0;
					if (options[1].contains(SKIP_KEY)) {
						String[] skipOptions = options[1].split(SKIP_KEY);
						skip = Integer.parseInt(skipOptions[1]);
						if (numbersToBeGenerated == null) {
							numbersToBeGenerated = skipOptions[0].equalsIgnoreCase("all") ?
									(rightBound + 1) - leftBound :
									Integer.parseInt(skipOptions[0]);
						}
					}
					List<Integer> generatedNumbers = new ArrayList<>();
					Iterator<Integer> numberGenerator = context.numberGeneratorFactory.apply(numberGeneratorType).apply(leftBound).apply(rightBound);
					if (skip > 0) {
						while (skip-- > 0) {
							numberGenerator.next();
						}
					}
					if (numbersToBeGenerated == null) {
						numbersToBeGenerated = options[1].equalsIgnoreCase("all") ?
							(rightBound + 1) - leftBound :
							Integer.parseInt(options[1]);
					}
					while (generatedNumbers.size() < numbersToBeGenerated) {
						Integer generatedNumber = numberGenerator.next();
						if (numbersToBeIncludedPredicate.apply(numberToBeIncluded).apply(numberToBeExcluded).test(generatedNumber) && !generatedNumbers.contains(generatedNumber)) {
							generatedNumbers.add(generatedNumber);
						}
					}
					collectedNumbers.addAll(generatedNumbers);
				} else {
					List<Integer> numberToBeIncluded = new ArrayList<>();
					if (options[0].contains(INCLUSIVE_KEY)) {
						String[] inclusionOptions = options[0].split(INCLUSIVE_KEY);
						rightBound = Integer.parseInt(rangeValues[1] = inclusionOptions[0]);
						for (String numberToBeIncludedAsString : inclusionOptions[1].substring(0, inclusionOptions[1].indexOf(EXCLUSIVE_KEY) > -1?inclusionOptions[1].indexOf(EXCLUSIVE_KEY):inclusionOptions[1].length()).split("-")) {
							if (numberToBeIncludedAsString.contains(PREVIOUS_KEY)) {
								numberToBeIncluded.addAll(getPreviousNumbers(context, numberToBeIncludedAsString, collectedNumbers, extractionDate));
							} else {
								numberToBeIncluded.add(Integer.parseInt(numberToBeIncludedAsString));
							}
						}
					}
					List<Integer> numberToBeExcluded = new ArrayList<>();
					if (options[0].contains(EXCLUSIVE_KEY)) {
						String[] exclusionsOptions = options[0].split(EXCLUSIVE_KEY);
						rightBound = Integer.parseInt(rangeValues[1] = exclusionsOptions[0]);
						for (String numberToBeExludedAsString : exclusionsOptions[1].split("-")) {
							if (numberToBeExludedAsString.contains(PREVIOUS_KEY)) {
								List<Integer> prevChosenNumbers = getPreviousNumbers(context, numberToBeExludedAsString, collectedNumbers, extractionDate);
								numberToBeExcluded.addAll(prevChosenNumbers);
							} else {
								numberToBeExcluded.add(Integer.parseInt(numberToBeExludedAsString));
							}
						}
					}
					int skip = 0;
					if (options[0].contains(SKIP_KEY)) {
						String[] skipOptions = options[0].split(SKIP_KEY);
						skip = Integer.parseInt(skipOptions[1]);
					}
					if (rightBound == null) {
						rightBound = Integer.parseInt(rangeValues[1]);
					}
					List<Integer> sequence = new ArrayList<>();
					for (int i = leftBound; i <= rightBound; i++) {
						if (skip > 0) {
							skip--;
							continue;
						}
						if (numbersToBeIncludedPredicate.apply(numberToBeIncluded).apply(numberToBeExcluded).test(i)) {
							sequence.add(i);
						}
					}
					collectedNumbers.addAll(sequence);
				}
			} else {
				if (rangeValues[0].contains(PREVIOUS_SYSTEM_KEY)) {
					List<Integer> numberToBeExcluded = new ArrayList<>();
					if (rangeValues[0].contains(EXCLUSIVE_KEY)) {
						String[] exclusionsOptions = rangeValues[0].split(EXCLUSIVE_KEY);
						rangeValues[0] = exclusionsOptions[0];
						for (String numberToBeExludedAsString : exclusionsOptions[1].split("-")) {
							if (numberToBeExludedAsString.contains(PREVIOUS_KEY)) {
								List<Integer> prevChosenNumbers = getPreviousNumbers(context, numberToBeExludedAsString, collectedNumbers, extractionDate);
								numberToBeExcluded.addAll(prevChosenNumbers);
							} else {
								numberToBeExcluded.add(Integer.parseInt(numberToBeExludedAsString));
							}
						}
					}
					int skip = 0;
					if (rangeValues[0].contains(SKIP_KEY)) {
						String[] skipOptions = rangeValues[0].split(SKIP_KEY);
						skip = Integer.parseInt(skipOptions[1]);
					}
					for (Integer number : getPreviousNumbers(context, rangeValues[0], collectedNumbers, extractionDate)) {
						if (skip > 0) {
							skip--;
							continue;
						}
						if (numbersToBeIncludedPredicate.apply(new ArrayList<>()).apply(numberToBeExcluded).test(number)) {
							collectedNumbers.add(number);
						}
					}
				} else {
					collectedNumbers.add(Integer.valueOf(rangeValues[0]));
				}
			}
		}
		if (sorted) {
			Collections.sort(collectedNumbers);
		}
		return collectedNumbers;
	}

	private Integer countNumbersInRange(List<Integer> prevChosenNumbers, Integer leftBound, Integer rightBound) {
		int count = 0;
		for (Integer number : prevChosenNumbers) {
			if (number >= leftBound && number <= rightBound) {
				count++;
			}
		}
		return count;
	}

	private List<Integer> getPreviousNumbers(Context context, String exclusionsOptions, List<Integer> collector, LocalDate extractionDate) {
		return exclusionsOptions.startsWith(PREVIOUS_SYSTEM_KEY) ?
				exclusionsOptions.contains("discard") ?
					getAllExcludedNumbers(context, exclusionsOptions.split("\\/"), extractionDate) :
					getAllChosenNumbers(context, exclusionsOptions.split("\\/"), extractionDate) :
			new ArrayList<>(collector);
	}

	private List<Integer> getAllChosenNumbers(Context context, String[] numbersAsString, LocalDate extractionDate) {
		if (numbersAsString.length == 1) {
			return context.allChosenNumbers.get(context.elaborationIndex - 1).get(simpleDateFormatter.format(extractionDate));
		}
		List<Integer> chosenNumbers = new ArrayList<>();
		for (int i = 1; i < numbersAsString.length; i++) {
			chosenNumbers.addAll(context.allChosenNumbers.get(context.elaborationIndex - Integer.valueOf(numbersAsString[i])).get(simpleDateFormatter.format(extractionDate)));
		}
		return chosenNumbers;
	}

	private List<Integer> getAllExcludedNumbers(Context context, String[] numbersAsString, LocalDate extractionDate) {
		if (numbersAsString.length == 1) {
			return context.allDiscardedNumbers.get(context.elaborationIndex - 1).get(simpleDateFormatter.format(extractionDate));
		}
		List<Integer> chosenNumbers = new ArrayList<>();
		for (int i = 1; i < numbersAsString.length; i++) {
			chosenNumbers.addAll(context.allDiscardedNumbers.get(context.elaborationIndex - Integer.valueOf(numbersAsString[i])).get(simpleDateFormatter.format(extractionDate)));
		}
		return chosenNumbers;
	}

	static class Context {
		final Integer elaborationIndex;
		final List<Map<String,List<Integer>>> allChosenNumbers;
		final List<Map<String,List<Integer>>> allDiscardedNumbers;
		final Function<Integer, Function<Integer, Function<Integer, Iterator<Integer>>>> numberGeneratorFactory;

		public Context(
			Function<Integer, Function<Integer, Function<Integer, Iterator<Integer>>>> numberGeneratorFactory,
			Integer elaborationIndex,
			List<Map<String, List<Integer>>> allChosenNumbers,
			List<Map<String, List<Integer>>> allDiscardedNumbers
		) {
			this.numberGeneratorFactory = numberGeneratorFactory;
			this.elaborationIndex = elaborationIndex;
			this.allChosenNumbers = allChosenNumbers;
			this.allDiscardedNumbers = allDiscardedNumbers;
		}

	}

}