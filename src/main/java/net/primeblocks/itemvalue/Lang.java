package net.primeblocks.itemvalue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

/** Loads translations in the selected mod language. */
public final class Lang {

	public static final List<String> CHOICES = List.of("de", "en", "auto");

	private static final Map<String, Map<String, String>> TABLES = new LinkedHashMap<>();

	private Lang() {
	}

	public static String get(String key, Object... args) {
		String template = lookup(key);

		if (template == null) {
			return I18n.get(key, args);
		}

		return args.length == 0 ? template : String.format(template, args);
	}

	public static String current() {
		ItemValueConfig config = PrimeItemValueClient.config();
		String chosen = config == null ? null : config.language;

		if (chosen == null || chosen.isBlank() || "auto".equalsIgnoreCase(chosen)) {
			return fromGame();
		}

		return "en".equalsIgnoreCase(chosen) ? "en" : "de";
	}

	public static String fromGame() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft == null || minecraft.options == null) {
			return "de";
		}

		String code = minecraft.options.languageCode;

		return code != null && code.toLowerCase(Locale.ROOT).startsWith("de") ? "de" : "en";
	}

	private static String lookup(String key) {
		String text = table(current()).get(key);

		return text != null ? text : table("de").get(key);
	}

	private static Map<String, String> table(String language) {
		Map<String, String> known = TABLES.get(language);

		if (known != null) {
			return known;
		}

		Map<String, String> loaded = load("de".equals(language) ? "de_de" : "en_us");
		TABLES.put(language, loaded);

		return loaded;
	}

	private static Map<String, String> load(String file) {
		String path = "/assets/primeitemvalue/lang/" + file + ".json";

		try (InputStream in = Lang.class.getResourceAsStream(path)) {
			if (in == null) {
				PrimeItemValueClient.LOGGER.warn("{} fehlt im Jar", path);
				return Map.of();
			}

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				Map<String, String> parsed = new Gson().fromJson(reader,
						new TypeToken<Map<String, String>>() { }.getType());

				return parsed == null ? Map.of() : parsed;
			}
		} catch (Exception failure) {
			PrimeItemValueClient.LOGGER.warn("{} nicht lesbar: {}", path, failure.toString());
			return Map.of();
		}
	}
}
