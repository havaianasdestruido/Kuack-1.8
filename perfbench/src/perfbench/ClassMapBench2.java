package perfbench;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityPig;

/**
 * Benchmarks ONLY the key-resolution step that ClassInheritanceMultiMap.add() performs, on real
 * entity classes and with the real key sets:
 *   original: scan every known key, calling isAssignableFrom on each
 *   patched:  look the concrete class up in the memo, then bind that many keys
 * Everything else (building the per-key lists) is identical in both versions, so this isolates the
 * change.
 */
public class ClassMapBench2 {
    static volatile long blackhole;

    static final Class<?>[] KEYS = { Entity.class, EntityLivingBase.class, EntityLiving.class,
            EntityZombie.class, EntityCreeper.class, EntityPig.class, EntityItem.class };

    static int resolveOriginal(Object o) {
        int n = 0;
        for (int i = 0; i < KEYS.length; ++i) {
            if (KEYS[i].isAssignableFrom(o.getClass())) {
                ++n;
            }
        }
        return n;
    }

    static final Map<Class<?>, List<Class<?>>> MEMO = new HashMap<>();

    static int resolveScan(List<Class<?>> keys, Object o) {
        int c = 0;
        for (int i = 0, l = keys.size(); i < l; ++i) {
            if (keys.get(i).isAssignableFrom(o.getClass())) ++c;
        }
        return c;
    }

    static int resolveMemoIn(List<Class<?>> keys, Map<Class<?>, List<Class<?>>> memo, Object o) {
        List<Class<?>> t = memo.get(o.getClass());
        if (t == null) {
            t = new ArrayList<>(keys.size());
            for (int i = 0, l = keys.size(); i < l; ++i) {
                if (keys.get(i).isAssignableFrom(o.getClass())) t.add(keys.get(i));
            }
            memo.put(o.getClass(), t);
        }
        return t.size();
    }

    static int resolveMemo(Object o) {
        List<Class<?>> t = MEMO.get(o.getClass());
        if (t == null) {
            t = new ArrayList<>(KEYS.length);
            for (int i = 0; i < KEYS.length; ++i) {
                if (KEYS[i].isAssignableFrom(o.getClass())) {
                    t.add(KEYS[i]);
                }
            }
            MEMO.put(o.getClass(), t);
        }
        return t.size();
    }

    public static void main(String[] args) {
        // crossover study: how many known class keys before the memo wins?
        int[] keyCounts = { 7, 12, 20, 30 };
        int n = 1000;
        BenchUtil.header("key-resolution crossover: " + n + " adds per batch");
        System.out.printf("  %6s %14s %14s %10s%n", "keys", "scan (us)", "memo (us)", "speedup");
        Random rng = new Random(5);
        Object[] pool = new Object[n];
        for (int i = 0; i < n; ++i) {
            int k = rng.nextInt(10);
            pool[i] = k < 4 ? new EntityZombie(null)
                    : k < 6 ? new EntityCreeper(null) : k < 8 ? new EntityPig(null) : new EntityItem(null);
        }
        for (int kc : keyCounts) {
            List<Class<?>> keys = new ArrayList<>();
            for (int i = 0; i < kc; ++i) keys.add(KEYS[i % KEYS.length]);
            while (keys.size() < kc) keys.add(Entity.class);
            final List<Class<?>> fk = keys;
            final Map<Class<?>, List<Class<?>>> memo = new HashMap<>();
            for (Object o : pool) resolveMemoIn(fk, memo, o);
            long[] a = new long[9], b = new long[9];
            for (int w = 0; w < 300; ++w) {
                long s = 0;
                for (int i = 0; i < n; ++i) s += resolveScan(fk, pool[i]);
                for (int i = 0; i < n; ++i) s += resolveMemoIn(fk, memo, pool[i]);
                blackhole += s;
            }
            int reps = 2000;
            for (int t = 0; t < 9; ++t) {
                long s = 0;
                long t0 = System.nanoTime();
                for (int r = 0; r < reps; ++r) for (int i = 0; i < n; ++i) s += resolveScan(fk, pool[i]);
                a[t] = System.nanoTime() - t0;
                blackhole += s;
            }
            for (int t = 0; t < 9; ++t) {
                long s = 0;
                long t0 = System.nanoTime();
                for (int r = 0; r < reps; ++r) for (int i = 0; i < n; ++i) s += resolveMemoIn(fk, memo, pool[i]);
                b[t] = System.nanoTime() - t0;
                blackhole += s;
            }
            double ma = (double) BenchUtil.median(a) / reps, mb = (double) BenchUtil.median(b) / reps;
            System.out.printf("  %6d %14.4f %14.4f %9.2fx%n", keys.size(), ma / 1e3, mb / 1e3, ma / mb);
        }
        if (true) { System.out.println("  blackhole " + blackhole); return; }
    }
}
