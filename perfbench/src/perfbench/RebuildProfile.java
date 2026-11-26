package perfbench;

import java.util.Random;

import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/** Studies which per-block operations dominate a chunk-section rebuild at the data-structure level. */
public class RebuildProfile {
    static volatile long blackhole;

    public static void main(String[] args) throws Exception {
        BenchBootstrap.register();
        WorldStub world = new WorldStub(99L);
        ((ChunkProviderStub) world.getChunkProvider()).preload(0, 0, 2);
        RegionRenderCache cache = new RegionRenderCache(world, new BlockPos(0, 0, 0), new BlockPos(15, 255, 15), 1);

        // measure the primitives used per block during rebuild
        int n = 4096;
        BlockPos[] pos = new BlockPos[n];
        Random rng = new Random(1);
        for (int i = 0; i < n; ++i) {
            pos[i] = new BlockPos(rng.nextInt(16), 55 + rng.nextInt(20), rng.nextInt(16));
        }

        BenchUtil.header("Per-block primitive costs (4096 lookups, real RegionRenderCache)");
        time("getBlockStateFaster", 200, () -> {
            long a = 0;
            for (int i = 0; i < n; ++i) {
                a += cache.getBlockStateFaster(pos[i]).hashCode();
            }
            return a;
        }, n);
        time("getCombinedLight", 200, () -> {
            long a = 0;
            for (int i = 0; i < n; ++i) {
                a += cache.getCombinedLight(pos[i], 0);
            }
            return a;
        }, n);
        time("isOpaqueCube", 200, () -> {
            long a = 0;
            for (int i = 0; i < n; ++i) {
                a += cache.getBlockStateFaster(pos[i]).getBlock().isOpaqueCube() ? 1 : 0;
            }
            return a;
        }, n);
        time("colorMultiplier(grass)", 200, () -> {
            long a = 0;
            for (int i = 0; i < n; ++i) {
                a += cache.getBlockStateFaster(pos[i]).getBlock().colorMultiplier(cache, pos[i], 0);
            }
            return a;
        }, n);
        time("new BlockPos", 200, () -> {
            long a = 0;
            for (int i = 0; i < n; ++i) {
                a += new BlockPos(pos[i].getX(), pos[i].getY(), pos[i].getZ()).hashCode();
            }
            return a;
        }, n);
        System.out.println("  blackhole " + blackhole);
    }

    interface Body {
        long run();
    }

    static void time(String label, int trials, Body body, int n) {
        for (int i = 0; i < 50; ++i) {
            blackhole += body.run();
        }
        long[] s = new long[trials > 15 ? 9 : trials];
        for (int t = 0; t < s.length; ++t) {
            long t0 = System.nanoTime();
            blackhole += body.run();
            s[t] = System.nanoTime() - t0;
        }
        java.util.Arrays.sort(s);
        double per = (double) s[s.length / 2] / n;
        System.out.printf("  %-26s %10.2f ns/lookup   (%8.3f ms / 4096)%n", label, per, s[s.length / 2] / 1e6);
    }
}
