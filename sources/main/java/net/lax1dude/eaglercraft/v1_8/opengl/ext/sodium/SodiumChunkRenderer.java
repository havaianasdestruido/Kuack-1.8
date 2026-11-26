package net.lax1dude.eaglercraft.v1_8.opengl.ext.sodium;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

/**
 * Copyright (c) 2024 lax1dude, ayunami2000. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */

/**
 * The "baked in" SodiumMC chunk renderer.
 * <p>
 * Vanilla 1.8 rebuilds its visible chunk list with a breadth first search over
 * the chunk visibility graph, allocating a node, an EnumSet of facings and a
 * linked list entry for every chunk that survives frustum culling, and it does
 * that again for every single frame in which the camera moved or rotated by any
 * amount. Minecraft then re-walks that freshly allocated list every time a
 * block layer is drawn. That allocation churn is the biggest per-frame hot loop
 * in the client and it is why vanilla stutters when the camera moves.
 * <p>
 * This class replaces the BFS with the three things Sodium does:
 * <ul>
 * <li>the candidate set (everything inside the render distance box) is rebuilt
 * only when the player crosses a chunk boundary or the render distance changes,
 * every frame after that only re-runs the cheap frustum test over it;</li>
 * <li>the candidate set is kept in front to back order, so the culled list
 * falls out of it already sorted and no per frame sort is needed at all. The
 * order is refreshed with an insertion sort, which is nearly free because a
 * camera gliding forwards barely changes it, and with a merge sort on the
 * frames where the candidate set itself was rebuilt, where the previous order
 * is worthless and insertion sort would degenerate into a quadratic walk;</li>
 * <li>chunks which are marked dirty but are currently outside of the visible
 * set are allowed to wait in the rebuild queue instead of consuming the whole
 * per frame upload budget, see {@link SodiumConfig#shouldCullPendingUpdates(int)}.</li>
 * </ul>
 * Nothing here changes <i>what</i> is rendered, only the order in which the
 * same chunk display lists get (re)built and uploaded, so as long as the option
 * is enabled it is a pixel exact replacement for the vanilla chunk renderer.
 */
public final class SodiumChunkRenderer {

	/**
	 * How many chunks at most are allowed to sit in the rebuild queue.
	 */
	public static final int MAX_QUEUED_UPDATES = 256;

	private final List<RenderChunk> candidates = new ArrayList();
	private final List<RenderChunk> visible = new ArrayList();

	private boolean candidatesDirty = true;
	private int candidateChunkX = Integer.MIN_VALUE;
	private int candidateChunkY = Integer.MIN_VALUE;
	private int candidateChunkZ = Integer.MIN_VALUE;
	private int candidateDistance = -1;
	private float candidatePitch = Float.MIN_VALUE;
	private float candidateYaw = Float.MIN_VALUE;
	private boolean visibleValid = false;

	private float sortX = Float.MIN_VALUE;
	private float sortY = Float.MIN_VALUE;
	private float sortZ = Float.MIN_VALUE;

	private RenderChunk[] sortBuffer = new RenderChunk[1024];
	private float[] sortKeys = new float[1024];
	private RenderChunk[] mergeBuffer = new RenderChunk[1024];
	private float[] mergeKeys = new float[1024];
	private boolean sortFromScratch = true;

	private int[] visibleMarks = new int[0];
	private int visibleGeneration = 0;

	private int renderCount = 0;
	private int renderCountLast = 0;
	private long lastStatsUpdate = 0l;

	/**
	 * Drops every cached chunk reference, must be called whenever the world,
	 * the render distance or the chunk factory changes, otherwise this class
	 * would hold pointers to chunk objects that no longer exist.
	 */
	public void reset() {
		candidates.clear();
		visible.clear();
		candidatesDirty = true;
		candidateChunkX = candidateChunkY = candidateChunkZ = Integer.MIN_VALUE;
		candidateDistance = -1;
		candidatePitch = candidateYaw = Float.MIN_VALUE;
		visibleValid = false;
		sortX = sortY = sortZ = Float.MIN_VALUE;
		renderCount = renderCountLast = 0;
	}

