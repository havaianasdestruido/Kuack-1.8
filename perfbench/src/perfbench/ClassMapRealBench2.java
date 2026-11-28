package perfbench;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Same as ClassMapRealBench but with the key set grown the way a real session grows it: each
 * distinct entity class that is ever queried gets registered in the static KNOWN_CLASSES set, and
 * every ClassInheritanceMultiMap.add() then tests isAssignableFrom against ALL of them.
 */
public class ClassMapRealBench2 {
    static volatile long blackhole;

    static class A { } static class B extends A { } static class C extends B { }
    static class D extends B { } static class E extends B { } static class F extends A { }
    static class G extends A { } static class H extends A { } static class I extends H { }
    static class J extends H { } static class K extends A { } static class L extends A { }
    static class M extends A { } static class N extends A { } static class O extends A { }
    static class P extends A { }

    static final Class<?>[] ALL = { A.class, B.class, C.class, D.class, E.class, F.class, G.class,
            H.class, I.class, J.class, K.class, L.class, M.class, N.class, O.class, P.class };

    static final Object[] INSTANCES;
    static {
        INSTANCES = new Object[ALL.length];
        try {
            for (int i = 0; i < ALL.length; ++i) {
                INSTANCES[i] = i < 2 ? new A() : ALL[i].newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static int vanilla(Object obj, List<Class<?>> keys) {
        int n = 0;
        for (int i = 0, l = keys.size(); i < l; ++i) {
            if (keys.get(i).isAssignableFrom(obj.getClass())) ++n;
        }
        return n;
    }

    static int cached(Object obj, List<Class<?>> keys, java.util.Map<Class<?>, List<Class<?>>> cache,
            java.util.Map<Class<?>, Boolean> known) {
        List<Class<?>> t = cache.get(obj.getClass());
        if (t == null) {
            t = new ArrayList<>();
            for (int i = 0, l = keys.size(); i < l; ++i) {
                if (keys.get(i).isAssignableFrom(obj.getClass())) t.add(keys.get(i));
            }
            cache.put(obj.getClass(), t);
        }
        return t.size();
    }

    public static void main(String[] args) {
        BenchUtil.header("ClassInheritanceMultiMap.add() vs size of the static known-key set");
        System.out.printf("  %8s %10s %18s %18s %10s%n", "keys", "adds", "isAssignableFrom", "per-class cache", "speedup");
        int[] keyCounts = { 2, 4, 8, 12, 16 };
        for (int k : keyCounts) {
            List<Class<?>> keys = new ArrayList<>();
            for (int i = 0; i < k && i < ALL.length; ++i) keys.add(ALL[i]);
            final List<Class<?>> fkeys = keys;
            final java.util.Map<Class<?>, List<Class<?>>> cache = new java.util.HashMap<>();
            java.util.Map<Class<?>, Boolean> known = new java.util.HashMap<>();
            int adds = 2000;
            Random rng = new Random(11);
            Object[] objs = new Object[adds];
            for (int i = 0; i < adds; ++i) objs[i] = INSTANCES[rng.nextInt(INSTANCES.length)];

            long[] a = new long[9], b = new long[9];
            for (int w = 0; w < 300; ++w) {
                long s = 0;
                for (int i = 0; i < adds; ++i) s += vanilla(objs[i], fkeys);
                for (int i = 0; i < adds; ++i) s += cached(objs[i], fkeys, cache, known);
                blackhole += s;
            }
            for (int t = 0; t < 9; ++t) {
                long s = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < adds; ++i) s += vanilla(objs[i], fkeys);
                a[t] = System.nanoTime() - t0;
                blackhole += s;
            }
            for (int t = 0; t < 9; ++t) {
                long s = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < adds; ++i) s += cached(objs[i], fkeys, cache, known);
                b[t] = System.nanoTime() - t0;
                blackhole += s;
            }
            long ma = BenchUtil.median(a), mb = BenchUtil.median(b);
            System.out.printf("  %8d %10d %15.3f ms %15.3f ms %9.2fx%n", k, adds, ma / 1e6, mb / 1e6,
                    (double) ma / (double) mb);
        }
        System.out.println("  blackhole " + blackhole);
    }
}
