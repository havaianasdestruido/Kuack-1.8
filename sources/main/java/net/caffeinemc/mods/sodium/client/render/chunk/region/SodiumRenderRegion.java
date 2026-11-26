package net.caffeinemc.mods.sodium.client.render.chunk.region;

import static net.lax1dude.eaglercraft.v1_8.internal.PlatformOpenGL.*;
import static net.lax1dude.eaglercraft.v1_8.opengl.RealOpenGLEnums.*;

import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferArena;
import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferSegment;
import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.internal.IBufferArrayGL;
import net.lax1dude.eaglercraft.v1_8.internal.IBufferGL;
import net.lax1dude.eaglercraft.v1_8.internal.buffer.ByteBuffer;
import net.lax1dude.eaglercraft.v1_8.internal.buffer.IntBuffer;
import net.lax1dude.eaglercraft.v1_8.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.v1_8.opengl.FixedFunctionPipeline;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.ext.deferred.DeferredStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.ext.dynamiclights.DynamicLightsStateManager;
import net.minecraft.client.renderer.chunk.ListedRenderChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

/**
 * 8x4x8 section region (128x64x128 blocks) sharing a single GlBufferArena VBO and VAO.
 */
public final class SodiumRenderRegion {

	public static final int REGION_WIDTH = 8;
	public static final int REGION_HEIGHT = 4;
	public static final int REGION_LENGTH = 8;
	public static final int SECTION_COUNT = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH; // 256
	private static final int INITIAL_ARENA_VERTICES = 16384;
	private static final float ANTI_TJUNCTION_SCALE = 1.000001F;

	private static ByteBuffer sharedVertexStaging = null;
	private static IntBuffer sharedIndexStaging = null;
	private static int[] sharedIndexArray = new int[32768];

	private static final class LayerIndexCache {
		IBufferGL ebo;
		int eboCapacityInts;
		final short[] cachedSectionIds = new short[SECTION_COUNT];
		int cachedSectionCount = -1;
		int cachedDataVersion = -1;
		int cachedArenaGeneration = -1;
		int cachedIndexCount = 0;
		int dataVersion = 1;

		void invalidate() {
			++this.dataVersion;
			this.cachedSectionCount = -1;
		}

		void destroy() {
			if (this.ebo != null) {
				_wglDeleteBuffers(this.ebo);
				this.ebo = null;
			}
			this.eboCapacityInts = 0;
			this.cachedSectionCount = -1;
		}
	}

	private final int regionX;
	private final int regionY;
	private final int regionZ;
	private final double originX;
	private final double originY;
	private final double originZ;

	private GlBufferArena arena;
	private int strideBytes;
	private int attribBits;

	private IBufferArrayGL vao;
	private int vaoBoundGeneration = -1;
	private int vaoBoundAttribs = -1;
	private IBufferGL vaoBoundEbo = null;

	private final LayerIndexCache[] layerCaches = new LayerIndexCache[SodiumRenderSection.LAYER_COUNT];
	private IBufferGL scratchEbo = null;
	private int scratchEboCapacityInts = 0;

	// Current batch accumulation for renderChunkLayer
	private final ListedRenderChunk[] batchChunks = new ListedRenderChunk[SECTION_COUNT];
	private final short[] batchSectionIds = new short[SECTION_COUNT];
	private int batchCount = 0;
	boolean queuedInCurrentBatch = false;

