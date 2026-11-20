package net.primeblocks.itemvalue.value;

import java.util.Locale;

/** Formats prices with German separators and the k and M suffixes. */
public final class ValueFormatter {
	private static final long THOUSAND = 1_000L;
	private static final long MILLION = 1_000_000L;
	private static final long BILLION = 1_000_000_000L;

	private ValueFormatter() {
	}

	public static String shortForm(long amount) {
		if (amount < 0) {
			return "-" + shortForm(-amount);
		}

		if (amount < THOUSAND) {
			return Long.toString(amount);
		}

		if (amount < MILLION) {
			return scaled(amount, THOUSAND, "k");
		}

		if (amount < BILLION) {
			return scaled(amount, MILLION, "M");
		}

		return scaled(amount, BILLION, "B");
	}

	public static String longForm(long amount) {
		return String.format(Locale.GERMANY, "%,d", amount);
	}

	public static String display(ItemValue value) {
		return value.confidence().prefix() + shortForm(value.amount());
	}

	private static String scaled(long amount, long unit, String suffix) {
		long whole = amount / unit;

		if (whole >= 10) {
			return whole + suffix;
		}

		long tenths = (amount % unit) * 10 / unit;

		if (tenths == 0) {
			return whole + suffix;
		}

		return whole + "," + tenths + suffix;
	}
}
