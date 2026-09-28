void main() throws InterruptedException {
    Bench.measureSeries("thread_local", ThreadLocalCollector::new, 1, 2, 4, 8, 14, 16);
}
