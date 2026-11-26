package perfbench;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Models the cost profile of ClassInheritanceMultiMap.add/remove, which iterates EVERY class key
 * known to the (process-wide, monotonically growing) static field_181158_a set and calls
 * isAssignableFrom on it for every single entity add.
 */
public class ClassMapBench {
    static final Set<Class<?>> KNOWN = new HashSet<>();
    static volatile long blackhole;

    static class A { }
    static final class B extends A { }
    static final class C extends A { }
    static final class D extends A { }
    static final class E extends A { }
    static final class F extends A { }
    static final class G extends A { }
    static final class H extends A { }

    static List<Class<?>> syntheticTypes() {
        List<Class<?>> l = new ArrayList<>();
        l.add(A.class); l.add(B.class); l.add(C.class); l.add(D.class);
        l.add(E.class); l.add(F.class); l.add(G.class); l.add(H.class);
        return l;
    }

    /** Vanilla: linear isAssignableFrom over every known class for one object. */
    static int vanillaAdd(Object obj, Map<Class<?>, List<Object>> map, List<Object> all) {
        int n = 0;
        for (Class<?> c : KNOWN) {
            if (c.isAssignableFrom(obj.getClass())) {
                List<Object> l = map.get(c);
                if (l == null) {
                    l = new ArrayList<>();
                    map.put(c, l);
                }
                l.add(obj);
                ++n;
            }
        }
        all.add(obj);
        return n;
    }

    /**
     * Optimised: keep one map entry per concrete class and one per known superclass, but resolve the
     * assignable set ONCE per concrete class instead of once per object.
     */
    static final Map<Class<?>, List<Class<?>>> assignCache = new HashMap<>();

    static List<Class<?>> assignableTo(Class<?> concrete) {
        List<Class<?>> cached = assignCache.get(concrete);
        if (cached != null) {
            return cached;
        }
        List<Class<?>> l = new ArrayList<>();
        for (Class<?> c : KNOWN) {
            if (c.isAssignableFrom(concrete)) {
                l.add(c);
            }
        }
        assignCache.put(concrete, l);
        return l;
    }

    static int cachedAdd(Object obj, Map<Class<?>, List<Object>> map, List<Object> all) {
        List<Class<?>> targets = assignableTo(obj.getClass());
        for (int i = 0, n = targets.size(); i < n; ++i) {
            List<Object> l = map.get(targets.get(i));
            if (l == null) {
                l = new ArrayList<>();
                map.put(targets.get(i), l);
            }
            l.add(obj);
        }
        all.add(obj);
        return targets.size();
    }

    public static void main(String[] args) {
        // realistic: the static set accumulates every class ever queried, for the whole session
        for (Class<?> c : syntheticTypes()) {
            KNOWN.add(c);
        }
        assignCache.clear();

        int n = 2000;
        Random rng = new Random(1);
        Object[] objs = new Object[n];
        Class<?>[] pool = { B.class, C.class, D.class, E.class, F.class, G.class, H.class };
        for (int i = 0; i < n; ++i) {
            try {
                objs[i] = pool[rng.nextInt(pool.length)].newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        long[] s = new long[9];
        for (int w = 0; w < 200; ++w) {
            blackhole += runVanilla(objs);
            blackhole += runCached(objs);
        }
        for (int t = 0; t < 9; ++t) {
            long t0 = System.nanoTime();
            blackhole += runVanilla(objs);
            s[t] = System.nanoTime() - t0;
        }
        long v = BenchUtil.median(s);
        for (int t = 0; t < 9; ++t) {
            long t0 = System.nanoTime();
            blackhole += runCached(objs);
            s[t] = System.nanoTime() - t0;
        }
        long c = BenchUtil.median(s);

        BenchUtil.header("ClassInheritanceMultiMap add(), " + n + " entities, " + KNOWN.size() + " known class keys");
        System.out.printf("  vanilla isAssignableFrom scan %10.3f ms  (%,.0f ns/entity)%n", v / 1e6, (double) v / n);
        System.out.printf("  per-class resolution cache   %10.3f ms  (%,.0f ns/entity)  speedup %.2fx%n", c / 1e6,
                (double) c / n, (double) v / (double) c);
        System.out.println("  blackhole " + blackhole);
    }

    static long runVanilla(Object[] objs) {
        Map<Class<?>, List<Object>> map = new HashMap<>();
        List<Object> all = new ArrayList<>();
        int acc = 0;
        for (Object o : objs) {
            acc += vanillaAdd(o, map, all);
        }
        return acc;
    }

    static long runCached(Object[] objs) {
        Map<Class<?>, List<Object>> map = new HashMap<>();
        List<Object> all = new ArrayList<>();
        int acc = 0;
        for (Object o : objs) {
            acc += cachedAdd(o, map, all);
        }
        return acc;
    }
}
