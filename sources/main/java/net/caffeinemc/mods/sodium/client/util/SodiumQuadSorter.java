package net.caffeinemc.mods.sodium.client.util;

import net.lax1dude.eaglercraft.v1_8.internal.buffer.FloatBuffer;
import net.lax1dude.eaglercraft.v1_8.internal.buffer.IntBuffer;

/**
 * Zero-boxing translucent quad distance sorter inspired by Sodium's QuadSorting.
 * Replaces Integer[] boxing + Comparator + BitSet + IntBuffer.slice() allocations
 * in WorldRenderer with primitive insertion/radix sort and reusable scratch buffers.
 */
public final class SodiumQuadSorter {

	private static final int INSERTION_THRESHOLD = 32;
	private static final int RADIX_BITS = 8;
	private static final int RADIX_BUCKETS = 1 << RADIX_BITS;
	private static final int RADIX_MASK = RADIX_BUCKETS - 1;

	private static int[] keysA = new int[256];
	private static int[] keysB = new int[256];
	private static int[] idxA = new int[256];
	private static int[] idxB = new int[256];
	private static final int[] histogram0 = new int[RADIX_BUCKETS];
	private static final int[] histogram1 = new int[RADIX_BUCKETS];
	private static final int[] histogram2 = new int[RADIX_BUCKETS];
	private static final int[] histogram3 = new int[RADIX_BUCKETS];
	private static int[] vertexScratch = new int[4096];
	private static int[] vertexReordered = new int[4096];

	private SodiumQuadSorter() {
	}

	private static void ensureQuadCapacity(int quadCount) {
		if (keysA.length < quadCount) {
			int next = Math.max(quadCount, keysA.length << 1);
			keysA = new int[next];
			keysB = new int[next];
			idxA = new int[next];
			idxB = new int[next];
		}
	}

	private static void ensureIntCapacity(int totalInts) {
		if (vertexScratch.length < totalInts) {
			int next = Math.max(totalInts, vertexScratch.length << 1);
			vertexScratch = new int[next];
			vertexReordered = new int[next];
		}
	}

	/**
	 * Sorts quads in intBuffer/floatBuffer in descending squared distance order from (camX, camY, camZ).
	 */
	public static void sortQuadsDescending(IntBuffer intBuffer, FloatBuffer floatBuffer, int quadCount,
			int attribStrideBytes, float camX, float camY, float camZ) {
		if (quadCount <= 1) {
			return;
		}
		ensureQuadCapacity(quadCount);

		int floatsPerVertex = attribStrideBytes >> 2;
		int intsPerQuad = attribStrideBytes; // 4 vertices * (attribStrideBytes / 4) ints

		boolean alreadySorted = true;
		int prevKey = Integer.MAX_VALUE;

		for (int q = 0; q < quadCount; ++q) {
			int base = q * intsPerQuad;
			float x0 = floatBuffer.get(base);
			float y0 = floatBuffer.get(base + 1);
			float z0 = floatBuffer.get(base + 2);

			int v1 = base + floatsPerVertex;
			float x1 = floatBuffer.get(v1);
			float y1 = floatBuffer.get(v1 + 1);
			float z1 = floatBuffer.get(v1 + 2);

			int v2 = v1 + floatsPerVertex;
			float x2 = floatBuffer.get(v2);
			float y2 = floatBuffer.get(v2 + 1);
			float z2 = floatBuffer.get(v2 + 2);

			int v3 = v2 + floatsPerVertex;
			float x3 = floatBuffer.get(v3);
			float y3 = floatBuffer.get(v3 + 1);
			float z3 = floatBuffer.get(v3 + 2);

			float dx = (x0 + x1 + x2 + x3) * 0.25F - camX;
			float dy = (y0 + y1 + y2 + y3) * 0.25F - camY;
			float dz = (z0 + z1 + z2 + z3) * 0.25F - camZ;
			float distSq = dx * dx + dy * dy + dz * dz;

			// Non-negative float bits preserve IEEE-754 monotonic ordering as signed int!
			int key = Float.floatToRawIntBits(distSq);
			if (key < 0) {
				key = 0;
			}
			if (key > prevKey) {
				alreadySorted = false;
			}
			prevKey = key;
			keysA[q] = key;
			idxA[q] = q;
		}

		if (alreadySorted) {
			return;
		}

		int[] sortedOrder = sortIndicesDescending(quadCount);

		int totalInts = quadCount * intsPerQuad;
		ensureIntCapacity(totalInts);

		intBuffer.position(0);
		intBuffer.limit(totalInts);
		intBuffer.get(vertexScratch, 0, totalInts);

		for (int dstQuad = 0; dstQuad < quadCount; ++dstQuad) {
			int srcQuad = sortedOrder[dstQuad];
			System.arraycopy(vertexScratch, srcQuad * intsPerQuad, vertexReordered, dstQuad * intsPerQuad, intsPerQuad);
		}

		intBuffer.clear();
		intBuffer.put(vertexReordered, 0, totalInts);
		intBuffer.clear();
	}

