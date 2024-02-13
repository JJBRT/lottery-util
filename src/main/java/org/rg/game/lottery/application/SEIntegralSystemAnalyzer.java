package org.rg.game.lottery.application;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.PrimitiveIterator.OfLong;
import java.util.Properties;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.Throwables;
import org.rg.game.core.CollectionUtils;
import org.rg.game.core.ConcurrentUtils;
import org.rg.game.core.IOUtils;
import org.rg.game.core.LogUtils;
import org.rg.game.core.MathUtils;
import org.rg.game.core.NetworkUtils;
import org.rg.game.core.ResourceUtils;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.ComboHandler;
import org.rg.game.lottery.engine.ComboHandler.IterationData;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.Premium;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEPremium;
import org.rg.game.lottery.engine.SEStats;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

public class SEIntegralSystemAnalyzer extends Shared {
	private static BiFunction<String, String, Record> recordLoader;
	private static BiFunction<String, String, Consumer<Record>> recordWriter;

	public static void main(String[] args) throws IOException {
		long startTime = System.currentTimeMillis();
		try {
			String firebaseUrl = Optional.ofNullable(System.getenv().get("integral-system-analysis.firebase.url"))
				.orElseGet(() -> System.getenv().get("INTEGRAL_SYSTEM_ANALYSIS_FIREBASE_URL"));
			if (firebaseUrl == null) {
				throw new NoSuchElementException("Firebase URL not set");
			}
			LogUtils.INSTANCE.info("Database URL " + firebaseUrl);
			InputStream serviceAccount;
			try {
				serviceAccount = new ByteArrayInputStream(
					Optional.ofNullable(System.getenv().get("integral-system-analysis.firebase.credentials"))
					.orElseGet(() -> System.getenv().get("INTEGRAL_SYSTEM_ANALYSIS_FIREBASE_CREDENTIALS")
				).getBytes());
				LogUtils.INSTANCE.info("Credentials loaded from integral-system-analysis.firebase.credentials");
			} catch (Throwable exc) {
				String credentialsFilePath =
					Paths.get(
						Optional.ofNullable(System.getenv().get("integral-system-analysis.firebase.credentials.file"))
							.orElseGet(() -> System.getenv().get("INTEGRAL_SYSTEM_ANALYSIS_FIREBASE_CREDENTIALS_FILE"))
					).normalize().toAbsolutePath().toString();
				serviceAccount =
					new FileInputStream(
						credentialsFilePath
					);
				LogUtils.INSTANCE.info("Credentials loaded from " + credentialsFilePath);
			}
			FirebaseOptions options = FirebaseOptions.builder()
				  .setCredentials(com.google.auth.oauth2.GoogleCredentials.fromStream(serviceAccount))
				  .setDatabaseUrl(firebaseUrl)
				  .build();

			FirebaseApp.initializeApp(options);
			Firestore firestore = FirestoreClient.getFirestore();
			recordLoader = (String key, String basePath) -> {
				DocumentReference recordAsDocumentWrapper = firestore.collection("IntegralSystemStats").document(key);
				ApiFuture<DocumentSnapshot> ap = recordAsDocumentWrapper.get();
				DocumentSnapshot recordAsDocument;
				try {
					recordAsDocument = ap.get();
				} catch (InterruptedException | ExecutionException exc) {
					return Throwables.INSTANCE.throwException(exc);
				}
				return readFromJson((String)recordAsDocument.get("value"));
			};
			recordWriter = (String key, String basePath) -> record -> {
				DocumentReference recordAsDocumentWrapper = firestore.collection("IntegralSystemStats").document(key);
				Map<String, Object> recordAsRawValue = new LinkedHashMap<>();
				recordAsRawValue.put("value", IOUtils.INSTANCE.writeToJSONFormat(record));
				try {
					recordAsDocumentWrapper.set(recordAsRawValue).get();
				} catch (InterruptedException | ExecutionException exc) {
					Throwables.INSTANCE.throwException(exc);
				}
			};
		} catch (NoSuchElementException exc) {
			LogUtils.INSTANCE.info(exc.getMessage());
			setDefaultRecordLoaderAndWriter();
		} catch (Throwable exc) {
			LogUtils.INSTANCE.error(exc, "Unable to connect to Firebase");
			setDefaultRecordLoaderAndWriter();
		}

		String[] configurationFileFolders = ResourceUtils.INSTANCE.pathsFromSystemEnv(
			"working-path.integral-system-analysis.folder",
			"WORKING-PATH_INTEGRAL-SYSTEM-ANALYSIS_FOLDER",
			"resources.integral-system-analysis.folder",
			"RESOURCES_INTEGRAL_SYSTEM_ANALYSIS_FOLDER"
		);
		LogUtils.INSTANCE.info("Set configuration files folder to " + String.join(", ", configurationFileFolders) + "\n");
		List<File> configurationFiles =
			ResourceUtils.INSTANCE.find(
				"se-integral-systems-analysis", "properties",
				configurationFileFolders
			);
		String taskMaxParallel = Optional.ofNullable(System.getenv().get("tasks.max-parallel")).orElseGet(() -> System.getenv().get("TASKS_MAX_PARALLEL"));
		int maxParallelTasks = Optional.ofNullable(taskMaxParallel).map(Integer::valueOf)
				.orElseGet(() -> Math.max((Runtime.getRuntime().availableProcessors() / 2) - 1, 1));
		Collection<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
		boolean onlyShowComputed = false;
		String timeoutRawValue = null;
		for (String arg : args) {
			if (arg != null) {
				if (arg.contains("onlyShowComputed")) {
					onlyShowComputed = true;
					LogUtils.INSTANCE.info("Analysis disabled");
				} else if (arg.contains("timeout")) {
					timeoutRawValue = arg.split("=")[1];
				}
			}
		}
		if (timeoutRawValue == null) {
			timeoutRawValue = Optional.ofNullable(System.getenv().get("timeout"))
				.orElseGet(() -> System.getenv().get("TIMEOUT"));
		}
		if (timeoutRawValue != null) {
			LogUtils.INSTANCE.info("Set timeout to " + timeoutRawValue + " seconds");
			long timeout = Long.valueOf(timeoutRawValue);
			Thread exiter = new Thread(() -> {
				long elapsedTimeFromStart = System.currentTimeMillis() - startTime;
				long effectiveTimeout = (timeout * 1000) - elapsedTimeFromStart;
				if (effectiveTimeout > 0) {
					try {
						Thread.sleep(effectiveTimeout);
					} catch (InterruptedException e) {

					}
				}
				LogUtils.INSTANCE.info("Timeout reached");
				System.exit(0);
			});
			exiter.setDaemon(true);
			exiter.start();
		}
		for (Properties config : ResourceUtils.INSTANCE.toOrderedProperties(configurationFiles)) {
			if (CollectionUtils.INSTANCE.retrieveBoolean(config, "enabled", "false")) {
				Runnable task =
					onlyShowComputed ?
						() ->
							showComputed(config) :
						() ->
							analyze(config);
				if (!onlyShowComputed && CollectionUtils.INSTANCE.retrieveBoolean(config, "async", "false")) {
					ConcurrentUtils.INSTANCE.addTask(futures, task);
				} else {
					task.run();
				}
			}
			ConcurrentUtils.INSTANCE.waitUntil(futures, ft -> ft.size() >= maxParallelTasks);
		}
		futures.forEach(CompletableFuture::join);
		LogUtils.INSTANCE.warn("All activities are finished");

	}

