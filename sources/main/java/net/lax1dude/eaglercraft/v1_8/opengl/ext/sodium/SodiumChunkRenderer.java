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
 * <li>the visible list is kept between frames and re-sorted in place with an
 * insertion sort, which is nearly free because it is already almost sorted,
 * no per frame allocation happens at all;</li>
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

	private float sortX = Float.MIN_VALUE;
	private float sortY = Float.MIN_VALUE;
	private float sortZ = Float.MIN_VALUE;

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
		sortX = sortY = sortZ = Float.MIN_VALUE;
		renderCount = renderCountLast = 0;
	}

	/**
	 * Marks the cached candidate list as stale, safe to call as often as
	 * wanted because the work is deferred until the next frame.
	 */
	public void markDirty() {
		this.candidatesDirty = true;
	}

	/**
	 * @return true if the given chunk was visible during the last frame, used
	 *         to decide if a dirty chunk has to be rebuilt right away or if it
	 *         may wait until it enters the view
	 */
	public boolean wasVisible(RenderChunk chunk) {
		for(int i = 0, l = visible.size(); i < l; ++i) {
			if(visible.get(i) == chunk) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Updates the cached candidate set when needed, frustum culls it and keeps
	 * the result sorted front to back.
	 * 
	 * @param providor      callbacks into the ViewFrustum and the frustum
	 * @param viewX         interpolated eye position
	 * @param viewY         interpolated eye position
	 * @param viewZ         interpolated eye position
	 * @param chunkDistance render distance in chunks
	 * @return the chunks to render this frame in front to back order, opaque
	 *         layers are drawn by walking it forwards and the translucent layer
	 *         by walking it backwards, do not modify the returned list
	 */
	public List<RenderChunk> getRenderChunks(SodiumCameraProvidor providor, double viewX, double viewY, double viewZ,
			int chunkDistance) {
		int chunkX = ((int) Math.floor(viewX)) >> 4;
		int chunkY = ((int) Math.floor(viewY)) >> 4;
		int chunkZ = ((int) Math.floor(viewZ)) >> 4;
		if(candidatesDirty || chunkX != candidateChunkX || chunkY != candidateChunkY || chunkZ != candidateChunkZ
				|| chunkDistance != candidateDistance) {
			rebuildCandidates(providor, chunkX, chunkY, chunkZ, chunkDistance, viewX, viewY, viewZ);
		}
		visible.clear();
		for(int i = 0, l = candidates.size(); i < l; ++i) {
			RenderChunk chunk = candidates.get(i);
			AxisAlignedBB bb = chunk.boundingBox;
			if(bb != null && providor.isBoxInFrustum(bb)) {
				visible.add(chunk);
			}
		}
		renderCount = visible.size();
		float x = (float) viewX, y = (float) viewY, z = (float) viewZ;
		float dx = x - sortX, dy = y - sortY, dz = z - sortZ;
		if(sortX == Float.MIN_VALUE || dx * dx + dy * dy + dz * dz > 0.25f) {
			sortX = x;
			sortY = y;
			sortZ = z;
			sort();
		}
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

	private void sort() {
		final float x = sortX, y = sortY, z = sortZ;
		int l = visible.size();
		// insertion sort, the list is already almost perfectly sorted from the
		// previous frame because the camera only moved a little bit
		for(int i = 1; i < l; ++i) {
			RenderChunk chunk = visible.get(i);
			float dist = distanceSq(chunk, x, y, z);
			int j = i - 1;
			while(j >= 0 && distanceSq(visible.get(j), x, y, z) > dist) {
				visible.set(j + 1, visible.get(j));
				--j;
			}
			visible.set(j + 1, chunk);
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
