package net.primeblocks.itemvalue.ui;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.item.ItemStack;

import net.primeblocks.itemvalue.catalog.CatalogEntry;
import net.primeblocks.itemvalue.catalog.ItemIdentity;

/** Reuses item stacks instead of creating them on every frame. */
public final class ItemStackCache {
	private final Map<String, ItemStack> cache = new HashMap<>();

	public ItemStack get(CatalogEntry entry) {
		return cache.computeIfAbsent(entry.id(), id -> ItemIdentity.toStack(entry));
	}

	public void clear() {
		cache.clear();
	}
}