	protected static void setDefaultRecordLoaderAndWriter() {
		recordLoader = (String key, String basePath) -> {
			Record record =
				IOUtils.INSTANCE.load(basePath, key);
			if (record == null) {
				record = readFromJson(
					IOUtils.INSTANCE.fileToString(
						basePath + "/" + key + ".json",
						StandardCharsets.UTF_8
					)
				);
			}
			return record;
		};
		recordWriter = (String key, String basePath) -> record -> {
			IOUtils.INSTANCE.store(key, record, basePath);
			IOUtils.INSTANCE.writeToJSONPrettyFormat(new File(basePath + "/" + key + ".json"), record);
		};
	}

	protected static void showComputed(Properties config) {
		ProcessingContext processingContext = new ProcessingContext(config);
		printData(processingContext.record);
		LogUtils.INSTANCE.info(
			MathUtils.INSTANCE.format(processedSystemsCounter(processingContext.record)) + " of " +
			MathUtils.INSTANCE.format(processingContext.comboHandler.getSize()) +
			" systems have been processed\n"
		);
	}

	protected static void analyze(Properties config) {
		ProcessingContext processingContext = new ProcessingContext(config);
		while (!processingContext.assignedBlocks.isEmpty()) {
			AtomicReference<Block> currentBlockWrapper = new AtomicReference<>();
			AtomicBoolean blockNotAlignedWrapper = new AtomicBoolean(false);
			Supplier<Block> blockSupplier = () -> {
				Block block = currentBlockWrapper.get();
				if (block != null && block.counter != null && block.counter.compareTo(block.end) >= 0) {
					currentBlockWrapper.set(block = null);
				}
				if (block == null) {
					Iterator<Block> blocksIterator =  processingContext.assignedBlocks.iterator();
					while (blocksIterator.hasNext()) {
						block = blocksIterator.next();
						blocksIterator.remove();
						if (block.counter != null && block.counter.compareTo(block.end) >= 0) {
							continue;
						}
						currentBlockWrapper.set(block);
						blockNotAlignedWrapper.set(true);
						LogUtils.INSTANCE.info("Received in assignment " + block);
						break;
					}
				}
				return block;
			};
			processingContext.comboHandler.iterate(iterationData -> {
				Block currentBlock = blockSupplier.get();
				if (currentBlock == null) {
					iterationData.terminateIteration();
					return;
				}
				if (blockNotAlignedWrapper.get()) {
					if (iterationData.getCounter().compareTo(currentBlock.start) < 0 || iterationData.getCounter().compareTo(currentBlock.end) > 0) {
						if (iterationData.getCounter().mod(processingContext.modderForSkipLog).compareTo(BigInteger.ZERO) == 0) {
							LogUtils.INSTANCE.info("Skipped " + MathUtils.INSTANCE.format(iterationData.getCounter()) + " of systems");
						}
						return;
					}
					BigInteger currentBlockCounter = currentBlock.counter;
					if (currentBlockCounter != null) {
						if (currentBlockCounter.compareTo(iterationData.getCounter()) > 0) {
							return;
						}
						if (currentBlockCounter.compareTo(iterationData.getCounter()) == 0) {
							LogUtils.INSTANCE.info(
								"Skipped " + MathUtils.INSTANCE.format(iterationData.getCounter()) + " of systems\n" +
								"Cache succesfully restored, starting from index " + MathUtils.INSTANCE.format(iterationData.getCounter()) + ". " +
								MathUtils.INSTANCE.format(remainedSystems(processingContext.record)) + " systems remained."
							);
							printDataIfChanged(processingContext.record, processingContext.previousLoggedRankWrapper);
							return;
						}
					}
					blockNotAlignedWrapper.set(false);
				}
				currentBlock.counter = iterationData.getCounter();
				List<Integer> combo = iterationData.getCombo();
				Map<Number, Integer> allPremiums = new LinkedHashMap<>();
				for (Number premiumType : processingContext.orderedPremiumsToBeAnalyzed) {
					allPremiums.put(premiumType, 0);
				}
				for (List<Integer> winningComboWithSuperStar : processingContext.allWinningCombos) {
					Map<Number, Integer> premiums = SEPremium.checkIntegral(combo, winningComboWithSuperStar);
					for (Map.Entry<Number, Integer> premiumTypeAndCounter : allPremiums.entrySet()) {
						Number premiumType = premiumTypeAndCounter.getKey();
						Integer premiumCounter = premiums.get(premiumType);
						if (premiumCounter != null) {
							allPremiums.put(premiumType, allPremiums.get(premiumType) + premiumCounter);
						}
					}
				}
				boolean highWinningFound = false;
				for (Map.Entry<Number, Integer> premiumTypeAndCounter : allPremiums.entrySet()) {
					if (premiumTypeAndCounter.getKey().doubleValue() > Premium.TYPE_FIVE.doubleValue() && premiumTypeAndCounter.getValue() > 0) {
						highWinningFound = true;
						break;
					}
				}
				if (highWinningFound) {
					Map.Entry<List<Integer>, Map<Number, Integer>> addedItem = new AbstractMap.SimpleEntry<>(combo, allPremiums);
					boolean addedItemFlag = processingContext.systemsRank.add(addedItem);
					if (processingContext.systemsRank.size() > processingContext.rankSize) {
						Map.Entry<List<Integer>, Map<Number, Integer>> removedItem = processingContext.systemsRank.pollLast();
						if (removedItem != addedItem) {
							//store(basePath, cacheKey, iterationData, systemsRank, cacheRecord, currentBlock, rankSize);
							LogUtils.INSTANCE.info(
								"Replaced data from rank:\n\t" + ComboHandler.toString(removedItem.getKey(), ", ") + ": " + removedItem.getValue() + "\n" +
								"\t\twith\n"+
								"\t" + ComboHandler.toString(addedItem.getKey(), ", ") + ": " + addedItem.getValue()
							);
						}
					} else if (addedItemFlag) {
						//store(basePath, cacheKey, iterationData, systemsRank, cacheRecord, currentBlock, rankSize);
						LogUtils.INSTANCE.info("Added data to rank: " + ComboHandler.toString(combo, ", ") + ": " + allPremiums);
					}
				}
				if (iterationData.getCounter().mod(processingContext.modderForAutoSave).compareTo(BigInteger.ZERO) == 0 || iterationData.getCounter().compareTo(currentBlock.end) == 0) {
					store(processingContext.basePath, processingContext.cacheKey, iterationData, processingContext.systemsRank, processingContext.record, currentBlock, processingContext.rankSize);
					printDataIfChanged(processingContext.record, processingContext.previousLoggedRankWrapper);
					LogUtils.INSTANCE.info(MathUtils.INSTANCE.format(processedSystemsCounter(processingContext.record)) + " of systems have been processed");
	    		}
			});
			if (processingContext.assignedBlocks.isEmpty()) {
				processingContext.assignedBlocks.addAll(retrieveAssignedBlocks(config, processingContext.record));
			}
		}
		printData(processingContext.record);
		//LogUtils.INSTANCE.info(processedSystemsCounterWrapper.get() + " of combinations analyzed");
	}

