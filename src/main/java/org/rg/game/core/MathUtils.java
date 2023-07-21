package org.rg.game.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

public class MathUtils {

	public static final MathUtils INSTANCE = new MathUtils();

	public DecimalFormat decimalFormat = new DecimalFormat( "#,##0.##" );
	public DecimalFormat integerFormat = new DecimalFormat( "#,##0" );
	public Comparator<Number> numberComparator = (numberOne, numberTwo) -> {
		double numberOneAsDouble = numberOne.doubleValue();
		double numberTwoAsDouble = numberTwo.doubleValue();
		return numberOneAsDouble > numberTwoAsDouble ? 1 :
			numberOneAsDouble < numberTwoAsDouble ? -1 : 0;
	};

	public DecimalFormat getNewDecimalFormat() {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setGroupingSeparator(',');
		symbols.setDecimalSeparator('.');
		DecimalFormat decimalFormat = new DecimalFormat("#.#", symbols);
		decimalFormat.setParseBigDecimal(true);
		return decimalFormat;
	}

	public BigDecimal stringToBigDecimal(String value) {
		return stringToBigDecimal(value, getNewDecimalFormat());
	}

	public BigDecimal stringToBigDecimal(String value, DecimalFormat decimalFormat) {
		value =	value.trim();
		if (value.contains(".")) {
			String wholeNumber = value.substring(0, value.indexOf("."));
			String fractionalPart = value.substring(value.indexOf(".") + 1, value.length());
			fractionalPart = fractionalPart.replace(".", "");
			value = wholeNumber + "." + fractionalPart;
		}
		try {
			return ((BigDecimal)decimalFormat.parse(value));
		} catch (ParseException exc) {
			return Throwables.sneakyThrow(exc);
		}
	}


	public BigInteger factorial(BigInteger number) {
		return Factorial.of(number).get();
	}
	/*
	public BigInteger factorial(BigInteger number) {
		BigInteger factorial = BigInteger.ONE;
		BigInteger divisor = BigInteger.valueOf(100_000);
		BigInteger initialValue = number;
		while (number.compareTo(BigInteger.ZERO) > 0) {
			factorial = factorial.multiply(number);
			number = number.subtract(BigInteger.ONE);
			BigInteger processedNumbers = initialValue.subtract(number);
			if (processedNumbers.mod(divisor).compareTo(BigInteger.ZERO) == 0) {
				LogUtils.INSTANCE.info("Processed " + processedNumbers
					.toString() + " numbers - Factorial: " + factorial.toString());
			}
		}
		return factorial;
	}*/

	public BigInteger factorial(Number number) {
		return factorial(BigInteger.valueOf(number.longValue()));
	}

	public String format(Number value) {
		if (value == null) {
			return "null";
		}
		return String.format("%,d", value);
	}

	public static class Factorial {
		private final static BigInteger loggerStartingThreshold = BigInteger.valueOf(200000);
		BigInteger factorial;
		BigInteger initialValue;
		BigInteger number;
		boolean computed;

		private Factorial(BigInteger number) {
			initialValue = number;
			this.number = number;
		}

		public static Factorial of(BigInteger number) {
			return new Factorial(number);
		}

		Factorial startLogging() {
			CompletableFuture.runAsync(() -> {
				while (!computed) {
					ConcurrentUtils.INSTANCE.sleep(10000);
					if (computed) {
						continue;
					}
					BigInteger processedNumbers = initialValue.subtract(number);
					LogUtils.INSTANCE.info(
						"Processed " + processedNumbers
						.toString() + " numbers - Factorial: " + factorial.toString()
					);
				}
			});
			return this;
		}

		public BigInteger get() {
			if (factorial != null) {
				return factorial;
			}
			if (number.compareTo(loggerStartingThreshold) >= 0) {
				startLogging();
			}
			factorial = BigInteger.ONE;
			while (number.compareTo(BigInteger.ZERO) > 0) {
				factorial = factorial.multiply(number);
				number = number.subtract(BigInteger.ONE);
			}
			computed = true;
			return factorial;
		}

	}

}
