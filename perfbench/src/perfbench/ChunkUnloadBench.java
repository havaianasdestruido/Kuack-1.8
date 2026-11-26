package perfbench;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * ChunkProviderServer.unloadQueuedChunks calls loadedChunks.remove(chunk) up to 100 times per tick.
 * loadedChunks is a LinkedList, so each remove() is an O(n) walk with an equals() call per node.
 * Chunk does not override equals, so this is an identity scan -- ideal for an identity-keyed set.
 */
public class ChunkUnloadBench {
    static volatile long blackhole;

    /** Mirrors net.minecraft.world.chunk.Chunk for the purposes of identity semantics. */
    static final class Chunk {
        final int x, z;

        Chunk(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    public static void main(String[] args) {
        int[] sizes = { 256, 1024, 4096, 8192, 16384 };

        BenchUtil.header("ChunkProviderServer.unloadQueuedChunks: 100 removals per tick");

        System.out.printf("  %7s %16s %16s %10s%n", "chunks", "LinkedList ms", "LinkedHashSet ms", "speedup");
        for (int n : sizes) {
            List<Chunk> chunks = new ArrayList<>(n);
            for (int i = 0; i < n; ++i) {
                chunks.add(new Chunk(i, i * 7));
            }

            long[] a = new long[9], b = new long[9];
            for (int w = 0; w < 20; ++w) {
                blackhole += runList(chunks);
                blackhole += runSet(chunks);
            }
            for (int t = 0; t < 9; ++t) {
                long t0 = System.nanoTime();
                blackhole += runList(chunks);
                a[t] = System.nanoTime() - t0;
            }
            for (int t = 0; t < 9; ++t) {
                long t0 = System.nanoTime();
                blackhole += runSet(chunks);
                b[t] = System.nanoTime() - t0;
            }
            long ma = BenchUtil.median(a), mb = BenchUtil.median(b);
            System.out.printf("  %7d %16.4f %16.4f %9.2fx%n", n, ma / 1e6, mb / 1e6, (double) ma / (double) mb);
        }

        // steady-state: a server that keeps ~R^2 chunks live and drops a few per tick
        BenchUtil.header("steady state: 5929 live chunks, 24 unloads/tick (radius 12 view)");
        {
            int n = 5929;
            List<Chunk> chunks = new ArrayList<>(n);
            for (int i = 0; i < n; ++i) chunks.add(new Chunk(i % 77 - 38, i / 77 - 38));
            long accA = 0, accB = 0;
            int trials = 2000;
            for (int w = 0; w < 200; ++w) { accA += runList(chunks); accB += runSet(chunks); }
            long[] a = new long[9], b = new long[9];
            for (int t = 0; t < 9; ++t) {
                long t0 = System.nanoTime();
                for (int i = 0; i < trials; ++i) accA += runList(chunks);
                a[t] = System.nanoTime() - t0;
            }
            for (int t = 0; t < 9; ++t) {
                long t0 = System.nanoTime();
                for (int i = 0; i < trials; ++i) accB += runSet(chunks);
                b[t] = System.nanoTime() - t0;
            }
            double ma = (double) BenchUtil.median(a) / trials, mb = (double) BenchUtil.median(b) / trials;
            System.out.printf("  per tick: LinkedList %.3f ms   LinkedHashSet %.3f ms   %.1fx%n",
                    ma / 1e6, mb / 1e6, ma / mb);
            System.out.printf("  at 20 tps that is %.1f ms/s of steady server time%n", mb / 1e3 * 20);
            blackhole += accA + accB;
        }
        System.out.println("  blackhole " + blackhole);
    }

    /** Vanilla shape: LinkedList removes. */
    static long runList(List<Chunk> chunks) {
        LinkedList<Chunk> l = new LinkedList<>(chunks);
        Set<Chunk> dropped = droppedFrom(chunks, 100);
        int c = 0;
        for (Chunk ch : dropped) {
            if (l.remove(ch)) ++c;
        }
        return c + l.size();
    }

    /** Patched shape: LinkedHashSet removes. */
    static long runSet(List<Chunk> chunks) {
        Set<Chunk> l = new LinkedHashSet<>(chunks);
        Set<Chunk> dropped = droppedFrom(chunks, 100);
        int c = 0;
        for (Chunk ch : dropped) {
            if (l.remove(ch)) ++c;
        }
        return c + l.size();
    }

    static Set<Chunk> droppedFrom(List<Chunk> chunks, int howMany) {
        Set<Chunk> s = new LinkedHashSet<>();
        int step = Math.max(1, chunks.size() / howMany);
        for (int i = 0; i < howMany && i * step < chunks.size(); ++i) {
            s.add(chunks.get(i * step));
        }
        return s;
    }
}