	public SodiumRenderRegion(int regionX, int regionY, int regionZ) {
		this.regionX = regionX;
		this.regionY = regionY;
		this.regionZ = regionZ;
		this.originX = (double) (regionX << 7);
		this.originY = (double) (regionY << 6);
		this.originZ = (double) (regionZ << 7);
		for (int i = 0; i < SodiumRenderSection.LAYER_COUNT; ++i) {
			this.layerCaches[i] = new LayerIndexCache();
		}
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

	public double getOriginX() {
		return this.originX;
	}

	public double getOriginY() {
		return this.originY;
	}

	public double getOriginZ() {
		return this.originZ;
	}

	public boolean isEmpty() {
		return this.arena == null || this.arena.isEmpty();
	}

	public long getCapacityBytes() {
		return this.arena != null ? this.arena.getCapacityBytes() : 0L;
	}

	public long getUsedBytes() {
		return this.arena != null ? this.arena.getUsedBytes() : 0L;
	}

	private void ensureArenaFormat(int requiredStride, int requiredAttribs) {
		if (this.arena == null) {
			this.strideBytes = requiredStride;
			this.attribBits = requiredAttribs;
			this.arena = new GlBufferArena(INITIAL_ARENA_VERTICES, requiredStride);
		} else if (this.strideBytes != requiredStride || this.attribBits != requiredAttribs) {
			this.arena.destroy();
			this.strideBytes = requiredStride;
			this.attribBits = requiredAttribs;
			this.arena = new GlBufferArena(INITIAL_ARENA_VERTICES, requiredStride);
			this.vaoBoundGeneration = -1;
			for (int i = 0; i < SodiumRenderSection.LAYER_COUNT; ++i) {
				this.layerCaches[i].invalidate();
			}
		}
	}

	public void uploadSectionLayer(SodiumRenderSection section, int layerIdx, ByteBuffer srcBuffer, int vertexCount,
			int stride, int attribs) {
		if (vertexCount <= 0 || (vertexCount & 3) != 0) {
			section.freeLayer(layerIdx);
			return;
		}

		ensureArenaFormat(stride, attribs);
		int totalBytes = vertexCount * stride;
		ByteBuffer staged = prepareTranslatedStaging(srcBuffer, vertexCount, stride, section.getOffsetX(),
				section.getOffsetY(), section.getOffsetZ(), totalBytes);

		int quadCount = vertexCount >> 2;
		GlBufferSegment existing = section.getSegment(layerIdx);
		if (existing != null && !existing.isFree() && existing.getLength() == vertexCount
				&& section.getRegion() == this) {
			// In-place overwrite (e.g. translucent quad resort) - zero fragmentation and EBO remains valid!
			this.arena.uploadInPlace(existing, staged);
			section.setLayerSlice(layerIdx, existing, quadCount);
			return;
		}

		if (existing != null) {
			section.freeLayer(layerIdx);
		}

		int prevGen = this.arena.getBufferGeneration();
		GlBufferSegment allocated = this.arena.upload(staged, vertexCount);
		section.setRegion(this);
		section.setLayerSlice(layerIdx, allocated, quadCount);
		this.layerCaches[layerIdx].invalidate();

		if (this.arena.getBufferGeneration() != prevGen) {
			for (int i = 0; i < SodiumRenderSection.LAYER_COUNT; ++i) {
				this.layerCaches[i].invalidate();
			}
		}
	}

	void freeSectionSlice(GlBufferSegment seg, int layerIdx) {
		if (this.arena != null && seg != null) {
			this.arena.free(seg);
			this.layerCaches[layerIdx].invalidate();
		}
	}

	private static ByteBuffer prepareTranslatedStaging(ByteBuffer src, int vertexCount, int stride, float offX,
			float offY, float offZ, int totalBytes) {
		if (sharedVertexStaging == null || sharedVertexStaging.capacity() < totalBytes) {
			int newCap = Math.max(totalBytes, sharedVertexStaging == null ? 131072 : sharedVertexStaging.capacity() << 1);
			if (sharedVertexStaging != null) {
				EagRuntime.freeByteBuffer(sharedVertexStaging);
			}
			sharedVertexStaging = EagRuntime.allocateByteBuffer(newCap);
		}

		int oldPos = src.position();
		int oldLim = src.limit();
		src.position(0);
		src.limit(totalBytes);

		ByteBuffer dst = sharedVertexStaging;
		dst.clear();
		dst.put(src);
		dst.flip();

		src.limit(oldLim);
		src.position(oldPos);

		// Transform chunk-local [0..16] into region-local [0..128, 0..64, 0..128] with vanilla 1.000001f seam guard
		float adjX = offX + 8.0F;
		float adjY = offY + 8.0F;
		float adjZ = offZ + 8.0F;
		for (int v = 0, byteBase = 0; v < vertexCount; ++v, byteBase += stride) {
			float x = (dst.getFloat(byteBase) - 8.0F) * ANTI_TJUNCTION_SCALE + adjX;
			float y = (dst.getFloat(byteBase + 4) - 8.0F) * ANTI_TJUNCTION_SCALE + adjY;
			float z = (dst.getFloat(byteBase + 8) - 8.0F) * ANTI_TJUNCTION_SCALE + adjZ;
			dst.putFloat(byteBase, x);
			dst.putFloat(byteBase + 4, y);
			dst.putFloat(byteBase + 8, z);
		}

		dst.position(0);
		dst.limit(totalBytes);
		return dst;
	}

	public void addChunkToBatch(ListedRenderChunk chunk) {
		if (this.batchCount < SECTION_COUNT) {
			SodiumRenderSection sec = chunk.getSodiumSection();
			this.batchChunks[this.batchCount] = chunk;
			this.batchSectionIds[this.batchCount] = sec.getLocalSectionIndex();
			++this.batchCount;
		}
	}

	public void resetBatch() {
		for (int i = 0; i < this.batchCount; ++i) {
			this.batchChunks[i] = null;
		}
		this.batchCount = 0;
		this.queuedInCurrentBatch = false;
	}

	private void ensureVaoReady() {
		if (this.vao == null) {
			this.vao = _wglGenVertexArrays();
			this.vaoBoundGeneration = -1;
			this.vaoBoundEbo = null;
		}
		if (this.vaoBoundGeneration != this.arena.getBufferGeneration() || this.vaoBoundAttribs != this.attribBits) {
			EaglercraftGPU.setupSodiumVAO(this.vao, this.arena.getBufferObject(), this.attribBits);
			this.vaoBoundGeneration = this.arena.getBufferGeneration();
			this.vaoBoundAttribs = this.attribBits;
			// Re-establish element buffer binding if needed
			if (this.vaoBoundEbo != null) {
				_wglBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.vaoBoundEbo);
			}
		} else {
			EaglercraftGPU.bindGLBufferArray(this.vao);
		}
	}

