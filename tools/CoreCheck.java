import java.util.List;
import net.primeblocks.itemvalue.catalog.CatalogEntry;
import net.primeblocks.itemvalue.catalog.ItemCatalog;
import net.primeblocks.itemvalue.catalog.ItemCategory;
import net.primeblocks.itemvalue.market.MarketApi;
import net.primeblocks.itemvalue.market.MarketItem;
import net.primeblocks.itemvalue.market.ServerGroup;
import net.primeblocks.itemvalue.value.DemoValueProvider;
import net.primeblocks.itemvalue.value.ValueFormatter;
import net.primeblocks.itemvalue.value.ValueHistory;
import net.primeblocks.itemvalue.value.ValuePoint;

public class CoreCheck {
	static int fails = 0;

	static void eq(String what, Object actual, Object expected) {
		boolean ok = String.valueOf(actual).equals(String.valueOf(expected));
		if (!ok) fails++;
		System.out.println((ok ? "  ok   " : "  FAIL ") + what
				+ " -> " + actual + (ok ? "" : "  (erwartet " + expected + ")"));
	}

	static CatalogEntry custom(String id, String base, String name, String coll) {
		return new CatalogEntry(id, base, name, name, 7100, ItemCategory.CUSTOM, coll, true, true);
	}

	public static void main(String[] args) {
		System.out.println("== ValueFormatter (k/M nach Server-Konvention) ==");
		eq("48", ValueFormatter.shortForm(48), "48");
		eq("999", ValueFormatter.shortForm(999), "999");
		eq("1000", ValueFormatter.shortForm(1000), "1k");
		eq("9876", ValueFormatter.shortForm(9876), "9,8k");
		eq("10000", ValueFormatter.shortForm(10_000), "10k");
		eq("523400", ValueFormatter.shortForm(523_400), "523k");
		eq("1234567", ValueFormatter.shortForm(1_234_567), "1,2M");
		eq("2000000", ValueFormatter.shortForm(2_000_000), "2M");
		eq("negativ", ValueFormatter.shortForm(-1500), "-1,5k");
		eq("longForm", ValueFormatter.longForm(523_400), "523.400");

		System.out.println("== normalise (Trennzeichen glaetten) ==");
		eq("registry id", CatalogEntry.normalise("minecraft:carved_pumpkin"),
				"minecraft carved pumpkin");
		eq("mehrfache Trenner", CatalogEntry.normalise("Boo!__und--RiP"), "boo! und rip");

		System.out.println("== Suche ==");
		ItemCatalog catalog = ItemCatalog.of(List.of(
				custom("7100", "minecraft:carved_pumpkin", "Kuerbiskopf #7100", "Halloween"),
				custom("7101", "minecraft:netherite_sword", "Kristall-Schwert #7101", "Kristall"),
				new CatalogEntry("minecraft:stone", "minecraft:stone", "Stein", "Stein",
						null, ItemCategory.VANILLA, null, true, true)));

		eq("Basisname findet Custom-Item",
				catalog.search("carved pumpkin", ItemCategory.CUSTOM).size(), 1);
		eq("Anzeigename findet Custom-Item",
				catalog.search("kristall", ItemCategory.CUSTOM).size(), 1);
		eq("ID findet Custom-Item",
				catalog.search("7101", ItemCategory.CUSTOM).size(), 1);
		eq("Kollektion findet Custom-Item",
				catalog.search("halloween", ItemCategory.CUSTOM).size(), 1);
		eq("mehrere Tokens verengen",
				catalog.search("kristall pumpkin", ItemCategory.CUSTOM).size(), 0);
		eq("leere Suche gibt alle der Kategorie",
				catalog.search("", ItemCategory.CUSTOM).size(), 2);
		eq("Reiter trennt",
				catalog.search("", ItemCategory.VANILLA).size(), 1);
		eq("Zaehler Custom", catalog.countOf(ItemCategory.CUSTOM), 2);
		eq("Zaehler Vanilla", catalog.countOf(ItemCategory.VANILLA), 1);

		System.out.println("== Vanilla-Suche (deutscher Client, englische Registry-ID) ==");
		ItemCatalog vanilla = ItemCatalog.of(List.of(
				new CatalogEntry("minecraft:carved_pumpkin", "minecraft:carved_pumpkin",
						"Geschnitzter Kuerbis", "Geschnitzter Kuerbis",
						null, ItemCategory.VANILLA, null, true, true),
				new CatalogEntry("minecraft:stone", "minecraft:stone", "Stein", "Stein",
						null, ItemCategory.VANILLA, null, true, true),
				new CatalogEntry("minecraft:netherite_sword", "minecraft:netherite_sword",
						"Netheritschwert", "Netheritschwert",
						null, ItemCategory.VANILLA, null, true, true),

				new CatalogEntry("minecraft:redstone", "minecraft:redstone",
						"Redstone-Staub", "Redstone-Staub",
						null, ItemCategory.VANILLA, null, true, true, "Redstone Dust"),
				new CatalogEntry("minecraft:crimson_stairs", "minecraft:crimson_stairs",
						"Karmesintreppe", "Karmesintreppe",
						null, ItemCategory.VANILLA, null, true, true, "Crimson Stairs")));

		eq("englisch: pumpkin", vanilla.search("pumpkin", ItemCategory.VANILLA).size(), 1);
		eq("englisch: carved pumpkin", vanilla.search("carved pumpkin", ItemCategory.VANILLA).size(), 1);
		eq("deutsch: stein", vanilla.search("stein", ItemCategory.VANILLA).size(), 1);
		eq("deutsch gross: Stein", vanilla.search("Stein", ItemCategory.VANILLA).size(), 1);

		eq("mit Namespace: minecraft:stone", vanilla.search("minecraft:stone", ItemCategory.VANILLA).size(), 2);
		eq("genauer: minecraft:stone findet Stein", vanilla.search("stein", ItemCategory.VANILLA).size(), 1);
		eq("Unterstrich: netherite_sword", vanilla.search("netherite_sword", ItemCategory.VANILLA).size(), 1);
		eq("Teilwort: sword", vanilla.search("sword", ItemCategory.VANILLA).size(), 1);
		eq("deutsch: karmesintreppe", vanilla.search("karmesintreppe", ItemCategory.VANILLA).size(), 1);
		eq("englisch: crimson stairs", vanilla.search("crimson stairs", ItemCategory.VANILLA).size(), 1);
		eq("deutsch: redstone-staub", vanilla.search("redstone-staub", ItemCategory.VANILLA).size(), 1);
		eq("englisch: redstone dust", vanilla.search("redstone dust", ItemCategory.VANILLA).size(), 1);
		eq("englisch: dust nur ueber Zweitnamen", vanilla.search("dust", ItemCategory.VANILLA).size(), 1);
		eq("Unsinn findet nichts", vanilla.search("zzzz", ItemCategory.VANILLA).size(), 0);
		eq("Custom-Reiter bleibt leer", vanilla.search("stein", ItemCategory.CUSTOM).size(), 0);

		System.out.println("== CityBuild aus dem, was der Server schreibt ==");

		eq("Tab-Fussleiste", ServerGroup.fromText(
				"Du befindest dich derzeit auf: CityBuild-1 (Farmwelt-5)"), "cb1");
		eq("Farmwelt gehoert zum CityBuild", ServerGroup.fromText(
				"CityBuild-2 (Farmwelt-3)"), "cb2");
		eq("Kurzform", ServerGroup.fromText("CB3"), "cb3");
		eq("Kurzform mit Strich", ServerGroup.fromText("CB-2"), "cb2");
		eq("Kurzform mit Leerzeichen", ServerGroup.fromText("CB 3"), "cb3");
		eq("Instanz a", ServerGroup.fromText("Teleportiere zu cb1a"), "cb1");
		eq("Instanz b", ServerGroup.fromText("CityBuild-1b"), "cb1");
		eq("Instanz gross", ServerGroup.fromText("CB2A"), "cb2");
		eq("Kleinschreibung", ServerGroup.fromText("citybuild-3"), "cb3");
		eq("Adresse sagt nichts", ServerGroup.fromText("dev.primeblocks.net"), null);
		eq("Farmwelt allein sagt nichts", ServerGroup.fromText("Farmwelt-5"), null);
		eq("leer", ServerGroup.fromText(""), null);
		eq("null", ServerGroup.fromText(null), null);

		System.out.println("== Suche auf echten Servernamen ==");

		ItemCatalog odd = ItemCatalog.of(List.of(

				new CatalogEntry("preset:rocket", "minecraft:firework_rocket",
						"ʀᴀᴋᴇᴛᴇɴᴀɴᴛʀɪᴇʙ",
						"ʀᴀᴋᴇᴛᴇɴᴀɴᴛʀɪᴇʙ",
						7001, ItemCategory.CUSTOM, "Galaxy Kollektion", true, true),

				new CatalogEntry("preset:sword", "minecraft:netherite_sword",
						" Schwert v2", " Schwert v2",
						7105, ItemCategory.CUSTOM, "Apex-Kollektion", true, true),

				new CatalogEntry("preset:booster", "minecraft:nautilus_shell",
						"ѕpawner Booster", "ѕpawner Booster",
						4019, ItemCategory.CUSTOM, "Piraten-Kollektion", true, true),
				new CatalogEntry("preset:tuerkis", "minecraft:diamond", "Türkis", "Türkis",
						7002, ItemCategory.CUSTOM, "Kristall-Kollektion", true, true)));

		eq("Kapitaelchen: raketenantrieb", odd.search("raketenantrieb", ItemCategory.CUSTOM).size(), 1);
		eq("Glyphe stoert nicht: schwert", odd.search("schwert", ItemCategory.CUSTOM).size(), 1);
		eq("kyrillisches s: spawner", odd.search("spawner", ItemCategory.CUSTOM).size(), 1);
		eq("Umlaut: turkis", odd.search("turkis", ItemCategory.CUSTOM).size(), 1);
		eq("Umlaut geschrieben: tuerkis", odd.search("türkis", ItemCategory.CUSTOM).size(), 1);
		eq("Kollektion: piraten", odd.search("piraten", ItemCategory.CUSTOM).size(), 1);
		eq("Kollektion: kollektion findet alle", odd.search("kollektion", ItemCategory.CUSTOM).size(), 4);

		System.out.println("== Platzhalter-Verlauf (Kurvenform) ==");

		ItemCatalog chartCatalog = ItemCatalog.of(List.of(
				new CatalogEntry("minecraft:elytra", "minecraft:elytra", "Elytra", "Elytra",
						null, ItemCategory.VANILLA, null, true, true),
				new CatalogEntry("collection.crystal.sword", "minecraft:netherite_sword",
						"Kristallschwert", "Kristallschwert",
						7100, ItemCategory.CUSTOM, "Kristall", true, true),
				new CatalogEntry("minecraft:stone", "minecraft:stone", "Stein", "Stein",
						null, ItemCategory.VANILLA, null, true, true)));

		DemoValueProvider chartStub = new DemoValueProvider(chartCatalog.asMap());
		boolean shapeOk = true;
		boolean endsOnValue = true;
		boolean hasGap = false;
		double worstRatio = 0;

		for (CatalogEntry entry : chartCatalog.asMap().values()) {
			long today = chartStub.valueOf(entry.id()).orElseThrow().amount();

			for (int days : new int[] {7, 30, 90}) {
				ValueHistory history = chartStub.historyOf(entry.id(), days).orElseThrow();

				if (history.points().size() != days) {
					shapeOk = false;
				}

				for (ValuePoint point : history.points()) {
					if (!point.known()) {
						hasGap = true;
						continue;
					}

					double ratio = point.value() / (double) today;
					worstRatio = Math.max(worstRatio, Math.max(ratio, 1 / ratio));

					if (ratio > 3 || ratio < 1.0 / 3) {
						shapeOk = false;
					}
				}

				ValuePoint last = history.points().get(history.points().size() - 1);

				if (!last.known() || last.value() != today) {
					endsOnValue = false;
				}
			}
		}

		eq("Kurve bleibt im Korridor", shapeOk, true);
		eq("letzter Tag ist der heutige Wert", endsOnValue, true);
		eq("Tage ohne Handel kommen vor", hasGap, true);
		System.out.printf("  ..   groesster Ausschlag: Faktor %.2f%n", worstRatio);

		System.out.println("== Zwischenspeicher-Format (Seiten mit Kennung) ==");

		MarketItem diamant = new MarketItem("standard:DIAMOND", "STANDARD", "DIAMOND", null,
				"Diamant", 10644, 4867, 380.0, 395.5);

		List<MarketApi.Page> seiten = List.of(
				new MarketApi.Page(0, "\"abc123\"", List.of(diamant)),
				new MarketApi.Page(100, null, List.of(diamant)));

		com.google.gson.Gson gson = new com.google.gson.Gson();
		String json = gson.toJson(seiten);
		java.lang.reflect.Type typ =
				new com.google.gson.reflect.TypeToken<List<MarketApi.Page>>() {}.getType();
		List<MarketApi.Page> zurueck = gson.fromJson(json, typ);

		eq("Seitenzahl ueberlebt", zurueck.size(), 2);
		eq("Offset ueberlebt", zurueck.get(1).offset(), 100);
		eq("Kennung ueberlebt", zurueck.get(0).etag(), "\"abc123\"");
		eq("Seite ohne Kennung bleibt ohne", zurueck.get(1).etag(), null);
		eq("Item ueberlebt", zurueck.get(0).items().get(0).key(), "standard:DIAMOND");
		eq("Preis ueberlebt", zurueck.get(0).items().get(0).medianPrice(), 380.0);
		eq("Verkaufszahl ueberlebt", zurueck.get(0).items().get(0).transactions(), 10644);

		System.out.println("== Resize-Mathematik (Griff-Position hin und zurueck) ==");

		final int SLOT = 18, MARGIN = 4, ROW_HEIGHT = 12, GRIP = 8;
		final int headerHeight = ROW_HEIGHT + 2 + ROW_HEIGHT + 4;
		final int footerHeight = ROW_HEIGHT + 2;
		final int chrome = MARGIN * 2 + headerHeight + footerHeight;

		boolean roundTripOk = true;

		for (int columns = 5; columns <= 20; columns++) {
			for (int rows = 2; rows <= 40; rows++) {
				int width = columns * SLOT + MARGIN * 2;
				int height = chrome + rows * SLOT;

				int gripX = width - GRIP - 1;
				int gripY = height - GRIP - 1;

				int hChrome = width - columns * SLOT;
				int vChrome = height - rows * SLOT;

				int backColumns = (int) Math.round((gripX - hChrome + GRIP + 1) / (double) SLOT);
				int backRows = (int) Math.round((gripY - vChrome + GRIP + 1) / (double) SLOT);

				if (backColumns != columns || backRows != rows) {
					roundTripOk = false;
					System.out.println("  FAIL " + columns + "x" + rows
							+ " -> " + backColumns + "x" + backRows);
				}
			}
		}

		eq("Griff-Umrechnung fuer alle Groessen", roundTripOk, true);

		System.out.println(fails == 0
				? "\nAlle Checks ok."
				: "\n" + fails + " Checks fehlgeschlagen.");
		System.exit(fails == 0 ? 0 : 1);
	}
}