	protected static Record readFromJson(String recordAsFlatRawValue) {
		if (recordAsFlatRawValue == null) {
			return null;
		}
		Map<String, Object> recordAsRawValue = IOUtils.INSTANCE.readFromJSONFormat(recordAsFlatRawValue, Map.class);
		Collection<Block> blocks = new ArrayList<>();
		for (Map<String, Number> blocksAsRawValue : (Collection<Map<String, Number>>)recordAsRawValue.get("blocks")) {
			blocks.add(
				new Block(
					new BigInteger(blocksAsRawValue.get("start").toString()),
					new BigInteger(blocksAsRawValue.get("end").toString()),
					Optional.ofNullable(
						blocksAsRawValue.get("counter")
					).map(Object::toString).map(BigInteger::new).orElseGet(() -> null)
				)
			);
		}
		Collection<Map.Entry<List<Integer>, Map<Number, Integer>>> data = new ArrayList<>();
		for (Map<String, Map<String, Integer>> comboForResultAsRawValue : (Collection<Map<String, Map<String, Integer>>>) recordAsRawValue.get("data")) {
			Map.Entry<String, Map<String, Integer>> comboForResultAsRawValueEntry = comboForResultAsRawValue.entrySet().iterator().next();
			Map<Number, Integer> premiums = new LinkedHashMap<>();
			for (Map.Entry<String, Integer> premium : comboForResultAsRawValueEntry.getValue().entrySet()) {
				premiums.put(Premium.parseType(premium.getKey()), premium.getValue());
			}
			data.add(
				new AbstractMap.SimpleEntry<>(
					ComboHandler.fromString(comboForResultAsRawValueEntry.getKey().replaceAll("\\[|\\]", "")),
					premiums
				)
			);
		}
		return new Record(blocks, data);
	}

