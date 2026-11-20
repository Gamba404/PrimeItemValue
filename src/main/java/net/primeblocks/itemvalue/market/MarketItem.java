package net.primeblocks.itemvalue.market;

import com.google.gson.annotations.SerializedName;

/** A market item. Field names match the API response for Gson. */
public record MarketItem(
		String key,
		String kind,
		String material,
		@SerializedName("customModelData") Integer customModelData,
		String name,
		int transactions,
		@SerializedName("transactionsFiltered") int transactionsFiltered,
		@SerializedName("medianPrice") Double medianPrice,
		@SerializedName("meanPrice") Double meanPrice) {

	public String registryId() {
		return material == null ? null : "minecraft:" + material.toLowerCase(java.util.Locale.ROOT);
	}

	public boolean custom() {
		return customModelData != null || (kind != null && kind.startsWith("CUSTOM"));
	}

	public boolean priced() {
		return medianPrice != null && medianPrice > 0;
	}
}
