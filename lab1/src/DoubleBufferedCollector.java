import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class DoubleBufferedCollector implements MetricsCollector {

    private static final int NOWHERE = -1;

    private static final class ThreadBuffers {
        final long[][] buckets = new long[2][256];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = new long[2];
        final AtomicInteger inside = new AtomicInteger(NOWHERE);
    }

    private final AtomicInteger active = new AtomicInteger();
    private final Object snapshotLock = new Object();
    private final List<ThreadBuffers> allBuffers = new ArrayList<>();
    private final ThreadLocal<ThreadBuffers> myBuffers = ThreadLocal.withInitial(() -> {
        ThreadBuffers buffers = new ThreadBuffers();
        synchronized (snapshotLock) {
            allBuffers.add(buffers);
        }
        return buffers;
    });
    private final boolean recheckActive;

    private final long[] globalBuckets = new long[256];
    private long globalCount;
    private long globalSum;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax;

    public DoubleBufferedCollector() {
        this(true);
    }

    DoubleBufferedCollector(boolean recheckActive) {
        this.recheckActive = recheckActive;
    }

    @Override
    public void record(long value) {
        ThreadBuffers buffers = myBuffers.get();
        int buffer;
        while (true) {
            buffer = active.get();
            buffers.inside.set(buffer);
            if (!recheckActive || active.get() == buffer) {
                break;
            }
            buffers.inside.setRelease(NOWHERE);
        }

        int bucket = (int) Math.min(value / 4, 255);
        buffers.buckets[buffer][bucket]++;
        buffers.count[buffer]++;
        buffers.sum[buffer] += value;
        buffers.min[buffer] = Math.min(buffers.min[buffer], value);
        buffers.max[buffer] = Math.max(buffers.max[buffer], value);
        buffers.inside.setRelease(NOWHERE);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapshotLock) {
            int old = active.get();
            active.set(1 - old);

            for (ThreadBuffers buffers : allBuffers) {
                while (buffers.inside.get() == old) {
                    Thread.onSpinWait();
                }

                globalCount += buffers.count[old];
                globalSum += buffers.sum[old];
                globalMin = Math.min(globalMin, buffers.min[old]);
                globalMax = Math.max(globalMax, buffers.max[old]);
                for (int index = 0; index < globalBuckets.length; index++) {
                    globalBuckets[index] += buffers.buckets[old][index];
                }

                buffers.count[old] = 0;
                buffers.sum[old] = 0;
                buffers.min[old] = Long.MAX_VALUE;
                buffers.max[old] = 0;
                Arrays.fill(buffers.buckets[old], 0);
            }

            long[] copy = Arrays.copyOf(globalBuckets, globalBuckets.length);
            return new Snapshot(
                copy, globalCount, globalSum, globalMin, globalMax,
                percentile(copy, globalCount, 50),
                percentile(copy, globalCount, 99)
            );
        }
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
