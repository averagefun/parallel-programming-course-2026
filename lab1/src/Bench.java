import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Bench {

    private static final int VALUE_COUNT = 1_048_576; // 2**20
    private static final int RUN_SECONDS = 5;
    private static final int TRIALS = 5;

    private static int[] generateValues() {
        double[] cumulative = new double[1023];
        double totalWeight = 0;
        for (int index = 0; index < cumulative.length; index++) {
            totalWeight += 1.0 / Math.pow(index + 1, 1.15);
            cumulative[index] = totalWeight;
        }
        for (int index = 0; index < cumulative.length; index++) {
            cumulative[index] /= totalWeight;
        }

        Random random = new Random(42);
        int[] values = new int[VALUE_COUNT];
        for (int index = 0; index < values.length; index++) {
            double sample = random.nextDouble();
            int low = 0;
            int high = cumulative.length - 1;
            while (low < high) {
                int middle = (low + high) / 2;
                if (sample < cumulative[middle]) {
                    high = middle;
                } else {
                    low = middle + 1;
                }
            }
            values[index] = low + 1;
        }
        return values;
    }

    private static double run(MetricsCollector collector, int[] values, int threadCount)
            throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean();
        long[] operations = new long[threadCount];
        Thread[] threads = new Thread[threadCount];

        for (int worker = 0; worker < threadCount; worker++) {
            int workerIndex = worker;
            threads[worker] = new Thread(() -> {
                long localCount = 0;
                int index = workerIndex * 1000 % values.length;
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                while (!stop.get()) {
                    collector.record(values[index]);
                    localCount++;
                    index++;
                    if (index == values.length) {
                        index = 0;
                    }
                }
                operations[workerIndex] = localCount;
            });
            threads[worker].start();
        }

        ready.await();
        long started = System.nanoTime();
        start.countDown();
        Thread.sleep(RUN_SECONDS * 1000L);
        stop.set(true);
        long stopped = System.nanoTime();

        for (Thread thread : threads) {
            thread.join();
        }

        long total = 0;
        for (long operationCount : operations) {
            total += operationCount;
        }
        return total * 1_000_000_000.0 / (stopped - started);
    }

    private static double measurePoint(MetricsCollector collector, int[] values, int threadCount)
            throws InterruptedException {
        run(collector, values, threadCount);
        double[] results = new double[TRIALS];
        for (int trial = 0; trial < results.length; trial++) {
            results[trial] = run(collector, values, threadCount);
        }

        Arrays.sort(results);
        Snapshot snapshot = collector.snapshot();
        long bucketTotal = Arrays.stream(snapshot.buckets()).sum();
        System.out.printf(
                "snapshot: count=%d buckets=%d sum=%d min=%d max=%d p50=%d p99=%d%n",
                snapshot.count(), bucketTotal, snapshot.sum(), snapshot.min(), snapshot.max(),
                snapshot.p50(), snapshot.p99()
        );
        return results[results.length / 2];
    }

    static void main() throws InterruptedException {
        int[] values = generateValues();
        SingleThreadCollector collector = new SingleThreadCollector();
        double baseline = measurePoint(collector, values, 1);
        System.out.printf("stage 0 baseline: %.2f million ops/s%n", baseline / 1_000_000);
    }
}
