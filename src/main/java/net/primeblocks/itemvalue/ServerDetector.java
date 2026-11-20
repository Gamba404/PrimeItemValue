package net.primeblocks.itemvalue;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Checks whether the mod is enabled for the current server. */
public final class ServerDetector {

	private ServerDetector() {
	}

	public static boolean active(Minecraft minecraft, ItemValueConfig config) {
		if (minecraft == null) {
			return false;
		}

		String filter = config.serverAddressFilter;

		if (filter == null || filter.isBlank()) {
			return true;
		}

		ServerData server = minecraft.getCurrentServer();

		if (server == null) {
			return false;
		}

		String needle = filter.toLowerCase(Locale.ROOT);
		return contains(server.ip, needle) || contains(server.name, needle);
	}

	private static boolean contains(String haystack, String needle) {
		return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
	}
}
