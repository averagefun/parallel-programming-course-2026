package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    private static final ThreadLocal<Worker> SELF = new ThreadLocal<>();
    private final List<Worker> crew = new ArrayList<>();
    final List<SchedLock> locks = new ArrayList<>();
    private final Semaphore reply = new Semaphore(0);
    private final StringBuilder path = new StringBuilder();
    private boolean started;
    private boolean stop;

    private static final class RunCancelled extends RuntimeException { }

    private static final class Worker {
        final Scheduler host;
        final int id;
        final Semaphore turn = new Semaphore(0);
        final Thread runner;
        boolean used = true;
        BooleanSupplier ready;
        boolean done;
        Throwable error;

        Worker(Scheduler host, int id, Runnable body) {
            this.host = host;
            this.id = id;
            runner = new Thread(() -> {
                SELF.set(this);
                try {
                    body.run();
                } catch (RunCancelled ignored) {
                } catch (Throwable e) {
                    error = e;
                } finally {
                    done = true;
                    SELF.remove();
                    host.reply.release();
                }
            }, "scheduler-" + (char) ('A' + id));
        }

        void pause() {
            used = false;
            host.reply.release();
            turn.acquireUninterruptibly();
            if (host.stop) throw new RunCancelled();
        }
    }

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        if (started) throw new IllegalStateException("already ran");
        crew.add(new Worker(this, crew.size(), body));
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        if (started) throw new IllegalStateException("already ran");
        started = true;
        int count = 0;
        try {
            for (Worker pal : crew) {
                pal.runner.start();
                count++;
                reply.acquireUninterruptibly();
                checkFailure(pal);
            }

            int last = -1;
            while (true) {
                List<Integer> enabled = new ArrayList<>();
                boolean alive = false;
                for (Worker pal : crew) {
                    if (pal.done) continue;
                    alive = true;
                    if (pal.ready == null || pal.ready.getAsBoolean()) {
                        enabled.add(pal.id);
                    }
                }
                if (!alive) break;
                if (enabled.isEmpty()) {
                    StringJoiner held = new StringJoiner(", ", "held={", "}");
                    for (SchedLock lock : locks) {
                        if (lock.owner >= 0) held.add(lock.name + ":" + (char) ('A' + lock.owner));
                    }
                    deadlock = held.toString();
                    break;
                }
                int next = strategy.pick(enabled, last);
                if (!enabled.contains(next)) {
                    throw new IllegalArgumentException("bad pick: " + next);
                }
                path.append((char) ('A' + next));
                last = next;
                Worker pal = crew.get(next);
                pal.turn.release();
                reply.acquireUninterruptibly();
                checkFailure(pal);
            }
        } finally {
            stop = true;
            for (int i = 0; i < count; i++) crew.get(i).turn.release();
            boolean interrupted = false;
            for (int i = 0; i < count; i++) {
                while (true) {
                    try {
                        crew.get(i).runner.join();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void checkFailure(Worker pal) {
        if (pal.error != null) {
            throw new IllegalStateException("worker " + (char) ('A' + pal.id) + " failed", pal.error);
        }
    }

    private static Worker current() {
        Worker pal = SELF.get();
        if (pal == null) throw new IllegalStateException("no worker");
        return pal;
    }

    static void beforeAccess() {
        Worker pal = current();
        if (pal.used) pal.pause();
        pal.used = true;
    }

    static void beforeAccess(BooleanSupplier ready) {
        Worker pal = current();
        if (pal.used || !ready.getAsBoolean()) {
            pal.ready = ready;
            pal.pause();
            pal.ready = null;
        }
        pal.used = true;
    }

    int workerId() {
        Worker pal = current();
        if (pal.host != this) throw new IllegalStateException("wrong scheduler");
        return pal.id;
    }

    boolean stopped() {
        return stop;
    }

    static void logOperation(String op) {
        Worker pal = current();
        if (!pal.host.stop && pal.host.logging) {
            pal.host.log.add((char) ('A' + pal.id) + "  " + op);
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return path.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        current().used = true;
        logOperation("window");
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        beforeAccess(cond);
        logOperation("await done");
    }
}