	protected static void chooseAndPrintNextCompetitionSystem(Record cacheRecord, int rankSize) {
		LocalDate nextExtractionDate = SELotteryMatrixGeneratorEngine.DEFAULT_INSTANCE.computeNextExtractionDate(LocalDate.now(), false);
		Map.Entry<LocalDate, Long> seedData = getSEAllStats().getSeedData(nextExtractionDate);
		seedData.getValue();
		Long size = cacheRecord.blocks.stream().reduce((first, second) -> second)
		  .orElse(null).end.longValue();
		Random random = new Random(seedData.getValue());
		OfLong randomizer = random.longs(1L, size + 1).iterator();
		long nextLong = -1;
		while (nextLong > rankSize || nextLong < 0) {
			nextLong = randomizer.nextLong();
		}
		Map.Entry<List<Integer>, Map<Number, Integer>> combo = new ArrayList<>(cacheRecord.data).get(Long.valueOf(nextLong).intValue());
		ComboHandler cH = new ComboHandler(combo.getKey(), 6);
		LogUtils.INSTANCE.info(
			"La combinazione scelta per il concorso " + seedData.getValue() + " del " +
			TimeUtils.defaultLocalDateFormat.format(nextExtractionDate) + " è:\n\t" + ComboHandler.toString(combo.getKey(), ", ") +
			"\nposizionata al " + nextLong + "° posto. Il relativo sistema è:"
		);
		cH.iterate(iterationData -> {
			LogUtils.INSTANCE.info("\t" + ComboHandler.toString(iterationData.getCombo()));
		});
	}

