package org.rg.game.lottery.engine;

import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.rg.game.core.CollectionUtils;
import org.rg.game.core.LogUtils;
import org.rg.game.core.NetworkUtils;
import org.rg.game.core.Throwables;
import org.rg.game.core.TimeUtils;

public abstract class LotteryMatrixGeneratorAbstEngine {
	private static final NumberProcessor numberProcessor;

	static {
		numberProcessor = new NumberProcessor();
	}

	Random random;
	protected boolean reportEnabled;
	protected boolean reportDetailEnabled;
	protected Function<Integer, Integer> comboIndexSupplier;
	protected String comboIndexSelectorType;
	protected AtomicInteger comboSequencedIndexSelectorCounter;

	protected DecimalFormat decimalFormat = new DecimalFormat( "#,##0.##" );
	protected DecimalFormat integerFormat = new DecimalFormat( "#,##0" );
	protected DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	protected String extractionArchiveStartDate;
	protected String extractionArchiveForSeedStartDate;
	protected String storageType;
	protected Function<Function<LocalDate, Function<List<Storage>, Integer>>, Function<Function<LocalDate, Consumer<List<Storage>>>, List<Storage>>> executor;
	protected int engineIndex;
	protected Integer avoidMode;
	protected Predicate<List<Integer>> combinationFilter;
	protected ExpressionToPredicateEngine<List<Integer>> combinationFilterPreProcessor;
	public LocalDate extractionDate;


	LotteryMatrixGeneratorAbstEngine() {
		engineIndex = getAllChosenNumbers().size();
		combinationFilterPreProcessor = new ExpressionToPredicateEngine<>();
		setupCombinationFilterPreProcessor();
	}

