import java.util.concurrent.atomic.AtomicLong;

public final class StripedCollector implements MetricsCollector {

    private final long[] buckets = new long[256];
    private final Object[] locks = new Object[16];
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong();

    public StripedCollector() {
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
    }

    @Override
    public void record(long value) {
        int bucket = (int) Math.min(value / 4, 255);
        synchronized (locks[bucket % locks.length]) {
            buckets[bucket]++;
        }

        count.incrementAndGet();
        sum.addAndGet(value);

        long currentMin = min.get();
        while (value < currentMin && !min.compareAndSet(currentMin, value)) {
            currentMin = min.get();
        }
        long currentMax = max.get();
        while (value > currentMax && !max.compareAndSet(currentMax, value)) {
            currentMax = max.get();
        }
    }

    @Override
    public Snapshot snapshot() {
        long[] copy = new long[buckets.length];
        for (int stripe = 0; stripe < locks.length; stripe++) {
            synchronized (locks[stripe]) {
                for (int bucket = stripe; bucket < buckets.length; bucket += locks.length) {
                    copy[bucket] = buckets[bucket];
                }
            }
        }

        long snapshotCount = count.get();
        return new Snapshot(
            copy,
            snapshotCount,
            sum.get(),
            min.get(),
            max.get(),
            percentile(copy, snapshotCount, 50),
            percentile(copy, snapshotCount, 99)
        );
    }

    private static long percentile(long[] buckets, long count, long percentage) {
        if (count == 0) {
            return 0;
        }

        long rank = (long) Math.ceil((double) count * percentage / 100);
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
