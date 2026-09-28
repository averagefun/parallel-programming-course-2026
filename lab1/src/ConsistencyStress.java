import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConsistencyStress {

    private static final int WRITERS = 4;
    private static final int SNAPSHOTS = 10_000;

    public static void run(MetricsCollector collector) throws InterruptedException {
        AtomicBoolean stop = new AtomicBoolean();
        CountDownLatch ready = new CountDownLatch(WRITERS);
        CountDownLatch start = new CountDownLatch(1);
        long[] recorded = new long[WRITERS];
        Thread[] writers = new Thread[WRITERS];

        for (int worker = 0; worker < WRITERS; worker++) {
            int workerIndex = worker;
            writers[worker] = new Thread(() -> {
                long localCount = 0;
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                while (!stop.get()) {
                    collector.record(1 + (localCount + workerIndex * 256) % 1023);
                    localCount++;
                }
                recorded[workerIndex] = localCount;
            });
            writers[worker].start();
        }

        ready.await();
        start.countDown();
        int less = 0;
        int greater = 0;
        try {
            for (int index = 0; index < SNAPSHOTS; index++) {
                Snapshot snapshot = collector.snapshot();
                long bucketTotal = Arrays.stream(snapshot.buckets()).sum();
                if (bucketTotal < snapshot.count()) {
                    less++;
                } else if (bucketTotal > snapshot.count()) {
                    greater++;
                }
            }
        } finally {
            stop.set(true);
            for (Thread writer : writers) {
                writer.join();
            }
        }

        long totalRecorded = Arrays.stream(recorded).sum();
        long finalCount = collector.snapshot().count();
        int broken = less + greater;
        System.out.printf(
            "broken=%d (%.2f%%), buckets<count=%d, buckets>count=%d, final_count=%d, recorded=%d, difference=%d%n",
            broken, broken * 100.0 / SNAPSHOTS, less, greater,
            finalCount, totalRecorded, finalCount - totalRecorded
        );
    }
}
