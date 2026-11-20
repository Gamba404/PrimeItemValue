package net.primeblocks.itemvalue.catalog;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import net.primeblocks.itemvalue.PrimeItemValueClient;

/** Loads German and English item names for the search index. */
public final class ItemNames {

	private static final List<String> LANG_FILES = List.of("lang/en_us.json", "lang/de_de.json");

	private static Map<String, String> names;

	private ItemNames() {
	}

	public static String of(String descriptionId) {
		Map<String, String> loaded = names;

		if (loaded == null) {
			loaded = load();
			names = loaded;
		}

		return loaded.get(descriptionId);
	}

	public static void forget() {
		names = null;
	}

	private static Map<String, String> load() {
		Map<String, String> out = new HashMap<>(2048);

		for (String file : LANG_FILES) {
			merge(out, file);
		}

		return out;
	}

	private static void merge(Map<String, String> out, String file) {
		try {
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
					.getResource(Identifier.withDefaultNamespace(file));

			if (resource.isEmpty()) {
				PrimeItemValueClient.LOGGER.info(
						"{} nicht vorhanden; die Suche kommt ohne diese Sprache aus", file);
				return;
			}

			try (BufferedReader reader = resource.get().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

				for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
					String key = entry.getKey();

					if (!key.startsWith("item.") && !key.startsWith("block.")) {
						continue;
					}

					String name = entry.getValue().getAsString();
					String existing = out.get(key);

					if (existing == null) {
						out.put(key, name);
					} else if (!existing.equalsIgnoreCase(name)) {
						out.put(key, existing + ' ' + name);
					}
				}
			}
		} catch (Exception failure) {
			PrimeItemValueClient.LOGGER.warn(
					"{} konnte nicht gelesen werden; Suche läuft ohne diese Sprache", file, failure);
		}
	}
}
