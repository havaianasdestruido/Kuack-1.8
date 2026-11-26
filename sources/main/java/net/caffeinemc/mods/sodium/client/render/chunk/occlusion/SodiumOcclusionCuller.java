package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import java.util.List;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.viewport.SodiumViewportFrustum;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Zero-allocation double-buffered BFS chunk graph occlusion culler inspired by Sodium's OcclusionCuller.
 * Replaces LinkedList<ContainerLocalRenderInformation>, EnumSet<EnumFacing>, and per-frame camera-chunk
 * 4096-block scans in RenderGlobal.setupTerrain.
 */
public final class SodiumOcclusionCuller {

	// Direction ordinals: 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST
	private static final int ALL_DIRECTIONS = 0x3F;

	private int[] queueA = new int[4096];
	private int[] queueB = new int[4096];
	private byte[] incomingDirs = new byte[4096];
	private byte[] allowedStepMask = new byte[4096];
	private int[] visitedStamp = new int[4096];
	private int currentStamp = 1;

	private final SodiumVisGraph eyeVisGraph = new SodiumVisGraph();
	private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();

	private int cachedEyeSectionX = Integer.MIN_VALUE;
	private int cachedEyeSectionY = Integer.MIN_VALUE;
	private int cachedEyeSectionZ = Integer.MIN_VALUE;
	private int cachedEyeLocalPos = -1;
	private CompiledChunk cachedEyeCompiled = null;
	private int cachedEyeOpenMask = ALL_DIRECTIONS;

	private int lastTotalSections = 0;
	private int lastVisibleSections = 0;
	private int lastFogCulledSections = 0;

	private void ensureCapacity(int totalChunks) {
		if (this.queueA.length < totalChunks) {
			int cap = Math.max(totalChunks, this.queueA.length << 1);
			this.queueA = new int[cap];
			this.queueB = new int[cap];
			this.incomingDirs = new byte[cap];
			this.allowedStepMask = new byte[cap];
			this.visitedStamp = new int[cap];
			this.currentStamp = 1;
		}
		if (++this.currentStamp == Integer.MAX_VALUE) {
			for (int i = 0; i < this.visitedStamp.length; ++i) {
				this.visitedStamp[i] = 0;
			}
			this.currentStamp = 1;
		}
	}

	public void invalidateEyeCache() {
		this.cachedEyeSectionX = Integer.MIN_VALUE;
		this.cachedEyeCompiled = null;
	}

