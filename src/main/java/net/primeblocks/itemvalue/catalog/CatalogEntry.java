package net.primeblocks.itemvalue.catalog;

import java.util.Locale;

/** An item in the searchable catalog, independent of Minecraft classes. */
public record CatalogEntry(
		String id,
		String baseItem,
		String displayName,
		String plainName,
		Integer customModelData,
		ItemCategory category,
		String collection,
		boolean tradeable,
		boolean obtainable,
		String searchAlias) {

	public CatalogEntry(String id, String baseItem, String displayName, String plainName,
			Integer customModelData, ItemCategory category, String collection,
			boolean tradeable, boolean obtainable) {
		this(id, baseItem, displayName, plainName, customModelData, category, collection,
				tradeable, obtainable, null);
	}

	public String searchText() {
		StringBuilder builder = new StringBuilder(96);

		append(builder, plainName);
		append(builder, displayName);
		append(builder, baseItem);
		append(builder, collection);
		append(builder, id);
		append(builder, searchAlias);

		return builder.toString();
	}

	private static final String SMALL_CAPS =
			"\u1D00a\u0299b\u1D04c\u1D05d\u1D07e\uA730f\u0262g\u029Ch\u026Ai\u1D0Aj"
			+ "\u1D0Bk\u029Fl\u1D0Dm\u0274n\u1D0Fo\u1D18p\u0280r\uA731s\u1D1Bt"
			+ "\u1D1Cu\u1D20v\u1D21w\u1D22z\u0455s";

	private static String fold(String value) {
		StringBuilder builder = new StringBuilder(value.length());

		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);

			if (c >= '\uE000' && c <= '\uF8FF') {
				builder.append(' ');
				continue;
			}

			int found = SMALL_CAPS.indexOf(c);
			builder.append(found >= 0 && found % 2 == 0 ? SMALL_CAPS.charAt(found + 1) : c);
		}

		String decomposed = java.text.Normalizer.normalize(builder, java.text.Normalizer.Form.NFD);

		return decomposed.replaceAll("\\p{M}+", "");
	}

	private static void append(StringBuilder builder, String value) {
		if (value == null || value.isBlank()) {
			return;
		}

		builder.append(normalise(value)).append(' ');
	}

	// Apply the same folding to names and queries, including small caps and accents.
	public static String normalise(String value) {
		String lower = fold(value).toLowerCase(Locale.ROOT);
		StringBuilder builder = new StringBuilder(lower.length());
		boolean lastWasSpace = false;

		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			boolean separator = c == '_' || c == '-' || c == ':' || c == '/' || Character.isWhitespace(c);

			if (separator) {
				if (!lastWasSpace && builder.length() > 0) {
					builder.append(' ');
				}

				lastWasSpace = true;
				continue;
			}

			builder.append(c);
			lastWasSpace = false;
		}

		return builder.toString().trim();
	}
}
