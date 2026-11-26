package perfbench;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Benchmarks ClassInheritanceMultiMap.add / .remove, which iterate EVERY class key known to the
 * process-wide static field_181158_a and call isAssignableFrom on it, per entity operation.
 *
 * addEntity/removeEntity on a chunk section does this for each of the 16 sections' maps, and the
 * known-key set only ever grows for the lifetime of the game session (every distinct
 * Entity class that is ever queried gets registered).
 */
public class ClassMapRealBench {
    static volatile long blackhole;
    static final Set<Class<?>> KNOWN = new HashSet<>();

    static class Entity { }
    static class EntityLiving extends Entity { }
    static class EntityMob extends EntityLiving { }
    static class EntityZombie extends EntityMob { }
    static class EntityCreeper extends EntityMob { }
    static class EntitySkeleton extends EntityMob { }
    static class EntityItem extends Entity { }
    static class EntityArrow extends Entity { }
    static class EntityPlayer extends EntityLiving { }
    static class EntityPig extends EntityLiving { }

    static final Class<?>[] POOL = { EntityZombie.class, EntityCreeper.class, EntitySkeleton.class,
            EntityItem.class, EntityArrow.class, EntityPig.class };

    static int vanillaAdd(Object obj, List<Class<?>> keys) {
        int n = 0;
        for (int i = 0, l = keys.size(); i < l; ++i) {
            if (keys.get(i).isAssignableFrom(obj.getClass())) {
                ++n;
            }
        }
        return n;
    }

    /** Resolve the assignable key list once per concrete class, then reuse it. */
    static int cachedAdd(Object obj, List<Class<?>> keys, java.util.Map<Class<?>, List<Class<?>>> cache) {
        List<Class<?>> targets = cache.get(obj.getClass());
        if (targets == null) {
            targets = new ArrayList<>();
            for (int i = 0, l = keys.size(); i < l; ++i) {
                if (keys.get(i).isAssignableFrom(obj.getClass())) {
                    targets.add(keys.get(i));
                }
            }
            cache.put(obj.getClass(), targets);
        }
        return targets.size();
    }

    public static void main(String[] args) {
        // realistic accumulation: the static key set grows as the session touches entity types
        Class<?>[] growth = { EntityLiving.class, EntityMob.class, EntityZombie.class, EntityCreeper.class,
                EntitySkeleton.class, EntityItem.class, EntityArrow.class, EntityPlayer.class, EntityPig.class };
        for (Class<?> c : growth) KNOWN.add(c);

        int n = 4096; // one full chunk column's worth of section adds/removes over time
        Random rng = new Random(9);
        Object[] objs = new Object[n];
        for (int i = 0; i < n; ++i) {
            try {
                objs[i] = POOL[rng.nextInt(POOL.length)].newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        final List<Class<?>> keys = new ArrayList<>(KNOWN);
        final java.util.Map<Class<?>, List<Class<?>>> cache = new java.util.HashMap<>();

        BenchUtil.header("ClassInheritanceMultiMap add(): " + keys.size() + " known class keys");
        System.out.printf("  %10s %16s %16s %10s%n", "ops", "isAssignableFrom", "per-class cache", "speedup");
        int[] shapes = { 256, 1024, 4096, 16384 };
        for (int ops : shapes) {
            long[] a = new long[9], b = new long[9];
            for (int w = 0; w < 200; ++w) {
                long s = 0;
                for (int i = 0; i < ops; ++i) s += vanillaAdd(objs[i % n], keys);
                for (int i = 0; i < ops; ++i) s += cachedAdd(objs[i % n], keys, cache);
                blackhole += s;
            }
            for (int t = 0; t < 9; ++t) {
                long s = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < ops; ++i) s += vanillaAdd(objs[i % n], keys);
                a[t] = System.nanoTime() - t0;
                blackhole += s;
            }
            for (int t = 0; t < 9; ++t) {
                long s = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < ops; ++i) s += cachedAdd(objs[i % n], keys, cache);
                b[t] = System.nanoTime() - t0;
                blackhole += s;
            }
            long ma = BenchUtil.median(a), mb = BenchUtil.median(b);
            System.out.printf("  %10d %13.3f ms %13.3f ms %9.2fx%n", ops, ma / 1e6, mb / 1e6, (double) ma / (double) mb);
        }
        System.out.println("  blackhole " + blackhole);
    }
}
