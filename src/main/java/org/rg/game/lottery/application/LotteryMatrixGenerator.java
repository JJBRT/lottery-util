package org.rg.game.lottery.application;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.rg.game.core.CollectionUtils;
import org.rg.game.core.LogUtils;
import org.rg.game.core.ResourceUtils;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.MDLotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;



public class LotteryMatrixGenerator {

	public static void main(String[] args) throws IOException {
		Collection<CompletableFuture<Void>> futures = new ArrayList<>();
		execute("se", futures);
		execute("md", futures);
		futures.stream().forEach(CompletableFuture::join);
	}

	private static <L extends LotteryMatrixGeneratorAbstEngine> void execute(
		String configFilePrefix,
		Collection<CompletableFuture<Void>> futures
	) throws IOException {
		Supplier<LotteryMatrixGeneratorAbstEngine> engineSupplier =
			configFilePrefix.equals("se") ? SELotteryMatrixGeneratorEngine::new :
				configFilePrefix.equals("md") ? MDLotteryMatrixGeneratorEngine::new : null;
		String[] configurationFileFolders = ResourceUtils.INSTANCE.pathsFromSystemEnv(
			"working-path.generations.folder",
			"resources.generations.folder"
		);
		LogUtils.info("Set configuration files folder to " + String.join(", ", configurationFileFolders) + "\n");
		List<File> configurationFiles =
			ResourceUtils.INSTANCE.find(
				configFilePrefix + "-matrix-generator", "properties",
				configurationFileFolders
			);
		List<Properties> configurations = new ArrayList<>();
		for (Properties config : ResourceUtils.INSTANCE.toOrderedProperties(configurationFiles)) {
			String simulationDates = config.getProperty("simulation.dates");
			if (CollectionUtils.retrieveBoolean(config, "enabled", "false")) {
				configurations.add(config);
			}
		}
		for (Properties configuration : configurations) {
			process(futures, engineSupplier, configuration);
		}
	}

	protected static void process(
		Collection<CompletableFuture<Void>> futures,
		Supplier<LotteryMatrixGeneratorAbstEngine> engineSupplier,
		Properties configuration
	) {
		LogUtils.info(
			"Processing file '" + configuration.getProperty("file.name") + "' located in '" + configuration.getProperty("file.parent.absolutePath") + "'"
		);
		String info = configuration.getProperty("info");
		if (info != null) {
			LogUtils.info(info);
		}
		LotteryMatrixGeneratorAbstEngine engine = engineSupplier.get();
		configuration.setProperty("nameSuffix", configuration.getProperty("file.name")
			.replace("." + configuration.getProperty("file.extension"), ""));
		engine.setup(configuration, true);
		if (CollectionUtils.retrieveBoolean(configuration, "async", "false")) {
			futures.add(CompletableFuture.runAsync(() -> engine.getExecutor().apply(null).apply(null)));
		} else {
			engine.getExecutor().apply(null).apply(null);
		}
	}

}