	protected static String buildCacheKey(ComboHandler comboHandler, SEStats sEStats, String premiumsToBeAnalyzed, int rankSize) {
		return "[" + MathUtils.INSTANCE.format(comboHandler.getSize()).replace(".", "_") + "][" + comboHandler.getCombinationSize() + "]" +
				"[" + premiumsToBeAnalyzed.replace(".", "_") + "]" + "[" + rankSize + "]" +
				"[" + TimeUtils.getAlternativeDateFormat().format(sEStats.getStartDate()) + "]" +
				"[" + TimeUtils.getAlternativeDateFormat().format(sEStats.getEndDate()) + "]";
	}

	protected static BigInteger processedSystemsCounter(Record record) {
		BigInteger processed = BigInteger.ZERO;
		for (Block block : record.blocks) {
			if (block.counter != null) {
				processed = processed.add(block.counter.subtract(block.start.subtract(BigInteger.ONE)));
			}
		}
		return processed;
	}

	protected static BigInteger remainedSystems(Record record) {
		BigInteger processedSystemsCounter = processedSystemsCounter(record);
		Block latestBlock = CollectionUtils.INSTANCE.getLastElement(record.blocks);
		return latestBlock.end.subtract(processedSystemsCounter);
	}

	protected static Record prepareCacheRecord(
		String basePath, String cacheKey, ComboHandler cH,
		TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> systemsRank
	){
		Record cacheRecordTemp = recordLoader.apply(cacheKey, basePath);
		if (cacheRecordTemp != null) {
			systemsRank.addAll(cacheRecordTemp.data);
		} else {
			cacheRecordTemp = new Record();
		}
		if (cacheRecordTemp.blocks == null) {
			long blockNumbers = cH.getCombinationSize() * 2;
			BigInteger aHundredMillion = BigInteger.valueOf(100_000_000L);
			if (cH.getSize().compareTo(aHundredMillion) > 0) {
				blockNumbers = cH.getSize().divide(aHundredMillion).longValue();
			}
			cacheRecordTemp.blocks = divide(cH.getSize(), blockNumbers);
		}
		return cacheRecordTemp;
	}

