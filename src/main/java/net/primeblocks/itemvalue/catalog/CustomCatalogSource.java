package net.primeblocks.itemvalue.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import net.primeblocks.itemvalue.PrimeItemValueClient;

/** Loads the bundled custom items until market data is available. */
public final class CustomCatalogSource {
	private static final Gson GSON = new Gson();
	private static final String BUNDLED = "/data/primeitemvalue/custom-items.json";

	private CustomCatalogSource() {
	}

	public static List<CatalogEntry> loadBundled() {
		try (InputStream in = CustomCatalogSource.class.getResourceAsStream(BUNDLED)) {
			if (in == null) {
				PrimeItemValueClient.LOGGER.warn("Bundled catalog {} is missing", BUNDLED);
				return List.of();
			}

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return parse(reader);
			}
		} catch (IOException | JsonParseException e) {
			PrimeItemValueClient.LOGGER.warn("Could not read bundled catalog {}", BUNDLED, e);
			return List.of();
		}
	}

	static List<CatalogEntry> parse(Reader reader) {
		Document document = GSON.fromJson(reader, Document.class);

		if (document == null || document.items == null) {
			return List.of();
		}

		List<CatalogEntry> entries = new ArrayList<>(document.items.size());

		for (Item item : document.items) {
			CatalogEntry entry = item.toEntry();

			if (entry != null) {
				entries.add(entry);
			}
		}

		return entries;
	}

	private static final class Document {
		@SerializedName("catalogVersion")
		String catalogVersion;

		List<Item> items;
	}

	private static final class Item {
		String id;
		String baseItem;
		String displayName;
		String plainName;
		Integer customModelData;
		String category;
		String collection;
		Boolean tradeable;
		Boolean obtainable;

		CatalogEntry toEntry() {

			if (id == null || id.isBlank() || baseItem == null || baseItem.isBlank()) {
				return null;
			}

			String shown = displayName != null && !displayName.isBlank() ? displayName : id;
			String plain = plainName != null && !plainName.isBlank() ? plainName : stripFormatting(shown);

			return new CatalogEntry(
					id,
					baseItem,
					shown,
					plain,
					customModelData,
					"VANILLA".equalsIgnoreCase(category) ? ItemCategory.VANILLA : ItemCategory.CUSTOM,
					collection,
					tradeable == null || tradeable,
					obtainable == null || obtainable);
		}
	}

	private static String stripFormatting(String value) {
		StringBuilder builder = new StringBuilder(value.length());

		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);

			if (c == '§' && i + 1 < value.length()) {
				i++;
				continue;
			}

			builder.append(c);
		}

		return builder.toString().trim();
	}
}
