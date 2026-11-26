package perfbench;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Differential test for the EntityAITasks change: executingTaskEntries.contains(x) was replaced by
 * executingTaskSet.contains(x), with every mutation mirrored into the set.
 *
 * Both versions below are line-by-line transcriptions of onUpdateTasks()/canUse()/removeTask();
 * they run the same randomised task schedules and we compare the full per-tick execution trace
 * (which tasks started, continued, reset) plus the final entry/set contents.
 */
public class VerifyAITasks {

    static final class Task {
        final String name;
        final int priority;
        final int mutexBits;
        final boolean interruptible;
        final Random rng;
        final int seed;
        // scripted behaviour
        final boolean canUseNow;
        final boolean canContinueNow;

        Task(String name, int priority, int mutexBits, boolean interruptible, boolean canUseNow,
                boolean canContinueNow, int seed) {
            this.name = name;
            this.priority = priority;
            this.mutexBits = mutexBits;
            this.interruptible = interruptible;
            this.canUseNow = canUseNow;
            this.canContinueNow = canContinueNow;
            this.rng = new Random(seed);
            this.seed = seed;
        }

        boolean shouldExecute() {
            return canUseNow;
        }

        boolean continueExecuting() {
            return canContinueNow;
        }

        boolean isInterruptible() {
            return interruptible;
        }

        int getMutexBits() {
            return mutexBits;
        }

        public String toString() {
            return name;
        }
    }

    /** Container abstraction so the same algorithm text can run over List or List+Set. */
    interface Exec {
        boolean containsEntry(Task t);

        void addEntry(Task t);

        void removeEntry(Task t);

        int size();

        Task get(int i);

        Iterator<Task> iterator();

        boolean removeFromIterator(Task t);
    }

    static final class ListOnly implements Exec {
        final List<Task> list = new ArrayList<>();

        public boolean containsEntry(Task t) {
            return list.contains(t); // identity, Task has no equals override
        }

        public void addEntry(Task t) {
            list.add(t);
        }

        public void removeEntry(Task t) {
            list.remove(t);
        }

        public int size() {
            return list.size();
        }

        public Task get(int i) {
            return list.get(i);
        }

        public Iterator<Task> iterator() {
            return list.iterator();
        }

        public boolean removeFromIterator(Task t) {
            return false;
        }
    }

    static final class ListPlusSet implements Exec {
        final List<Task> list = new ArrayList<>();
        final Set<Task> set = new HashSet<>();

        public boolean containsEntry(Task t) {
            return set.contains(t);
        }

        public void addEntry(Task t) {
            list.add(t);
            set.add(t);
        }

        public void removeEntry(Task t) {
            list.remove(t);
            set.remove(t);
        }

        public int size() {
            return list.size();
        }

        public Task get(int i) {
            return list.get(i);
        }

        public Iterator<Task> iterator() {
            return list.iterator();
        }

        public boolean removeFromIterator(Task t) {
            return set.remove(t);
        }
    }

    /** Verbatim transcription of EntityAITasks.onUpdateTasks + canUse. */
    static void onUpdateTasks(List<Task> taskEntries, Exec executing, int tickCount, int tickRate,
            StringBuilder trace) {
        if (tickCount % tickRate == 0) {
            Iterator<Task> iterator = taskEntries.iterator();
            label38: while (true) {
                Task entry;
                while (true) {
                    if (!iterator.hasNext()) {
                        break label38;
                    }
                    entry = iterator.next();
                    boolean flag = executing.containsEntry(entry);
                    if (!flag) {
                        break;
                    }
                    if (!canUse(taskEntries, executing, entry) || !entry.continueExecuting()) {
                        entry.rng.setSeed(entry.seed + tickCount);
                        trace.append("reset:").append(entry.name).append(' ');
                        executing.removeEntry(entry);
                        break;
                    }
                }
                if (canUse(taskEntries, executing, entry) && entry.shouldExecute()) {
                    trace.append("start:").append(entry.name).append(' ');
                    executing.addEntry(entry);
                }
            }
        } else {
            Iterator<Task> iterator1 = executing.iterator();
            while (iterator1.hasNext()) {
                Task e = iterator1.next();
                if (!e.continueExecuting()) {
                    trace.append("reset:").append(e.name).append(' ');
                    iterator1.remove();
                    executing.removeFromIterator(e);
                }
            }
        }
        for (int i = 0, l = executing.size(); i < l; ++i) {
            trace.append("tick:").append(executing.get(i).name).append(' ');
        }
    }