	protected static List<Block> retrieveAssignedBlocks(Properties config, Record cacheRecordTemp) {
		String blockAssignees = config.getProperty("blocks.assegnee");
		Collection<Block> blocks = new LinkedHashSet<>();
		boolean random = false;
		if (blockAssignees != null) {
			String thisHostName = NetworkUtils.INSTANCE.thisHostName();
			for (String blockAssignee : blockAssignees.replaceAll("\\s+","").split(";")) {
				String[] blockAssigneeInfo = blockAssignee.split(":");
				if (blockAssigneeInfo.length > 1 && blockAssigneeInfo[1].contains("random")) {
					random = true;
					blockAssigneeInfo[1] = blockAssigneeInfo[1].replace("random", "").replace("[", "").replace("]", "");
				}
				if (blockAssigneeInfo[0].equalsIgnoreCase(thisHostName) || blockAssigneeInfo[0].equals("all")) {
					if (blockAssigneeInfo[1].isEmpty() || blockAssigneeInfo[1].equals("all")) {
						blocks.addAll(cacheRecordTemp.blocks);
					} else {
						blocks.clear();
						for (String blockIndex : blockAssigneeInfo[1].split(",")) {
							if (blockIndex.equalsIgnoreCase("odd")) {
								blocks.addAll(CollectionUtils.INSTANCE.odd(cacheRecordTemp.blocks));
							} else if (blockIndex.equalsIgnoreCase("even")) {
								blocks.addAll(CollectionUtils.INSTANCE.even(cacheRecordTemp.blocks));
							} else if (blockIndex.contains("/")) {
								String[] subListsInfo = blockIndex.split("/");
								List<List<Block>> subList =
									CollectionUtils.INSTANCE.toSubLists((List<Block>)cacheRecordTemp.blocks,
										Double.valueOf(Math.ceil(((List<Block>)cacheRecordTemp.blocks).size() / Double.valueOf(subListsInfo[1]))).intValue()
									);
								blocks.addAll(subList.get(Integer.valueOf(subListsInfo[0]) - 1));
							} else {
								blocks.add(cacheRecordTemp.getBlock(Integer.valueOf(blockIndex) - 1));
							}
						}
						break;
					}
				} else if (blockAssigneeInfo[0].contains("random")) {
					blocks.addAll(cacheRecordTemp.blocks);
					random = true;
				}
			}
		} else {
			blocks.addAll(cacheRecordTemp.blocks);
		}
		Iterator<Block> blocksIterator = blocks.iterator();
		while (blocksIterator.hasNext()) {
			Block block = blocksIterator.next();
			BigInteger counter = block.counter;
			if (counter != null && counter.compareTo(block.end) == 0) {
				blocksIterator.remove();
			}
		}
		List<Block> toBeProcessed = new ArrayList<>(blocks);
		if (random) {
			Collections.shuffle(toBeProcessed) ;
		}
		return toBeProcessed;
	}

	protected static TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> buildDataCollection(Number[] orderedPremiumsToBeAnalyzed) {
		TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> bestSystems = new TreeSet<>((itemOne, itemTwo) -> {
			if (itemOne != itemTwo) {
				for(Number type : orderedPremiumsToBeAnalyzed) {
					int comparitionResult = itemOne.getValue().getOrDefault(type, 0).compareTo(itemTwo.getValue().getOrDefault(type, 0));
					if (comparitionResult != 0) {
						return comparitionResult * -1;
					}
				}
				for (int i = 0; i < Math.max(itemOne.getKey().size(), itemTwo.getKey().size()); i++) {
					int numberComparition = itemOne.getKey().get(i).compareTo(itemTwo.getKey().get(i));
					if (numberComparition != 0) {
						return numberComparition * -1;
					}
				}
			}
			return 0;
		});
		return bestSystems;
	}