	/**
	 * Exposed for standalone unit testing on plain float[] distances.
	 */
	public static int[] sortDistancesDescending(float[] distances, int count) {
		ensureQuadCapacity(count);
		for (int i = 0; i < count; ++i) {
			float d = distances[i];
			int bits = Float.floatToRawIntBits(d < 0.0F ? 0.0F : d);
			keysA[i] = bits < 0 ? 0 : bits;
			idxA[i] = i;
		}
		int[] internal = sortIndicesDescending(count);
		int[] copy = new int[count];
		System.arraycopy(internal, 0, copy, 0, count);
		return copy;
	}

	private static int[] sortIndicesDescending(int count) {
		if (count <= INSERTION_THRESHOLD) {
			for (int i = 1; i < count; ++i) {
				int k = keysA[i];
				int idx = idxA[i];
				int j = i - 1;
				while (j >= 0 && keysA[j] < k) {
					keysA[j + 1] = keysA[j];
					idxA[j + 1] = idxA[j];
					--j;
				}
				keysA[j + 1] = k;
				idxA[j + 1] = idx;
			}
			return idxA;
		}

		// 4-pass 8-bit LSD Radix Sort in descending order (bucket = 255 - byte)
		for (int b = 0; b < RADIX_BUCKETS; ++b) {
			histogram0[b] = 0;
			histogram1[b] = 0;
			histogram2[b] = 0;
			histogram3[b] = 0;
		}

		for (int i = 0; i < count; ++i) {
			int k = keysA[i];
			++histogram0[255 - (k & RADIX_MASK)];
			++histogram1[255 - ((k >>> 8) & RADIX_MASK)];
			++histogram2[255 - ((k >>> 16) & RADIX_MASK)];
			++histogram3[255 - ((k >>> 24) & RADIX_MASK)];
		}

		prefixSums(histogram0);
		prefixSums(histogram1);
		prefixSums(histogram2);
		prefixSums(histogram3);

		radixPass(keysA, idxA, keysB, idxB, count, 0, histogram0);
		radixPass(keysB, idxB, keysA, idxA, count, 8, histogram1);
		radixPass(keysA, idxA, keysB, idxB, count, 16, histogram2);
		radixPass(keysB, idxB, keysA, idxA, count, 24, histogram3);
		return idxA;
	}

	private static void prefixSums(int[] hist) {
		int sum = 0;
		for (int i = 0; i < RADIX_BUCKETS; ++i) {
			int c = hist[i];
			hist[i] = sum;
			sum += c;
		}
	}

	private static void radixPass(int[] srcKeys, int[] srcIdx, int[] dstKeys, int[] dstIdx, int count, int shift,
			int[] offsets) {
		for (int i = 0; i < count; ++i) {
			int k = srcKeys[i];
			int bucket = 255 - ((k >>> shift) & RADIX_MASK);
			int pos = offsets[bucket]++;
			dstKeys[pos] = k;
			dstIdx[pos] = srcIdx[i];
		}
	}
}