    static boolean canUse(List<Task> taskEntries, Exec executing, Task taskEntry) {
        for (int i = 0, l = taskEntries.size(); i < l; ++i) {
            Task e = taskEntries.get(i);
            if (e != taskEntry) {
                if (taskEntry.priority >= e.priority) {
                    if (!((taskEntry.getMutexBits() & e.getMutexBits()) == 0) && executing.containsEntry(e)) {
                        return false;
                    }
                } else if (!e.isInterruptible() && executing.containsEntry(e)) {
                    return false;
                }
            }
        }
        return true;
    }

    static void removeTask(List<Task> taskEntries, Exec executing, Task task, StringBuilder trace) {
        Iterator<Task> iterator = taskEntries.iterator();
        while (iterator.hasNext()) {
            Task entry = iterator.next();
            if (entry.name.equals(task.name)) {
                if (executing.containsEntry(entry)) {
                    trace.append("removeTask-reset:").append(entry.name).append(' ');
                    executing.removeEntry(entry);
                }
                iterator.remove();
            }
        }
    }

    static String run(long seed, boolean useSet, int maxTasks, int ticks) {
        Random rng = new Random(seed);
        int n = 1 + rng.nextInt(maxTasks);
        List<Task> entries = new ArrayList<>();
        Exec exec = useSet ? new ListPlusSet() : new ListOnly();
        for (int i = 0; i < n; ++i) {
            entries.add(new Task("t" + i, rng.nextInt(4), rng.nextInt(4), rng.nextBoolean(),
                    rng.nextInt(3) != 0, rng.nextInt(5) != 0, rng.nextInt(1000)));
        }
        int tickRate = 1 + rng.nextInt(3);
        StringBuilder trace = new StringBuilder();
        for (int tick = 0; tick < ticks; ++tick) {
            onUpdateTasks(entries, exec, tick, tickRate, trace);
            // occasionally remove a random task, like EntityLiving.removeAI task swaps
            if (rng.nextInt(17) == 0 && !entries.isEmpty()) {
                Task victim = entries.get(rng.nextInt(entries.size()));
                removeTask(entries, exec, victim, trace);
            }
        }
        trace.append("ENTRIES:").append(entries);
        trace.append(" EXEC:").append(describe(exec));
        return trace.toString();
    }

    static String describe(Exec e) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < e.size(); ++i) {
            if (i > 0) sb.append(',');
            sb.append(e.get(i).name);
        }
        return sb.append(']').toString();
    }

    public static void main(String[] args) {
        long cases = 0, failures = 0;
        // vary number of tasks and tick counts to hit every branch
        int[][] shapes = { { 1, 10 }, { 2, 30 }, { 3, 60 }, { 4, 100 }, { 6, 150 }, { 8, 200 }, { 12, 300 } };
        for (int[] shape : shapes) {
            for (long seed = 0; seed < 3000; ++seed) {
                String a = run(seed * 31 + shape[0], false, shape[0], shape[1]);
                String b = run(seed * 31 + shape[0], true, shape[0], shape[1]);
                ++cases;
                if (!a.equals(b)) {
                    ++failures;
                    if (failures <= 3) {
                        System.out.println("MISMATCH shape=" + shape[0] + "/" + shape[1] + " seed=" + seed);
                        int p = 0;
                        while (p < a.length() && p < b.length() && a.charAt(p) == b.charAt(p)) ++p;
                        System.out.println("  at " + p + ": A=" + a.substring(Math.max(0, p - 60), Math.min(a.length(), p + 60)));
                        System.out.println("        B=" + b.substring(Math.max(0, p - 60), Math.min(b.length(), p + 60)));
                    }
                }
            }
        }
        System.out.println("cases=" + cases + " failures=" + failures);
        if (failures != 0) {
            throw new AssertionError("EntityAITasks behaviour changed");
        }
    }
}
