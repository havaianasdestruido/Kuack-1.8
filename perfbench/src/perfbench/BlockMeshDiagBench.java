package perfbench;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/**
 * Diagnostic bench: isolates which part of the scratch-reuse change moves AO mesh time on
 * HotSpot. Three bench-only variants of the mesher (generated from the vanilla reference):
 * vanilla (fresh scratch), shared scratch with per-facing offset(), shared scratch with
 * offsetEvenFaster(). Production equals the third variant. All arms run interleaved per trial
 * under the same GC conditions. See FINDINGS #14 for the recorded numbers.
 */
public class BlockMeshDiagBench {
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
        int n = 2048;
        Workload w = new Workload(new Random(77L), n);
        MeshHarness.setAnaglyph(false);
        MeshHarness.setDeferred(false);
        MeshHarness.setDynamicLights(false);

        final BlockModelRendererDiagShared shared = new BlockModelRendererDiagShared();
        final BlockModelRendererDiagSharedMutable mut = new BlockModelRendererDiagSharedMutable();
        final BlockModelRendererVanilla van = new BlockModelRendererVanilla();
        final net.minecraft.client.renderer.BlockModelRenderer prod = new net.minecraft.client.renderer.BlockModelRenderer();

        Arm[] arms = new Arm[] {
            (c, m, b, p, cs, rw) -> van.renderModelAmbientOcclusion(c, m, b, p, rw, cs),
            (c, m, b, p, cs, rw) -> shared.renderModelAmbientOcclusion(c, m, b, p, rw, cs),
            (c, m, b, p, cs, rw) -> mut.renderModelAmbientOcclusion(c, m, b, p, rw, cs),
            (c, m, b, p, cs, rw) -> prod.renderModelAmbientOcclusion(c, m, b, p, rw, cs),
        };
        String[] names = { "vanilla", "shared", "shared+mutable", "production" };

        for (int i = 0; i < 5; ++i) {
            for (Arm a : arms) {
                blackhole += pass(a, w, world, n);
            }
        }
        int trials = 15;
        long[][] samples = new long[arms.length][trials];
        for (int t = 0; t < trials; ++t) {
            for (int k = 0; k < arms.length; ++k) {
                long t0 = System.nanoTime();
                blackhole += pass(arms[k], w, world, n);
                samples[k][t] = System.nanoTime() - t0;
            }
        }
        for (int k = 0; k < arms.length; ++k) {
            long med = BenchUtil.median(samples[k]);
            long min = samples[k][0];
            for (long s : samples[k]) {
                if (s < min) {
                    min = s;
                }
            }
            System.out.printf("  AO %-18s median %8.1f ns/block   min %8.1f ns/block%n", names[k],
                    med / (double) n, min / (double) n);
        }
        System.out.println("  blackhole " + blackhole);
    }

    static long pass(Arm a, Workload w, World world, int n) {
        RegionRenderCache cache = MeshHarness.newCache(world);
        MeshHarness.BlackholeWorldRenderer rw = new MeshHarness.BlackholeWorldRenderer();
        long acc = 0;
        for (int i = 0; i < n; ++i) {
            acc += a.render(cache, w.models[i], w.blocks[i], w.positions[i], w.checkSides[i], rw) ? 1 : 0;
        }
        return acc ^ rw.bh;
    }
}