	public void setup(Properties config) {
		comboSequencedIndexSelectorCounter = new AtomicInteger(0);
		extractionArchiveStartDate = config.getProperty("competition.archive.start-date");
		extractionArchiveForSeedStartDate = config.getProperty("seed-data.start-date");
		comboIndexSelectorType = config.getProperty("combination.selector", "random");
		String extractionDatesAsString = config.getProperty("competition");
		Collection<LocalDate> extractionDates = computeExtractionDates(extractionDatesAsString);
		LogUtils.info(
			"Computing for the following extraction dates:\n\t"+
			String.join(", ",
				extractionDates.stream().map(TimeUtils.defaultLocalDateFormat::format).collect(Collectors.toList())
			)
		);
		storageType = config.getProperty("storage", "memory").replaceAll("\\s+","");
		String combinationFilterRaw = config.getProperty("combination.filter");
		Function<LocalDate, Map<String, Object>> basicDataSupplier = extractionDate -> {
			this.extractionDate = extractionDate;
			if (combinationFilter == null) {
				combinationFilter = CombinationFilterFactory.INSTANCE.parse(
					preProcess(combinationFilterRaw)
				);
			}
			Map<String, Object> data = adjustSeed();
			NumberProcessor.Context numberProcessorContext = new NumberProcessor.Context(
				getNumberGeneratorFactory(), engineIndex, getAllChosenNumbers(), getAllDiscardedNumbers()
			);
			List<Integer> chosenNumbers = numberProcessor.retrieveNumbersToBePlayed(
				numberProcessorContext,
				config.getProperty("numbers", getDefaultNumberRange()),
				extractionDate,
				CollectionUtils.retrieveBoolean(config, "numbers.ordered", "false")
			);
			SEStats.clear();
			data.put("chosenNumbers", chosenNumbers);
			List<Integer> numbersToBePlayed = new ArrayList<>(chosenNumbers);
			data.put(
				"numbersToBeDiscarded",
				numberProcessor.retrieveNumbersToBeExcluded(
					numberProcessorContext,
					config.getProperty("numbers.discard"),
					extractionDate,
					numbersToBePlayed,
					CollectionUtils.retrieveBoolean(config, "numbers.ordered", "false")
				)
			);
			data.put("numbersToBePlayed", numbersToBePlayed);
			return data;
		};
		for (LocalDate extractionDate : extractionDates) {
			Map<String,List<Integer>> numbersToBePlayedForData = null;
			Map<String,List<Integer>> numbersToBeExludedForData = null;
			try {
				numbersToBePlayedForData = getAllChosenNumbers().get(engineIndex);
				numbersToBeExludedForData = getAllDiscardedNumbers().get(engineIndex);
			} catch (IndexOutOfBoundsException exc) {
				getAllChosenNumbers().add(numbersToBePlayedForData = new LinkedHashMap<>());
				getAllDiscardedNumbers().add(numbersToBeExludedForData = new LinkedHashMap<>());
			}
			Map<String, Object> basicData = basicDataSupplier.apply(extractionDate);
			numbersToBePlayedForData.put(
				TimeUtils.defaultLocalDateFormat.format(extractionDate),
				(List<Integer>)basicData.get("chosenNumbers")
			);
			numbersToBeExludedForData.put(
				TimeUtils.defaultLocalDateFormat.format(extractionDate),
				(List<Integer>)basicData.get("numbersToBeDiscarded")
			);
		}
		reportEnabled = CollectionUtils.retrieveBoolean(
			config,
			"report.enabled",
			Optional.ofNullable(System.getenv("report.enabled")).orElseGet(() -> "true")
		);
		reportDetailEnabled = CollectionUtils.retrieveBoolean(
			config,
			"report.detail.enabled",
			Optional.ofNullable(System.getenv("report.detail.enabled")).orElseGet(() -> "false")
		);
		String group = config.getProperty("group") != null ?
			config.getProperty("group").replace("${localhost.name}", NetworkUtils.INSTANCE.thisHostName()):
			null;
		executor = extractionDatePredicate -> storageProcessor -> {
			List<Storage> storages = new ArrayList<>();
			for (LocalDate extractionDate : extractionDates) {
				if (extractionDatePredicate != null) {
					Integer checkResult = extractionDatePredicate.apply(extractionDate).apply(storages);
					if (checkResult == 0 && storageProcessor != null) {
						storageProcessor.apply(extractionDate).accept(storages);
						continue;
					} else if (checkResult == -1) {
						continue;
					}
				}
				Storage storage = generate(
					basicDataSupplier,
					combinationFilterRaw,
					CollectionUtils.retrieveBoolean(config, "combination.filter.test", "true"),
					CollectionUtils.retrieveBoolean(config, "combination.filter.test.fine-info", "true"),
					Integer.valueOf(config.getProperty("combination.components")),
					Optional.ofNullable(config.getProperty("numbers.occurrences")).map(Double::parseDouble).orElseGet(() -> null),
					Optional.ofNullable(config.getProperty("combination.count")).filter(value -> !value.replaceAll("\\s+","").isEmpty()).map(Integer::parseInt).orElseGet(() -> null),
					Integer.valueOf(config.getProperty("combination.choose-random.count", "0")),
					() -> {
						String equilibrateCombinations = config.getProperty("combination.equilibrate");
						if (equilibrateCombinations != null && !equilibrateCombinations.isEmpty() && !equilibrateCombinations.replaceAll("\\s+","").equalsIgnoreCase("random")) {
							return Boolean.parseBoolean(equilibrateCombinations);
						}
						return random.nextBoolean();
					},
					extractionDate,
					CollectionUtils.retrieveBoolean(config, "combination.magic.enabled", "true") ?
						Integer.valueOf(Optional.ofNullable(config.getProperty("combination.magic.min-number")).orElseGet(() -> "1"))
						:null,
						CollectionUtils.retrieveBoolean(config, "combination.magic.enabled", "true") ?
						Integer.valueOf(Optional.ofNullable(config.getProperty("combination.magic.max-number")).orElseGet(() -> "90"))
						:null,
					group,
					config.getProperty("nameSuffix"),
					CollectionUtils.retrieveBoolean(
						config,
							"combination.not-equilibrate.at-least-one-number-among-those-chosen",
							String.valueOf(!"sequence".equals(comboIndexSelectorType))
						),
					Integer.parseInt(
						config.getProperty(
							"overwrite-if-exists",
							"1"
						)
					),
					Integer.parseInt(
						config.getProperty(
							"waiting-someone-for-generation.timeout",
							"300"
						)
					)
				);
				storages.add(
					storage
				);
				if (storageProcessor != null) {
					storageProcessor.apply(extractionDate).accept(storages);
				}
				LogUtils.info("\n\n");
			}
			return storages;
		};
		String avoidModeConfigValue = config.getProperty("avoid", "never");
		if (avoidModeConfigValue.equals("never")) {
			avoidMode = 0;
		} else if (avoidModeConfigValue.equals("if not suggested")) {
			avoidMode = 1;
		} else if (avoidModeConfigValue.equals("if not strongly suggested")) {
			avoidMode = 2;
		}
	}