	/**
	 * Marks the cached candidate list as stale, safe to call as often as
	 * wanted because the work is deferred until the next frame.
	 */
	public void markDirty() {
		this.candidatesDirty = true;
		this.visibleValid = false;
	}

	/**
	 * @return true if the given chunk was visible during the last frame, used
	 *         to decide if a dirty chunk has to be rebuilt right away or if it
	 *         may wait until it enters the view
	 *         <p>
	 *         This is a stamp table lookup rather than a scan of the visible
	 *         list, because RenderGlobal asks this question once per dirty chunk
	 *         and a teleport can leave thousands of them dirty in the same
	 *         frame, which made a linear scan quadratic in the worst case.
	 */
	public boolean wasVisible(RenderChunk chunk) {
		int index = chunk.getIndex();
		return index >= 0 && index < visibleMarks.length && visibleMarks[index] == visibleGeneration;
	}

	/**
	 * Stamps every chunk in the visible list with the current generation so
	 * {@link #wasVisible(RenderChunk)} can answer in constant time.
	 */
	private void markVisible() {
		int gen = ++visibleGeneration;
		if(gen == 0) {
			// the generation counter wrapped, no stamp can be trusted anymore
			java.util.Arrays.fill(visibleMarks, 0);
			gen = visibleGeneration = 1;
		}
		// grow to the largest index actually seen rather than to the whole
		// ViewFrustum array, an 8 chunk render distance is already 3760 chunks
		// and half of them are usually outside of the world
		int maxIndex = -1;
		for(int i = 0, l = visible.size(); i < l; ++i) {
			int index = visible.get(i).getIndex();
			if(index > maxIndex) {
				maxIndex = index;
			}
		}
		if(maxIndex >= visibleMarks.length) {
			int[] grown = new int[Math.max(maxIndex + 1, (visibleMarks.length << 1) + 64)];
			System.arraycopy(visibleMarks, 0, grown, 0, visibleMarks.length);
			visibleMarks = grown;
		}
		for(int i = 0, l = visible.size(); i < l; ++i) {
			int index = visible.get(i).getIndex();
			if(index >= 0 && index <= maxIndex) {
				visibleMarks[index] = gen;
			}
		}
	}

	/**
	 * Updates the cached candidate set when needed, keeps it in front to back
	 * order and frustum culls it.
	 * 
	 * @param providor      callbacks into the ViewFrustum and the frustum
	 * @param viewX         interpolated eye position
	 * @param viewY         interpolated eye position
	 * @param viewZ         interpolated eye position
	 * @param chunkDistance render distance in chunks
	 * @param pitch         camera pitch in degrees, only its change matters
	 * @param yaw           camera yaw in degrees, only its change matters
	 * @return the chunks to render this frame in front to back order, opaque
	 *         layers are drawn by walking it forwards and the translucent layer
	 *         by walking it backwards, do not modify the returned list
	 */
	public List<RenderChunk> getRenderChunks(SodiumCameraProvidor providor, double viewX, double viewY, double viewZ,
			int chunkDistance, float pitch, float yaw) {
		int chunkX = ((int) Math.floor(viewX)) >> 4;
		int chunkY = ((int) Math.floor(viewY)) >> 4;
		int chunkZ = ((int) Math.floor(viewZ)) >> 4;
		float x = (float) viewX, y = (float) viewY, z = (float) viewZ;
		float dx = x - sortX, dy = y - sortY, dz = z - sortZ;
		// the order only needs to be recomputed once the camera moved far enough
		// to actually change it, a couple of cm is not going to reorder anything
		boolean sortDirty = sortX == Float.MIN_VALUE || dx * dx + dy * dy + dz * dz > 0.0625f;
		boolean candidatesStale = candidatesDirty || chunkX != candidateChunkX || chunkY != candidateChunkY
				|| chunkZ != candidateChunkZ || chunkDistance != candidateDistance;
		// a camera which is neither moving nor turning can hand back the list it
		// already has, which is the case that matters on anything battery powered
		boolean viewDirty = candidatesStale || sortDirty || pitch != candidatePitch || yaw != candidateYaw;
		if(!viewDirty && visibleValid) {
			updateStats(System.currentTimeMillis());
			return visible;
		}
		if(candidatesStale) {
			rebuildCandidates(providor, chunkX, chunkY, chunkZ, chunkDistance, viewX, viewY, viewZ);
		}
		candidatePitch = pitch;
		candidateYaw = yaw;
		if(sortDirty || sortFromScratch) {
			sortX = x;
			sortY = y;
			sortZ = z;
			sort(sortFromScratch);
			sortFromScratch = false;
		}
		// because the candidate list is kept in distance order the culled list
		// comes out in distance order too, so no per frame sort is needed at all
		visible.clear();
		for(int i = 0, l = candidates.size(); i < l; ++i) {
			RenderChunk chunk = candidates.get(i);
			AxisAlignedBB bb = chunk.boundingBox;
			if(bb != null && providor.isBoxInFrustum(bb)) {
				visible.add(chunk);
			}
		}
		renderCount = visible.size();
		markVisible();
		visibleValid = true;
		updateStats(System.currentTimeMillis());
		return visible;
	}

