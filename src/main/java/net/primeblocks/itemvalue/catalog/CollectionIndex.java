package net.primeblocks.itemvalue.catalog;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.primeblocks.itemvalue.PrimeItemValueClient;

/** Looks up collection names by custom model number. */
public final class CollectionIndex {

	private static final String BUNDLED = "/data/primeitemvalue/collections.json";

	private static Map<String, String> byModelData;

	private CollectionIndex() {
	}

	public static String of(Integer customModelData) {
		if (customModelData == null) {
			return null;
		}

		Map<String, String> loaded = byModelData;

		if (loaded == null) {
			loaded = load();
			byModelData = loaded;
		}

		return loaded.get(String.valueOf(customModelData));
	}

	private static Map<String, String> load() {
		try (InputStream in = CollectionIndex.class.getResourceAsStream(BUNDLED)) {
			if (in == null) {
				PrimeItemValueClient.LOGGER.warn("{} fehlt im Jar", BUNDLED);
				return Map.of();
			}

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				Map<String, String> parsed = new Gson().fromJson(reader,
						new TypeToken<Map<String, String>>() { }.getType());

				return parsed == null ? Map.of() : parsed;
			}
		} catch (Exception failure) {
			PrimeItemValueClient.LOGGER.warn("Kollektionen nicht lesbar: {}", failure.toString());
			return Map.of();
		}
	}
}
