package org.rg.game.lottery.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PersistentStorage implements Storage {
	BufferedWriter bufferedWriter = null;
	String absolutePath;
	int size;

	public PersistentStorage(
		LocalDate extractionDate,
		int combinationCount,
		int numberOfCombos,
		List<Integer> numbers,
		String suffix
	) {
		buildWorkingPath();
		absolutePath = buildWorkingPath() + File.separator +
			"[" + extractionDate.toString() + "]"+"[" + combinationCount +"]" +
			"[" + numberOfCombos + "]" + /*"[" + toRawString(numbers) + "]" +*/ suffix + ".txt";
		try (FileChannel outChan = new FileOutputStream(absolutePath, true).getChannel()) {
		  outChan.truncate(0);
		} catch (IOException exc) {
			//exc.printStackTrace();
		}
		try {
			bufferedWriter = new BufferedWriter(new FileWriter(absolutePath, false));
			bufferedWriter.write("Il sistema e' composto da " + numbers.size() + " numeri: " + toSimpleString(numbers) + "\n");
			bufferedWriter.flush();
		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}
	}

	public static String buildWorkingPath() {
		String workingPath =
			/*System.getProperty("user.home") + File.separator +
			"Desktop" + File.separator +
			"Combos";*/
			"M:"  + File.separator + "Il mio Drive" + File.separator + "Superenalotto";
		File workingFolder = new File(workingPath);
		workingFolder.mkdirs();
		return workingPath;
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public List<Integer> getCombo(int idx) {
		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(absolutePath))){
			String line = bufferedReader.readLine();
			line = bufferedReader.readLine();
			int iterationIndex = 0;
			while ((line = bufferedReader.readLine()) != null) {
				if (line.split("\\t").length > 0) {
					if (iterationIndex == idx) {
						List<Integer> selectedCombo = new ArrayList<>();
						for (String numberAsString : line.split("\\t")) {
							selectedCombo.add(Integer.parseInt(numberAsString));
						}
						return selectedCombo;
					}
					iterationIndex++;
				}
			}
		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}
		return null;
	}

	@Override
	public boolean addCombo(List<Integer> selectedCombo) {
		if (!contains(selectedCombo)) {
			try {
				bufferedWriter.write("\n" + toString(selectedCombo));
				bufferedWriter.flush();
				++size;
			} catch (IOException exc) {
				throw new RuntimeException(exc);
			}
			return true;
		}
		return false;
	}

	@Override
	public void addUnindexedCombo(List<Integer> selectedCombo) {
		try {
			bufferedWriter.write("\n" + toString(selectedCombo));
			bufferedWriter.flush();
		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}
	}

	public boolean contains(List<Integer> selectedCombo) {
		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(absolutePath))){
			String line = bufferedReader.readLine();
			line = bufferedReader.readLine();
			while ((line = bufferedReader.readLine()) != null) {
				for (String numberAsString : line.split("\\t")) {
					if (!selectedCombo.contains(Integer.parseInt(numberAsString))) {
						return false;
					}
				}
				return true;
			}
		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}
		return false;
	}

	@Override
	public void printAll() {
	    try (BufferedReader br = new BufferedReader(new FileReader(absolutePath))) {
	        String line;
	        while ((line = br.readLine()) != null) {
	           System.out.println(line);
	        }
	    } catch (IOException e) {
			e.printStackTrace();
		}
	 }


	@Override
	public void close() {
		try {
			bufferedWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean addLine(String value) {
		try {
			bufferedWriter.write("\n" + value);
			bufferedWriter.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	@Override
	public boolean addLine() {
		try {
			bufferedWriter.write("\n");
			bufferedWriter.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	@Override
	public void delete() {
		try {
			bufferedWriter.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		new File(absolutePath).delete();
	}

}