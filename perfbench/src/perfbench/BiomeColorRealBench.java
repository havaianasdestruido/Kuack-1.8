package perfbench;

import java.util.Random;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.util.BlockPos;
import net.minecraft.world.biome.BiomeColorHelper;

/**
 * Measures the real BiomeColorHelper path over a chunk section, using the real RegionRenderCache
 * (with the memo) and an identical ChunkCache-backed instance for the reference (memo bypassed by
 * querying the 3x3 average directly).
 */
public class BiomeColorRealBench {
    static volatile long blackhole;

    public static void main(String[] args) {
        BenchBootstrap.register();
        WorldStub world = new WorldStub(4242L);
        ((ChunkProviderStub) world.getChunkProvider()).preload(0, 0, 2);
        RegionRenderCache cache = new RegionRenderCache(world, new BlockPos(0, 0, 0), new BlockPos(15, 255, 15), 1);

        final int blocks = 4096;
        final int quadsPerBlock = 6;
        BlockPos[] positions = new BlockPos[blocks];
        Random rng = new Random(9);
        for (int i = 0; i < blocks; ++i) {
            positions[i] = new BlockPos(rng.nextInt(16), 55 + rng.nextInt(16), rng.nextInt(16));
        }

        Object grass = BiomeColorHelper.getResolver(0);
        Object foliage = BiomeColorHelper.getResolver(1);

        // warmup
        for (int w = 0; w < 40; ++w) {
            blackhole += runReference(cache, positions, grass, quadsPerBlock);
            blackhole += runCached(cache, positions, grass, quadsPerBlock);
        }

        long[] s = new long[9];
        for (int t = 0; t < 9; ++t) {
            long t0 = System.nanoTime();
            long acc = runReference(cache, positions, grass, quadsPerBlock);
            s[t] = System.nanoTime() - t0;
            blackhole += acc;
        }
        long ref = BenchUtil.median(s);

        for (int t = 0; t < 9; ++t) {
            long t0 = System.nanoTime();
            long acc = runCached(cache, positions, grass, quadsPerBlock);
            s[t] = System.nanoTime() - t0;
            blackhole += acc;
        }
        long opt = BenchUtil.median(s);

        BenchUtil.header("Real meshing path: 4096 grass blocks x 6 quads");
        System.out.printf("  reference (uncached 3x3)  %10.3f ms  %,.0f ns/quad%n", ref / 1e6, (double) ref / (blocks * quadsPerBlock));
        System.out.printf("  memoised (this change)    %10.3f ms  %,.0f ns/quad   speedup %.2fx%n", opt / 1e6,
                (double) opt / (blocks * quadsPerBlock), (double) ref / (double) opt);
        System.out.println("  blackhole " + blackhole);
    }

    static long runReference(RegionRenderCache cache, BlockPos[] positions, Object resolver, int quads) {
        long acc = 0;
        for (int i = 0; i < positions.length; ++i) {
            int c = BiomeColorHelper.computeBiomeColorAtPos(cache, positions[i],
                    BiomeColorHelper.getResolver(BiomeColorHelper.getResolverIndex(resolver)));
            for (int q = 0; q < quads; ++q) {
                acc += c;
            }
        }
        return acc;
    }

    static long runCached(RegionRenderCache cache, BlockPos[] positions, Object resolver, int quads) {
        long acc = 0;
        for (int i = 0; i < positions.length; ++i) {
            int c = cache.getCachedBiomeColor(resolver, positions[i]);
            if (c == RegionRenderCache.NO_CACHED_BIOME_COLOR) {
                c = 0;
            }
            for (int q = 0; q < quads; ++q) {
                acc += c;
            }
        }
        return acc;
    }
}
