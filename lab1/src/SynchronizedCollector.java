public final class SynchronizedCollector implements MetricsCollector {

    private final SingleThreadCollector delegate = new SingleThreadCollector();

    @Override
    public synchronized void record(long value) {
        delegate.record(value);
    }

    @Override
    public synchronized Snapshot snapshot() {
        return delegate.snapshot();
    }
}
