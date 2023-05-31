package org.rg.game.lottery.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.formula.BaseFormulaEvaluator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rg.game.core.LogUtils;
import org.rg.game.core.Throwables;
import org.rg.game.lottery.engine.SimpleWorkbookTemplate;

public class SESimulationSummaryGenerator {

	public static void main(String[] args) {
		String simulationSummaryFolder = Arrays.stream(
			Shared.pathsFromSystemEnv(
					"working-path.simulations.folder"
				)
			).findFirst().orElseGet(() -> null);
		String simulationSummaryFile = simulationSummaryFolder + File.separator + "Summary.xlsx";
		SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(true);
		Sheet summarySheet = workBookTemplate.getOrCreateSheet("Riepilogo", true);
		List<String> headerLabels = new ArrayList<>(SELotterySimpleSimulator.excelHeaderLabels);
		List<String> headersToBeSkipped = Arrays.asList(
			SELotterySimpleSimulator.DATA_AGGIORNAMENTO_STORICO_LABEL,
			SELotterySimpleSimulator.FILE_LABEL
		);

		headerLabels.remove(SELotterySimpleSimulator.DATA_AGGIORNAMENTO_STORICO_LABEL);
		headerLabels.set(0, "Conteggio estrazioni");
		workBookTemplate.createHeader(true, headerLabels);
		int reportCounter = 0;
		for (File singleSimFolder : new File(simulationSummaryFolder).listFiles((file, name) -> new File(file, name).isDirectory())) {
			File report = Arrays.stream(singleSimFolder.listFiles((file, name) -> name.endsWith("report.xlsx"))).findFirst().orElseGet(() -> null);
			if (report != null) {
				reportCounter++;
				try (InputStream inputStream = new FileInputStream(report.getAbsolutePath())) {
					Workbook workBook = new XSSFWorkbook(inputStream);
					FormulaEvaluator evaluator = workBook.getCreationHelper().createFormulaEvaluator();
					Sheet resultSheet = workBook.getSheet("Risultati");
					workBookTemplate.addRow();
					for (String cellLabel : SELotterySimpleSimulator.excelHeaderLabels) {
						if (!headersToBeSkipped.contains(cellLabel)) {
							Cell cell = resultSheet.getRow(1).getCell(Shared.getCellIndex(resultSheet, cellLabel));
							CellValue cellValue = evaluator.evaluate(cell);
							if (cellValue.getCellType().equals(CellType.NUMERIC)) {
								workBookTemplate.addCell(cellValue.getNumberValue(), "#,##0");
							} else if (cellValue.getCellType().equals(CellType.STRING)) {
								workBookTemplate.addCell(cellValue.getStringValue());
							}
						}
					}
					Cell cellName = workBookTemplate.addCell(singleSimFolder.getName() + "/" + report.getName()).get(0);
					workBookTemplate.setLinkForCell(
						HyperlinkType.FILE,
						cellName,
						URLEncoder.encode(cellName.getStringCellValue(), "UTF-8")
					);
				} catch (Throwable exc) {
					LogUtils.error("Unable to open file " + report.getAbsolutePath() + ": " + exc.getMessage());
				}
			}
		}
		summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.FILE_LABEL), 12000);
		try (OutputStream destFileOutputStream = new FileOutputStream(simulationSummaryFile)){
			workBookTemplate.setAutoFilter(0, reportCounter, 0, headerLabels.size() - 1);
			BaseFormulaEvaluator.evaluateAllFormulaCells(workBookTemplate.getWorkbook());
			workBookTemplate.getWorkbook().write(destFileOutputStream);
		} catch (IOException e) {
			Throwables.sneakyThrow(e);
		}
	}

}
