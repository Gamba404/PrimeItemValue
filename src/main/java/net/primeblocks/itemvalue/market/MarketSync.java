package net.primeblocks.itemvalue.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import net.primeblocks.itemvalue.ItemValueConfig;
import net.primeblocks.itemvalue.PrimeItemValueClient;
import net.primeblocks.itemvalue.catalog.CatalogManager;
import net.primeblocks.itemvalue.value.ValueLookup;

/** Loads market data in the background and updates the active catalog. */
public final class MarketSync {

	private final ItemValueConfig config;
	private final ValueLookup lookup;
	private final CatalogManager catalogManager;
	private final MarketApi api;

	private final ExecutorService catalogWorker = createWorker("PrimeItemValue market");

	// Keep history requests independent of the longer catalog sync.
	private final ExecutorService historyWorker = createWorker("PrimeItemValue history");

	private static ExecutorService createWorker(String name) {
		return Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, name);
			thread.setDaemon(true);
			return thread;
		});
	}

	// Ten client ticks are half a second.
	private static final int DETECTION_EVERY_TICKS = 10;

	private static final int DETECTION_ATTEMPTS = 16;

	public MarketSync(ItemValueConfig config, ValueLookup lookup, CatalogManager catalogManager,
			String modVersion) {
		this.config = config;
		this.lookup = lookup;
		this.catalogManager = catalogManager;
		this.api = new MarketApi(config.apiBaseUrl, modVersion);
	}

	private volatile String syncedGroup;

	private final java.util.Map<String, Long> lastAttempt = new java.util.concurrent.ConcurrentHashMap<>();

	// A failed sync must not restart on every tick.
	private static final long RETRY_PAUSE_MILLIS = 60_000;

	private static final int NAMES_BUDGET = 25;

	private final AtomicBoolean running = new AtomicBoolean();

	private int waitedAttempts;

	private int sinceDetection;

	private boolean fallbackAnnounced;

	public void tick() {
		if (running.get()) {
			return;
		}

		if (++sinceDetection < DETECTION_EVERY_TICKS) {
			return;
		}

		sinceDetection = 0;

		String wanted = wantedGroup();

		if (wanted == null) {

			if (waitedAttempts <= DETECTION_ATTEMPTS) {
				waitedAttempts++;
				return;
			}

			wanted = ServerGroup.FALLBACK;

			if (!fallbackAnnounced) {
				fallbackAnnounced = true;
				PrimeItemValueClient.LOGGER.info(
						"Kein CityBuild erkannt, es wird {} angenommen", wanted);
			}
		} else {

			waitedAttempts = 0;
			fallbackAnnounced = false;
		}

		if (wanted.equals(syncedGroup)) {
			return;
		}

		Long attempted = lastAttempt.get(wanted);

		if (attempted != null && System.currentTimeMillis() - attempted < RETRY_PAUSE_MILLIS) {
			return;
		}

		waitedAttempts = 0;
		String target = wanted;

		if (running.compareAndSet(false, true)) {
			catalogWorker.execute(() -> {
				try {
					run(target);
				} finally {
					running.set(false);
				}
			});
		}
	}

	public void resync() {
		syncedGroup = null;
		waitedAttempts = 0;
		sinceDetection = DETECTION_EVERY_TICKS;
		fallbackAnnounced = false;
		lastAttempt.clear();
	}

	public String group() {
		String synced = syncedGroup;
		return synced != null ? synced : wantedGroupOrFallback();
	}

	public boolean syncing() {
		return running.get();
	}

	private String wantedGroup() {
		String configured = config.database;

		if (configured != null && !configured.isBlank()) {
			return configured.trim().toLowerCase(java.util.Locale.ROOT);
		}

		return ServerGroup.detect(net.minecraft.client.Minecraft.getInstance());
	}

	private String wantedGroupOrFallback() {
		String wanted = wantedGroup();
		return wanted == null ? ServerGroup.FALLBACK : wanted;
	}

	private void run(String group) {
		String period = MarketApi.period(config.marketPeriodDays);

		MarketCache.Snapshot cached = MarketCache.load(group, period);

		if (!cached.items().isEmpty()) {
			publish(cached.items(), cached.names(), group, "Zwischenspeicher", 0);
		}

		long started = System.currentTimeMillis();
		List<MarketApi.Page> pages = new ArrayList<>(20);
		List<MarketItem> fresh = new ArrayList<>(2000);
		boolean[] changed = {false};

		boolean complete = api.forEachPage(group, period, cached.byOffset(), (page, isFresh) -> {
			pages.add(page);
			fresh.addAll(page.items());

			if (isFresh) {
				changed[0] = true;
			}

			if (cached.items().isEmpty() && fresh.size() == page.items().size()) {
				publish(fresh, Map.of(), group, "erste Seite", 0);
			}
		});

		lastAttempt.put(group, System.currentTimeMillis());

		if (fresh.isEmpty()) {
			PrimeItemValueClient.LOGGER.warn(
					"Keine Marktdaten erhalten{} — es bleibt bei dem, was da ist",
					api.blocked() ? " (API bremst noch " + api.blockedSeconds() + "s)" : "");
			return;
		}

		if (complete && !changed[0] && !cached.items().isEmpty() && !cached.names().isEmpty()) {
			PrimeItemValueClient.LOGGER.info(
					"Marktdaten unverändert: {} Items für {} bestätigt, {} ms",
					fresh.size(), group, System.currentTimeMillis() - started);

			syncedGroup = group;
			MarketCache.save(group, period, pages, cached.names());
			return;
		}

		Map<String, String> names = Map.of();
		int left = api.remaining();

		if (left >= 0 && left < NAMES_BUDGET) {
			PrimeItemValueClient.LOGGER.info(
					"Nur noch {} Anfragen frei — die echten Custom-Namen warten auf den"
							+ " nächsten Abgleich", left);
		} else {
			names = api.presetNames(group, period);
		}

		if (names.isEmpty() && !cached.names().isEmpty()) {

			names = cached.names();
		}

		publish(fresh, names, group, complete ? "API" : "API, unvollständig",
				System.currentTimeMillis() - started);

		if (!names.isEmpty()) {
			PrimeItemValueClient.LOGGER.info("{} echte Custom-Namen übernommen", names.size());
		}

		syncedGroup = group;

		if (complete) {
			MarketCache.save(group, period, pages, names);
		}
	}

	private volatile MarketValueProvider current;

	private void publish(List<MarketItem> items, Map<String, String> names, String group,
			String source, long millis) {
		MarketValueProvider provider = new MarketValueProvider(List.copyOf(items), api,
				historyWorker, group);
		provider.presetNames(names);

		MarketValueProvider previous = current;

		if (previous != null && group.equals(previous.serverGroup())) {
			provider.inherit(previous);
		}

		current = provider;

		lookup.setProvider(provider);
		catalogManager.useMarket(provider);

		long priced = items.stream().filter(MarketItem::priced).count();
		long custom = items.stream().filter(MarketItem::custom).count();

		PrimeItemValueClient.LOGGER.info(
				"Marktdaten ({}): {} Items, {} mit Preis, {} Custom, für {}{}",
				source, items.size(), priced, custom, group,
				millis > 0 ? ", " + millis + " ms" : "");
	}
}
