package net.primeblocks.itemvalue.catalog;

import java.util.ArrayList;
import java.util.List;

import net.primeblocks.itemvalue.market.MarketItem;

/** Builds custom catalog entries from market data and preset names. */
public final class MarketCatalogSource {

	private MarketCatalogSource() {
	}

	public static List<CatalogEntry> from(Iterable<MarketItem> items,
			java.util.Map<String, String> presetNames) {
		List<CatalogEntry> entries = new ArrayList<>(600);

		for (MarketItem item : items) {
			if (!item.custom() || item.key() == null) {
				continue;
			}

			String name = nameFor(item, presetNames.get(item.key()));
			String alias = aliasFor(item);

			entries.add(new CatalogEntry(
					item.key(),
					item.registryId(),
					name,
					name,
					item.customModelData(),
					ItemCategory.CUSTOM,
					collectionOf(item),
					true,
					true,
					alias));
		}

		return entries;
	}

	private static String nameFor(MarketItem item, String presetName) {
		if (presetName != null && !presetName.isBlank() && !looksLikeKey(presetName)) {
			return presetName.trim();
		}

		return aliasFor(item);
	}

	private static boolean looksLikeKey(String name) {
		return name.indexOf('.') > 0 && name.chars()
				.allMatch(c -> c == '.' || c == '_' || Character.isDigit(c)
						|| (c >= 'a' && c <= 'z'));
	}

	private static String aliasFor(MarketItem item) {
		String base = item.name() == null || item.name().isBlank() ? item.key() : item.name();

		return item.customModelData() == null ? base : base + " #" + item.customModelData();
	}

	private static String collectionOf(MarketItem item) {
		return CollectionIndex.of(item.customModelData());
	}
}
