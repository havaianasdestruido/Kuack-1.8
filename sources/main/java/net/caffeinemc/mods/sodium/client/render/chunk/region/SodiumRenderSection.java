package net.caffeinemc.mods.sodium.client.render.chunk.region;

import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferSegment;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;

/**
 * Per-16x16x16 RenderChunk section metadata inside an 8x4x8 SodiumRenderRegion.
 */
public final class SodiumRenderSection {

	public static final int LAYER_COUNT = EnumWorldBlockLayer._VALUES.length;

	private SodiumRenderRegion region;
	private int regionX;
	private int regionY;
	private int regionZ;
	private short localSectionIndex;
	private float offsetX;
	private float offsetY;
	private float offsetZ;

	private final GlBufferSegment[] layerSegments = new GlBufferSegment[LAYER_COUNT];
	private final int[] layerQuadCounts = new int[LAYER_COUNT];

	public void updatePosition(BlockPos pos) {
		freeAllLayers();
		int chunkX = pos.getX() >> 4;
		int chunkY = pos.getY() >> 4;
		int chunkZ = pos.getZ() >> 4;
		this.regionX = chunkX >> 3;
		this.regionY = chunkY >> 2;
		this.regionZ = chunkZ >> 3;
		int lx = chunkX & 7;
		int ly = chunkY & 3;
		int lz = chunkZ & 7;
		this.localSectionIndex = (short) ((ly << 6) | (lz << 3) | lx);
		this.offsetX = (float) (lx << 4);
		this.offsetY = (float) (ly << 4);
		this.offsetZ = (float) (lz << 4);
	}

	public SodiumRenderRegion getRegion() {
		return this.region;
	}

	public void setRegion(SodiumRenderRegion region) {
		this.region = region;
	}

	public int getRegionX() {
		return this.regionX;
	}

	public int getRegionY() {
		return this.regionY;
	}

	public int getRegionZ() {
		return this.regionZ;
	}

	public short getLocalSectionIndex() {
		return this.localSectionIndex;
	}

	public float getOffsetX() {
		return this.offsetX;
	}

	public float getOffsetY() {
		return this.offsetY;
	}

	public float getOffsetZ() {
		return this.offsetZ;
	}

	public GlBufferSegment getSegment(int layerIdx) {
		return this.layerSegments[layerIdx];
	}

	public int getQuadCount(int layerIdx) {
		return this.layerQuadCounts[layerIdx];
	}

	public boolean hasUploadedGeometry(int layerIdx) {
		return this.layerSegments[layerIdx] != null && this.layerQuadCounts[layerIdx] > 0;
	}

	void setLayerSlice(int layerIdx, GlBufferSegment seg, int quadCount) {
		this.layerSegments[layerIdx] = seg;
		this.layerQuadCounts[layerIdx] = quadCount;
	}

	public void freeLayer(int layerIdx) {
		GlBufferSegment seg = this.layerSegments[layerIdx];
		if (seg != null) {
			if (this.region != null) {
				this.region.freeSectionSlice(seg, layerIdx);
			}
			this.layerSegments[layerIdx] = null;
			this.layerQuadCounts[layerIdx] = 0;
		}
	}

	public void freeAllLayers() {
		SodiumRenderRegion reg = this.region;
		for (int i = 0; i < LAYER_COUNT; ++i) {
			GlBufferSegment seg = this.layerSegments[i];
			if (seg != null) {
				if (reg != null) {
					reg.freeSectionSlice(seg, i);
				}
				this.layerSegments[i] = null;
				this.layerQuadCounts[i] = 0;
			}
		}
		this.region = null;
	}
}
