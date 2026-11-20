package net.primeblocks.itemvalue.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Indexes items by ID and filters them by category and search text. */
public final class ItemCatalog {
	private final List<Indexed> indexed;
	private final Map<String, CatalogEntry> byId;
	private final Map<ItemCategory, Integer> counts;

	private ItemCatalog(List<Indexed> indexed, Map<String, CatalogEntry> byId) {
		this.indexed = indexed;
		this.byId = byId;
		this.counts = countByCategory(indexed);
	}

	private static Map<ItemCategory, Integer> countByCategory(List<Indexed> indexed) {
		Map<ItemCategory, Integer> counts = new EnumMap<>(ItemCategory.class);

		for (ItemCategory category : ItemCategory.values()) {
			counts.put(category, 0);
		}

		for (Indexed candidate : indexed) {
			counts.merge(candidate.entry().category(), 1, Integer::sum);
		}

		return counts;
	}

	public static ItemCatalog of(List<CatalogEntry> entries) {
		List<Indexed> indexed = new ArrayList<>(entries.size());
		Map<String, CatalogEntry> byId = new LinkedHashMap<>(entries.size() * 2);

		for (CatalogEntry entry : entries) {

			if (byId.put(entry.id(), entry) == null) {
				indexed.add(new Indexed(entry, entry.searchText()));
			} else {
				replace(indexed, entry);
			}
		}

		return new ItemCatalog(indexed, byId);
	}

	public static ItemCatalog empty() {
		return new ItemCatalog(List.of(), Map.of());
	}

	private static void replace(List<Indexed> indexed, CatalogEntry entry) {
		for (int i = 0; i < indexed.size(); i++) {
			if (indexed.get(i).entry().id().equals(entry.id())) {
				indexed.set(i, new Indexed(entry, entry.searchText()));
				return;
			}
		}
	}

	public int size() {
		return indexed.size();
	}

	public CatalogEntry byId(String id) {
		return byId.get(id);
	}

	public Map<String, CatalogEntry> asMap() {
		return Collections.unmodifiableMap(byId);
	}

	public int countOf(ItemCategory category) {
		return counts.getOrDefault(category, 0);
	}

	// Every query token must match part of the normalized item text.
	public List<CatalogEntry> search(String query, ItemCategory category) {
		String[] tokens = tokenise(query);
		List<CatalogEntry> hits = new ArrayList<>();

		for (Indexed candidate : indexed) {
			if (candidate.entry().category() != category) {
				continue;
			}

			if (matchesAll(candidate.searchText(), tokens)) {
				hits.add(candidate.entry());
			}
		}

		return hits;
	}

	private static String[] tokenise(String query) {
		if (query == null || query.isBlank()) {
			return new String[0];
		}

		String normalised = CatalogEntry.normalise(query);
		return normalised.isEmpty() ? new String[0] : normalised.split(" +");
	}

	private static boolean matchesAll(String haystack, String[] tokens) {
		for (String token : tokens) {
			if (!haystack.contains(token)) {
				return false;
			}
		}

		return true;
	}

	private record Indexed(CatalogEntry entry, String searchText) {
	}
}
