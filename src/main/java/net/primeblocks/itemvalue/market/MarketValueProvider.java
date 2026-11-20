package net.primeblocks.itemvalue.market;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import net.primeblocks.itemvalue.Lang;
import net.primeblocks.itemvalue.PrimeItemValueClient;
import net.primeblocks.itemvalue.value.Confidence;
import net.primeblocks.itemvalue.value.Currency;
import net.primeblocks.itemvalue.value.ItemValue;
import net.primeblocks.itemvalue.value.ValueHistory;
import net.primeblocks.itemvalue.value.ValuePoint;
import net.primeblocks.itemvalue.value.ValueProvider;

/** Provides market prices and caches item histories. */
public final class MarketValueProvider implements ValueProvider {

	private final Map<String, MarketItem> items;
	private final Map<String, String> aliases = new ConcurrentHashMap<>();
	private final Map<String, ValueHistory> histories = new ConcurrentHashMap<>();
	private final java.util.Set<String> loading = ConcurrentHashMap.newKeySet();

	private final Map<String, Long> failedAt = new ConcurrentHashMap<>();

	private static final long RETRY_PAUSE_MILLIS = 30_000;

	private final MarketApi api;
	private final Executor background;
	private final String serverGroup;

	private volatile Map<String, String> presetNames = Map.of();

	public MarketValueProvider(Collection<MarketItem> loaded, MarketApi api, Executor background,
			String serverGroup) {
		this.items = new ConcurrentHashMap<>();
		this.api = api;
		this.background = background;
		this.serverGroup = serverGroup;

		for (MarketItem item : loaded) {
			if (item.key() == null) {
				continue;
			}

			this.items.put(item.key(), item);

			// Map vanilla registry IDs to market keys such as standard:DIAMOND.
			if (!item.custom()) {
				String registry = item.registryId();

				if (registry != null) {
					this.aliases.putIfAbsent(registry, item.key());
				}
			}
		}
	}

	private String resolve(String id) {
		if (id == null) {
			return null;
		}

		String alias = aliases.get(id);
		return alias != null ? alias : id;
	}

	public String serverGroup() {
		return serverGroup;
	}

	public void inherit(MarketValueProvider previous) {
		if (previous == null || previous == this) {
			return;
		}

		histories.putAll(previous.histories);
	}

	public Collection<MarketItem> items() {
		return items.values();
	}

	public Map<String, String> presetNames() {
		return presetNames;
	}

	public void presetNames(Map<String, String> names) {
		this.presetNames = names == null ? Map.of() : names;
	}

	@Override
	public Optional<ItemValue> valueOf(String id) {
		MarketItem item = items.get(resolve(id));

		if (item == null || !item.priced()) {
			return Optional.empty();
		}

		return Optional.of(new ItemValue(Math.round(item.medianPrice()), Currency.COINS,
				confidenceFor(item)));
	}

	@Override
	public boolean ready() {
		return !items.isEmpty();
	}

	@Override
	public String sourceLabel() {
		return Lang.get("primeitemvalue.source.market", serverGroup);
	}

	@Override
	public Optional<ValueHistory> historyOf(String id, int days) {
		String key = resolve(id);
		String cacheKey = key + '@' + days;
		ValueHistory known = histories.get(cacheKey);

		if (known != null) {
			return Optional.of(known);
		}

		if (!items.containsKey(key)) {
			return Optional.empty();
		}

		// Do not turn an API cooldown into a cached history failure.
		if (api.blocked()) {
			return Optional.empty();
		}

		Long failed = failedAt.get(cacheKey);

		if (failed != null && System.currentTimeMillis() - failed < RETRY_PAUSE_MILLIS) {
			return Optional.empty();
		}

		// Allow only one pending request per item and time range.
		if (loading.add(cacheKey)) {
			background.execute(() -> fetch(key, days, cacheKey));
		}

		return Optional.empty();
	}

	@Override
	public boolean historyLoading(String id, int days) {
		return loading.contains(resolve(id) + '@' + days);
	}

	@Override
	public long throttledSeconds() {
		return api.blockedSeconds();
	}

	private static final int BUDGET_FLOOR = 10;

	private void fetch(String id, int days, String cacheKey) {
		try {

			int left = api.remaining();

			if (left >= 0 && left <= BUDGET_FLOOR && !api.blocked()) {
				PrimeItemValueClient.LOGGER.info(
						"Verlauf {}: nur noch {} Anfragen frei, kurz warten", id, left);
				Thread.sleep(4_000);
			}
			long started = System.currentTimeMillis();
			String period = MarketApi.period(days);
			MarketApi.SeriesResult result = api.timeseries(id, serverGroup, period);
			long millis = System.currentTimeMillis() - started;

			switch (result.status()) {
				case OK -> {
					failedAt.remove(cacheKey);
					ValueHistory history = convert(result.points());
					histories.put(cacheKey, history);

					long tradingDays = result.points().stream()
							.filter(MarketPoint::priced).count();

					PrimeItemValueClient.LOGGER.info(
							"Verlauf {} ({}): {} Tage, {} davon mit Handel, {} Verkäufe, {} ms",
							id, period, result.points().size(), tradingDays,
							history.trades(), millis);
				}

				case NONE_IN_PERIOD -> {

					failedAt.remove(cacheKey);
					histories.put(cacheKey, ValueHistory.of(Currency.COINS, List.of()));

					PrimeItemValueClient.LOGGER.info(
							"Verlauf {} ({}): in dem Zeitraum nicht gehandelt", id, period);
				}

				case FAILED -> {
					if (api.blocked()) {

						PrimeItemValueClient.LOGGER.info(
								"Verlauf {} ({}): wartet, API bremst noch {}s",
								id, period, api.blockedSeconds());
					} else {

						failedAt.put(cacheKey, System.currentTimeMillis());

						PrimeItemValueClient.LOGGER.warn(
								"Verlauf {} ({}): nichts erhalten", id, period);
					}
				}
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		} finally {

			loading.remove(cacheKey);
		}
	}

	private static ValueHistory convert(List<MarketPoint> points) {
		List<ValuePoint> converted = new ArrayList<>(points.size());

		for (MarketPoint point : points) {
			converted.add(point.priced()
					? new ValuePoint(Math.round(point.medianPrice()), point.transactions())
					: ValuePoint.none());
		}

		return ValueHistory.of(Currency.COINS, converted);
	}

	private static Confidence confidenceFor(MarketItem item) {
		int trades = item.transactionsFiltered();

		if (trades >= 50) {
			return Confidence.HIGH;
		}

		return trades >= 10 ? Confidence.MEDIUM : Confidence.LOW;
	}
}
