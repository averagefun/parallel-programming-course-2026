import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class ThreadLocalCollector implements MetricsCollector {

    private static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(256);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong();
    }

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();
    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState state = new ThreadState();
        synchronized (listLock) {
            allStates.add(state);
        }
        return state;
    });

    @Override
    public void record(long value) {
        ThreadState state = myState.get();
        int bucket = (int) Math.min(value / 4, 255);

        state.buckets.setRelease(bucket, state.buckets.getPlain(bucket) + 1);
        state.count.setRelease(state.count.getPlain() + 1);
        state.sum.setRelease(state.sum.getPlain() + value);
        if (value < state.min.getPlain()) {
            state.min.setRelease(value);
        }
        if (value > state.max.getPlain()) {
            state.max.setRelease(value);
        }
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> states;
        synchronized (listLock) {
            states = new ArrayList<>(allStates);
        }

        long[] buckets = new long[256];
        long count = 0;
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = 0;
        for (ThreadState state : states) {
            for (int index = 0; index < buckets.length; index++) {
                buckets[index] += state.buckets.get(index);
            }
            count += state.count.get();
            sum += state.sum.get();
            min = Math.min(min, state.min.get());
            max = Math.max(max, state.max.get());
        }

        return new Snapshot(
            buckets, count, sum, min, max,
            percentile(buckets, count, 50),
            percentile(buckets, count, 99)
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