	public Collection<LocalDate> computeExtractionDates(String extractionDatesAsString) {
		Collection<LocalDate> extractionDates = new LinkedHashSet<>();
		if (extractionDatesAsString == null || extractionDatesAsString.isEmpty()) {
			extractionDates.add(LocalDate.now());
		} else {
			for(String expressionRaw : extractionDatesAsString.replaceAll("\\s+","").split(",")) {
				Collection<LocalDate> extractionDatesForExpression = new LinkedHashSet<>();
				String[] expressions = expressionRaw.split(":");
				Map<String, Object> nestedExpressionsData = new LinkedHashMap<>();
				String expression = ExpressionToPredicateEngine.findAndReplaceNextBracketArea(expressions[0], nestedExpressionsData);
				expression = (String)nestedExpressionsData.getOrDefault(expression, expression);
				String[] dateWithOffset = expression.split("\\+");
				if ("thisWeek".equalsIgnoreCase(dateWithOffset[0])) {
					if (dateWithOffset.length == 2) {
						String[] range = dateWithOffset[1].split("\\*");
						if (range.length == 2) {
							for (int i = 0; i < Integer.parseInt(range[1]); i++) {
								extractionDatesForExpression.addAll(forNextWeek(Integer.parseInt(range[0])+i));
							}
						} else {
							extractionDatesForExpression.addAll(forNextWeek(Integer.valueOf(range[0])));
						}
					} else {
						extractionDatesForExpression.addAll(forThisWeek());
					}
				} else {
					LocalDate extractionDate = "next".equalsIgnoreCase(dateWithOffset[0])?
						computeNextExtractionDate(LocalDate.now(), true) :
						computeNextExtractionDate(LocalDate.parse(dateWithOffset[0],  TimeUtils.defaultLocalDateFormat), false);
					if (dateWithOffset.length == 2) {
						String[] range = dateWithOffset[1].split("\\*");
						for (int i = 0; i < Integer.parseInt(range[0]); i++) {
							extractionDate = extractionDate.plus(getIncrementDays(extractionDate), ChronoUnit.DAYS);
						}
						if (range.length == 2) {
							for (int i = 0; i < Integer.parseInt(range[1]); i++) {
								extractionDatesForExpression.add(extractionDate);
								extractionDate = extractionDate.plus(getIncrementDays(extractionDate), ChronoUnit.DAYS);
							}
						} else {
							extractionDatesForExpression.add(extractionDate);
						}
					} else {
						extractionDatesForExpression.add(extractionDate);
					}
				}
				if (expressions.length > 1) {
					Integer step = Integer.valueOf(expressions[1]);
					List<LocalDate> extractionDatesList = new ArrayList<>(extractionDatesForExpression);
					extractionDatesForExpression = new LinkedHashSet<>();
					for (int i = 0; i < extractionDatesList.size(); i++) {
						if ((i + 1) % step == 0) {
							extractionDatesForExpression.add(extractionDatesList.get(i));
						}
					}
				}
				extractionDates.addAll(extractionDatesForExpression);
			}
		}
		return extractionDates;
	}

	protected abstract String getDefaultNumberRange();

	public String preProcess(String filterAsString) {
		return combinationFilterPreProcessor.preProcess(filterAsString);
	}

