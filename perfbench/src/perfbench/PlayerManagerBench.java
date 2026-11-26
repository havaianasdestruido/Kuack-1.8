package perfbench;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Models PlayerManager.filterChunkLoadQueue: a spiral walk over the (2r)^2 chunk view area that
 * calls loadedChunks.contains() for every visited chunk. loadedChunks is a LinkedList in vanilla,
 * so contains() is O(n) with pointer chasing.
 */
public class PlayerManagerBench {
    static volatile long blackhole;

    /** Vanilla: LinkedList.contains inside the spiral. */
    static int vanilla(List<Object> loadedChunks, int viewRadius, Set<Object> visited) {
        int hits = 0;
        int i1 = 0, j1 = 0, i = 0;
        int j = viewRadius;
        for (int k1 = 1; k1 <= j * 2; ++k1) {
            for (int l1 = 0; l1 < 2; ++l1) {
                int dir = (i++ % 4) * 2;
                int dx = DIRS[dir], dz = DIRS[dir + 1];
                for (int i2 = 0; i2 < k1; ++i2) {
                    i1 += dx;
                    j1 += dz;
                    Object p = key(i1, j1);
                    visited.add(p);
                    if (loadedChunks.contains(p)) {
                        ++hits;
                    }
                }
            }
        }
        i = i % 4;
        for (int j2 = 0; j2 < j * 2; ++j2) {
            i1 += DIRS[i * 2];
            j1 += DIRS[i * 2 + 1];
            Object p = key(i1, j1);
            visited.add(p);
            if (loadedChunks.contains(p)) {
                ++hits;
            }
        }
        return hits;
    }

    /** Optimised: membership via a hash set built once from the same contents. */
    static int optimized(List<Object> loadedChunks, int viewRadius, Set<Object> visited) {
        Set<Object> membership = new HashSet<>(loadedChunks.size() * 2);
        for (Object o : loadedChunks) {
            membership.add(o);
        }
        return vanilla(membership, membership, viewRadius, visited);
    }

    static int vanilla(Object unused, Set<Object> membership, int viewRadius, Set<Object> visited) {
        return optimizedInner(membership, viewRadius, visited);
    }

    static int optimizedInner(Set<Object> membership, int viewRadius, Set<Object> visited) {
        int hits = 0;
        int i1 = 0, j1 = 0, i = 0;
        int j = viewRadius;
        for (int k1 = 1; k1 <= j * 2; ++k1) {
            for (int l1 = 0; l1 < 2; ++l1) {
                int dir = (i++ % 4) * 2;
                int dx = DIRS[dir], dz = DIRS[dir + 1];
                for (int i2 = 0; i2 < k1; ++i2) {
                    i1 += dx;
                    j1 += dz;
                    Object p = key(i1, j1);
                    visited.add(p);
                    if (membership.contains(p)) {
                        ++hits;
                    }
                }
            }
        }
        i = i % 4;
        for (int j2 = 0; j2 < j * 2; ++j2) {
            i1 += DIRS[i * 2];
            j1 += DIRS[i * 2 + 1];
            Object p = key(i1, j1);
            visited.add(p);
            if (membership.contains(p)) {
                ++hits;
            }
        }
        return hits;
    }

    static final int[] DIRS = { 0, -1, -1, 0, 0, 1, 1, 0 };

    static Object key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static void main(String[] args) {
        int[] radii = { 5, 8, 10, 16 };
        BenchUtil.header("PlayerManager.filterChunkLoadQueue: LinkedList.contains spiral vs hash membership");
        System.out.printf("  %6s %14s %14s %10s%n", "radius", "vanilla (ms)", "hash (ms)", "speedup");
        for (int r : radii) {
            List<Object> loaded = new LinkedList<>();
            Random rng = new Random(3);
            // view area is (2r)^2 chunks; loadedChunks holds roughly that many entries
            int n = (2 * r) * (2 * r);
            for (int k = 0; k < n; ++k) {
                loaded.add(key(rng.nextInt(2 * r + 1) - r, rng.nextInt(2 * r + 1) - r));
            }
            Set<Object> visited = new HashSet<>();

            long[] s = new long[9];
            for (int w = 0; w < 300; ++w) {
                visited.clear();
                blackhole += vanilla(loaded, r, visited);
                visited.clear();
                blackhole += optimized(loaded, r, visited);
            }
            for (int t = 0; t < 9; ++t) {
                visited.clear();
                long t0 = System.nanoTime();
                blackhole += vanilla(loaded, r, visited);
                s[t] = System.nanoTime() - t0;
            }
            long v = BenchUtil.median(s);
            for (int t = 0; t < 9; ++t) {
                visited.clear();
                long t0 = System.nanoTime();
                blackhole += optimized(loaded, r, visited);
                s[t] = System.nanoTime() - t0;
            }
            long o = BenchUtil.median(s);
            System.out.printf("  %6d %14.3f %14.3f %9.2fx%n", r, v / 1e6, o / 1e6, (double) v / (double) o);
        }
        System.out.println("  blackhole " + blackhole);
    }
}
