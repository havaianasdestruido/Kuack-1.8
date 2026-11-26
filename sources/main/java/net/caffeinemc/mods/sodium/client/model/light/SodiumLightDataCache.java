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
 *
 * Uses O(1) stamp invalidation on reset() and two-stage lazy evaluation so outer
 * translucency probes never compute mixed sky/block brightness unless needed.
 */
public final class SodiumLightDataCache {

	private static final int DIAMETER = 20;
	private static final int SLICE = DIAMETER * DIAMETER;
	private static final int VOLUME = DIAMETER * DIAMETER * DIAMETER;

	private static final int STAGE_LIGHT_READY = 2;
	private static final int STAGE_FLAGS_ONLY = 1;

	private final long[] words = new long[VOLUME];
	private final int[] stamps = new int[VOLUME];
	private int currentStamp = 0;

	private IBlockAccess world;
	private int baseX;
	private int baseY;
	private int baseZ;
	private final BlockPos scratchPos = new BlockPos(0, 0, 0);

	public void reset(IBlockAccess worldIn, int sectionOriginX, int sectionOriginY, int sectionOriginZ) {
		this.world = worldIn;
		this.baseX = sectionOriginX - 2;
		this.baseY = sectionOriginY - 2;
		this.baseZ = sectionOriginZ - 2;
		// Reserve 2 stamp values per reset (stamp+1 = flags cached, stamp+2 = full light+AO cached)
		this.currentStamp += 4;
		if (this.currentStamp <= 0 || this.currentStamp >= Integer.MAX_VALUE - 8) {
			Arrays.fill(this.stamps, 0);
			this.currentStamp = 4;
		}
	}

	/**
	 * Lightweight translucency lookup that caches block flags without computing
	 * sky/block mixed brightness unless full light data is later requested.
	 */
	public boolean isTranslucent(int x, int y, int z) {
		int rx = x - this.baseX;
		int ry = y - this.baseY;
		int rz = z - this.baseZ;
		if ((rx | ry | rz) < 0 || rx >= DIAMETER || ry >= DIAMETER || rz >= DIAMETER) {
			BlockPos pos = this.scratchPos;
			pos.x = x;
			pos.y = y;
			pos.z = z;
			return this.world.getBlockState(pos).getBlock().isTranslucent();
		}
		int idx = (ry * SLICE) + (rz * DIAMETER) + rx;
		int stamp = this.stamps[idx] - this.currentStamp;
		if (stamp == STAGE_FLAGS_ONLY || stamp == STAGE_LIGHT_READY) {
			return unpackTranslucent(this.words[idx]);
		}
		BlockPos pos = this.scratchPos;
		pos.x = x;
		pos.y = y;
		pos.z = z;
		Block block = this.world.getBlockState(pos).getBlock();
		long word = pack(0, block.getAmbientOcclusionLightValue(), block.isTranslucent(), block.isOpaqueCube(),
				block.isFullCube());
		this.words[idx] = word;
		this.stamps[idx] = this.currentStamp + STAGE_FLAGS_ONLY;
		return unpackTranslucent(word);
	}

	public long get(int x, int y, int z) {
		int rx = x - this.baseX;
		int ry = y - this.baseY;
		int rz = z - this.baseZ;
		if ((rx | ry | rz) < 0 || rx >= DIAMETER || ry >= DIAMETER || rz >= DIAMETER) {
			return computeFull(x, y, z);
		}
		int idx = (ry * SLICE) + (rz * DIAMETER) + rx;
		int stamp = this.stamps[idx] - this.currentStamp;
		if (stamp == STAGE_LIGHT_READY) {
			return this.words[idx];
		}
		if (stamp == STAGE_FLAGS_ONLY) {
			BlockPos pos = this.scratchPos;
			pos.x = x;
			pos.y = y;
			pos.z = z;
			int light = this.world.getBlockState(pos).getBlock().getMixedBrightnessForBlock(this.world, pos);
			long word = (this.words[idx] & ~0x00FFFFFFL) | (light & 0x00FFFFFFL);
			this.words[idx] = word;
			this.stamps[idx] = this.currentStamp + STAGE_LIGHT_READY;
			return word;
		}
		long word = computeFull(x, y, z);
		this.words[idx] = word;
		this.stamps[idx] = this.currentStamp + STAGE_LIGHT_READY;
		return word;
	}

	private long computeFull(int x, int y, int z) {
		BlockPos pos = this.scratchPos;
		pos.x = x;
		pos.y = y;
		pos.z = z;
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
		long flags = (translucent ? (1L << 24) : 0L) | (opaque ? (1L << 25) : 0L) | (fullCube ? (1L << 26) : 0L);
		return (lightBits & 0x00FFFFFFL) | flags | aoBits;
	}

	public static int unpackLight(long word) {
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
