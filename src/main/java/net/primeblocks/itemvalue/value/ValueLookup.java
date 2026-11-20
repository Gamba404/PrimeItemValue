package net.primeblocks.itemvalue.value;

import java.util.Optional;

import net.minecraft.world.item.ItemStack;

import net.primeblocks.itemvalue.ItemValueConfig;
import net.primeblocks.itemvalue.catalog.CatalogEntry;
import net.primeblocks.itemvalue.catalog.CatalogManager;
import net.primeblocks.itemvalue.catalog.ItemCatalog;
import net.primeblocks.itemvalue.catalog.ItemIdentity;

/** Shares price lookups between the panel and tooltips. */
public final class ValueLookup {
	private final CatalogManager catalogManager;
	private final ItemValueConfig config;

	private volatile ValueProvider marketProvider;

	public ValueLookup(CatalogManager catalogManager, ItemValueConfig config) {
		this.catalogManager = catalogManager;
		this.config = config;
	}

	public void setProvider(ValueProvider provider) {
		this.marketProvider = provider;
	}

	public ValueProvider provider() {
		ValueProvider provider = marketProvider;
		return provider != null ? provider : catalogManager.demoProvider();
	}

	public ItemCatalog catalog() {
		return catalogManager.catalog();
	}

	public Optional<ItemValue> forStack(ItemStack stack) {
		String id = ItemIdentity.of(stack, config.customDataKey);

		if (id == null) {
			return Optional.empty();
		}

		ValueProvider source = provider();
		Optional<ItemValue> direct = source.valueOf(id);

		if (direct.isPresent()) {
			return direct;
		}

		// Keep the vanilla base price as a fallback for unknown custom items.
		String registryId = ItemIdentity.registryId(stack);
		return registryId.equals(id) ? Optional.empty() : source.valueOf(registryId);
	}

	public Optional<ItemValue> forEntry(CatalogEntry entry) {
		return provider().valueOf(entry.id());
	}

	public Optional<ValueHistory> historyFor(CatalogEntry entry, int days) {
		return provider().historyOf(entry.id(), days);
	}
}
