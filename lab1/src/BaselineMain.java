void main() throws InterruptedException {
    Bench.measureSeries("baseline", SingleThreadCollector::new, 1);
}