	protected void setupCombinationFilterPreProcessor() {
		combinationFilterPreProcessor.addSimpleExpressionPreprocessor(
			expression ->
				expression.split("lessExtCouple|lessExt|mostExtCouple|mostExt").length > 1,
			expression ->
				parameters ->
					processStatsExpression(expression)
		);
		combinationFilterPreProcessor.addSimpleExpressionPreprocessor(
			expression ->
				expression.contains("sum"),
			expression ->
				parameters ->
					processMathExpression(expression)
		);
		combinationFilterPreProcessor.addSimpleExpressionPreprocessor(
			expression ->
				expression.contains("in"),
			expression ->
				parameters ->
					processInExpression(expression)
		);
	}

	protected String processInExpression(String expression) {
		throw new UnsupportedOperationException("Expression is not supported: " + expression);
	}

	protected String processMathExpression(String expression) {
		throw new UnsupportedOperationException("Expression is not supported: " + expression);
	}

	protected String processStatsExpression(String expression) {
		throw new UnsupportedOperationException("Expression is not supported: " + expression);
	}

	protected abstract Map<String, Object> testEffectiveness(String combinationFilterRaw, List<Integer> numbers, boolean fineLog);

	private List<LocalDate> forThisWeek() {
		return forWeekOf(LocalDate.now());
	}

	private List<LocalDate> forNextWeek() {
		return forNextWeek(1);
	}

	private List<LocalDate> forNextWeek(int offset) {
		return forWeekOf(LocalDate.now().plus(offset, ChronoUnit.WEEKS));
	}

	private Storage buildStorage(
		LocalDate extractionDate,
		int combinationCount,
		int numberOfCombos,
		String group,
		String suffix
	) {
		if ("memory".equalsIgnoreCase(storageType)) {
			return new MemoryStorage(extractionDate, combinationCount, numberOfCombos, group, suffix);
		}
		return new PersistentStorage(extractionDate, combinationCount, numberOfCombos, group, suffix);
	}

