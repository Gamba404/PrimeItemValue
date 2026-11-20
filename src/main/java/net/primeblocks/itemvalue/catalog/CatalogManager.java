package net.primeblocks.itemvalue.catalog;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import net.primeblocks.itemvalue.PrimeItemValueClient;
import net.primeblocks.itemvalue.value.DemoValueProvider;
import net.primeblocks.itemvalue.value.ValueProvider;

/** Builds the catalog on first use and rebuilds it when the game language changes. */
public final class CatalogManager {
	private volatile ItemCatalog catalog;
	private volatile ValueProvider demoPrices;

	private volatile String catalogLanguage;

	private volatile net.primeblocks.itemvalue.market.MarketValueProvider market;

	public ItemCatalog catalog() {
		ensureBuilt();
		return catalog;
	}

	public void useMarket(net.primeblocks.itemvalue.market.MarketValueProvider provider) {
		synchronized (this) {
			market = provider;
			catalog = build(provider);
			catalogLanguage = currentLanguage();
		}
	}

	public ValueProvider demoProvider() {
		ensureBuilt();
		return demoPrices;
	}

	private void ensureBuilt() {
		String language = currentLanguage();

		// Avoid locking on every frame; check again under the lock before rebuilding.
		if (catalog != null && language.equals(catalogLanguage)) {
			return;
		}

		synchronized (this) {
			if (catalog != null && language.equals(catalogLanguage)) {
				return;
			}

			ItemNames.forget();

			ItemCatalog built = build(market);
			demoPrices = new DemoValueProvider(built.asMap());
			catalog = built;
			catalogLanguage = language;
		}
	}

	private static String currentLanguage() {
		Minecraft minecraft = Minecraft.getInstance();

		return minecraft == null || minecraft.options == null
				? ""
				: minecraft.options.languageCode;
	}

	private static ItemCatalog build(net.primeblocks.itemvalue.market.MarketValueProvider market) {
		try {
			List<CatalogEntry> entries = new ArrayList<>(2000);

			// Later custom entries may replace vanilla entries with the same ID.
			entries.addAll(VanillaCatalogSource.load());

			entries.addAll(market != null
					? MarketCatalogSource.from(market.items(), market.presetNames())
					: CustomCatalogSource.loadBundled());

			ItemCatalog catalog = ItemCatalog.of(entries);

			PrimeItemValueClient.LOGGER.info("Katalog geladen: {} Items ({} Custom, {} Vanilla)",
					catalog.size(),
					catalog.countOf(ItemCategory.CUSTOM),
					catalog.countOf(ItemCategory.VANILLA));

			return catalog;
		} catch (RuntimeException e) {
			PrimeItemValueClient.LOGGER.error(
					"Katalog konnte nicht aufgebaut werden — Panel und Tooltips bleiben leer", e);
			return ItemCatalog.empty();
		}
	}
}
