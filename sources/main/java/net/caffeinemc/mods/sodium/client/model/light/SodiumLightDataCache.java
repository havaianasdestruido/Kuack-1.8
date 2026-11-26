package net.caffeinemc.mods.sodium.client.model.light;

import java.util.Arrays;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Caches packed block lighting, ambient occlusion factor, and opacity flags across
 * the 20x20x20 neighborhood of a 16x16x16 chunk section to eliminate redundant
 * IBlockAccess queries during smooth/flat block meshing.
 */
public final class SodiumLightDataCache {

	private static final int DIAMETER = 20;
	private static final int SLICE = DIAMETER * DIAMETER;
	private static final int VOLUME = DIAMETER * DIAMETER * DIAMETER;

	private final long[] words = new long[VOLUME];
	private final boolean[] populated = new boolean[VOLUME];

	private IBlockAccess world;
	private int baseX;
	private int baseY;
	private int baseZ;
	private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();

	public void reset(IBlockAccess worldIn, int sectionOriginX, int sectionOriginY, int sectionOriginZ) {
		this.world = worldIn;
		this.baseX = sectionOriginX - 2;
		this.baseY = sectionOriginY - 2;
		this.baseZ = sectionOriginZ - 2;
		Arrays.fill(this.populated, false);
	}

	public long get(int x, int y, int z) {
		int rx = x - this.baseX;
		int ry = y - this.baseY;
		int rz = z - this.baseZ;
		if ((rx | ry | rz) < 0 || rx >= DIAMETER || ry >= DIAMETER || rz >= DIAMETER) {
			return compute(x, y, z);
		}
		int idx = (ry * SLICE) + (rz * DIAMETER) + rx;
		if (!this.populated[idx]) {
			long word = compute(x, y, z);
			this.words[idx] = word;
			this.populated[idx] = true;
			return word;
		}
		return this.words[idx];
	}

	private long compute(int x, int y, int z) {
		BlockPos.MutableBlockPos pos = this.scratchPos.func_181079_c(x, y, z);
		IBlockState state = this.world.getBlockState(pos);
		Block block = state.getBlock();
		int light = block.getMixedBrightnessForBlock(this.world, pos);
		float ao = block.getAmbientOcclusionLightValue();
		boolean translucent = block.isTranslucent();
		boolean opaque = block.isOpaqueCube();
		boolean fullCube = block.isFullCube();
		return pack(light, ao, translucent, opaque, fullCube);
	}

	public static long pack(int mixedBrightness, float ao, boolean translucent, boolean opaque, boolean fullCube) {
		long lightBits = ((long) mixedBrightness) & 0xFFFFFFFFL;
		long aoBits = ((long) (Float.floatToRawIntBits(ao)) & 0xFFFFFFFFL) << 32;
		// Encode boolean flags into unused upper bits of mixedBrightness (bits 24..31 are unused in MC lightmap!)
		long flags = (translucent ? (1L << 24) : 0L) | (opaque ? (1L << 25) : 0L) | (fullCube ? (1L << 26) : 0L);
		return (lightBits & 0x00FFFFFFL) | flags | aoBits;
	}

	public static int unpackLight(long word) {
		// Mixed brightness stores skyLight in 16..23 (up to 0xF0 = 240) and blockLight in 0..7 (up to 0xF0 = 240)
		return (int) (word & 0x00FF00FFL);
	}

	public static float unpackAO(long word) {
		return Float.intBitsToFloat((int) (word >>> 32));
	}

	public static boolean unpackTranslucent(long word) {
		return (word & (1L << 24)) != 0L;
	}

	public static boolean unpackOpaque(long word) {
		return (word & (1L << 25)) != 0L;
	}

	public static boolean unpackFullCube(long word) {
		return (word & (1L << 26)) != 0L;
	}
}
