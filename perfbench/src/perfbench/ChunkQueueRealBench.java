package perfbench;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.world.ChunkCoordIntPair;

/**
 * Benchmarks the two membership checks that PlayerManager performs on EntityPlayerMP.loadedChunks,
 * using the REAL ChunkCoordIntPair class and the real container types:
 *
 *   - filterChunkLoadQueue's spiral walk over the (2r)^2 view area
 *   - sendToAllPlayersWatchingChunk's per-player membership test on a block change
 *   - isPlayerWatchingChunk's membership test, run once per (entity, player) pair per tick
 */
public class ChunkQueueRealBench {
    static volatile long blackhole;

    static final int[] DIRS = { 0, -1, -1, 0, 0, 1, 1, 0 };

    interface Memb {
        boolean contains(ChunkCoordIntPair p);
    }

    static List<ChunkCoordIntPair> spiralOrder(int radius) {
        List<ChunkCoordIntPair> out = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
        out.add(new ChunkCoordIntPair(0, 0));
        int i = 0, i1 = 0, j1 = 0, j = radius;
        for (int k1 = 1; k1 <= j * 2; ++k1) {
            for (int l1 = 0; l1 < 2; ++l1) {
                int d = (i++ % 4) * 2;
                for (int i2 = 0; i2 < k1; ++i2) {
                    i1 += DIRS[d];
                    j1 += DIRS[d + 1];
                    out.add(new ChunkCoordIntPair(i1, j1));
                }
            }
        }
        i = i % 4;
        for (int j2 = 0; j2 < j * 2; ++j2) {
            i1 += DIRS[i * 2];
            j1 += DIRS[i * 2 + 1];
            out.add(new ChunkCoordIntPair(i1, j1));
        }
        return out;
    }

    static long walk(Memb m, List<ChunkCoordIntPair> order) {
        long c = 0;
        for (int i = 0, n = order.size(); i < n; ++i) {
            if (m.contains(order.get(i))) {
                ++c;
            }
        }
        return c;
    }

    public static void main(String[] args) {
        int[] radii = { 5, 8, 10, 16, 24, 32 };

        BenchUtil.header("PlayerManager.filterChunkLoadQueue spiral walk (real ChunkCoordIntPair)");
        System.out.printf("  %6s %8s %14s %14s %10s%n", "radius", "chunks", "LinkedList ms", "LinkedHashSet ms", "speedup");
        for (int r : radii) {
            List<ChunkCoordIntPair> order = spiralOrder(r);
            final List<ChunkCoordIntPair> ll = new LinkedList<>(order);
            final Collection<ChunkCoordIntPair> hs = new LinkedHashSet<>(order);
            Memb mLL = new Memb() {
                public boolean contains(ChunkCoordIntPair p) {
                    return ll.contains(p);
                }
            };
            Memb mHS = new Memb() {
                public boolean contains(ChunkCoordIntPair p) {
                    return hs.contains(p);
                }
            };
            long[] a = new long[9], b = new long[9];
            for (int w = 0; w < 40; ++w) {
                blackhole += walk(mLL, order);
                blackhole += walk(mHS, order);
            }
            for (int t = 0; t < 9; ++t) {
                long t0 = System.nanoTime();
                blackhole += walk(mLL, order);
                a[t] = System.nanoTime() - t0;
            }
            for (int t = 0; t < 9; ++t) {
                long t0 = System.nanoTime();
                blackhole += walk(mHS, order);
                b[t] = System.nanoTime() - t0;
            }
            long ma = BenchUtil.median(a), mb = BenchUtil.median(b);
            System.out.printf("  %6d %8d %14.3f %14.3f %9.2fx%n", r, order.size(), ma / 1e6, mb / 1e6,
                    (double) ma / (double) mb);
        }

        // sendToAllPlayersWatchingChunk / isPlayerWatchingChunk: single membership query.
        // With P players per chunk and B block-change batches per tick this runs P*B times per tick.
        BenchUtil.header("single loadedChunks.contains() (real ChunkCoordIntPair)");
        for (int r : radii) {
            List<ChunkCoordIntPair> order = spiralOrder(r);
            final List<ChunkCoordIntPair> ll = new LinkedList<>(order);
            final Collection<ChunkCoordIntPair> hs = new LinkedHashSet<>(order);
            int n = 200000;
            ChunkCoordIntPair probe = order.get(order.size() / 2); // mid-list: worst case for LinkedList
            long[] a = new long[9], b = new long[9];
            for (int w = 0; w < 5; ++w) {
                long s = 0;
                for (int i = 0; i < n; ++i) if (ll.contains(probe)) ++s;
                blackhole += s;
                s = 0;
                for (int i = 0; i < n; ++i) if (hs.contains(probe)) ++s;
                blackhole += s;
            }
            for (int t = 0; t < 9; ++t) {
                long s = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < n; ++i) if (ll.contains(probe)) ++s;
                a[t] = System.nanoTime() - t0;
                blackhole += s;
            }
            for (int t = 0; t < 9; ++t) {
                long s = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < n; ++i) if (hs.contains(probe)) ++s;
                b[t] = System.nanoTime() - t0;
                blackhole += s;
            }
            double na = (double) BenchUtil.median(a) / n, nb = (double) BenchUtil.median(b) / n;
            System.out.printf("  r=%2d n=%5d  LinkedList %8.1f ns   LinkedHashSet %6.1f ns   %6.2fx%n", r,
                    order.size(), na, nb, na / nb);
        }
        System.out.println("  blackhole " + blackhole);
    }
}
