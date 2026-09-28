void main() throws InterruptedException {
    Bench.measureSeries("double_buffered", DoubleBufferedCollector::new, 1, 2, 4, 8, 14, 16);
}