	/**
	 * Performs full graph + frustum + cylindrical/fog occlusion traversal and populates outVisibleChunks
	 * in front-to-back BFS order.
	 */
	public void findVisibleChunks(ViewFrustum viewFrustum, World world, SodiumViewportFrustum frustum, double camX,
			double camY, double camZ, double eyeY, float viewVecX, float viewVecY, float viewVecZ,
			int renderDistanceChunks, int frameCount, boolean spectator, boolean useOcclusionGraph,
			List<RenderChunk> outVisibleChunks) {

		outVisibleChunks.clear();
		RenderChunk[] grid = viewFrustum.renderChunks;
		int totalChunks = grid.length;
		this.lastTotalSections = totalChunks;
		this.lastFogCulledSections = 0;
		if (totalChunks == 0) {
			this.lastVisibleSections = 0;
			return;
		}

		ensureCapacity(totalChunks);
		int stamp = this.currentStamp;

		boolean useFogCull = SodiumClientMod.options().performance.useFogOcclusion;
		double maxHorizontalDistSq = computeHorizontalCullDistSq(renderDistanceChunks, useFogCull);

		int eyeBlockX = MathHelper.floor_double(camX);
		int eyeBlockY = MathHelper.floor_double(eyeY);
		int eyeBlockZ = MathHelper.floor_double(camZ);

		int camChunkBaseX = MathHelper.floor_double(camX / 16.0D) << 4;
		int camChunkBaseY = MathHelper.floor_double(camY / 16.0D) << 4;
		int camChunkBaseZ = MathHelper.floor_double(camZ / 16.0D) << 4;
		int maxBlockChebyshev = renderDistanceChunks << 4;

		this.scratchPos.func_181079_c(eyeBlockX, eyeBlockY, eyeBlockZ);
		RenderChunk startChunk = viewFrustum.sodiumGetRenderChunk(this.scratchPos);

		int writeCount = 0;
		boolean graphCullEnabled = useOcclusionGraph;

		if (startChunk != null) {
			int startIdx = startChunk.sodiumGetGridIndex();
			int openFacingsMask = computeEyeOpenFacings(world, startChunk, eyeBlockX, eyeBlockY, eyeBlockZ);
			int bitCount = Integer.bitCount(openFacingsMask);
			if (bitCount == 1) {
				EnumFacing oppositeView = EnumFacing.getFacingFromVector(viewVecX, viewVecY, viewVecZ).getOpposite();
				openFacingsMask &= ~(1 << oppositeView.ordinal());
			}

			if (openFacingsMask == 0 && !spectator) {
				startChunk.setFrameIndex(frameCount);
				startChunk.sodiumVisibleFrame = frameCount;
				outVisibleChunks.add(startChunk);
				this.lastVisibleSections = 1;
				return;
			}

			if (spectator) {
				IBlockState eyeState = world.getBlockState(this.scratchPos);
				if (eyeState.getBlock().isOpaqueCube()) {
					graphCullEnabled = false;
				}
			}

			startChunk.setFrameIndex(frameCount);
			startChunk.sodiumVisibleFrame = frameCount;
			this.visitedStamp[startIdx] = stamp;
			this.incomingDirs[startIdx] = 0; // Origin chunk emits in all reachable directions
			this.allowedStepMask[startIdx] = (byte) (graphCullEnabled ? openFacingsMask : ALL_DIRECTIONS);
			this.queueA[writeCount++] = startIdx;
		} else {
			// Camera is above Y=255 or below Y=0: seed visible surface slice chunks
			int targetY = eyeBlockY > 0 ? 248 : 8;
			for (int dx = -renderDistanceChunks; dx <= renderDistanceChunks; ++dx) {
				for (int dz = -renderDistanceChunks; dz <= renderDistanceChunks; ++dz) {
					this.scratchPos.func_181079_c(camChunkBaseX + (dx << 4) + 8, targetY,
							camChunkBaseZ + (dz << 4) + 8);
					RenderChunk seed = viewFrustum.sodiumGetRenderChunk(this.scratchPos);
					if (seed != null && frustum.testBox(seed.boundingBox)) {
						int idx = seed.sodiumGetGridIndex();
						if (this.visitedStamp[idx] != stamp) {
							this.visitedStamp[idx] = stamp;
							seed.setFrameIndex(frameCount);
							seed.sodiumVisibleFrame = frameCount;
							this.incomingDirs[idx] = 0;
							this.allowedStepMask[idx] = ALL_DIRECTIONS;
							this.queueA[writeCount++] = idx;
						}
					}
				}
			}
		}

		int[] curQueue = this.queueA;
		int[] nextQueue = this.queueB;
		int curSize = writeCount;
		EnumFacing[] facings = EnumFacing._VALUES;

		while (curSize > 0) {
			int nextSize = 0;

			for (int q = 0; q < curSize; ++q) {
				int idx = curQueue[q];
				RenderChunk chunk = grid[idx];
				outVisibleChunks.add(chunk);

				int incoming = this.incomingDirs[idx] & ALL_DIRECTIONS;
				int allowed = this.allowedStepMask[idx] & ALL_DIRECTIONS;

				int outgoingMask;
				if (!graphCullEnabled || incoming == 0) {
					outgoingMask = allowed;
				} else {
					long visData = chunk.getCompiledChunk().getSodiumVisibilityData();
					outgoingMask = VisibilityEncoding.foldOutgoingDirections(visData, incoming) & allowed;
				}

				if (outgoingMask == 0) {
					continue;
				}

				for (int dirOrd = 0; dirOrd < 6; ++dirOrd) {
					if ((outgoingMask & (1 << dirOrd)) == 0) {
						continue;
					}

					BlockPos adjPos = chunk.func_181701_a(facings[dirOrd]);
					int adjY = adjPos.getY();
					if (adjY < 0 || adjY >= 256) {
						continue;
					}
					int dx = adjPos.getX() - camChunkBaseX;
					int dz = adjPos.getZ() - camChunkBaseZ;
					if (Math.abs(dx) > maxBlockChebyshev || Math.abs(dz) > maxBlockChebyshev) {
						continue;
					}

					RenderChunk adjChunk = viewFrustum.sodiumGetRenderChunk(adjPos);
					if (adjChunk == null) {
						continue;
					}

					int adjIdx = adjChunk.sodiumGetGridIndex();
					int enteredFaceBit = 1 << (dirOrd ^ 1);

					if (this.visitedStamp[adjIdx] == stamp) {
						// Already queued on this BFS wavefront: merge additional incoming face!
						this.incomingDirs[adjIdx] |= (byte) enteredFaceBit;
						continue;
					}

					// Cylindrical / fog distance check on horizontal chunk center
					if (useFogCull) {
						double cx = (double) (adjPos.getX() + 8) - camX;
						double cz = (double) (adjPos.getZ() + 8) - camZ;
						if (cx * cx + cz * cz > maxHorizontalDistSq) {
							this.visitedStamp[adjIdx] = stamp;
							this.incomingDirs[adjIdx] = 0;
							this.allowedStepMask[adjIdx] = 0;
							++this.lastFogCulledSections;
							continue;
						}
					}

					if (!frustum.testBox(adjChunk.boundingBox)) {
						continue;
					}

					this.visitedStamp[adjIdx] = stamp;
					adjChunk.setFrameIndex(frameCount);
					adjChunk.sodiumVisibleFrame = frameCount;
					this.incomingDirs[adjIdx] = (byte) enteredFaceBit;

					// Prevent stepping backwards along any axis already traversed + octant angle mask
					int nextAllowed = allowed & ~(1 << (dirOrd ^ 1));
					if (graphCullEnabled) {
						nextAllowed &= computeOctantMask(dx >> 4, (adjY - camChunkBaseY) >> 4, dz >> 4);
					}
					this.allowedStepMask[adjIdx] = (byte) nextAllowed;
					nextQueue[nextSize++] = adjIdx;
				}
			}

			int[] swap = curQueue;
			curQueue = nextQueue;
			nextQueue = swap;
			curSize = nextSize;
		}

		this.lastVisibleSections = outVisibleChunks.size();
	}