	private void bindElementBufferOnVao(IBufferGL targetEbo) {
		EaglercraftGPU.bindGLBufferArray(this.vao);
		if (this.vaoBoundEbo != targetEbo) {
			_wglBindBuffer(GL_ELEMENT_ARRAY_BUFFER, targetEbo);
			this.vaoBoundEbo = targetEbo;
		}
	}

	/**
	 * Flushes all queued sections for layerIdx in this region.
	 * Returns number of GL drawElements calls issued (normally 1!).
	 */
	public int flushBatch(int layerIdx, double viewX, double viewY, double viewZ, boolean useCachedLayerEbo,
			SodiumRenderRegionManager statsSink) {
		int count = this.batchCount;
		if (count == 0 || this.arena == null || this.arena.isEmpty()) {
			resetBatch();
			return 0;
		}

		boolean inForwardPass = DeferredStateManager.isInForwardPass();
		boolean inDynamicLightsPass = DynamicLightsStateManager.isInDynamicLightsPass();
		boolean perSectionLightUniforms = inForwardPass || inDynamicLightsPass;

		ensureVaoReady();

		GlStateManager.pushMatrix();
		GlStateManager.translate((float) (this.originX - viewX), (float) (this.originY - viewY),
				(float) (this.originZ - viewZ));

		FixedFunctionPipeline pipeline = EaglercraftGPU.prepareSodiumPipeline(this.attribBits);
		int drawCalls = 0;

		if (!perSectionLightUniforms && useCachedLayerEbo) {
			LayerIndexCache cache = this.layerCaches[layerIdx];
			if (isCacheHit(cache, count)) {
				bindElementBufferOnVao(cache.ebo);
				if (cache.cachedIndexCount > 0) {
					EaglercraftGPU.drawSodiumElements(pipeline, this.vao, cache.cachedIndexCount, 0);
					drawCalls = 1;
					statsSink.recordEboCacheHit();
				}
			} else {
				int totalIndices = buildIndexArrayForBatch(layerIdx, count);
				if (totalIndices > 0) {
					if (cache.ebo == null) {
						cache.ebo = _wglGenBuffers();
						cache.eboCapacityInts = 0;
					}
					bindElementBufferOnVao(cache.ebo);
					cache.eboCapacityInts = uploadIndexData(cache.ebo, cache.eboCapacityInts, totalIndices,
							GL_DYNAMIC_DRAW);
					System.arraycopy(this.batchSectionIds, 0, cache.cachedSectionIds, 0, count);
					cache.cachedSectionCount = count;
					cache.cachedDataVersion = cache.dataVersion;
					cache.cachedArenaGeneration = this.arena.getBufferGeneration();
					cache.cachedIndexCount = totalIndices;

					EaglercraftGPU.drawSodiumElements(pipeline, this.vao, totalIndices, 0);
					drawCalls = 1;
				}
			}
		} else {
			// Shadow / Paraboloid / Forward / DynamicLights pass: use scratchEbo without invalidating camera EBO cache
			int totalIndices = buildIndexArrayForBatch(layerIdx, count);
			if (totalIndices > 0) {
				if (this.scratchEbo == null) {
					this.scratchEbo = _wglGenBuffers();
					this.scratchEboCapacityInts = 0;
				}
				bindElementBufferOnVao(this.scratchEbo);
				this.scratchEboCapacityInts = uploadIndexData(this.scratchEbo, this.scratchEboCapacityInts,
						totalIndices, GL_STREAM_DRAW);

				if (!perSectionLightUniforms) {
					EaglercraftGPU.drawSodiumElements(pipeline, this.vao, totalIndices, 0);
					drawCalls = 1;
				} else {
					int indexCursorBytes = 0;
					for (int i = 0; i < count; ++i) {
						ListedRenderChunk chunk = this.batchChunks[i];
						SodiumRenderSection sec = chunk.getSodiumSection();
						int quads = sec.getQuadCount(layerIdx);
						if (quads <= 0) {
							continue;
						}
						int secIndices = quads * 6;
						reportChunkLightCenter(chunk, viewX, viewY, viewZ, inForwardPass, inDynamicLightsPass);
						EaglercraftGPU.drawSodiumElements(pipeline, this.vao, secIndices, indexCursorBytes);
						indexCursorBytes += secIndices << 2;
						++drawCalls;
					}
				}
			}
		}

		GlStateManager.popMatrix();
		statsSink.recordSectionsRendered(count);
		resetBatch();
		return drawCalls;
	}

