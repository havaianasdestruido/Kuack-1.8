package net.caffeinemc.mods.sodium.client.render.chunk.region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v1_8.opengl.VertexFormat;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.minecraft.client.renderer.chunk.ListedRenderChunk;
import net.minecraft.util.EnumWorldBlockLayer;

public final class SodiumRenderRegionManager {

	private final Map<Long, SodiumRenderRegion> regions = new HashMap<Long, SodiumRenderRegion>();
	private final List<SodiumRenderRegion> activeBatchRegions = new ArrayList<SodiumRenderRegion>(64);

	private int lastFrameDrawCalls = 0;
	private int lastFrameRegions = 0;
	private int lastFrameSections = 0;
	private int lastFrameEboHits = 0;
	private int frameDrawCallsAccum = 0;
	private int frameRegionsAccum = 0;
	private int frameSectionsAccum = 0;
	private int frameEboHitsAccum = 0;
	private int pruneCounter = 0;

	public static long packRegionKey(int rx, int ry, int rz) {
		return (((long) rx & 0x3FFFFFL) << 42) | (((long) ry & 0xFFFFFL) << 22) | ((long) rz & 0x3FFFFFL);
	}

	public SodiumRenderRegion getOrCreateRegion(int rx, int ry, int rz) {
		Long key = Long.valueOf(packRegionKey(rx, ry, rz));
		SodiumRenderRegion region = this.regions.get(key);
		if (region == null) {
			region = new SodiumRenderRegion(rx, ry, rz);
			this.regions.put(key, region);
		}
		return region;
	}

	public void uploadChunkLayer(ListedRenderChunk chunk, EnumWorldBlockLayer layer, WorldRenderer worldRenderer) {
		SodiumRenderSection section = chunk.getSodiumSection();
		int vertexCount = worldRenderer.getVertexCount();
		int layerIdx = layer.ordinal();
		if (vertexCount <= 0) {
			section.freeLayer(layerIdx);
			return;
		}
		VertexFormat fmt = worldRenderer.getVertexFormat();
		SodiumRenderRegion region = getOrCreateRegion(section.getRegionX(), section.getRegionY(), section.getRegionZ());
		region.uploadSectionLayer(section, layerIdx, worldRenderer.getByteBuffer(), vertexCount, fmt.attribStride,
				fmt.eaglercraftAttribBits);
	}

	public void enqueueForDraw(ListedRenderChunk chunk, int layerIdx) {
		SodiumRenderSection sec = chunk.getSodiumSection();
		if (!sec.hasUploadedGeometry(layerIdx)) {
			return;
		}
		SodiumRenderRegion reg = sec.getRegion();
		if (reg == null) {
			return;
		}
		if (!reg.queuedInCurrentBatch) {
			reg.queuedInCurrentBatch = true;
			this.activeBatchRegions.add(reg);
		}
		reg.addChunkToBatch(chunk);
	}

	public void flushLayerBatch(EnumWorldBlockLayer layer, double viewX, double viewY, double viewZ,
			boolean primaryCameraPass) {
		int n = this.activeBatchRegions.size();
		if (n == 0) {
			return;
		}
		int layerIdx = layer.ordinal();
		for (int i = 0; i < n; ++i) {
			SodiumRenderRegion reg = this.activeBatchRegions.get(i);
			int draws = reg.flushBatch(layerIdx, viewX, viewY, viewZ, primaryCameraPass, this);
			if (draws > 0) {
				this.frameDrawCallsAccum += draws;
				++this.frameRegionsAccum;
			}
		}
		this.activeBatchRegions.clear();
	}

	public void beginFrame() {
		this.lastFrameDrawCalls = this.frameDrawCallsAccum;
		this.lastFrameRegions = this.frameRegionsAccum;
		this.lastFrameSections = this.frameSectionsAccum;
		this.lastFrameEboHits = this.frameEboHitsAccum;
		this.frameDrawCallsAccum = 0;
		this.frameRegionsAccum = 0;
		this.frameSectionsAccum = 0;
		this.frameEboHitsAccum = 0;

		if (++this.pruneCounter >= 120) {
			this.pruneCounter = 0;
			pruneEmptyRegions();
		}
	}

	private void pruneEmptyRegions() {
		if (this.regions.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<Long, SodiumRenderRegion>> it = this.regions.entrySet().iterator();
		while (it.hasNext()) {
			SodiumRenderRegion reg = it.next().getValue();
			if (reg.isEmpty()) {
				reg.destroy();
				it.remove();
			}
		}
	}

	void recordSectionsRendered(int count) {
		this.frameSectionsAccum += count;
	}

	void recordEboCacheHit() {
		++this.frameEboHitsAccum;
	}

	public int getRegionCount() {
		return this.regions.size();
	}

	public long getTotalCapacityBytes() {
		long sum = 0L;
		for (SodiumRenderRegion reg : this.regions.values()) {
			sum += reg.getCapacityBytes();
		}
		return sum;
	}

	public long getTotalUsedBytes() {
		long sum = 0L;
		for (SodiumRenderRegion reg : this.regions.values()) {
			sum += reg.getUsedBytes();
		}
		return sum;
	}

	public int getLastFrameDrawCalls() {
		return this.lastFrameDrawCalls;
	}

	public int getLastFrameRegions() {
		return this.lastFrameRegions;
	}

	public int getLastFrameSections() {
		return this.lastFrameSections;
	}

	public int getLastFrameEboHits() {
		return this.lastFrameEboHits;
	}

	public void destroyAll() {
		this.activeBatchRegions.clear();
		for (SodiumRenderRegion reg : this.regions.values()) {
			reg.destroy();
		}
		this.regions.clear();
	}
}
