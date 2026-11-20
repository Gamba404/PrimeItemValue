package net.primeblocks.itemvalue.catalog;

/** The custom and vanilla tabs in the panel. */
public enum ItemCategory {
	CUSTOM("Custom"),
	VANILLA("Vanilla");

	private final String label;

	ItemCategory(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
