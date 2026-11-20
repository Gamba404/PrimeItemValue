package net.primeblocks.itemvalue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.fabricmc.loader.api.FabricLoader;

/** Stores panel, tooltip and API settings in config/primeitemvalue.json. */
public final class ItemValueConfig {
	private static final String FILE_NAME = "primeitemvalue.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final double MIN_SCALE = 0.5;
	public static final double MAX_SCALE = 2.0;
	public static final double SCALE_STEP = 0.1;

	public static final int MIN_COLUMNS = 5;
	public static final int MAX_COLUMNS = 20;
	public static final int DEFAULT_COLUMNS = 9;

	public static final int MIN_ROWS = 2;
	public static final int MAX_ROWS = 40;
	public static final int DEFAULT_ROWS = 6;

	private static final List<Background> BACKGROUNDS = List.of(
			new Background("primeitemvalue.background.dark", 0x16161C, 180),
			new Background("primeitemvalue.background.black", 0x000000, 205),
			new Background("primeitemvalue.background.blue", 0x1A2038, 190),
			new Background("primeitemvalue.background.grey", 0x2E2E2E, 170),
			new Background("primeitemvalue.background.subtle", 0x16161C, 95),
			new Background("primeitemvalue.background.none", 0x000000, 0));

	public boolean panelEnabled = true;

	// Collapsing keeps the inventory handle available; disabling hides the panel.
	public boolean panelCollapsed = false;

	public boolean panelAutoSize = true;

	public boolean tooltipEnabled = true;

	public boolean tooltipStackTotal = true;

	// Null coordinates use automatic placement.
	public Integer panelX = null;
	public Integer panelY = null;

	// Zero uses the default size, limited by the available space.
	public int panelColumns = 0;

	public int panelRows = 0;

	public double panelScale = 1.0;

	public boolean debugTiming = false;

	public boolean debugSearch = false;

	public int backgroundPreset = 0;

	public int backgroundRgb = 0x16161C;

	public int backgroundOpacity = 180;

	public String serverAddressFilter = "primeblocks";

	// An empty key skips custom data and falls back to the item name and model.
	public String customDataKey = "pb_id";

	public String apiBaseUrl = "https://markt-api.primeblocks.net";

	public String database = null;

	public String language = "de";

	public boolean detailCompact = false;

	public int marketPeriodDays = 30;

	private static final String OUTDATED_API = "public-api.primeblocks.net";

	private void migrate() {
		if (apiBaseUrl != null && apiBaseUrl.contains(OUTDATED_API)) {
			PrimeItemValueClient.LOGGER.info(
					"apiBaseUrl zeigte noch auf {} — auf die Markt-API umgestellt", apiBaseUrl);
			apiBaseUrl = new ItemValueConfig().apiBaseUrl;
			save();
		}
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static ItemValueConfig load() {
		Path file = path();

		if (!Files.exists(file)) {
			ItemValueConfig config = new ItemValueConfig();
			config.save();
			return config;
		}

		try {
			ItemValueConfig config = GSON.fromJson(
					Files.readString(file, StandardCharsets.UTF_8), ItemValueConfig.class);

			if (config == null) {
				return new ItemValueConfig();
			}

			config.migrate();
			return config;
		} catch (IOException | JsonSyntaxException e) {
			PrimeItemValueClient.LOGGER.warn("Could not read {}, using defaults", file, e);
			return new ItemValueConfig();
		}
	}

	public void save() {
		Path file = path();

		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			PrimeItemValueClient.LOGGER.warn("Could not write {}", file, e);
		}
	}

	public float scale() {
		return (float) Math.clamp(panelScale, MIN_SCALE, MAX_SCALE);
	}

	public void changeScale(double delta) {
		double next = Math.round((scale() + delta) / SCALE_STEP) * SCALE_STEP;
		panelScale = Math.clamp(next, MIN_SCALE, MAX_SCALE);
	}

	public String scaleLabel() {
		return Math.round(scale() * 100) + "%";
	}

	private transient boolean dirty;

	// Defer disk writes during dragging; save when the screen closes.
	public void markDirty() {
		dirty = true;
	}

	public void saveIfDirty() {
		if (!dirty) {
			return;
		}

		dirty = false;
		save();
	}

	public int backgroundColour() {
		if (backgroundPreset < 0 || backgroundPreset >= BACKGROUNDS.size()) {
			return (Math.clamp(backgroundOpacity, 0, 255) << 24) | (backgroundRgb & 0xFFFFFF);
		}

		return BACKGROUNDS.get(backgroundPreset).argb();
	}

	public String backgroundLabel() {
		if (backgroundPreset < 0 || backgroundPreset >= BACKGROUNDS.size()) {
			return Lang.get("primeitemvalue.background.custom");
		}

		return Lang.get(BACKGROUNDS.get(backgroundPreset).name());
	}

	public void cycleBackground() {
		if (backgroundPreset < 0 || backgroundPreset >= BACKGROUNDS.size()) {
			backgroundPreset = 0;
			return;
		}

		backgroundPreset = (backgroundPreset + 1) % BACKGROUNDS.size();
	}

	public int wantedColumns() {
		int requested = panelColumns > 0 ? panelColumns : DEFAULT_COLUMNS;
		return Math.clamp(requested, MIN_COLUMNS, MAX_COLUMNS);
	}

	public int wantedRows() {
		int requested = panelRows > 0 ? panelRows : DEFAULT_ROWS;
		return Math.clamp(requested, MIN_ROWS, MAX_ROWS);
	}

	private record Background(String name, int rgb, int opacity) {
		int argb() {
			return (Math.clamp(opacity, 0, 255) << 24) | (rgb & 0xFFFFFF);
		}
	}
}