	protected static void printData(
		Record record
	) {
		String currentRank = String.join(
			"\n\t",
			record.data.stream().map(entry ->
				ComboHandler.toString(entry.getKey(), ", ") + ": " + Premium.toString(entry.getValue(), "=", ", ")
			).collect(Collectors.toList())
		);
		String currentLog = "\nBlocks (size: " + record.blocks.size() + ") status:\n" +
			"\t" + String.join(
				"\n\t",
				record.blocks.stream().map(Object::toString).collect(Collectors.toList())
			) + "\n" +
			"Rank (size: " + record.data.size() + "):\n" +
			"\t" + currentRank + "\n"
		;
		LogUtils.INSTANCE.info(currentLog);
	}

	protected static void printDataIfChanged(
		Record record,
		AtomicReference<String> previousLoggedRankWrapper
	) {
		String currentRank = String.join(
			"\n\t",
			record.data.stream().map(entry ->
				ComboHandler.toString(entry.getKey(), ", ") + ": " + Premium.toString(entry.getValue(), "=", ", ")
			).collect(Collectors.toList())
		);
		String currentLog = "\nBlocks (size: " + record.blocks.size() + ") status:\n" +
			"\t" + String.join(
				"\n\t",
				record.blocks.stream().map(Object::toString).collect(Collectors.toList())
			) + "\n" +
			"Rank (size: " + record.data.size() + "):\n" +
			"\t" + currentRank + "\n"
		;
		String previousLoggedRank = previousLoggedRankWrapper.get();
		if (previousLoggedRank == null || !previousLoggedRank.equals(currentRank)) {
			LogUtils.INSTANCE.info(currentLog);
		}
		previousLoggedRankWrapper.set(currentRank);
	}

	protected static void store(
		String basePath,
		String cacheKey,
		Record record,
		Record cacheRecord
	) {
		cacheRecord.data = new ArrayList<>(record.data);
		IOUtils.INSTANCE.store(cacheKey, cacheRecord, basePath);
	}

	private static Record load(String cacheKey, String basePath) {
		return null;
	}

	private static void store(
		String basePath,
		String cacheKey,
		IterationData iterationData,
		TreeSet<Entry<List<Integer>, Map<Number, Integer>>> systemsRank,
		Record toBeCached,
		Block currentBlock,
		int rankSize
	){
		Record cacheRecord = recordLoader.apply(cacheKey, basePath);
		if (cacheRecord != null) {
			systemsRank.addAll(cacheRecord.data);
			List<Block> cachedBlocks = (List<Block>)cacheRecord.blocks;
			for (int i = 0; i < cachedBlocks.size(); i++) {
				Block toBeCachedBlock = ((List<Block>)toBeCached.blocks).get(i);
				if (currentBlock == toBeCachedBlock) {
					continue;
				}
				Block cachedBlock = cachedBlocks.get(i);
				BigInteger cachedBlockCounter = cachedBlock.counter;
				if (cachedBlockCounter != null && (toBeCachedBlock.counter == null || cachedBlockCounter.compareTo(toBeCachedBlock.counter) > 0)) {
					toBeCachedBlock.counter = cachedBlock.counter;
				}
			}
		}
		while (systemsRank.size() > rankSize) {
			systemsRank.pollLast();
		}
		toBeCached.data = new ArrayList<>(systemsRank);
		recordWriter.apply(cacheKey, basePath).accept(toBeCached);
	}

	public static List<Block> divide(BigInteger size, long blockNumber) {
		BigInteger blockSize = size.divide(BigInteger.valueOf(blockNumber));
		BigInteger remainedSize = size.mod(BigInteger.valueOf(blockNumber));
		List<Block> blocks = new ArrayList<>();
		BigInteger blockStart = BigInteger.ONE;
		for (int i = 0; i < blockNumber; i++) {
			BigInteger blockEnd = blockStart.add(blockSize.subtract(BigInteger.ONE));
			blocks.add(new Block(blockStart, blockEnd, null));
			blockStart = blockEnd.add(BigInteger.ONE);
		}
		if (remainedSize.compareTo(BigInteger.ZERO) != 0) {
			blocks.add(new Block(blockStart, blockStart.add(remainedSize.subtract(BigInteger.ONE)), null));
		}
		return blocks;
	}