	public Storage generate(
		Function<LocalDate, Map<String, Object>> basicDataSupplier,
		String combinationFilterRaw,
		boolean testFilter,
		boolean testFilterFineInfo,
		int combinationComponents,
		Double occurrencesNumberRequested,
		Integer numberOfCombosRequested,
		int chooseRandom,
		BooleanSupplier equilibrateFlagSupplier,
		LocalDate extractionDate,
		Integer magicCombinationMinNumber,
		Integer magicCombinationMaxNumber,
		String group,
		String suffix,
		boolean notEquilibrateCombinationAtLeastOneNumberAmongThoseChosen,
		int overwriteIfExists,
		int waitingSomeoneForGenerationTimeout
	) {
		Map<String, Object> data = basicDataSupplier.apply(extractionDate);
		List<Integer> numbers = (List<Integer>)data.get("numbersToBePlayed");
		List<Integer> notSelectedNumbersToBePlayed = new ArrayList<>(numbers);
		Map<String, Object> effectivenessTestResults = null;
		if (combinationFilterRaw != null && testFilter) {
			effectivenessTestResults = testEffectiveness(combinationFilterRaw, numbers, testFilterFineInfo);
		}
		Double ratio;
		Double occurrencesNumber = occurrencesNumberRequested;
		Integer numberOfCombos = numberOfCombosRequested;
		if (numberOfCombos != null) {
			ratio = (combinationComponents * numberOfCombos) / (double)numbers.size();
		} else {
			ratio = occurrencesNumber;
			numberOfCombos = new BigDecimal((ratio * numbers.size()) / combinationComponents).setScale(0, RoundingMode.UP).intValue();
		}
		Storage storageRef = null;
		if (overwriteIfExists < 1 && "filesystem".equalsIgnoreCase(storageType)) {
			storageRef = PersistentStorage.restore(group, Storage.computeName(extractionDate, combinationComponents, numberOfCombos, suffix));
			if (storageRef != null) {
				try {
					long timeout = waitingSomeoneForGenerationTimeout * 1000;
					while (!storageRef.isClosed() && overwriteIfExists == 0 && timeout >= 0) {
						try {
							LogUtils.info("Waiting a maximum of " + timeout/1000 + " seconds for " + storageRef.getName() + " prepared by someone else");
							Thread.sleep(timeout - (timeout -= 1000));
						} catch (InterruptedException e) {
							Throwables.sneakyThrow(e);
						}
					}
					if (storageRef.isClosed()) {
						LogUtils.info(storageRef.getName() + " succesfully restored\n");
						storageRef.printAll();
						return storageRef;
					}
					if (overwriteIfExists == -1) {
						LogUtils.info(storageRef.getName() + " not generated");
						return null;
					}
					if (timeout < 0) {
						LogUtils.info("Waiting for system generation by others ended: " + storageRef.getName() + " will be overwritten");
					}
				} catch (Throwable exc) {
					if (!(exc instanceof FileNotFoundException)) {
						throw exc;
					}
				}
			}
		}
		AtomicInteger discoveredComboCounter = new AtomicInteger(0);
		AtomicLong fromFilterDiscardedComboCounter = new AtomicLong(0);
		ComboHandler comboHandler = new ComboHandler(numbers, combinationComponents);
		try (Storage storage = buildStorage(((LocalDate)data.get("seedStartDate")), combinationComponents, numberOfCombos, group, suffix);) {
			storageRef = storage;
			boolean equilibrate = equilibrateFlagSupplier.getAsBoolean();
			AtomicReference<Iterator<List<Integer>>> randomCombosIteratorWrapper = new AtomicReference<>();
			boolean[] alreadyComputed = new boolean[comboHandler.getSizeAsInt()];
			AtomicLong indexGeneratorCallsCounter = new AtomicLong(0L);
			AtomicInteger uniqueIndexCounter = new AtomicInteger(0);
			Integer ratioAsInt = null;
			//Integer remainder = null;
			try {
				if (equilibrate) {
					Map<Integer, AtomicInteger> occurrences = new LinkedHashMap<>();
					ratioAsInt = ratio.intValue();
					while (storage.size() < numberOfCombos) {
						List<Integer> underRatioNumbers = new ArrayList<>(numbers);
						List<Integer> overRatioNumbers = new ArrayList<>();
						for (Entry<Integer, AtomicInteger> entry : occurrences.entrySet()) {
							if (entry.getValue().get() >= ratioAsInt) {
								underRatioNumbers.remove(entry.getKey());
								if (entry.getValue().get() > ratioAsInt) {
									overRatioNumbers.add(entry.getKey());
								}
							}
						}
						List<Integer> selectedCombo;
						if (underRatioNumbers.size() < combinationComponents) {
							do {
								selectedCombo = getNextCombo(
									randomCombosIteratorWrapper,
									comboHandler,
									alreadyComputed,
									indexGeneratorCallsCounter,
									uniqueIndexCounter,
									discoveredComboCounter,
									fromFilterDiscardedComboCounter
								);
							} while(selectedCombo == null || !selectedCombo.containsAll(underRatioNumbers) || containsOneOf(overRatioNumbers, selectedCombo));
							if (storage.addCombo(selectedCombo)) {
								incrementOccurences(occurrences, selectedCombo);
								notSelectedNumbersToBePlayed.removeAll(selectedCombo);
							}
						} else {
							do {
								selectedCombo = getNextCombo(
									randomCombosIteratorWrapper,
									comboHandler,
									alreadyComputed,
									indexGeneratorCallsCounter,
									uniqueIndexCounter,
									discoveredComboCounter,
									fromFilterDiscardedComboCounter
								);
							} while(selectedCombo == null);
							boolean canBeAdded = true;
							for (Integer number : selectedCombo) {
								AtomicInteger counter = occurrences.computeIfAbsent(number, key -> new AtomicInteger(0));
								if (counter.get() >= ratioAsInt) {
									canBeAdded = false;
									break;
								}
							}
							if (canBeAdded && storage.addCombo(selectedCombo)) {
								incrementOccurences(occurrences, selectedCombo);
								notSelectedNumbersToBePlayed.removeAll(selectedCombo);
							}
						}
					}
				} else {
					List<Integer> selectedCombo;
					for (int i = 0; i < numberOfCombos; i++) {
						do {
							selectedCombo = getNextCombo(
								randomCombosIteratorWrapper,
								comboHandler,
								alreadyComputed,
								indexGeneratorCallsCounter,
								uniqueIndexCounter,
								discoveredComboCounter,
								fromFilterDiscardedComboCounter
							);
						} while(selectedCombo == null);
						discoveredComboCounter.incrementAndGet();
						if (storage.addCombo(selectedCombo)) {
							notSelectedNumbersToBePlayed.removeAll(
								selectedCombo
						    );
						}
					}
					if (notEquilibrateCombinationAtLeastOneNumberAmongThoseChosen) {
						while (!notSelectedNumbersToBePlayed.isEmpty()) {
							do {
								selectedCombo = getNextCombo(
									randomCombosIteratorWrapper,
									comboHandler,
									alreadyComputed,
									indexGeneratorCallsCounter,
									uniqueIndexCounter,
									discoveredComboCounter,
									fromFilterDiscardedComboCounter
								);
							} while(selectedCombo == null);
							discoveredComboCounter.incrementAndGet();
							List<Integer> numbersToBePlayedRemainedBeforeRemoving = new ArrayList<>(notSelectedNumbersToBePlayed);
							if (storage.addCombo(selectedCombo)) {
								notSelectedNumbersToBePlayed.removeAll(selectedCombo);
							} else {
								notSelectedNumbersToBePlayed = numbersToBePlayedRemainedBeforeRemoving;
							}
						}
					}
				}
			} catch (AllRandomNumbersHaveBeenGeneratedException exc) {
				LogUtils.info("\n" + exc.getMessage());
			}
			Integer minOccurrences = storage.getMinOccurence();
			Integer maxOccurrences = storage.getMaxOccurence();
			String systemGeneralInfo =
				"Per il concorso numero " + data.get("seed") + " del " + ((LocalDate)data.get("seedStartDate")).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) + " " +
				"il sistema " + (equilibrate ? "bilanciato " + (minOccurrences.compareTo(maxOccurrences) == 0 ? "perfetto " : "") +
				(minOccurrences.compareTo(maxOccurrences) == 0 ?
					"(occorrenza: " + minOccurrences :
					"(occorrenza minima: " + minOccurrences + ", occorrenza massima: " + maxOccurrences
				)+
				(numberOfCombosRequested == null ? ", richiesta: " + decimalFormat.format(occurrencesNumberRequested) : "") + ") " : "") +
				"e' composto da " + integerFormat.format(storage.size()) + " combinazioni " + "scelte su " + integerFormat.format(comboHandler.getSizeAsInt()) + " totali" +
					(fromFilterDiscardedComboCounter.get() > 0 ? " (scartate dal filtro: " + integerFormat.format(fromFilterDiscardedComboCounter.get()) + ")": "") +
				" e da " + numbers.size() + " numeri:\n" +  NumberProcessor.groupedForTenAsString(numbers, ", ", "\n") +
				(notSelectedNumbersToBePlayed.isEmpty() ? "" : "\nAttenzione: i seguenti numeri non sono stati inclusi nel sistema: " + NumberProcessor.toSimpleString(notSelectedNumbersToBePlayed))
			;
			boolean shouldBePlayed = random.nextBoolean();
			boolean shouldBePlayedAbsolutely = random.nextBoolean() && shouldBePlayed;
			if (magicCombinationMinNumber != null && magicCombinationMaxNumber != null) {
				Set<Integer> randomNumbers = new TreeSet<>();
				//Resettiamo il Random e simuliamo una generazione pulita
				basicDataSupplier.apply(extractionDate);
				moveRandomizerToLast(equilibrateFlagSupplier, discoveredComboCounter, comboHandler);
				Iterator<Integer> randomIntegers = random.ints(magicCombinationMinNumber, magicCombinationMaxNumber + 1).iterator();
				while (randomNumbers.size() < combinationComponents) {
					randomNumbers.add(randomIntegers.next());
				}
				List<Integer> randomCombo = new ArrayList<>();
				for (Integer number : randomNumbers) {
					randomCombo.add(number);
				}
				storage.addLine();
				storage.addLine("Combinazione magica:");
				storage.addUnindexedCombo(randomCombo);
			}
			if (chooseRandom > 0) {
				//Resettiamo il Random e simuliamo una generazione pulita
				basicDataSupplier.apply(extractionDate);
				moveRandomizerToLast(equilibrateFlagSupplier, discoveredComboCounter, comboHandler);
				Iterator<Integer> randomIntegers = random.ints(0, storageRef.size()).iterator();
				storage.addLine();
				storage.addLine("Combinazioni scelte casualmente dal sistema:");
				while (chooseRandom > 0) {
					storage.addUnindexedCombo(storage.getCombo(randomIntegers.next()));
					chooseRandom--;
				}
			}
			storage.addLine(systemGeneralInfo);
			if (reportEnabled) {
				Map<String, Object> report = checkQuality(storageRef);
				if (reportDetailEnabled) {
					storage.addLine("\n");
					storage.addLine(
						(String)report.get("report.detail")
					);
				}
				storage.addLine("\n");
				storage.addLine(
					(String)report.get("report.summary")
				);
			}

			String text = "\n" +Storage.END_LINE_PREFIX + " " + (shouldBePlayedAbsolutely? "assolutamente " : "") + "di " + (shouldBePlayed? "giocare" : "non giocare") + " il sistema per questo concorso";
			storage.addLine(text);
			if (avoidMode == 1 || avoidMode == 2) {
				if ((avoidMode == 1 && shouldBePlayed) || (avoidMode == 2 && shouldBePlayedAbsolutely)) {
					storageRef.printAll();
				} else {
					LogUtils.info(text);
					storageRef.delete();
				}
			} else {
				storageRef.printAll();
			}
		}
		return storageRef;
	}

	protected abstract Map<String, Object> checkQuality(Storage storageRef);

	private List<Integer> getNextCombo(
		AtomicReference<Iterator<List<Integer>>> combosIteratorWrapper,
		ComboHandler comboHandler,
		boolean[] alreadyComputed,
		AtomicLong indexGeneratorCallsCounter,
		AtomicInteger uniqueIndexCounter,
		AtomicInteger discoveredComboCounter,
		AtomicLong fromFilterDiscardedComboCounter
	) {
		Iterator<List<Integer>> combosIterator = combosIteratorWrapper.get();
		List<Integer> selectedCombo;
		if (combosIterator != null && combosIterator.hasNext()) {
			selectedCombo = combosIterator.next();
		} else {
			combosIterator = getNextCombos(
				comboHandler,
				alreadyComputed,
				indexGeneratorCallsCounter,
				uniqueIndexCounter
			).iterator();
			combosIteratorWrapper.set(combosIterator);
			selectedCombo = combosIterator.next();
		}
		discoveredComboCounter.incrementAndGet();
		if (selectedCombo != null) {
			if (combinationFilter.test(selectedCombo)) {
				return selectedCombo;
			} else {
				fromFilterDiscardedComboCounter.incrementAndGet();
			}
		}
		return null;
	}

	private void moveRandomizerToLast(
		BooleanSupplier equilibrateFlagSupplier,
		AtomicInteger effectiveRandomCounter,
		ComboHandler comboHandler
	) {
		equilibrateFlagSupplier.getAsBoolean();
		long browsedCombo = effectiveRandomCounter.get();
		while (browsedCombo-- > 0) {
			random.nextInt(comboHandler.getSizeAsInt());
		}
	}

	private Map<String, Object> resetRandomizer(Supplier<List<Integer>> numberSupplier) {
		Map<String, Object> data = adjustSeed();
		data.put("numbersToBePlayed", numberSupplier.get());
		return data;
	}

	private List<List<Integer>> getNextCombos(
		ComboHandler comboHandler,
		boolean[] alreadyComputed,
		AtomicLong indexGeneratorCallsCounter,
		AtomicInteger uniqueIndexCounter
	) {
		List<Integer> effectiveIndexes = new ArrayList<>();
		Set<Integer> indexesToBeProcessed = new HashSet<>();
		Integer size = comboHandler.getSizeAsInt();
		int randomCollSize = Math.min(size, 10_000_000);
		while (effectiveIndexes.size() < randomCollSize) {
			Integer idx = comboIndexSupplier.apply(size);
			indexGeneratorCallsCounter.incrementAndGet();
			if (!alreadyComputed[idx]) {
				effectiveIndexes.add(idx);
				indexesToBeProcessed.add(idx);
				uniqueIndexCounter.incrementAndGet();
				alreadyComputed[idx] = true;
			} else {
				effectiveIndexes.add(null);
			}
		}
		LogUtils.info(
			formatter.format(LocalDateTime.now()) +
			" - " + integerFormat.format(uniqueIndexCounter.get()) + " unique indexes generated on " +
			integerFormat.format(indexGeneratorCallsCounter.get()) + " calls. " +
			integerFormat.format(indexesToBeProcessed.size()) + " indexes will be processed in the current iteration."
		);
		if (size <= uniqueIndexCounter.get() && indexesToBeProcessed.isEmpty()) {
			throw new AllRandomNumbersHaveBeenGeneratedException();
		}
		Map<Integer, List<Integer>> indexForCombos = comboHandler.find(indexesToBeProcessed, true);
		List<List<Integer>> combos = new ArrayList<>();
		for (Integer index : effectiveIndexes) {
			List<Integer> combo = indexForCombos.get(index);
			combos.add(combo);
		}
		return combos;
	}

	protected boolean containsOneOf(List<Integer> overRatioNumbers, List<Integer> selectedCombo) {
		return overRatioNumbers.stream().anyMatch(element -> selectedCombo.contains(element));
	}

	protected void incrementOccurences(Map<Integer, AtomicInteger> occurrences, List<Integer> selectedCombo) {
		for (Integer number : selectedCombo) {
			AtomicInteger counter = occurrences.computeIfAbsent(number, key -> new AtomicInteger(0));
			counter.incrementAndGet();
		}
	}

	protected static String toString(List<Integer> numbers, int[] indexes) {
		return String.join(
			"\t",
			Arrays.stream(indexes)
			.map(numbers::get)
		    .mapToObj(String::valueOf)
		    .collect(Collectors.toList())
		);
	}

	public Function<Function<LocalDate, Function<List<Storage>, Integer>>, Function<Function<LocalDate, Consumer<List<Storage>>>, List<Storage>>> getExecutor() {
		return executor;
	}

	//competition.archive.start-date
	public String getExtractionArchiveStartDate() {
		return extractionArchiveStartDate != null ? extractionArchiveStartDate : getDefaultExtractionArchiveStartDate();
	}

	//seed-data.start-date
	public String getExtractionArchiveForSeedStartDate() {
		return extractionArchiveForSeedStartDate != null ? extractionArchiveForSeedStartDate : getDefaultExtractionArchiveForSeedStartDate();
	}

	void buildComboIndexSupplier() {
		comboIndexSupplier = comboIndexSelectorType.equals("random") ?
			random::nextInt :
			comboIndexSelectorType.equals("sequence") ?
				this::nextSequencedIndex
				: null;
	}

	private Integer nextSequencedIndex(Integer size) {
		Integer index = comboSequencedIndexSelectorCounter.getAndIncrement();
		if (index >= size) {
			comboSequencedIndexSelectorCounter = new AtomicInteger(0);
			return nextSequencedIndex(size);
		}
		return index;
	}

	public abstract String getDefaultExtractionArchiveStartDate();

	protected abstract List<Map<String,List<Integer>>> getAllChosenNumbers();

	protected abstract List<Map<String,List<Integer>>> getAllDiscardedNumbers();

	protected abstract List<LocalDate> forWeekOf(LocalDate dayOfWeek);

	public abstract Map<String, Object> adjustSeed();

	public abstract LocalDate computeNextExtractionDate(LocalDate startDate, boolean incrementIfExpired);

	protected abstract int getIncrementDays(LocalDate startDate);

	protected abstract Function<String, Function<Integer, Function<Integer, Iterator<Integer>>>> getNumberGeneratorFactory();

	public abstract String getDefaultExtractionArchiveForSeedStartDate();

}

class AllRandomNumbersHaveBeenGeneratedException extends RuntimeException {

	private static final long serialVersionUID = 7009851378700603746L;

	public AllRandomNumbersHaveBeenGeneratedException() {
		super("All random numbers have been generated");
	}

}