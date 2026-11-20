package net.primeblocks.itemvalue.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import net.primeblocks.itemvalue.ItemValueConfig;
import net.primeblocks.itemvalue.PrimeItemValueClient;
import net.primeblocks.itemvalue.mixin.PlayerTabOverlayAccessor;

/** Detects the CityBuild whose market prices apply to the current connection. */
public final class ServerGroup {

	private static final Pattern CITYBUILD =
			Pattern.compile("\\b(?:cb|citybuild)\\s*-?\\s*([1-9][0-9]?)\\s*-?\\s*[a-z]?\\b",
					Pattern.CASE_INSENSITIVE);

	public static final List<String> KNOWN = List.of("cb1", "cb2", "cb3");

	public static final String FALLBACK = "cb1";

	private static String lastReported;

	private ServerGroup() {
	}

	public static String current(ItemValueConfig config) {
		String configured = config == null ? null : config.database;

		if (configured != null && !configured.isBlank()) {
			return report(configured.trim().toLowerCase(Locale.ROOT), "Einstellung");
		}

		String detected = detect(Minecraft.getInstance());

		if (detected == null) {
			return report(FALLBACK, "Rückfallwert, nichts erkannt");
		}

		if (!KNOWN.contains(detected)) {

			PrimeItemValueClient.LOGGER.warn(
					"{} erkannt, aber die Markt-API kennt nur {} — es werden {}-Preise gezeigt",
					detected, KNOWN, FALLBACK);
			return report(FALLBACK, "Rückfallwert für " + detected);
		}

		return report(detected, "erkannt");
	}

	public static String detect(Minecraft minecraft) {
		if (minecraft == null) {
			return null;
		}

		for (String candidate : candidates(minecraft)) {
			String found = fromText(candidate);

			if (found != null) {
				return found;
			}
		}

		return null;
	}

	public static String fromText(String text) {
		if (text == null) {
			return null;
		}

		Matcher matcher = CITYBUILD.matcher(text);

		return matcher.find() ? "cb" + matcher.group(1) : null;
	}

	private static List<String> candidates(Minecraft minecraft) {
		List<String> texts = new ArrayList<>();
		ClientPacketListener connection = minecraft.getConnection();

		if (connection == null) {
			return texts;
		}

		addTabList(minecraft, texts);

		Scoreboard scoreboard = connection.scoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

		if (sidebar != null) {
			texts.add(sidebar.getDisplayName().getString());

			for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
				if (!entry.isHidden()) {
					texts.add(entry.ownerName().getString());
				}
			}
		}

		for (Objective objective : scoreboard.getObjectives()) {
			texts.add(objective.getName());
			texts.add(objective.getDisplayName().getString());
		}

		if (minecraft.level != null) {
			texts.add(minecraft.level.dimension().identifier().toString());
		}

		if (minecraft.getCurrentServer() != null) {
			texts.add(minecraft.getCurrentServer().ip);
			texts.add(minecraft.getCurrentServer().name);
		}

		texts.removeIf(text -> text == null || text.isBlank());

		return texts;
	}

	private static void addTabList(Minecraft minecraft, List<String> texts) {
		if (minecraft.gui == null || minecraft.gui.hud == null) {
			return;
		}

		PlayerTabOverlay tabList = minecraft.gui.hud.getTabList();

		if (!(tabList instanceof PlayerTabOverlayAccessor accessor)) {
			return;
		}

		add(texts, accessor.primeitemvalue$getFooter());
		add(texts, accessor.primeitemvalue$getHeader());
	}

	private static void add(List<String> texts, Component component) {
		if (component != null) {
			texts.add(component.getString());
		}
	}

	private static String report(String group, String source) {
		String line = group + " (" + source + ")";

		if (!line.equals(lastReported)) {
			lastReported = line;
			PrimeItemValueClient.LOGGER.info("Marktwerte für CityBuild {}", line);
		}

		return group;
	}
}
