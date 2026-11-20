package net.primeblocks.itemvalue.value;

import net.primeblocks.itemvalue.Lang;

/** Supported currencies and their display names and colors. */
public enum Currency {
	COINS("primeitemvalue.currency.coins", 0xFFFFD966),
	GEMS("primeitemvalue.currency.gems", 0xFF8FC7E8),
	BLOODSTONE("primeitemvalue.currency.bloodstone", 0xFFE08A8A);

	private final String key;
	private final int colour;

	Currency(String key, int colour) {
		this.key = key;
		this.colour = colour;
	}

	public String label() {
		return Lang.get(key);
	}

	public int colour() {
		return colour;
	}

	public static Currency parse(String raw) {
		if (raw == null) {
			return COINS;
		}

		for (Currency currency : values()) {
			if (currency.name().equalsIgnoreCase(raw)) {
				return currency;
			}
		}

		return COINS;
	}
}
