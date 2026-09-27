import java.util.Arrays;

public final class SingleThreadCollector implements MetricsCollector {

    private final long[] buckets = new long[256];
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max;

    @Override
    public void record(long value) {
        buckets[(int) Math.min(value / 4, 255)]++;
        count++;
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    @Override
    public Snapshot snapshot() {
        long[] copy = Arrays.copyOf(buckets, buckets.length);
        return new Snapshot(
            copy,
            count,
            sum,
            min,
            max,
            percentile(copy, count, 50),
            percentile(copy, count, 99)
        );
    }

    private static long percentile(long[] buckets, long count, long percentage) {
        if (count == 0) {
            return 0;
        }

        long rank = (long) Math.ceil((double) count * percentage / 100);
        // long rank = count / 100 * percentage + (count % 100 * percentage + 99) / 100;
        long cumulative = 0;
        for (int index = 0; index < buckets.length; index++) {
            cumulative += buckets[index];
            if (cumulative >= rank) {
                return index * 4L;
            }
        }
        return 0;
    }
}
