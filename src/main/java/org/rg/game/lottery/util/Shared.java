package org.rg.game.lottery.util;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.ComboHandler;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;

class Shared {

	static DateTimeFormatter datePattern = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	static DecimalFormat integerFormat = new DecimalFormat( "#,##0" );
	static SimpleDateFormat standardDatePattern = new SimpleDateFormat("dd/MM/yyyy");
	static DateTimeFormatter formatter = DateTimeFormatter.ofPattern(standardDatePattern.toPattern());
	static String SEStatsDefaultDate = new SELotteryMatrixGeneratorEngine().getDefaultExtractionArchiveStartDate();

	static String capitalizeFirstCharacter(String value) {
		return Character.toString(value.charAt(0)).toUpperCase()
		+ value.substring(1, value.length());
	}

	static LocalDate convert(String dateAsString) {
		if (dateAsString.equals("today")) {
			return LocalDateTime.now(ZoneId.of("Europe/Rome")).toLocalDate();
		}
		if (dateAsString.equals("nextExtraction")) {
			LotteryMatrixGeneratorAbstEngine engine = new SELotteryMatrixGeneratorEngine();
			return engine.computeNextExtractionDate(LocalDateTime.now(ZoneId.of("Europe/Rome")).toLocalDate(), false);
		}
		return LocalDate.parse(dateAsString, Shared.formatter);
	}

	static String getMonth(String date) {
		return getMonth(LocalDate.parse(date, formatter));
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

	static int getCellIndex(Sheet sheet, int headerIndex, String dayAsString) {
		Row header = sheet.getRow(headerIndex);
		Iterator<Cell> cellIterator = header.cellIterator();
		while (cellIterator.hasNext()) {
			Cell cell = cellIterator.next();
			if (CellType.STRING.equals(cell.getCellType()) && dayAsString.equals(cell.getStringCellValue())) {
				return cell.getColumnIndex();
			}
		}
		return -1;
	}

	static String toString(Collection<Integer> combo, String separator) {
		return String.join(
			separator,
			combo.stream()
		    .map(Object::toString)
		    .collect(Collectors.toList())
		);
	}

	static String toPremiumLabel(Integer hit) {
		if (hit == 2) {
			return "Ambo";
		}
		if (hit == 3) {
			return "Terno";
		}
		if (hit == 4) {
			return "Quaterna";
		}
		if (hit == 5) {
			return "Cinquina";
		}
		if (hit == 6) {
			return "Tombola";
		}
		throw new IllegalArgumentException();
	}

	static List<String> allPremiumLabels() {
		return Arrays.asList(
			"Ambo",
			"Terno",
			"Quaterna",
			"Cinquina",
			"Tombola"
		);
	}

	static FileSystemItem getSystemsFile(Integer year) {
		return getSystemsFile(year.toString());
	}

	static FileSystemItem getSystemsFile(String extractionYear) {
		return FileSystemItem.ofPath(PersistentStorage.buildWorkingPath() +
			File.separator + "[SE]["+ extractionYear +"] - Sistemi.xlsx");
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

	static String toString(Collection<Integer> combo, String separator, Collection<Integer> numbers) {
		return String.join(
			separator,
			combo.stream()
		    .map(Object::toString)
		    .collect(Collectors.toList())
		);
	}

	static String getLetterAtIndex(int index) {
		return Character.valueOf("ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(index)).toString();
	}

	public static SEStats getSEStats() {
		return SEStats.get(Shared.SEStatsDefaultDate);
	}

	public static void main(String[] args) {
		/*System.out.println(ComboHandler.sizeOf(ComboHandler.sizeOf(46, 6), 34));*/
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
		engine.adjustSeed(LocalDate.now());
		List<String> inClauses = new ArrayList<>();
		for (List<Integer> winningCombo : system) {
			inClauses.add("in " + ComboHandler.toString(winningCombo, ",") + ":" + bound + "," + 6);
		}
		System.out.println("(" + String.join("|", inClauses) + ")");
	}

}