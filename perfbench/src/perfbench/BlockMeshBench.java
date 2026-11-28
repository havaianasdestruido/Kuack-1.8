package perfbench;

import java.lang.management.ManagementFactory;
import java.util.Random;

import com.sun.management.ThreadMXBean;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/**
 * Benchmarks the BlockModelRenderer mesher loops: the pre-change implementation
 * (BlockModelRendererVanilla, fresh scratch per call) against the current production
 * implementation (reused scratch), on identical randomized (model, block, position,
 * checkSides) workloads with a real WorldStub + RegionRenderCache.
 *
 * Both arms run INTERLEAVED per trial under the same GC conditions (the mesher allocates very
 * differently in the two arms, so sequential trials would attribute GC timing to whichever arm
 * happens to run second). Reports median and min ns/block plus allocated bytes/block for both.
 *
 * Usage: ./perfbench/run_bench.sh perfbench.BlockMeshBench
 */
public class BlockMeshBench {
    static long blackhole;

    static final class Workload {
        final MeshHarness.SyntheticModel[] models;
        final Block[] blocks;
        final BlockPos[] positions;
        final boolean[] checkSides;

        Workload(Random rng, int n) {
            models = new MeshHarness.SyntheticModel[n];
            blocks = new Block[n];
            positions = new BlockPos[n];
            checkSides = new boolean[n];
            for (int i = 0; i < n; ++i) {
                models[i] = MeshHarness.SyntheticModel.build(rng, rng.nextBoolean(), rng.nextBoolean(),
                        rng.nextInt(4) == 0, rng.nextInt(3) == 0);
                blocks[i] = MeshHarness.PALETTE[rng.nextInt(MeshHarness.PALETTE.length)];
                positions[i] = MeshHarness.randomPos(rng);
                checkSides[i] = rng.nextInt(4) != 0;
            }
        }
    }

    interface Arm {
        boolean render(RegionRenderCache cache, MeshHarness.SyntheticModel m, Block b, BlockPos p, boolean cs,
                MeshHarness.BlackholeWorldRenderer rw);
    }

    public static void main(String[] args) throws Exception {
        BenchBootstrap.register();
        World world = MeshHarness.newWorld();
        ThreadMXBean tmx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (tmx.isThreadAllocatedMemorySupported()) {
            tmx.setThreadAllocatedMemoryEnabled(true);
        }
        long tid = Thread.currentThread().getId();
        final com.sun.management.ThreadMXBean tmx2 = tmx;

        final BlockModelRendererVanilla van = new BlockModelRendererVanilla();
        final net.minecraft.client.renderer.BlockModelRenderer prod = new net.minecraft.client.renderer.BlockModelRenderer();

        int n = 2048;
        MeshHarness.setAnaglyph(false);
        MeshHarness.setDeferred(false);
        MeshHarness.setDynamicLights(false);

        bench("AO mesh (default setting)", new Workload(new Random(77L), n), world, n,
                (c, m, b, p, cs, rw) -> van.renderModelAmbientOcclusion(c, m, b, p, rw, cs),
                (c, m, b, p, cs, rw) -> prod.renderModelAmbientOcclusion(c, m, b, p, rw, cs), tmx2, tid);

        MeshHarness.setDynamicLights(true);
        bench("AO mesh + dynamic lights", new Workload(new Random(78L), n), world, n,
                (c, m, b, p, cs, rw) -> van.renderModelAmbientOcclusion(c, m, b, p, rw, cs),
                (c, m, b, p, cs, rw) -> prod.renderModelAmbientOcclusion(c, m, b, p, rw, cs), tmx2, tid);
        MeshHarness.setDynamicLights(false);

        MeshHarness.setDeferred(true);
        bench("standard mesh, deferred pipeline", new Workload(new Random(79L), n), world, n,
                (c, m, b, p, cs, rw) -> van.renderModelStandard(c, m, b, p, rw, cs),
                (c, m, b, p, cs, rw) -> prod.renderModelStandard(c, m, b, p, rw, cs), tmx2, tid);
        MeshHarness.setDeferred(false);
        bench("standard mesh, non-deferred", new Workload(new Random(80L), n), world, n,
                (c, m, b, p, cs, rw) -> van.renderModelStandard(c, m, b, p, rw, cs),
                (c, m, b, p, cs, rw) -> prod.renderModelStandard(c, m, b, p, rw, cs), tmx2, tid);
        MeshHarness.setAnaglyph(false);
        System.out.println("  blackhole " + blackhole);
    }

    static void bench(String label, Workload w, World world, int n, Arm vanilla, Arm production,
            com.sun.management.ThreadMXBean tmx, long tid) {
        // warmup: interleave the two arms from the very start so both reach steady JIT/GC state together
        for (int i = 0; i < 5; ++i) {
            blackhole += pass(vanilla, w, world, n);
            blackhole += pass(production, w, world, n);
        }
        int trials = 15;
        long[] vSamples = new long[trials];
        long[] pSamples = new long[trials];
        for (int t = 0; t < trials; ++t) {
            long t0 = System.nanoTime();
            blackhole += pass(vanilla, w, world, n);
            vSamples[t] = System.nanoTime() - t0;
            t0 = System.nanoTime();
            blackhole += pass(production, w, world, n);
            pSamples[t] = System.nanoTime() - t0;
        }
        double vMed = BenchUtil.median(vSamples) / (double) n;
        double pMed = BenchUtil.median(pSamples) / (double) n;
        double vMin = min(vSamples) / (double) n;
        double pMin = min(pSamples) / (double) n;
        long vAlloc = alloc(vanilla, w, world, n, tmx, tid) / n;
        long pAlloc = alloc(production, w, world, n, tmx, tid) / n;
        System.out.printf(
                "  %-34s median vanilla %8.1f  prod %8.1f ns/block (%5.2fx)  min vanilla %8.1f  prod %8.1f  alloc/block vanilla %,6d B  prod %,6d B%n",
                label, vMed, pMed, vMed / pMed, vMin, pMin, vAlloc, pAlloc);
    }

    static long min(long[] s) {
        long m = s[0];
        for (long v : s) {
            if (v < m) {
                m = v;
            }
        }
        return m;
    }

    static long pass(Arm a, Workload w, World world, int n) {
        // one RegionRenderCache per pass, as a real chunk-section rebuild task would use
        RegionRenderCache cache = MeshHarness.newCache(world);
        MeshHarness.BlackholeWorldRenderer rw = new MeshHarness.BlackholeWorldRenderer();
        long acc = 0;
        for (int i = 0; i < n; ++i) {
            acc += a.render(cache, w.models[i], w.blocks[i], w.positions[i], w.checkSides[i], rw) ? 1 : 0;
        }
        return acc ^ rw.bh;
    }

    static long alloc(Arm a, Workload w, World world, int n, com.sun.management.ThreadMXBean tmx, long tid) {
        blackhole += pass(a, w, world, n);
        long before = tmx.getThreadAllocatedBytes(tid);
        blackhole += pass(a, w, world, n);
        long after = tmx.getThreadAllocatedBytes(tid);
        return after - before;
    }
}
