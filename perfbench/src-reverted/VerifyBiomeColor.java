package perfbench;

import java.lang.reflect.Field;
import java.util.Random;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.util.BlockPos;
import net.minecraft.world.biome.BiomeColorHelper;

/**
 * Verifies the RegionRenderCache biome-colour memo returns exactly the same values as the
 * uncached 3x3 loop, for every position and y-level inside an 18x18 region, and that positions
 * outside the region still take the uncached path.
 */
public class VerifyBiomeColor {
    public static void main(String[] args) throws Exception {
        try { run(); } catch (Throwable t) { t.printStackTrace(); System.exit(2); }
    }

    static void run() throws Exception {
        Random rng = new Random(2024);
        int checks = 0;
        int failures = 0;

        // Build a chunk cache over real (null) world data: BiomeColorHelper falls back to the
        // world chunk manager for unloaded chunks, which is deterministic, so repeated calls agree.
        BenchBootstrap.register();
        WorldStub world = new WorldStub(1234L);
        ((ChunkProviderStub) world.getChunkProvider()).preload(0, 0, 2);
        RegionRenderCache cache = new RegionRenderCache(world, new BlockPos(0, 0, 0), new BlockPos(15, 255, 15), 1);

        // Reflectively drive the cache the way the helper does, comparing against the reference.
        for (int pass = 0; pass < 3; ++pass) {
            for (int slot = 0; slot < 3; ++slot) {
                Object resolver = BiomeColorHelper.getResolver(slot);
                for (int y = 0; y < 256; y += (pass == 0 ? 1 : 7)) {
                    for (int z = -3; z < 21; z += 1) {
                        for (int x = -3; x < 21; x += 1) {
                            BlockPos pos = new BlockPos(x, y, z);
                            int cached = cache.getCachedBiomeColor(resolver, pos);
                            int reference = BiomeColorHelper.computeBiomeColorAtPos(cache, pos,
                                    BiomeColorHelper.getResolver(slot));
                            ++checks;
                            if (cached != RegionRenderCache.NO_CACHED_BIOME_COLOR && cached != reference) {
                                if (++failures < 6) {
                                    System.out.println("MISMATCH slot=" + slot + " pos=" + x + "," + y + "," + z
                                            + " cached=" + Integer.toHexString(cached) + " ref="
                                            + Integer.toHexString(reference));
                                }
                            }
                            // RegionRenderCache's origin is posFromIn - subIn = (-1,-1,-1), so the
                            // 18x18 cache window covers x,z in [-1, 16]. Anything else must bail out.
                            boolean inside = x >= -1 && x <= 16 && z >= -1 && z <= 16;
                            if (!inside && cached != RegionRenderCache.NO_CACHED_BIOME_COLOR) {
                                if (++failures < 6) {
                                    System.out.println("OUT-OF-RANGE CACHE HIT at " + x + "," + z);
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures != 0) {
            System.exit(1);
        }
    }
}
