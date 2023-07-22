package org.rg.game.lottery.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rg.game.core.LogUtils;
import org.rg.game.core.MathUtils;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SEStats;

public class SubscriptionExpirationDateUpdater extends Shared {
	static DateTimeFormatter datePattern = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	static List<Map.Entry<List<String>, Integer>> updateInfos = Arrays.asList(
		//addUpdateInfo(computeIncrementationOfWeeks(12), "Tomarelli Gianluca")
		//addUpdateInfo(computeIncrementationOfDays(1), "all")
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Barella Roberta")
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Bellacanzone Emanuele"),
		//addUpdateInfo(computeIncrementationOfWeeks(10), "Ingegneri Giuseppe")
		//addUpdateInfo(computeIncrementationOfWeeks(10), "Berni Riccardo"),
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Berni Valentina")
		addUpdateInfo(computeIncrementationOfWeeks(1), "Corinti Massimo")
		//addUpdateInfo(computeIncrementationOfWeeks(6), "Dante Marco")
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Fusi Francesco")
		//addUpdateInfo(computeIncrementationOfWeeks(1), "Pistella Maria Anna")
		/*addUpdateInfo(computeIncrementationOfWeeks(3),
			"Carrazza Alessandro",
			"Coletta Antonello",
			"Coletta Giuseppe",
			"Liberati Claudio"
		)*/
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Oroni Paola")
		//addUpdateInfo(computeIncrementationOfWeeks(10), "Perelli Rodolfo")
		//addUpdateInfo(computeIncrementationOfWeeks(60), "Porta Danilo")
		//addUpdateInfo(computeIncrementationOfWeeks(20), "Tondini Andrea")
		/*addUpdateInfo(computeIncrementationOfWeeks(-1),
			"Berni Valentina",
			"Carrazza Alessandro",
			"Dante Marco",
			"Ingegneri Giuseppe",
			"Liberati Claudio",
			"Pistella Maria Anna"
		)*/
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Pistella Federica")
	);

