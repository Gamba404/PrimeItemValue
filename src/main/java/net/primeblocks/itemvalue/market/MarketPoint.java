package net.primeblocks.itemvalue.market;

import com.google.gson.annotations.SerializedName;

/** A timeseries bucket with timestamps in Unix milliseconds. */
public record MarketPoint(
		Double from,
		Double to,
		int transactions,
		@SerializedName("medianPrice") Double medianPrice,
		@SerializedName("minPrice") Double minPrice,
		@SerializedName("maxPrice") Double maxPrice) {

	public boolean priced() {
		return medianPrice != null && medianPrice > 0;
	}
}
