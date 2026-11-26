package perfbench;

import net.minecraft.profiler.Profiler;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import java.util.Random;

/**
 * Minimal concrete World for algorithm testing: no save handler, procedural chunk provider.
 * Population is disabled so chunk generation is deterministic and side-effect free.
 */
public class WorldStub extends World {
    public WorldStub(long seed) {
        super((ISaveHandler) null, new WorldInfo(new net.minecraft.world.WorldSettings(seed, net.minecraft.world.WorldSettings.GameType.SURVIVAL,
                false, false, net.minecraft.world.WorldType.FLAT), "bench"), new WorldProviderSurface(), new Profiler(),
                false);
        this.getWorldInfo().setSpawn(new net.minecraft.util.BlockPos(0, 64, 0));
        // World's constructor does not assign chunkProvider (real worlds do it in initialize()); do it here.
        try {
            java.lang.reflect.Field f = World.class.getDeclaredField("chunkProvider");
            f.setAccessible(true);
            f.set(this, createChunkProvider());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected IChunkProvider createChunkProvider() {
        return new ChunkProviderStub(this);
    }

    protected int getRenderDistanceChunks() {
        return 8;
    }
}
