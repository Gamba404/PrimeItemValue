package net.primeblocks.itemvalue.value;

/** Controls the marker and color used for estimated prices. */
public enum Confidence {
	HIGH(0xFFFFFFFF, ""),
	MEDIUM(0xFFCFCFCF, "~"),
	LOW(0xFF9A9A9A, "~");

	private final int colour;
	private final String prefix;

	Confidence(int colour, String prefix) {
		this.colour = colour;
		this.prefix = prefix;
	}

	public int colour() {
		return colour;
	}

	public String prefix() {
		return prefix;
	}

	public static Confidence parse(String raw) {
		if (raw == null) {
			return MEDIUM;
		}

		for (Confidence confidence : values()) {
			if (confidence.name().equalsIgnoreCase(raw)) {
				return confidence;
			}
		}

		return MEDIUM;
	}
}
