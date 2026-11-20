package net.primeblocks.itemvalue.value;

/** A unit price with its currency and confidence level. */
public record ItemValue(long amount, Currency currency, Confidence confidence) {

	public static ItemValue of(long amount) {
		return new ItemValue(amount, Currency.COINS, Confidence.HIGH);
	}

	public ItemValue times(int count) {
		if (count <= 1) {
			return this;
		}

		return new ItemValue(amount * count, currency, confidence);
	}
}