	/**
	 * Sodium octant monotonicity mask: once a ray is in chunk octant (dx, dy, dz) away from the camera,
	 * it can never step back toward the camera along a non-zero axis.
	 */
	public static int computeOctantMask(int dx, int dy, int dz) {
		int mask = ALL_DIRECTIONS;
		if (dy > 0) {
			mask &= ~(1 << 0); // Cannot step DOWN
		} else if (dy < 0) {
			mask &= ~(1 << 1); // Cannot step UP
		}
		if (dz > 0) {
			mask &= ~(1 << 2); // Cannot step NORTH (-Z)
		} else if (dz < 0) {
			mask &= ~(1 << 3); // Cannot step SOUTH (+Z)
		}
		if (dx > 0) {
			mask &= ~(1 << 4); // Cannot step WEST (-X)
		} else if (dx < 0) {
			mask &= ~(1 << 5); // Cannot step EAST (+X)
		}
		return mask;
	}

	private static double computeHorizontalCullDistSq(int renderDistanceChunks, boolean useFogCull) {
		double baseRadius = (double) ((renderDistanceChunks + 1) << 4);
		double maxDistSq = baseRadius * baseRadius;
		if (useFogCull && GlStateManager.stateFog && GlStateManager.stateFogDensity > 0.0F) {
			if (GlStateManager.stateFogEXP) {
				// Exponential fog (underwater / lava / blindness): exp(-d * density) < 0.002 -> d ~ 6.2 / density
				double expCutoff = (6.5D / (double) GlStateManager.stateFogDensity) + 24.0D;
				double expSq = expCutoff * expCutoff;
				if (expSq < maxDistSq) {
					maxDistSq = expSq;
				}
			} else if (GlStateManager.stateFogEnd > 16.0F) {
				double fogCutoff = (double) GlStateManager.stateFogEnd + 24.0D;
				double fogSq = fogCutoff * fogCutoff;
				if (fogSq < maxDistSq) {
					maxDistSq = fogSq;
				}
			}
		}
		return maxDistSq;
	}

	private int computeEyeOpenFacings(World world, RenderChunk eyeChunk, int eyeX, int eyeY, int eyeZ) {
		CompiledChunk compiled = eyeChunk.getCompiledChunk();
		int secX = eyeX >> 4;
		int secY = eyeY >> 4;
		int secZ = eyeZ >> 4;
		int lx = eyeX & 15;
		int ly = eyeY & 15;
		int lz = eyeZ & 15;
		int packedLocal = SodiumVisGraph.packIndex(lx, ly, lz);

		if (secX == this.cachedEyeSectionX && secY == this.cachedEyeSectionY && secZ == this.cachedEyeSectionZ
				&& packedLocal == this.cachedEyeLocalPos && compiled == this.cachedEyeCompiled && compiled != null) {
			return this.cachedEyeOpenMask;
		}

		Chunk chunk = world.getChunkFromChunkCoords(secX, secZ);
		this.eyeVisGraph.clear();
		int baseX = secX << 4;
		int baseY = secY << 4;
		int baseZ = secZ << 4;
		for (int y = 0; y < 16; ++y) {
			for (int z = 0; z < 16; ++z) {
				for (int x = 0; x < 16; ++x) {
					this.scratchPos.func_181079_c(baseX + x, baseY + y, baseZ + z);
					if (chunk.getBlock(this.scratchPos).isOpaqueCube()) {
						this.eyeVisGraph.markOpaque(x, y, z);
					}
				}
			}
		}

		int mask = this.eyeVisGraph.computeReachableFacesMask(lx, ly, lz);
		this.cachedEyeSectionX = secX;
		this.cachedEyeSectionY = secY;
		this.cachedEyeSectionZ = secZ;
		this.cachedEyeLocalPos = packedLocal;
		this.cachedEyeCompiled = compiled;
		this.cachedEyeOpenMask = mask;
		return mask;
	}

	public int getLastTotalSections() {
		return this.lastTotalSections;
	}

	public int getLastVisibleSections() {
		return this.lastVisibleSections;
	}

	public int getLastFogCulledSections() {
		return this.lastFogCulledSections;
	}
}
