package perfbench;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Benchmarks the EntityAITasks membership cost for a single onUpdateTasks() pass.
 *
 * Vanilla: executingTaskEntries.contains() is an ArrayList identity scan (O(running)).
 * Patched: the same query hits a HashSet (O(1)).
 *
 * A mob's task list is typically 8-14 entries and several of them are running at once, and the
 * same pass also runs canUse() for every idle entry (whose inner loop queries membership once per
 * task => O(tasks^2) queries). Refresh happens every tickRate ticks for every mob alive.
 */
public class AITasksBench {
    static volatile long blackhole;

    static final class Task {
        final int priority, mutexBits;
        final boolean interruptible;
        final String name;

        Task(String name, int priority, int mutexBits, boolean interruptible) {
            this.name = name;
            this.priority = priority;
            this.mutexBits = mutexBits;
            this.interruptible = interruptible;
        }

        public String toString() {
            return name;
        }
    }

    interface Memb {
        boolean contains(Object o);
    }

    static final class ListOld implements Memb {
        final List<Object> l = new ArrayList<>();

        public boolean contains(Object o) {
            return l.contains(o);
        }
    }

    static final class SetNew implements Memb {
        final Set<Object> s = new HashSet<>();

        public boolean contains(Object o) {
            return s.contains(o);
        }
    }

    /** canUse(): the inner loop that queries membership for every other task. */
    static boolean canUse(List<Task> entries, Memb running, Task taskEntry) {
        for (int i = 0, l = entries.size(); i < l; ++i) {
            Task e = entries.get(i);
            if (e != taskEntry) {
                if (taskEntry.priority >= e.priority) {
                    if ((taskEntry.mutexBits & e.mutexBits) != 0 && running.contains(e)) {
                        return false;
                    }
                } else if (!e.interruptible && running.contains(e)) {
                    return false;
                }
            }
        }
        return true;
    }

    static long onePass(List<Task> entries, Memb running, List<Task> executing, Random rng) {
        long acc = 0;
        // goalSetup: every idle entry gets a canUse() (O(tasks^2) membership queries)
        for (int i = 0, n = entries.size(); i < n; ++i) {
            Task e = entries.get(i);
            if (running.contains(e)) {
                if (!canUse(entries, running, e)) {
                    ++acc;
                }
            } else if (canUse(entries, running, e)) {
                ++acc;
            }
        }
        // goalTick: one updateTask per running entry
        for (int i = 0, n = executing.size(); i < n; ++i) {
            acc += executing.get(i).priority;
        }
        return acc;
    }

    public static void main(String[] args) {
        int[] sizes = { 4, 8, 12, 16, 24, 32 };
        BenchUtil.header("EntityAITasks.onUpdateTasks: one pass over a mob's task list");
        System.out.printf("  %6s %14s %14s %10s %14s%n", "tasks", "ArrayList ms", "HashSet ms", "speedup", "ns/mob-pass");
        for (int n : sizes) {
            List<Task> entries = new ArrayList<>();
            Random rng = new Random(7);
            for (int i = 0; i < n; ++i) {
                entries.add(new Task("t" + i, rng.nextInt(4), rng.nextInt(4), rng.nextBoolean()));
            }
            List<Task> runningOld = new ArrayList<>();
            List<Object> asList = new ArrayList<>();
            Set<Object> asSet = new HashSet<>();
            List<Task> executing = new ArrayList<>();
            for (int i = 0; i < n; i += 2) {
                Task t = entries.get(i);
                executing.add(t);
                asList.add(t);
                asSet.add(t);
            }
            ListOld oldM = new ListOld();
            oldM.l.addAll(asList);
            SetNew newM = new SetNew();
            newM.s.addAll(asSet);

            int passes = 40000;
            long[] a = new long[11], b = new long[11];
            for (int w = 0; w < 400; ++w) {
                blackhole += onePass(entries, oldM, executing, rng);
                blackhole += onePass(entries, newM, executing, rng);
            }
            for (int t = 0; t < 11; ++t) {
                long t0 = System.nanoTime();
                for (int i = 0; i < passes; ++i) blackhole += onePass(entries, oldM, executing, rng);
                a[t] = System.nanoTime() - t0;
            }
            for (int t = 0; t < 11; ++t) {
                long t0 = System.nanoTime();
                for (int i = 0; i < passes; ++i) blackhole += onePass(entries, newM, executing, rng);
                b[t] = System.nanoTime() - t0;
            }
            long ma = BenchUtil.median(a), mb = BenchUtil.median(b);
            System.out.printf("  %6d %14.3f %14.3f %9.2fx %14.1f%n", n, ma / 1e6, mb / 1e6,
                    (double) ma / (double) mb, (double) ma / passes);
        }
        System.out.println("  blackhole " + blackhole);
    }
}
