package perfbench;

import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * Chunk provider that fills chunks deterministically with grass/dirt/stone layers so that
 * biome lookups, lighting input and block queries are representative, without running worldgen.
 */
public class ChunkProviderStub implements IChunkProvider {
    private final World world;
    private final java.util.Map<Long, Chunk> chunks = new java.util.HashMap<>();
    private final java.util.Map<Long, Boolean> pending = new java.util.HashMap<>();

    public ChunkProviderStub(World worldIn) {
        this.world = worldIn;
    }

    public boolean chunkExists(int i, int j) {
        long key = (((long) i) << 32) ^ (j & 0xFFFFFFFFL);
        return this.chunks.containsKey(key) || this.pending.containsKey(key);
    }

    /** Pre-generates a square of chunks so neighbourhood queries see loaded data. */
    public void preload(int centerX, int centerZ, int radius) {
        for (int cx = centerX - radius; cx <= centerX + radius; ++cx) {
            for (int cz = centerZ - radius; cz <= centerZ + radius; ++cz) {
                provideChunk(cx, cz);
            }
        }
    }

    public Chunk provideChunk(int cx, int cz) {
        long key = (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
        Chunk existing = this.chunks.get(key);
        if (existing != null) {
            return existing;
        }
        if (this.pending.containsKey(key)) {
            // re-entrant request while this chunk is being filled (light updates): return empty chunk
            return new Chunk(this.world, cx, cz);
        }
        Chunk chunk = new Chunk(this.world, cx, cz);
        this.chunks.put(key, chunk);
        this.pending.put(key, Boolean.TRUE);
        buildChunk(chunk, cx, cz);
        this.pending.remove(key);
        chunk.generateSkylightMap();
        return chunk;
    }

    private Chunk buildChunk(Chunk chunk, int cx, int cz) {
        Random rng = new Random((((long) cx) << 32) ^ (cz * 0x9E3779B9L));
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int height = 60 + rng.nextInt(8);
                for (int y = 0; y < 256; ++y) {
                    if (y == 0) {
                        chunk.setBlockState(new BlockPos(x, y, z), Blocks.bedrock.getDefaultState());
                    } else if (y < height - 4) {
                        chunk.setBlockState(new BlockPos(x, y, z), Blocks.stone.getDefaultState());
                    } else if (y < height) {
                        chunk.setBlockState(new BlockPos(x, y, z), Blocks.dirt.getDefaultState());
                    } else if (y == height) {
                        chunk.setBlockState(new BlockPos(x, y, z), Blocks.grass.getDefaultState());
                    }
                }
                // vary the biome across the chunk so grass colour actually differs per column
                chunk.getBiomeArray()[z << 4 | x] = (byte) (BiomeGenBase.getBiome(1 + rng.nextInt(20)).biomeID & 255);
            }
        }
        return chunk;
    }

    public Chunk provideChunk(BlockPos blockPosIn) {
        return this.provideChunk(blockPosIn.getX() >> 4, blockPosIn.getZ() >> 4);
    }

    public void populate(IChunkProvider chunkProviderIn, int x, int z) {
    }

    public boolean func_177460_a(IChunkProvider p_177460_1_, Chunk p_177460_2_, int p_177460_3_, int p_177460_4_) {
        return false;
    }

    public boolean saveChunks(boolean flag, net.minecraft.util.IProgressUpdate progressCallback) {
        return true;
    }

    public boolean unloadQueuedChunks() {
        return false;
    }

    public boolean canSave() {
        return false;
    }

    public String makeString() {
        return "ChunkProviderStub";
    }

    public java.util.List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(
            net.minecraft.entity.EnumCreatureType creatureType, BlockPos pos) {
        return java.util.Collections.emptyList();
    }

    public BlockPos getStrongholdGen(World worldIn, String structureName, BlockPos position) {
        return null;
    }

    public int getLoadedChunkCount() {
        return 0;
    }

    public void recreateStructures(Chunk chunkIn, int x, int z) {
    }

    public void saveExtraData() {
    }
}
