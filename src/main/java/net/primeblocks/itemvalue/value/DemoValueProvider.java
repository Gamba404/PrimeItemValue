package net.primeblocks.itemvalue.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.primeblocks.itemvalue.Lang;
import net.primeblocks.itemvalue.catalog.CatalogEntry;
import net.primeblocks.itemvalue.catalog.ItemCategory;

/** Generates repeatable example prices and histories, always marked as placeholders. */
public final class DemoValueProvider implements ValueProvider {
	private final Map<String, CatalogEntry> entries;

	public DemoValueProvider(Map<String, CatalogEntry> entries) {
		this.entries = entries;
	}

	@Override
	public Optional<ItemValue> valueOf(String id) {
		CatalogEntry entry = entries.get(id);

		if (entry == null || !entry.tradeable()) {
			return Optional.empty();
		}

		return Optional.of(new ItemValue(amountFor(entry), Currency.COINS, confidenceFor(entry)));
	}

	@Override
	public boolean ready() {
		return true;
	}

	@Override
	public String sourceLabel() {
		return Lang.get("primeitemvalue.source.placeholder");
	}

	@Override
	public boolean placeholder() {
		return true;
	}

	@Override
	public Optional<ValueHistory> historyOf(String id, int days) {
		CatalogEntry entry = entries.get(id);

		if (entry == null || !entry.tradeable()) {
			return Optional.empty();
		}

		long today = amountFor(entry);
		List<ValuePoint> points = new ArrayList<>(days);

		long seed = (entry.id().hashCode() & 0xFFFFFFFFL) ^ 0x5DEECE66DL;

		long start = Math.max(1, today * (65 + Math.floorMod(seed >> 11, 60)) / 100);
		long level = start;

		long floor = Math.max(1, Math.min(start, today) / 2);
		long ceiling = Math.max(floor + 1, Math.max(start, today) * 2);

		for (int day = 0; day < days; day++) {
			seed = (seed ^ 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
			seed ^= seed >>> 29;

			if (Math.floorMod(seed >> 7, 12) == 0) {
				points.add(ValuePoint.none());
				continue;
			}

			long step = Math.max(1, today / 40);
			long noise = Math.floorMod(seed >> 13, step * 2 + 1) - step;

			long trend = start + (today - start) * day / Math.max(1, days - 1);
			long pull = (trend - level) / 4;

			level = Math.clamp(level + pull + noise, floor, ceiling);
			points.add(new ValuePoint(level, 1 + (int) Math.floorMod(seed >> 19, 40)));
		}

		// The final chart value must match the displayed unit price.
		points.set(points.size() - 1, new ValuePoint(today,
				1 + (int) Math.floorMod(seed >> 23, 40)));

		return Optional.of(ValueHistory.of(Currency.COINS, points));
	}

	private static long amountFor(CatalogEntry entry) {

		long seed = entry.id().hashCode() & 0xFFFFFFFFL;
		seed = (seed ^ 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
		seed ^= seed >>> 31;
		long spread = Math.abs(seed % 1000);

		long amount;

		if (entry.category() == ItemCategory.CUSTOM) {
			amount = 50_000L + spread * 4_950L;
		} else {
			amount = 1L + spread * 20L;
		}

		if (!entry.obtainable()) {
			amount *= 3;
		}

		return amount;
	}

	private static Confidence confidenceFor(CatalogEntry entry) {
		if (!entry.obtainable()) {
			return Confidence.LOW;
		}

		return entry.category() == ItemCategory.CUSTOM ? Confidence.MEDIUM : Confidence.HIGH;
	}
}
