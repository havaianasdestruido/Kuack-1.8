package perfbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Models PlayerManager.isPlayerWatchingChunk's two membership queries, which run once per
 * (tracked entity, player) pair state-change inside EntityTrackerEntry.updatePlayerEntity:
 *
 *   playermanager$playerinstance.playersWatchingChunk.contains(player)   // ArrayList, O(P)
 *   !player.loadedChunks.contains(chunkCoords)                           // now LinkedHashSet, O(1)
 *
 * The first query is the remaining O(P) scan: for each of E tracked entities that moves and starts
 * being watched, every player in that chunk is scanned. E x P per tick.
 */
public class TrackerBench {
    static volatile long blackhole;

    public static void main(String[] args) {
        int[] playerCounts = { 1, 2, 4, 8, 16, 32 };
        int trackedEntities = 200; // e.g. 200 mobs/items moving into view per tick region

        BenchUtil.header("isPlayerWatchingChunk: playersWatchingChunk.contains() per call");
        System.out.printf("  %8s %14s %14s %10s%n", "players", "ArrayList ms", "HashSet ms", "speedup");
        for (int p : playerCounts) {
            List<Object> list = new ArrayList<>(p);
            java.util.Set<Object> set = new java.util.HashSet<>(p);
            List<Object> players = new ArrayList<>();
            for (int i = 0; i < p; ++i) {
                Object o = new Object();
                players.add(o);
                list.add(o);
                set.add(o);
            }
            Object probe = players.get(p - 1); // worst case: last element for the list
            int calls = trackedEntities * 50;
            long[] a = new long[9], b = new long[9];
            for (int w = 0; w < 100; ++w) {
                long s = 0;
                for (int i = 0; i < calls; ++i) if (list.contains(probe)) ++s;
                for (int i = 0; i < calls; ++i) if (set.contains(probe)) ++s;
                blackhole += s;
            }
            for (int t = 0; t < 9; ++t) {
                long s = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < calls; ++i) if (list.contains(probe)) ++s;
                a[t] = System.nanoTime() - t0;
                blackhole += s;
            }
            for (int t = 0; t < 9; ++t) {
                long s = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < calls; ++i) if (set.contains(probe)) ++s;
                b[t] = System.nanoTime() - t0;
                blackhole += s;
            }
            long ma = BenchUtil.median(a), mb = BenchUtil.median(b);
            System.out.printf("  %8d %14.3f %14.3f %9.2fx%n", p, ma / 1e6, mb / 1e6, (double) ma / (double) mb);
        }
        System.out.println("  blackhole " + blackhole);
    }
}
