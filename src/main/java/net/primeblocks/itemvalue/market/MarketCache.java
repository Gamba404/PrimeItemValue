package net.primeblocks.itemvalue.market;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

import net.primeblocks.itemvalue.PrimeItemValueClient;

/** Stores market pages and preset names between sessions. */
public final class MarketCache {

	private static final Gson GSON = new Gson();
	private static final String FILE = "primeitemvalue-market.json";

	private static final long MAX_AGE_MILLIS = 24L * 60 * 60 * 1000;

	private MarketCache() {
	}

	public record Snapshot(List<MarketApi.Page> pages, List<MarketItem> items,
			Map<String, String> names) {

		static Snapshot empty() {
			return new Snapshot(List.of(), List.of(), Map.of());
		}

		public Map<Integer, MarketApi.Page> byOffset() {
			Map<Integer, MarketApi.Page> known = new LinkedHashMap<>();

			for (MarketApi.Page page : pages) {
				if (page != null && page.etag() != null && page.items() != null) {
					known.put(page.offset(), page);
				}
			}

			return known;
		}
	}

	public static Snapshot load(String serverGroup, String period) {
		Path file = path();

		if (!Files.exists(file)) {
			return Snapshot.empty();
		}

		try {
			Stored stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
					Stored.class);

			if (stored == null) {
				return Snapshot.empty();
			}

			if (!serverGroup.equals(stored.serverGroup) || !period.equals(stored.period)) {
				return Snapshot.empty();
			}

			long age = System.currentTimeMillis() - stored.savedAt;

			if (age > MAX_AGE_MILLIS) {
				return Snapshot.empty();
			}

			List<MarketApi.Page> pages = stored.pages == null ? List.of() : stored.pages;
			List<MarketItem> items = flatten(pages);

			if (items.isEmpty() && stored.items != null) {
				items = stored.items;
				pages = List.of();
			}

			if (items.isEmpty()) {
				return Snapshot.empty();
			}

			PrimeItemValueClient.LOGGER.info(
					"Marktdaten aus dem Zwischenspeicher: {} Items, {} Minuten alt{}",
					items.size(), age / 60000,
					pages.isEmpty() ? "" : ", " + pages.size() + " Seiten mit Kennung");

			return new Snapshot(pages, items,
					stored.names == null ? Map.of() : stored.names);
		} catch (IOException | RuntimeException failure) {
			PrimeItemValueClient.LOGGER.warn("Zwischenspeicher nicht lesbar: {}",
					failure.toString());
			return Snapshot.empty();
		}
	}

	public static void save(String serverGroup, String period, List<MarketApi.Page> pages,
			Map<String, String> names) {
		Stored stored = new Stored();
		stored.serverGroup = serverGroup;
		stored.period = period;
		stored.savedAt = System.currentTimeMillis();
		stored.pages = pages;
		stored.names = names;

		try {
			Path file = path();
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(stored), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException failure) {
			PrimeItemValueClient.LOGGER.warn("Zwischenspeicher nicht schreibbar: {}",
					failure.toString());
		}
	}

	static List<MarketItem> flatten(List<MarketApi.Page> pages) {
		List<MarketItem> items = new ArrayList<>(2000);

		for (MarketApi.Page page : pages) {
			if (page != null && page.items() != null) {
				items.addAll(page.items());
			}
		}

		return items;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	private static final class Stored {
		String serverGroup;
		String period;
		long savedAt;

		List<MarketItem> items;

		List<MarketApi.Page> pages;
		Map<String, String> names;
	}

	static final TypeToken<List<MarketItem>> ITEM_LIST = new TypeToken<>() {
	};
}