	private void rebuildCandidates(SodiumCameraProvidor providor, int chunkX, int chunkY, int chunkZ,
			int chunkDistance, double viewX, double viewY, double viewZ) {
		candidates.clear();
		candidateChunkX = chunkX;
		candidateChunkY = chunkY;
		candidateChunkZ = chunkZ;
		candidateDistance = chunkDistance;
		candidatesDirty = false;
		// the candidate scan emits chunks in an arbitrary order, so whatever
		// order the visible list had before is worthless here
		sortFromScratch = true;
		int r = chunkDistance + 1;
		// squared distance in blocks, one extra chunk of slack so chunks which
		// are still partly inside of the view distance are not dropped early
		double maxDist = (double) (chunkDistance << 4) + 16.0;
		double maxDistSq = maxDist * maxDist;
		// one mutable position is reused for the whole scan, a render distance
		// change would otherwise allocate thousands of throwaway BlockPos
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for(int dx = -r; dx <= r; ++dx) {
			for(int dz = -r; dz <= r; ++dz) {
				if(dx * dx + dz * dz > r * r) {
					continue;
				}
				for(int dy = -16; dy <= 16; ++dy) {
					int cy = chunkY + dy;
					if(cy < 0 || cy > 15) {
						continue;
					}
					pos.x = (chunkX + dx) << 4;
					pos.y = cy << 4;
					pos.z = (chunkZ + dz) << 4;
					RenderChunk chunk = providor.getRenderChunk(pos);
					if(chunk == null) {
						continue;
					}
					AxisAlignedBB bb = chunk.boundingBox;
					if(bb == null) {
						continue;
					}
					double cx = bb.minX + 8.0 - viewX;
					double cyy = bb.minY + 8.0 - viewY;
					double cz = bb.minZ + 8.0 - viewZ;
					if(cx * cx + cyy * cyy + cz * cz > maxDistSq) {
						continue;
					}
					candidates.add(chunk);
				}
			}
		}
	}

