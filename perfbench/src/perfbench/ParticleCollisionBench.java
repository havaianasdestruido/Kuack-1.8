package perfbench;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

/**
 * Measures the cost of one collision query, to size the biggest CPU item found during the
 * maths/LUT sweep that is *not* a lookup-table problem: every particle runs the full entity
 * collision path in {@code Entity.moveEntity} because {@code EntityFX} does not set
 * {@code noClip}, and {@code World.getCollidingBoundingBoxes} allocates a fresh {@code ArrayList},
 * walks the padded AABB in x→z→y order and re-checks the world border per block.
 *
 * <p>Recorded here so the follow-up (removing the per-call allocation / merging the three
 * sequential sweep calls in {@code moveEntity}) has a baseline; see HOTSPOTS.md.</p>
 *
 * Run: {@code ./perfbench/run_bench.sh perfbench.ParticleCollisionBench}
 */
public final class ParticleCollisionBench {
    private static volatile long blackhole;

    public static void main(String[] args) throws Exception {
        BenchBootstrap.register();
        WorldStub world = new WorldStub(4242L);
        ((ChunkProviderStub) world.getChunkProvider()).preload(0, 0, 2);

        EntityItem probe = new EntityItem(world, 8.5, 70.0, 8.5, new ItemStack(Items.stick));
        AxisAlignedBB particleBox = new AxisAlignedBB(8.4, 69.9, 8.4, 8.6, 70.1, 8.6);
        AxisAlignedBB itemBox = new AxisAlignedBB(8.2, 69.8, 8.2, 8.8, 70.4, 8.8);
        AxisAlignedBB playerBox = new AxisAlignedBB(8.0, 69.0, 8.0, 8.6, 70.8, 8.6);

        System.out.println("=== ParticleCollisionBench: World.getCollidingBoundingBoxes ===");
        System.out.println("  (particles run this 1-3x per tick each, EntityFX.onUpdate -> Entity.moveEntity)");
        time("particle-sized AABB (0.2^3)", world, probe, particleBox);
        time("item-sized AABB (0.25^3 x gravity)", world, probe, itemBox);
        time("player-sized AABB (0.6 x 1.8)", world, probe, playerBox);
    }

    private static void time(String label, WorldStub world, EntityItem probe, AxisAlignedBB box) {
        for (int i = 0; i < 20000; ++i) {
            blackhole += world.getCollidingBoundingBoxes(probe, box).size();
        }
        int n = 200000;
        long[] s = new long[9];
        for (int t = 0; t < s.length; ++t) {
            long t0 = System.nanoTime();
            long acc = 0;
            for (int i = 0; i < n; ++i) {
                acc += world.getCollidingBoundingBoxes(probe, box).size();
            }
            s[t] = System.nanoTime() - t0;
            blackhole += acc;
        }
        java.util.Arrays.sort(s);
        double per = (double) s[s.length / 2] / n;
        System.out.printf("  %-34s %8.1f ns/call%n", label, per);
    }
}