	public static class Record implements Serializable {

		private static final long serialVersionUID = -5223969149097163659L;

		Record() {}

		Record(Collection<Block> blocks, Collection<Map.Entry<List<Integer>, Map<Number, Integer>>> data) {
			this.blocks = blocks;
			this.data = data;
		}

		@JsonProperty("blocks")
		private Collection<Block> blocks;

		@JsonProperty("data")
		private Collection<Map.Entry<List<Integer>, Map<Number, Integer>>> data;

		public Block getBlock(int index) {
			return ((List<Block>)blocks).get(index);
		}

	}

	public static class Block implements Serializable {

		private static final long serialVersionUID = 1725710713018555234L;

		@JsonProperty("start")
		private BigInteger start;

		@JsonProperty("end")
		private BigInteger end;

		@JsonProperty("counter")
		private BigInteger counter;

		public Block(BigInteger start, BigInteger end, BigInteger counter) {
			this.start = start;
			this.end = end;
			this.counter = counter;
		}

		@Override
		public String toString() {
			return "Block [start=" + MathUtils.INSTANCE.format(start) + ", end=" + MathUtils.INSTANCE.format(end) + ", counter=" + MathUtils.INSTANCE.format(counter) + "]";
		}

	}

	private static class ProcessingContext {
		private List<Block> assignedBlocks;
		private Record record;
		private Integer rankSize;
		private ComboHandler comboHandler;
		private BigInteger modderForSkipLog;
		private AtomicReference<String> previousLoggedRankWrapper;
		private Number[] orderedPremiumsToBeAnalyzed;
		private Collection<List<Integer>> allWinningCombos;
		private TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> systemsRank;
		private BigInteger modderForAutoSave;
		private String basePath;
		private String cacheKey;

		private ProcessingContext(Properties config) {
			String premiumsToBeAnalyzed = config.getProperty(
				"rank.premiums",
				String.join(",", Premium.allTypesListReversed().stream().map(Object::toString).collect(Collectors.toList()))
			).replaceAll("\\s+","");
			orderedPremiumsToBeAnalyzed =
				Arrays.asList(
					premiumsToBeAnalyzed.split(",")
				).stream().map(Premium::parseType).toArray(Number[]::new);
			long combinationSize = Long.valueOf(config.getProperty("combination.components"));
			comboHandler = new ComboHandler(SEStats.NUMBERS, combinationSize);
			modderForSkipLog = BigInteger.valueOf(1_000_000_000);
			modderForAutoSave = new BigInteger(config.getProperty("autosave-every", "1000000"));
			rankSize = Integer.valueOf(config.getProperty("rank.size", "100"));
			SEStats sEStats = SEStats.get(
				config.getProperty("competition.archive.start-date"),
				config.getProperty("competition.archive.end-date")
			);
			allWinningCombos = sEStats.getAllWinningCombosWithJollyAndSuperstar().values();
			LogUtils.INSTANCE.info("All " + combinationSize + " based integral systems size (" + comboHandler.getNumbers().size() + " numbers): " +  MathUtils.INSTANCE.format(comboHandler.getSize()));
			basePath = PersistentStorage.buildWorkingPath("Analisi sistemi integrali");
			cacheKey = buildCacheKey(comboHandler, sEStats, premiumsToBeAnalyzed, rankSize);
			systemsRank = buildDataCollection(orderedPremiumsToBeAnalyzed);
			record = prepareCacheRecord(
				basePath,
				cacheKey,
				comboHandler,
				systemsRank
			);
			assignedBlocks = retrieveAssignedBlocks(config, record);
			previousLoggedRankWrapper = new AtomicReference<>();
			if (record.data != null && !record.data.isEmpty() && record.data.size() >= rankSize) {
				chooseAndPrintNextCompetitionSystem(record, rankSize);
			}
		}
	}

}
