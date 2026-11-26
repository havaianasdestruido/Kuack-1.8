package net.caffeinemc.mods.sodium.client.model.color;

import java.util.Arrays;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;

public final class SodiumBiomeColorCache {

	public static final int TYPE_GRASS = 0;
	public static final int TYPE_FOLIAGE = 1;
	public static final int TYPE_WATER = 2;

	private static final int MAX_RADIUS = 7;
	private static final int MAX_PADDED_SIZE = 16 + MAX_RADIUS * 2;
	private static final int MAX_SLICE_LEN = MAX_PADDED_SIZE * MAX_PADDED_SIZE;

	private int originX;
	private int originY;
	private int originZ;
	private int radius;
	private int paddedSize;
	private IBlockAccess world;

	private final int[][] grassSlices = new int[16][];
	private final int[][] foliageSlices = new int[16][];
	private int[] waterSlice;
	private final boolean[] grassPopulated = new boolean[16];
	private final boolean[] foliagePopulated = new boolean[16];
	private boolean waterPopulated;

	private final int[] blurScratch = new int[MAX_SLICE_LEN];
	private final BiomeGenBase[] biomeGrid = new BiomeGenBase[MAX_SLICE_LEN];
	private boolean biomesLoaded;
	private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();

	public void reset(IBlockAccess worldIn, int sectionOriginX, int sectionOriginY, int sectionOriginZ,
			int blendRadius) {
		this.world = worldIn;
		this.originX = sectionOriginX;
		this.originY = sectionOriginY;
		this.originZ = sectionOriginZ;
		this.radius = blendRadius < 0 ? 0 : (blendRadius > MAX_RADIUS ? MAX_RADIUS : blendRadius);
		this.paddedSize = 16 + (this.radius << 1);
		this.biomesLoaded = false;
		this.waterPopulated = false;
		Arrays.fill(this.grassPopulated, false);
		Arrays.fill(this.foliagePopulated, false);
	}

	public int getColor(int type, int worldX, int worldY, int worldZ) {
		int relX = worldX - this.originX;
		int relY = worldY - this.originY;
		int relZ = worldZ - this.originZ;
		if (relX < 0 || relX >= 16 || relY < 0 || relY >= 16 || relZ < 0 || relZ >= 16) {
			return sampleFallback(type, worldX, worldY, worldZ);
		}
		if (this.radius == 0) {
			return sampleSingle(type, worldX, worldY, worldZ);
		}

		int size = this.paddedSize;
		int idx = (relZ + this.radius) * size + (relX + this.radius);

		if (type == TYPE_WATER) {
			if (!this.waterPopulated) {
				if (this.waterSlice == null) {
					this.waterSlice = new int[MAX_SLICE_LEN];
				}
				populateSlice(TYPE_WATER, 0, this.waterSlice);
				this.waterPopulated = true;
			}
			return this.waterSlice[idx];
		}

		// For Y <= 64, vanilla temperature is independent of Y, so share slice index 0
		int sliceIndex = worldY <= 64 ? 0 : relY;
		int effectiveY = worldY <= 64 ? 64 : worldY;

		if (type == TYPE_GRASS) {
			if (!this.grassPopulated[sliceIndex]) {
				int[] slice = this.grassSlices[sliceIndex];
				if (slice == null) {
					slice = new int[MAX_SLICE_LEN];
					this.grassSlices[sliceIndex] = slice;
				}
				populateSlice(TYPE_GRASS, effectiveY, slice);
				this.grassPopulated[sliceIndex] = true;
			}
			return this.grassSlices[sliceIndex][idx];
		} else {
			if (!this.foliagePopulated[sliceIndex]) {
				int[] slice = this.foliageSlices[sliceIndex];
				if (slice == null) {
					slice = new int[MAX_SLICE_LEN];
					this.foliageSlices[sliceIndex] = slice;
				}
				populateSlice(TYPE_FOLIAGE, effectiveY, slice);
				this.foliagePopulated[sliceIndex] = true;
			}
			return this.foliageSlices[sliceIndex][idx];
		}
	}

	private void ensureBiomesLoaded() {
		if (this.biomesLoaded) {
			return;
		}
		int size = this.paddedSize;
		int startX = this.originX - this.radius;
		int startZ = this.originZ - this.radius;
		BlockPos.MutableBlockPos pos = this.scratchPos;
		int p = 0;
		for (int dz = 0; dz < size; ++dz) {
			int wz = startZ + dz;
			for (int dx = 0; dx < size; ++dx) {
				pos.func_181079_c(startX + dx, 64, wz);
				this.biomeGrid[p++] = this.world.getBiomeGenForCoords(pos);
			}
		}
		this.biomesLoaded = true;
	}

	private void populateSlice(int type, int y, int[] dst) {
		ensureBiomesLoaded();
		int size = this.paddedSize;
		int startX = this.originX - this.radius;
		int startZ = this.originZ - this.radius;
		BlockPos.MutableBlockPos pos = this.scratchPos;
		int p = 0;
		for (int dz = 0; dz < size; ++dz) {
			int wz = startZ + dz;
			for (int dx = 0; dx < size; ++dx) {
				BiomeGenBase biome = this.biomeGrid[p];
				int color;
				if (type == TYPE_WATER) {
					color = biome.waterColorMultiplier;
				} else {
					pos.func_181079_c(startX + dx, y, wz);
					color = (type == TYPE_GRASS) ? biome.getGrassColorAtPos(pos) : biome.getFoliageColorAtPos(pos);
				}
				dst[p++] = color & 0xFFFFFF;
			}
		}
		BoxBlur.blur2D(dst, this.blurScratch, size, this.radius);
	}

	private int sampleSingle(int type, int x, int y, int z) {
		BlockPos.MutableBlockPos pos = this.scratchPos.func_181079_c(x, y, z);
		BiomeGenBase biome = this.world.getBiomeGenForCoords(pos);
		if (type == TYPE_WATER) {
			return biome.waterColorMultiplier & 0xFFFFFF;
		}
		return ((type == TYPE_GRASS) ? biome.getGrassColorAtPos(pos) : biome.getFoliageColorAtPos(pos)) & 0xFFFFFF;
	}

	private int sampleFallback(int type, int x, int y, int z) {
		int r = this.radius;
		if (r <= 0) {
			return sampleSingle(type, x, y, z);
		}
		int sumR = 0;
		int sumG = 0;
		int sumB = 0;
		int count = 0;
		BlockPos.MutableBlockPos pos = this.scratchPos;
		for (int dz = -r; dz <= r; ++dz) {
			for (int dx = -r; dx <= r; ++dx) {
				pos.func_181079_c(x + dx, y, z + dz);
				BiomeGenBase biome = this.world.getBiomeGenForCoords(pos);
				int c = (type == TYPE_WATER) ? biome.waterColorMultiplier
						: ((type == TYPE_GRASS) ? biome.getGrassColorAtPos(pos) : biome.getFoliageColorAtPos(pos));
				sumR += (c >>> 16) & 0xFF;
				sumG += (c >>> 8) & 0xFF;
				sumB += c & 0xFF;
				++count;
			}
		}
		return ((sumR / count) << 16) | ((sumG / count) << 8) | (sumB / count);
	}
}
