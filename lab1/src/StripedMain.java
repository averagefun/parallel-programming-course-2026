void main() throws InterruptedException {
    Bench.measureSeries("striped", StripedCollector::new, 1, 2, 4, 8, 16);
}
