package perfbench;

import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.util.BlockPos;
import net.minecraft.world.biome.BiomeColorHelper;

/**
 * Realistic chunk-section sweep: blocks are visited y-major (as RenderChunk.rebuildChunk does with
 * BlockPos.getAllInBox, which increments x, then y, then z), and every block does 6 quads.
 *
 * Compares:
 *   reference : pure 3x3 recompute per quad (vanilla cost)
 *   columnMemo: this change's per-(column,y) memo
 *   posMemo   : a hypothetical per-position memo (ideal lower bound)
 */
public class BiomeColorSweep {
    static volatile long blackhole;

    static final int N = 4096;
    static final int QUADS = 6;
    static final BlockPos[] POS = new BlockPos[N];

    public static void main(String[] args) {
        BenchBootstrap.register();
        WorldStub world = new WorldStub(4242L);
        ((ChunkProviderStub) world.getChunkProvider()).preload(0, 0, 2);
        RegionRenderCache cache = new RegionRenderCache(world, new BlockPos(0, 0, 0), new BlockPos(15, 255, 15), 1);

        // getAllInBox order: x fastest, then y, then z
        int i = 0;
        for (int z = 0; z < 16; ++z) {
            for (int y = 0; y < 16; ++y) {
                for (int x = 0; x < 16; ++x) {
                    POS[i++] = new BlockPos(x, 60 + y, z);
                }
            }
        }

        Object grass = BiomeColorHelper.getResolver(0);

        for (int w = 0; w < 30; ++w) {
            blackhole += runReference(cache, grass);
            blackhole += runColumn(cache, grass);
            blackhole += runPos(cache, grass);
        }

        long[] s = new long[9];
        for (int t = 0; t < 9; ++t) {
            long t0 = System.nanoTime();
            long acc = runReference(cache, grass);
            s[t] = System.nanoTime() - t0;
            blackhole += acc;
        }
        long ref = BenchUtil.median(s);

        for (int t = 0; t < 9; ++t) {
            long t0 = System.nanoTime();
            long acc = runColumn(cache, grass);
            s[t] = System.nanoTime() - t0;
            blackhole += acc;
        }
        long col = BenchUtil.median(s);

        for (int t = 0; t < 9; ++t) {
            long t0 = System.nanoTime();
            long acc = runPos(cache, grass);
            s[t] = System.nanoTime() - t0;
            blackhole += acc;
        }
        long pos = BenchUtil.median(s);

        BenchUtil.header("Chunk-section sweep, 4096 blocks x 6 tinted quads (real classes)");
        System.out.printf("  reference (per quad)  %8.3f ms  %,.0f ns/quad%n", ref / 1e6, (double) ref / (N * QUADS));
        System.out.printf("  column memo           %8.3f ms  %,.0f ns/quad   speedup %.2fx%n", col / 1e6,
                (double) col / (N * QUADS), (double) ref / (double) col);
        System.out.printf("  position memo (ideal) %8.3f ms  %,.0f ns/quad   speedup %.2fx%n", pos / 1e6,
                (double) pos / (N * QUADS), (double) ref / (double) pos);
        System.out.println("  blackhole " + blackhole);
    }

    static int compute(RegionRenderCache cache, BlockPos p, Object resolver) {
        return BiomeColorHelper.computeBiomeColorAtPos(cache, p, (BiomeColorHelper.ColorResolver) resolver);
    }

    static long runReference(RegionRenderCache cache, Object resolver) {
        Object r = BiomeColorHelper.getResolver(BiomeColorHelper.getResolverIndex(resolver));
        long acc = 0;
        for (int i = 0; i < N; ++i) {
            int c = compute(cache, POS[i], r);
            for (int q = 0; q < QUADS; ++q) {
                acc += c;
            }
        }
        return acc;
    }

    static long runColumn(RegionRenderCache cache, Object resolver) {
        long acc = 0;
        for (int i = 0; i < N; ++i) {
            int c = cache.getCachedBiomeColor(resolver, POS[i]);
            for (int q = 0; q < QUADS; ++q) {
                acc += c;
            }
        }
        return acc;
    }

    // Ideal case: 256-column x 16-y memo. Simulated locally to bound what is achievable.
    static int[] posCache = new int[4096];
    static boolean[] posValid = new boolean[4096];

    static long runPos(RegionRenderCache cache, Object resolver) {
        Arrays.fill(posValid, false);
        Object r = BiomeColorHelper.getResolver(BiomeColorHelper.getResolverIndex(resolver));
        long acc = 0;
        for (int i = 0; i < N; ++i) {
            BlockPos p = POS[i];
            int idx = ((p.getY() - 60) << 8) | ((p.getZ() & 15) << 4) | (p.getX() & 15);
            if (!posValid[idx]) {
                posCache[idx] = compute(cache, p, r);
                posValid[idx] = true;
            }
            int c = posCache[idx];
            for (int q = 0; q < QUADS; ++q) {
                acc += c;
            }
        }
        return acc;
    }
}
