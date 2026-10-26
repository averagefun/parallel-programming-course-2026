package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    private final Scheduler host;
    int owner = -1;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
        host = s;
        s.locks.add(this);
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        int me = host.workerId();
        Scheduler.beforeAccess(() -> owner < 0);
        owner = me;
        Scheduler.logOperation("lock " + name);
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        if (host.stopped()) return;
        int me = host.workerId();
        Scheduler.beforeAccess();
        if (owner != me) throw new IllegalStateException("not yours");
        owner = -1;
        Scheduler.logOperation("unlock " + name);
    }
}