	public static void main(String[] args) {
		if (args.length > 0) {
			updateInfos = new ArrayList<>();
			for (String updateInfoRaw : args[0].split(";")) {
				String[] updateInfo = updateInfoRaw.split(",");
				updateInfos.add(
					addUpdateInfo(parseIncrementation(updateInfo[1]).get(), updateInfo[0])
				);
			}
		}
		String destFileAbsolutePath = PersistentStorage.buildWorkingPath() + "\\Abbonamenti e altre informazioni.xlsx";
		File srcFile = new File(destFileAbsolutePath);
		File history = new File(srcFile.getParentFile() + "\\Storico abbonamenti");
		history.mkdirs();
		File backupFile = new File(history.getAbsolutePath() + "\\[" + datePattern.format(LocalDateTime.now()) + "] - " + srcFile.getName());
		srcFile.renameTo(backupFile);
		try (InputStream srcFileInputStream = new FileInputStream(backupFile); Workbook workbook = new XSSFWorkbook(srcFileInputStream);) {
			Sheet sheet = workbook.getSheet("Abbonamenti");
			int nameColumnIndex = getCellIndex(sheet, "Nominativo");
			int expiryColumnIndex = getCellIndex(sheet, "Scadenza");
			Iterator<Row> rowIterator = sheet.rowIterator();
			rowIterator.next();
			LogUtils.INSTANCE.info("Aggiornamento data scadenza abbonamento:\n");
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				String clientName = null;
				for (Map.Entry<List<String>, Integer> updateInfo : updateInfos) {
					if (updateInfo.getValue() == 0) {
						continue;
					}
					for (String name : updateInfo.getKey()) {
						if (!name.equalsIgnoreCase("all")) {
							Cell cell = row.getCell(nameColumnIndex);
							if (cell == null ||
								(clientName = cell.getStringCellValue()) == null ||
								clientName.isEmpty() ||
								!clientName.equalsIgnoreCase(name)
							) {
								continue;
							}
						}
						Cell expiryCell = row.getCell(expiryColumnIndex);
						if (expiryCell != null) {
							Date expiryDate = expiryCell.getDateCellValue();
							if (expiryDate != null) {
								LocalDate expiryLocalDate = TimeUtils.toLocalDate(expiryDate);
								LocalDate startExpiryDate = expiryLocalDate;
								if (TimeUtils.today().compareTo(expiryLocalDate) > 0) {
									if (name.equalsIgnoreCase("all")) {
										continue;
									}
									expiryLocalDate = TimeUtils.today();
								}
								if (updateInfo.getValue() > 0) {
									for (int i = 0; i < updateInfo.getValue(); i++) {
										expiryLocalDate = expiryLocalDate.plus(SEStats.computeDaysToNextExtractionDate(expiryLocalDate), ChronoUnit.DAYS);
									}
								} else {
									for (int i = updateInfo.getValue(); i < 0; i++) {
										expiryLocalDate = expiryLocalDate.plus(SEStats.computeDaysFromPreviousExtractionDate(expiryLocalDate), ChronoUnit.DAYS);
									}
								}
								boolean expireSoon = expiryLocalDate.minus(7, ChronoUnit.DAYS).compareTo(TimeUtils.today()) <= 0;
								LogUtils.INSTANCE.info(
									(expireSoon ? "*" : "") + row.getCell(nameColumnIndex).getStringCellValue() + (expireSoon ? "*" : "") +" da " + startExpiryDate.format(TimeUtils.defaultLocalDateFormat) +
									" a " + (expireSoon ? "*" : "") +expiryLocalDate.format(TimeUtils.defaultLocalDateFormat) + (expireSoon ? "*" : "")
								);
								expiryCell.setCellValue(Date.from(expiryLocalDate.atStartOfDay().atZone(ZoneId.of(TimeUtils.DEFAULT_TIME_ZONE)).toInstant()));
							}
						}
					}
				}
			}
			try (FileOutputStream outputStream = new FileOutputStream(destFileAbsolutePath)) {
				XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
				workbook.write(outputStream);
			}
			LogUtils.INSTANCE.info();
			rowIterator = sheet.rowIterator();
			rowIterator.next();
			double extractionCost = workbook.getSheet("Informazioni varie").getRow(3).getCell(1).getNumericCellValue() / 3;
			double total = 0;
			int displaySize = 70;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				String name = row.getCell(0).getStringCellValue();
				if (name.equals("Gentili Roberto")) {
					continue;
				}
				Cell expiryCell = row.getCell(expiryColumnIndex);
				Date expiryDate = expiryCell.getDateCellValue();
				if (expiryDate != null) {
					LocalDate expiryLocalDate = TimeUtils.toLocalDate(expiryDate);
					LocalDate today = TimeUtils.today();
					if (today.compareTo(expiryLocalDate) <= 0) {
						int extractionDateCounter = 0;
						while ((today = today.plus(SEStats.computeDaysToNextExtractionDate(today), ChronoUnit.DAYS)).compareTo(expiryLocalDate) <= 0) {
							extractionDateCounter++;
						}
						String label = name + " " + extractionDateCounter + " estrazioni rimaste. Importo credito: ";
						LogUtils.INSTANCE.info(label + rightAlignedString(MathUtils.INSTANCE.decimalFormat.format(extractionCost * extractionDateCounter), displaySize - label.length()) + "€");
						total += extractionCost * extractionDateCounter;
					}
				}
			}
			LogUtils.INSTANCE.info();
			String label = "Importo debito: ";
			LogUtils.INSTANCE.info(label + rightAlignedString(MathUtils.INSTANCE.decimalFormat.format(total), displaySize - label.length()) + "€");
		} catch (Throwable exc) {
			LogUtils.INSTANCE.error(exc);
		}
	}

	private static Supplier<Integer> parseIncrementation(String value) {
		if (value.charAt(value.length() - 1) == 'D' || value.charAt(value.length() - 1) == 'd') {
			return () -> computeIncrementationOfDays(Integer.valueOf(value.split("d|D")[0]));
		} else if (value.charAt(value.length() - 1) == 'W' || value.charAt(value.length() - 1) == 'w') {
			return () -> computeIncrementationOfWeeks(Integer.valueOf(value.split("w|W")[0]));
		}
		throw new IllegalArgumentException("Unvalid incrementation type: " + value.charAt(value.length() - 1));
	}

	static SimpleEntry<List<String>, Integer> addUpdateInfo(Integer incrementation, String... names) {
		return new AbstractMap.SimpleEntry<>(
			namesToListOfNames(names), computeIncrementation(incrementation, 0)
		);
	}

	protected static List<String> namesToListOfNames(String[] names) {
		List<String> listOfNames = new ArrayList<>();
		for (String name : names) {
			listOfNames.add(name.replace("-", " ").replace("_", " "));
		}
		return listOfNames;
	}

	static Integer computeIncrementationOfDays(int days) {
		return computeIncrementation(days, 0);
	}

	static Integer computeIncrementationOfWeeks(int weeks) {
		return computeIncrementation(0, weeks);
	}

	static Integer computeIncrementation(int days, int weeks) {
		return days + (weeks * 3);
	}
}
