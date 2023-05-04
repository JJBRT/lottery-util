package org.rg.game.lottery.application;

import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.ComboHandler;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;

class Shared {

	static DecimalFormat integerFormat = new DecimalFormat( "#,##0" );
	static String sEStatsDefaultDate = System.getenv("competition.archive.start-date") != null ?
		System.getenv("competition.archive.start-date"):
		new SELotteryMatrixGeneratorEngine().getExtractionArchiveStartDate();

	static String capitalizeFirstCharacter(String value) {
		return Character.toString(value.charAt(0)).toUpperCase()
		+ value.substring(1, value.length());
	}

	static LocalDate convert(String dateAsString) {
		if (dateAsString.equals("today")) {
			return LocalDateTime.now(ZoneId.of(TimeUtils.DEFAULT_TIME_ZONE)).toLocalDate();
		}
		return new SELotteryMatrixGeneratorEngine().computeExtractionDates(dateAsString).iterator().next();
	}

	static String getMonth(String date) {
		return getMonth(LocalDate.parse(date, TimeUtils.defaultLocalDateFormat));
	}

	static String getMonth(LocalDate date) {
		return capitalizeFirstCharacter(
			date.getMonth().getDisplayName(TextStyle.FULL, Locale.ITALY)
		);
	}

	static int getCellIndex(Sheet sheet, Date localDate) {
		return getCellIndex(sheet, 0, localDate);
	}

	static int getCellIndex(Sheet sheet, int headerIndex, Date date) {
		Row header = sheet.getRow(headerIndex);
		Iterator<Cell> cellIterator = header.cellIterator();
		while (cellIterator.hasNext()) {
			Cell cell = cellIterator.next();
			if (CellType.NUMERIC.equals(cell.getCellType()) && date.compareTo(cell.getDateCellValue()) == 0 ) {
				return cell.getColumnIndex();
			}
		}
		return -1;
	}

	static int getCellIndex(Sheet sheet, String localDate) {
		return getCellIndex(sheet, 0, localDate);
	}

	static int getCellIndex(Sheet sheet, int headerIndex, String value) {
		Row header = sheet.getRow(headerIndex);
		Iterator<Cell> cellIterator = header.cellIterator();
		while (cellIterator.hasNext()) {
			Cell cell = cellIterator.next();
			if (CellType.STRING.equals(cell.getCellType()) && value.equals(cell.getStringCellValue())) {
				return cell.getColumnIndex();
			}
		}
		return -1;
	}

	static File getSystemsFile(Integer year) {
		return getSystemsFile(year.toString());
	}

	static File getSystemsFile(String extractionYear) {
		String suffix = System.getenv("file-to-be-processed-suffix");
		File file = new File(PersistentStorage.buildWorkingPath() +
			File.separator + "[SE]["+ extractionYear +"] - " + (suffix != null ? suffix : "Sistemi") +".xlsx");
		//System.out.println("Processing file " + file.getName());
		return file;
	}

	static Sheet getSummarySheet(Workbook workbook) {
		Sheet sheet = workbook.getSheet("Riepilogo");
		if (sheet == null) {
			sheet = workbook.createSheet("Riepilogo");
			workbook.setSheetOrder("Riepilogo", 0);
		}
		return sheet;
	}

	static String toWAString(Collection<Integer> combo, String separator, Collection<Integer> numbers) {
		return String.join(
			separator,
			combo.stream()
		    .map(val -> {
		    	boolean hit = numbers.contains(val);
		    	return (hit ? "*" : "") + val.toString() + (hit ? "*" : "");
		    })
		    .collect(Collectors.toList())
		);
	}

	static String toString(Collection<Integer> combo, String separator) {
		return String.join(
			separator,
			combo.stream()
		    .map(Object::toString)
		    .collect(Collectors.toList())
		);
	}

	static SEStats getSEStats() {
		return SEStats.get(Shared.sEStatsDefaultDate, TimeUtils.defaultDateFormat.format(new Date()));
	}

	static String getSystemEnv(String key, String defaultValue) {
		String value = System.getenv(key);
		if (value == null) {
			return defaultValue;
		}
		return value;
	}

	public static void main(String[] args) {
		List<Integer> ourNumbers = Arrays.asList(
			1,2,3,4,5,7,8,9,
			10,11,12,13,14,16,17,19,
			20,21,23,24,25,27,28,29,
			32,33,35,
			40,47,49,
			51,52,55,
			64,68,69,
			75,77,79,
			80,83,84,85,86,88,90
		);
		//System.out.println(ComboHandler.sizeOf(ComboHandler.sizeOf(ourNumbers.size(), 6), 34));
		int count = 0;
		List<List<Integer>> system = new ArrayList<>();
		int bound = 4;
		for (List<Integer> winningCombo : getSEStats().getAllWinningCombos().values()) {
			int hit = 0;
			winningCombo = new ArrayList<>(winningCombo);
			Iterator<Integer> winningComboItr = winningCombo.iterator();
			while (winningComboItr.hasNext()) {
				Integer winningNumber = winningComboItr.next();
				if (ourNumbers.contains(winningNumber)) {
					++hit;
				} else {
					winningComboItr.remove();
				}
			}
			if (hit == bound) {
				system.add(winningCombo);
				System.out.println(toString(winningCombo, "\t"));
			}
		}
		SELotteryMatrixGeneratorEngine engine = new SELotteryMatrixGeneratorEngine();
		engine.extractionDate = LocalDate.now();
		engine.adjustSeed();
		List<String> inClauses = new ArrayList<>();
		for (List<Integer> winningCombo : system) {
			inClauses.add("in " + ComboHandler.toString(winningCombo, ",") + ":" + bound + "," + 6);
		}
		System.out.println("(" + String.join("|", inClauses) + ")");
	}

}