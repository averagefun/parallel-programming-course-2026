package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.getFirst();
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String path;
        private int pos;

        public Replay(String trace) { path = trace; }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() { pos = 0; }

        public int pick(List<Integer> enabled, int last) {
            if (pos == path.length()) throw new IllegalArgumentException("trace ended");
            int next = path.charAt(pos++) - 'A';
            if (!enabled.contains(next)) throw new IllegalArgumentException("bad step");
            return next;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final Random rnd;

        public RandomPick(long seed) { rnd = new Random(seed); }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private final List<Fork> path = new ArrayList<>();
        private final int bound;
        private int depth;
        private int used;

        private static final class Fork {
            final List<Integer> picks;
            int at;

            Fork(List<Integer> picks) { this.picks = List.copyOf(picks); }
        }

        public Dfs(int bound) {
            if (bound < 0) throw new IllegalArgumentException("bad bound");
            this.bound = bound;
        }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            depth = 0;
            used = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            boolean free = !enabled.contains(last);
            if (depth == path.size()) {
                List<Integer> picks = new ArrayList<>();
                for (int next : enabled) {
                    if (free || next == last || used < bound) picks.add(next);
                }
                path.add(new Fork(picks));
            }
            Fork fork = path.get(depth++);
            int next = fork.picks.get(fork.at);
            if (!free && next != last) used++;
            return next;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            path.subList(depth, path.size()).clear();
            while (!path.isEmpty()) {
                Fork fork = path.getLast();
                if (++fork.at < fork.picks.size()) return true;
                path.removeLast();
            }
            return false;
        }
    }
}
