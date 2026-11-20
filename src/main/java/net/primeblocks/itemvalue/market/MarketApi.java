package net.primeblocks.itemvalue.market;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import net.primeblocks.itemvalue.PrimeItemValueClient;

/** Blocking market API requests. Call these methods from a background thread. */
public final class MarketApi {

	// The API caps catalog pages at 100 items.
	private static final int PAGE_SIZE = 100;

	private static final int OVERVIEW_PAGE_SIZE = 250;

	private static final int MAX_OFFSET = 2000;

	private static final long PAGE_PAUSE_MILLIS = 150;

	private static final int BUDGET_FLOOR = 6;

	private static final long BUDGET_WAIT_MILLIS = 10_000;

	private static final Gson GSON = new Gson();

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private final String base;
	private final String userAgent;

	// No requests are sent until the Retry-After deadline has passed.
	private volatile long blockedUntil;

	private volatile int remaining = -1;

	private volatile boolean budgetWarned;

	public MarketApi(String baseUrl, String modVersion) {
		this.base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.userAgent = "PrimeItemValue/" + modVersion + " (Minecraft client mod)";
	}

	public boolean blocked() {
		return System.currentTimeMillis() < blockedUntil;
	}

	public long blockedSeconds() {
		return Math.max(0, (blockedUntil - System.currentTimeMillis()) / 1000);
	}

	public int remaining() {
		return remaining;
	}

	public List<MarketItem> items(String serverGroup, String period) {
		List<MarketItem> all = new ArrayList<>(2000);

		forEachPage(serverGroup, period, Map.of(), (page, fresh) -> all.addAll(page.items()));

		return all;
	}

	public record Page(int offset, String etag, List<MarketItem> items) {
	}

	// Reuse ETags from known pages. The callback runs on the calling thread.
	public boolean forEachPage(String serverGroup, String period, Map<Integer, Page> known,
			java.util.function.BiConsumer<Page, Boolean> onPage) {
		int offset = 0;

		while (offset <= MAX_OFFSET) {
			String url = base + "/api/public/v1/market/items"
					+ "?serverGroup=" + encode(serverGroup)
					+ "&period=" + encode(period)
					+ "&limit=" + PAGE_SIZE
					+ "&offset=" + offset;

			Page cached = known.get(offset);
			Fetched<ItemPage> fetched = request(url, ItemPage.class,
					cached == null ? null : cached.etag());

			if (fetched.status() == 304) {
				if (cached == null) {

					return false;
				}

				onPage.accept(cached, false);

				if (cached.items().size() < PAGE_SIZE) {
					return true;
				}

				offset += PAGE_SIZE;
				pause();
				continue;
			}

			ItemPage page = fetched.value();

			if (page == null || page.items == null) {

				return false;
			}

			onPage.accept(new Page(offset, fetched.etag(), page.items), true);

			if (page.items.size() < PAGE_SIZE) {
				return true;
			}

			offset += PAGE_SIZE;
			pause();
		}

		return true;
	}

	// The overview endpoint supplies preset names missing from the versioned endpoint.
	public Map<String, String> presetNames(String serverGroup, String period) {
		Map<String, String> names = new HashMap<>();
		int offset = 0;
		int days = days(period);

		while (offset <= MAX_OFFSET) {
			String url = base + "/api/items/overview"
					+ "?serverGroup=" + encode(serverGroup)
					+ "&days=" + days
					+ "&limit=" + OVERVIEW_PAGE_SIZE
					+ "&offset=" + offset;

			OverviewEntry[] page = get(url, OverviewEntry[].class);

			if (page == null) {
				break;
			}

			for (OverviewEntry entry : page) {
				if (entry.key != null && entry.appliedPresetName != null
						&& !entry.appliedPresetName.isBlank()) {
					names.put(entry.key, entry.appliedPresetName.trim());
				}
			}

			if (page.length < OVERVIEW_PAGE_SIZE) {
				break;
			}

			offset += OVERVIEW_PAGE_SIZE;
			pause();
		}

		return names;
	}

	private static int days(String period) {
		return switch (period) {
			case "24h" -> 1;
			case "7d" -> 7;
			case "90d" -> 90;
			case "180d" -> 180;
			case "365d" -> 365;
			default -> 30;
		};
	}

	// A 404 means no trades in this period, not a failed request.
	public enum SeriesStatus {
		OK,
		NONE_IN_PERIOD,
		FAILED
	}

	public record SeriesResult(SeriesStatus status, List<MarketPoint> points) {

		public static SeriesResult of(SeriesStatus status) {
			return new SeriesResult(status, List.of());
		}
	}

	public SeriesResult timeseries(String key, String serverGroup, String period) {
		String url = base + "/api/public/v1/market/items/" + pathSegment(key) + "/timeseries"
				+ "?serverGroup=" + encode(serverGroup)
				+ "&period=" + encode(period);

		Fetched<Series> fetched = request(url, Series.class, null);

		if (fetched.status() == 404) {
			return SeriesResult.of(SeriesStatus.NONE_IN_PERIOD);
		}

		Series series = fetched.value();

		if (series == null || series.points == null || series.points.isEmpty()) {
			return SeriesResult.of(SeriesStatus.FAILED);
		}

		return new SeriesResult(SeriesStatus.OK, series.points);
	}