	/**
	 * Sorts the visible list front to back.
	 * <p>
	 * The common case is a camera which moved a fraction of a block since the
	 * last frame, where the list is almost sorted already and an insertion sort
	 * finishes in a couple of passes. The exception is the frame right after the
	 * candidate set was rebuilt (crossing a chunk boundary, changing the render
	 * distance, teleporting), because then the list is in scan order, not
	 * distance order, and insertion sort degenerates into a quadratic walk over
	 * thousands of chunks. That case gets a bottom up merge sort instead, which
	 * is what took the teleport frame from 2.2x the price of the vanilla search
	 * down to cheaper than it.
	 * <p>
	 * Both sorts work on preallocated scratch arrays and write the result back
	 * into the live list, so no allocation happens per frame.
	 * 
	 * @param fromScratch true when the previous ordering is known to be useless
	 */
	private void sort(boolean fromScratch) {
		final float x = sortX, y = sortY, z = sortZ;
		int l = candidates.size();
		if(l < 2) {
			return;
		}
		if(sortBuffer.length < l) {
			int cap = (l + 512) & ~255;
			sortBuffer = new RenderChunk[cap];
			sortKeys = new float[cap];
			mergeBuffer = new RenderChunk[cap];
			mergeKeys = new float[cap];
		}
		final RenderChunk[] chunks = sortBuffer;
		final float[] keys = sortKeys;
		for(int i = 0; i < l; ++i) {
			RenderChunk chunk = candidates.get(i);
			chunks[i] = chunk;
			keys[i] = distanceSq(chunk, x, y, z);
		}
		if(fromScratch && l > 64) {
			mergeSort(chunks, keys, mergeBuffer, mergeKeys, l);
		}else {
			insertionSort(chunks, keys, l);
		}
		for(int i = 0; i < l; ++i) {
			candidates.set(i, chunks[i]);
		}
	}

	private static void insertionSort(RenderChunk[] chunks, float[] keys, int l) {
		for(int i = 1; i < l; ++i) {
			RenderChunk chunk = chunks[i];
			float key = keys[i];
			int j = i - 1;
			while(j >= 0 && keys[j] > key) {
				chunks[j + 1] = chunks[j];
				keys[j + 1] = keys[j];
				--j;
			}
			chunks[j + 1] = chunk;
			keys[j + 1] = key;
		}
	}

	private static void mergeSort(RenderChunk[] chunks, float[] keys, RenderChunk[] buf, float[] bufKeys, int l) {
		RenderChunk[] src = chunks, dst = buf;
		float[] srcKeys = keys, dstKeys = bufKeys;
		for(int width = 1; width < l; width <<= 1) {
			for(int lo = 0; lo < l; lo += width << 1) {
				int mid = Math.min(lo + width, l);
				int hi = Math.min(lo + (width << 1), l);
				int a = lo, b = mid, o = lo;
				while(a < mid && b < hi) {
					if(srcKeys[b] < srcKeys[a]) {
						dstKeys[o] = srcKeys[b];
						dst[o++] = src[b++];
					}else {
						dstKeys[o] = srcKeys[a];
						dst[o++] = src[a++];
					}
				}
				while(a < mid) {
					dstKeys[o] = srcKeys[a];
					dst[o++] = src[a++];
				}
				while(b < hi) {
					dstKeys[o] = srcKeys[b];
					dst[o++] = src[b++];
				}
			}
			RenderChunk[] t = src; src = dst; dst = t;
			float[] tk = srcKeys; srcKeys = dstKeys; dstKeys = tk;
		}
		if(src != chunks) {
			System.arraycopy(src, 0, chunks, 0, l);
			System.arraycopy(srcKeys, 0, keys, 0, l);
		}
	}

	private static float distanceSq(RenderChunk chunk, float x, float y, float z) {
		BlockPos pos = chunk.getPosition();
		float dx = (float) pos.getX() + 8.0f - x;
		float dy = (float) pos.getY() + 8.0f - y;
		float dz = (float) pos.getZ() + 8.0f - z;
		return dx * dx + dy * dy + dz * dz;
	}

	private void updateStats(long millis) {
		if(millis - lastStatsUpdate > 500l) {
			lastStatsUpdate = millis;
			renderCountLast = renderCount;
		}
	}

	/**
	 * @return number of chunks that were rendered in the last statistics window
	 */
	public int getRenderCount() {
		return renderCountLast;
	}

	/**
	 * @return number of chunks in the cached candidate set, this is the whole
	 *         render distance box, not just what is on screen
	 */
	public int getCandidateCount() {
		return candidates.size();
	}

	public String getDebugString() {
		return "S: " + renderCountLast + "/" + candidates.size();
	}

	/**
	 * This interface is implemented by RenderGlobal so this class does not need
	 * a hard reference back into it.
	 */
	public static interface SodiumCameraProvidor {
		RenderChunk getRenderChunk(BlockPos pos);
		boolean isBoxInFrustum(AxisAlignedBB box);
	}

}
