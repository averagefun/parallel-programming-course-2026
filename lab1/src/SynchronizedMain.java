void main() throws InterruptedException {
    Bench.measureSeries("synchronized", SynchronizedCollector::new, 1, 2, 4, 8, 16);
}
