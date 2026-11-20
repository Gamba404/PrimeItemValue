package net.primeblocks.itemvalue.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Builds the vanilla catalog from the client item registry. */
public final class VanillaCatalogSource {

	private VanillaCatalogSource() {
	}

	public static List<CatalogEntry> load() {
		List<CatalogEntry> entries = new ArrayList<>(1600);

		for (Map.Entry<ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
			Item item = entry.getValue();

			if (item == Items.AIR) {
				continue;
			}

			String id = entry.getKey().identifier().toString();
			String name = new ItemStack(item).getHoverName().getString();

			String alias = ItemNames.of(item.getDescriptionId());

			entries.add(new CatalogEntry(
					id,
					id,
					name,
					name,
					null,
					ItemCategory.VANILLA,
					null,
					true,
					true,
					alias));
		}

		return entries;
	}
}
