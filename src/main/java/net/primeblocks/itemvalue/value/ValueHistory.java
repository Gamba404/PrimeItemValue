package net.primeblocks.itemvalue.value;

import java.util.List;

/** Daily values, oldest first. Missing trading days remain gaps. */
public record ValueHistory(Currency currency, List<ValuePoint> points, long low, long high,
		double change) {

	public static ValueHistory of(Currency currency, List<ValuePoint> points) {
		long low = Long.MAX_VALUE;
		long high = Long.MIN_VALUE;
		Long first = null;
		Long last = null;

		for (ValuePoint point : points) {
			if (!point.known()) {
				continue;
			}

			long value = point.value();
			low = Math.min(low, value);
			high = Math.max(high, value);

			if (first == null) {
				first = value;
			}

			last = value;
		}

		if (first == null) {
			return new ValueHistory(currency, points, 0, 0, 0);
		}

		double change = first == 0 ? 0 : (last - (double) first) / first;

		return new ValueHistory(currency, points, low, high, change);
	}

	public Long firstKnown() {
		for (ValuePoint point : points) {
			if (point.known()) {
				return point.value();
			}
		}

		return null;
	}

	public Long lastKnown() {
		for (int i = points.size() - 1; i >= 0; i--) {
			if (points.get(i).known()) {
				return points.get(i).value();
			}
		}

		return null;
	}

	public int trades() {
		int total = 0;

		for (ValuePoint point : points) {
			total += point.sampleSize();
		}

		return total;
	}

	public boolean empty() {
		return points.stream().noneMatch(ValuePoint::known);
	}
}
