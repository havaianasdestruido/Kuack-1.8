package net.caffeinemc.mods.sodium.client.model.light;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;

/**
 * Sodium-style block face culler: computes per-block visible face bitmasks once per block
 * and optionally culls un-annotated general quads that lie flush on unit cube boundaries
 * against adjacent opaque full cubes.
 */
public final class SodiumFaceCuller {

	private static final float EPSILON = 0.0005F;

	private SodiumFaceCuller() {
	}

	public static int computeVisibleFacesMask(IBlockAccess world, Block block, BlockPos pos,
			BlockPos.MutableBlockPos scratchPos) {
		int mask = 0;
		EnumFacing[] facings = EnumFacing._VALUES;
		for (int i = 0; i < facings.length; ++i) {
			EnumFacing dir = facings[i];
			scratchPos.func_181079_c(pos.getX() + dir.getFrontOffsetX(), pos.getY() + dir.getFrontOffsetY(),
					pos.getZ() + dir.getFrontOffsetZ());
			if (block.shouldSideBeRendered(world, scratchPos, dir)) {
				mask |= (1 << dir.ordinal());
			}
		}
		return mask;
	}

	public static int computeNeighborOpaqueFullCubeMask(IBlockAccess world, BlockPos pos,
			BlockPos.MutableBlockPos scratchPos) {
		int mask = 0;
		EnumFacing[] facings = EnumFacing._VALUES;
		for (int i = 0; i < facings.length; ++i) {
			EnumFacing dir = facings[i];
			scratchPos.func_181079_c(pos.getX() + dir.getFrontOffsetX(), pos.getY() + dir.getFrontOffsetY(),
					pos.getZ() + dir.getFrontOffsetZ());
			IBlockState adjState = world.getBlockState(scratchPos);
			Block adjBlock = adjState.getBlock();
			if (adjBlock.isOpaqueCube() && adjBlock.isFullCube()) {
				mask |= (1 << dir.ordinal());
			}
		}
		return mask;
	}

	/**
	 * Checks whether a non-cullface general quad actually lies coplanar with one of the 6 unit cube faces
	 * and is completely covered by an adjacent opaque full cube.
	 */
	public static boolean isGeneralQuadOccluded(BakedQuad quad, int neighborOpaqueMask) {
		if (neighborOpaqueMask == 0) {
			return false;
		}
		EnumFacing face = quad.getFace();
		if (face == null) {
			return false;
		}
		int bit = 1 << face.ordinal();
		if ((neighborOpaqueMask & bit) == 0) {
			return false;
		}

		int[] data = quad.getVertexData();
		if (data == null || data.length < 28) {
			return false;
		}
		int stride = data.length >> 2;

		float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

		for (int i = 0; i < 4; ++i) {
			int base = i * stride;
			float x = Float.intBitsToFloat(data[base]);
			float y = Float.intBitsToFloat(data[base + 1]);
			float z = Float.intBitsToFloat(data[base + 2]);
			if (x < minX) minX = x;
			if (x > maxX) maxX = x;
			if (y < minY) minY = y;
			if (y > maxY) maxY = y;
			if (z < minZ) minZ = z;
			if (z > maxZ) maxZ = z;
		}

		switch (face) {
		case DOWN:
			return maxY <= EPSILON && minX >= -EPSILON && maxX <= 1.0F + EPSILON && minZ >= -EPSILON
					&& maxZ <= 1.0F + EPSILON;
		case UP:
			return minY >= 1.0F - EPSILON && minX >= -EPSILON && maxX <= 1.0F + EPSILON && minZ >= -EPSILON
					&& maxZ <= 1.0F + EPSILON;
		case NORTH:
			return maxZ <= EPSILON && minX >= -EPSILON && maxX <= 1.0F + EPSILON && minY >= -EPSILON
					&& maxY <= 1.0F + EPSILON;
		case SOUTH:
			return minZ >= 1.0F - EPSILON && minX >= -EPSILON && maxX <= 1.0F + EPSILON && minY >= -EPSILON
					&& maxY <= 1.0F + EPSILON;
		case WEST:
			return maxX <= EPSILON && minY >= -EPSILON && maxY <= 1.0F + EPSILON && minZ >= -EPSILON
					&& maxZ <= 1.0F + EPSILON;
		case EAST:
			return minX >= 1.0F - EPSILON && minY >= -EPSILON && maxY <= 1.0F + EPSILON && minZ >= -EPSILON
					&& maxZ <= 1.0F + EPSILON;
		default:
			return false;
		}
	}

	public static boolean hasAnyQuads(List<BakedQuad> list) {
		return list != null && !list.isEmpty();
	}
}
