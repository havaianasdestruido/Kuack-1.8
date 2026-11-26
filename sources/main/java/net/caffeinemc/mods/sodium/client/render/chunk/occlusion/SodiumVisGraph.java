package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * Primitive 64-word (4096-bit) flood-fill visibility graph for a 16x16x16 chunk section.
 * Avoids java.util.BitSet boxing and uses <256 opaque early-out (since no closed separating
 * surface across a 16x16x16 cube can contain fewer than 256 blocks).
 */
public final class SodiumVisGraph {

	private static final int[] BOUNDARY_INDICES = buildBoundaryIndices();
	private static final short[] SHARED_QUEUE = new short[4096];

	private final long[] words = new long[64];
	private int opaqueCount = 0;

	private static int[] buildBoundaryIndices() {
		int[] out = new int[1352];
		int ptr = 0;
		for (int y = 0; y < 16; ++y) {
			for (int z = 0; z < 16; ++z) {
				for (int x = 0; x < 16; ++x) {
					if (x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15) {
						out[ptr++] = packIndex(x, y, z);
					}
				}
			}
		}
		return out;
	}

	public static int packIndex(int x, int y, int z) {
		return (y << 8) | (z << 4) | x;
	}

	public void clear() {
		Arrays.fill(this.words, 0L);
		this.opaqueCount = 0;
	}

	public void markOpaque(BlockPos pos) {
		markOpaque(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
	}

	public void markOpaque(int x, int y, int z) {
		int idx = packIndex(x, y, z);
		int wordIdx = idx >>> 6;
		long mask = 1L << (idx & 63);
		if ((this.words[wordIdx] & mask) == 0L) {
			this.words[wordIdx] |= mask;
			++this.opaqueCount;
		}
	}

	public int getOpaqueCount() {
		return this.opaqueCount;
	}

	public long computeVisibilityEncoding() {
		if (this.opaqueCount < 256) {
			return VisibilityEncoding.EVERYTHING;
		}
		if (this.opaqueCount >= 4096) {
			return VisibilityEncoding.NULL;
		}

		long result = VisibilityEncoding.NULL;
		short[] queue = SHARED_QUEUE;

		for (int i = 0; i < BOUNDARY_INDICES.length; ++i) {
			int start = BOUNDARY_INDICES[i];
			if ((this.words[start >>> 6] & (1L << (start & 63))) == 0L) {
				int touchedMask = floodFillComponent(start, queue);
				if (touchedMask != 0 && (touchedMask & (touchedMask - 1)) != 0) {
					for (int f1 = 0; f1 < 6; ++f1) {
						if ((touchedMask & (1 << f1)) != 0) {
							for (int f2 = f1 + 1; f2 < 6; ++f2) {
								if ((touchedMask & (1 << f2)) != 0) {
									result = VisibilityEncoding.setVisible(result, f1, f2);
								}
							}
						}
					}
				}
			}
		}
		return result;
	}

	public SetVisibility computeSetVisibility() {
		SetVisibility setVis = new SetVisibility();
		long encoded = computeVisibilityEncoding();
		VisibilityEncoding.applyToSetVisibility(encoded, setVis);
		return setVis;
	}

	public int computeReachableFacesMask(int localX, int localY, int localZ) {
		int start = packIndex(localX & 15, localY & 15, localZ & 15);
		if ((this.words[start >>> 6] & (1L << (start & 63))) != 0L) {
			return 0;
		}
		return floodFillComponent(start, SHARED_QUEUE);
	}

	public Set<EnumFacing> computeReachableFacings(BlockPos pos) {
		int mask = computeReachableFacesMask(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
		EnumSet<EnumFacing> set = EnumSet.noneOf(EnumFacing.class);
		EnumFacing[] facings = EnumFacing._VALUES;
		for (int i = 0; i < 6; ++i) {
			if ((mask & (1 << i)) != 0) {
				set.add(facings[i]);
			}
		}
		return set;
	}

	private int floodFillComponent(int startIdx, short[] queue) {
		int head = 0;
		int tail = 0;
		this.words[startIdx >>> 6] |= (1L << (startIdx & 63));
		queue[tail++] = (short) startIdx;
		int faces = 0;

		while (head < tail) {
			int idx = queue[head++] & 0xFFF;

			int x = idx & 15;
			int z = (idx >>> 4) & 15;
			int y = (idx >>> 8) & 15;

			// DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5
			if (y == 0) {
				faces |= (1 << 0);
			} else {
				int n = idx - 256;
				int w = n >>> 6;
				long m = 1L << (n & 63);
				if ((this.words[w] & m) == 0L) {
					this.words[w] |= m;
					queue[tail++] = (short) n;
				}
			}

			if (y == 15) {
				faces |= (1 << 1);
			} else {
				int n = idx + 256;
				int w = n >>> 6;
				long m = 1L << (n & 63);
				if ((this.words[w] & m) == 0L) {
					this.words[w] |= m;
					queue[tail++] = (short) n;
				}
			}

			if (z == 0) {
				faces |= (1 << 2);
			} else {
				int n = idx - 16;
				int w = n >>> 6;
				long m = 1L << (n & 63);
				if ((this.words[w] & m) == 0L) {
					this.words[w] |= m;
					queue[tail++] = (short) n;
				}
			}

			if (z == 15) {
				faces |= (1 << 3);
			} else {
				int n = idx + 16;
				int w = n >>> 6;
				long m = 1L << (n & 63);
				if ((this.words[w] & m) == 0L) {
					this.words[w] |= m;
					queue[tail++] = (short) n;
				}
			}

			if (x == 0) {
				faces |= (1 << 4);
			} else {
				int n = idx - 1;
				int w = n >>> 6;
				long m = 1L << (n & 63);
				if ((this.words[w] & m) == 0L) {
					this.words[w] |= m;
					queue[tail++] = (short) n;
				}
			}

			if (x == 15) {
				faces |= (1 << 5);
			} else {
				int n = idx + 1;
				int w = n >>> 6;
				long m = 1L << (n & 63);
				if ((this.words[w] & m) == 0L) {
					this.words[w] |= m;
					queue[tail++] = (short) n;
				}
			}
		}

		return faces;
	}
}
