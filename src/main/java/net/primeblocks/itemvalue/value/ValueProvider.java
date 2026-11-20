package net.primeblocks.itemvalue.value;

import java.util.Optional;

/** A source of unit prices and optional price histories. */
public interface ValueProvider {

	Optional<ItemValue> valueOf(String id);

	boolean ready();

	String sourceLabel();

	// Example values must be identified in tooltips as well as the panel.
	default boolean placeholder() {
		return false;
	}

	default Optional<ValueHistory> historyOf(String id, int days) {
		return Optional.empty();
	}

	default long throttledSeconds() {
		return 0;
	}

	default boolean historyLoading(String id, int days) {
		return false;
	}
}