	private <T> T get(String url, Class<T> type) {
		Fetched<T> fetched = request(url, type, null);

		if (fetched.status() == 404) {
			PrimeItemValueClient.LOGGER.warn("Markt-API antwortete 404 auf {}", shorten(url));
		}

		return fetched.value();
	}

	private record Fetched<T>(T value, int status, String etag) {

		static <T> Fetched<T> nothing(int status) {
			return new Fetched<>(null, status, null);
		}
	}

	private <T> Fetched<T> request(String url, Class<T> type, String knownEtag) {
		if (blocked()) {
			return Fetched.nothing(0);
		}

		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", userAgent)
					.header("Accept", "application/json")

					.header("Accept-Encoding", "gzip")
					.timeout(Duration.ofSeconds(45))
					.GET();

			if (knownEtag != null && !knownEtag.isBlank()) {
				builder.header("If-None-Match", knownEtag);
			}

			HttpResponse<byte[]> response = http.send(builder.build(),
					HttpResponse.BodyHandlers.ofByteArray());

			recordBudget(response.headers());

			int status = response.statusCode();
			String etag = response.headers().firstValue("etag").orElse(knownEtag);

			if (status == 304) {
				return new Fetched<>(null, status, etag);
			}

			if (status == 429) {
				recordLockout(response.headers(), decode(response));
				return Fetched.nothing(status);
			}

			if (status != 200) {

				if (status != 404) {
					PrimeItemValueClient.LOGGER.warn("Markt-API antwortete {} auf {}",
							status, shorten(url));
				}

				return Fetched.nothing(status);
			}

			return new Fetched<>(GSON.fromJson(decode(response), type), status, etag);
		} catch (Exception failure) {

			PrimeItemValueClient.LOGGER.warn("Markt-API nicht erreichbar ({}): {}",
					shorten(url), failure.toString());
			return Fetched.nothing(0);
		}
	}

	private static String decode(HttpResponse<byte[]> response) throws java.io.IOException {
		byte[] raw = response.body();

		if (raw == null || raw.length == 0) {
			return "";
		}

		boolean gzipped = response.headers().firstValue("content-encoding")
				.map(value -> value.toLowerCase(Locale.ROOT).contains("gzip"))
				.orElse(false);

		if (!gzipped) {
			return new String(raw, StandardCharsets.UTF_8);
		}

		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private void recordBudget(HttpHeaders headers) {
		java.util.Optional<String> reported = headers.firstValue("x-ratelimit-remaining");

		if (reported.isEmpty()) {
			if (remaining > 0) {
				remaining--;
			}

			return;
		}

		try {
			remaining = Integer.parseInt(reported.get().trim());
		} catch (NumberFormatException ignored) {

			if (remaining > 0) {
				remaining--;
			}
		}
	}

	private void recordLockout(HttpHeaders headers, String body) {
		long seconds = headers.firstValue("retry-after")
				.map(MarketApi::parseSeconds)
				.orElse(0L);

		try {
			PublicError error = GSON.fromJson(body, PublicError.class);

			if (error != null && error.retryAfterSeconds != null) {
				seconds = Math.max(seconds, error.retryAfterSeconds);
			}
		} catch (Exception ignored) {

		}

		if (seconds <= 0) {
			seconds = 60;
		}

		blockedUntil = System.currentTimeMillis() + (seconds + 2) * 1000;

		PrimeItemValueClient.LOGGER.warn(
				"Markt-API bremst: {} Sekunden Pause, es wird nichts nachgefordert", seconds);
	}

	private static long parseSeconds(String raw) {
		try {
			return Long.parseLong(raw.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void pause() {
		long millis = PAGE_PAUSE_MILLIS;

		if (remaining >= 0 && remaining <= BUDGET_FLOOR) {
			millis = BUDGET_WAIT_MILLIS;

			if (!budgetWarned) {
				budgetWarned = true;
				PrimeItemValueClient.LOGGER.info(
						"Nur noch {} Anfragen im Fenster frei — der Abgleich macht langsamer"
								+ " weiter, statt eine Sperre zu riskieren", remaining);
			}
		} else if (remaining > BUDGET_FLOOR) {
			budgetWarned = false;
		}

		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String pathSegment(String value) {
		return encode(value).replace("%3A", ":").replace("+", "%20");
	}

	private static String shorten(String url) {
		int cut = url.indexOf("/api/");
		return cut < 0 ? url : url.substring(cut);
	}

	private static final class ItemPage {
		List<MarketItem> items;
	}

	private static final class OverviewEntry {
		String key;
		String appliedPresetName;
	}

	private static final class Series {
		List<MarketPoint> points;
	}

	private static final class PublicError {
		@SerializedName("retryAfterSeconds")
		Long retryAfterSeconds;
	}

	public static String period(int days) {
		return switch (days) {
			case 1 -> "24h";
			case 7 -> "7d";
			case 90 -> "90d";
			case 180 -> "180d";
			case 365 -> "365d";
			default -> "30d";
		};
	}

	static String lower(String value) {
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}
}