	private static void reportChunkLightCenter(RenderChunk chunk, double viewX, double viewY, double viewZ,
			boolean inForward, boolean inDynLights) {
		BlockPos pos = chunk.getPosition();
		int cx = pos.getX() - (MathHelper.floor_double(viewX / 16.0D) << 4);
		int cy = pos.getY() - (MathHelper.floor_double(viewY / 16.0D) << 4);
		int cz = pos.getZ() - (MathHelper.floor_double(viewZ / 16.0D) << 4);
		if (inForward) {
			DeferredStateManager.reportForwardRenderObjectPosition(cx, cy, cz);
		} else if (inDynLights) {
			DynamicLightsStateManager.reportForwardRenderObjectPosition(cx, cy, cz);
		}
	}

	private boolean isCacheHit(LayerIndexCache cache, int count) {
		if (cache.ebo == null || cache.cachedSectionCount != count || cache.cachedDataVersion != cache.dataVersion
				|| cache.cachedArenaGeneration != this.arena.getBufferGeneration()) {
			return false;
		}
		short[] a = this.batchSectionIds;
		short[] b = cache.cachedSectionIds;
		for (int i = 0; i < count; ++i) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	private int buildIndexArrayForBatch(int layerIdx, int count) {
		int totalQuads = 0;
		for (int i = 0; i < count; ++i) {
			totalQuads += this.batchChunks[i].getSodiumSection().getQuadCount(layerIdx);
		}
		int totalIndices = totalQuads * 6;
		if (totalIndices <= 0) {
			return 0;
		}
		if (sharedIndexArray.length < totalIndices) {
			sharedIndexArray = new int[Math.max(totalIndices, sharedIndexArray.length << 1)];
		}
		int[] out = sharedIndexArray;
		int ptr = 0;
		for (int i = 0; i < count; ++i) {
			SodiumRenderSection sec = this.batchChunks[i].getSodiumSection();
			GlBufferSegment seg = sec.getSegment(layerIdx);
			int quads = sec.getQuadCount(layerIdx);
			if (seg == null || quads <= 0) {
				continue;
			}
			int baseVertex = seg.getOffset();
			for (int q = 0; q < quads; ++q) {
				int v0 = baseVertex + (q << 2);
				int v1 = v0 + 1;
				int v2 = v0 + 2;
				int v3 = v0 + 3;
				out[ptr] = v0;
				out[ptr + 1] = v1;
				out[ptr + 2] = v3;
				out[ptr + 3] = v1;
				out[ptr + 4] = v2;
				out[ptr + 5] = v3;
				ptr += 6;
			}
		}
		return ptr;
	}

	private static int uploadIndexData(IBufferGL ebo, int currentCapInts, int indexCount, int usage) {
		if (sharedIndexStaging == null || sharedIndexStaging.capacity() < indexCount) {
			int nextCap = Math.max(indexCount, sharedIndexStaging == null ? 32768 : sharedIndexStaging.capacity() << 1);
			if (sharedIndexStaging != null) {
				EagRuntime.freeIntBuffer(sharedIndexStaging);
			}
			sharedIndexStaging = EagRuntime.allocateIntBuffer(nextCap);
		}
		IntBuffer ib = sharedIndexStaging;
		ib.clear();
		ib.put(sharedIndexArray, 0, indexCount);
		ib.flip();

		if (currentCapInts < indexCount) {
			int grownInts = (indexCount + (indexCount >> 2) + 1023) & ~1023;
			_wglBufferData(GL_ELEMENT_ARRAY_BUFFER, grownInts << 2, usage);
			_wglBufferSubData(GL_ELEMENT_ARRAY_BUFFER, 0, ib);
			return grownInts;
		} else {
			_wglBufferSubData(GL_ELEMENT_ARRAY_BUFFER, 0, ib);
			return currentCapInts;
		}
	}

	public void destroy() {
		resetBatch();
		for (int i = 0; i < SodiumRenderSection.LAYER_COUNT; ++i) {
			this.layerCaches[i].destroy();
		}
		if (this.scratchEbo != null) {
			_wglDeleteBuffers(this.scratchEbo);
			this.scratchEbo = null;
			this.scratchEboCapacityInts = 0;
		}
		if (this.vao != null) {
			EaglercraftGPU.bindGLBufferArray(null);
			_wglDeleteVertexArrays(this.vao);
			this.vao = null;
		}
		if (this.arena != null) {
			this.arena.destroy();
			this.arena = null;
		}
	}
}
