package net.primeblocks.itemvalue.value;

/** A daily price and trade count. A null price marks a day without data. */
public record ValuePoint(Long value, int sampleSize) {

	public static ValuePoint none() {
		return new ValuePoint(null, 0);
	}

	public boolean known() {
		return value != null;
	}
}
