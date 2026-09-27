void main() throws InterruptedException {
    Bench.measureSeries("empty", EmptyLockCollector::new, 1, 2, 4, 8, 16);
}
