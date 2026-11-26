package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.util.EnumFacing;

/**
 * Packs a 6x6 symmetric chunk section face-connectivity matrix into a single 64-bit long.
 * Bit ((from << 3) + to) is 1 iff a ray entering face `from` can exit face `to`.
 */
public final class VisibilityEncoding {

	public static final long NULL = 0L;
	public static final long EVERYTHING = 0x003F_3F_3F_3F_3F_3FL;

	private static final long SPREAD_MULTIPLIER = 0x0810204081L;
	private static final long BYTE_LSB_MASK = 0x010101010101L;

	private VisibilityEncoding() {
	}

	public static int bit(int fromOrdinal, int toOrdinal) {
		return (fromOrdinal << 3) + toOrdinal;
	}

	public static long setVisible(long data, int fromOrdinal, int toOrdinal) {
		return data | (1L << bit(fromOrdinal, toOrdinal)) | (1L << bit(toOrdinal, fromOrdinal));
	}

	public static boolean isVisible(long data, int fromOrdinal, int toOrdinal) {
		return (data & (1L << bit(fromOrdinal, toOrdinal))) != 0L;
	}

	public static int getConnections(long visibilityData, int incomingFaceOrdinal) {
		return (int) ((visibilityData >>> (incomingFaceOrdinal << 3)) & 0x3FL);
	}

	/**
	 * Folds outgoing 6-bit direction mask across all set bits in incomingFacesMask (0..63)
	 * simultaneously using parallel bitwise multiplication.
	 */
	public static int foldOutgoingDirections(long visibilityData, int incomingFacesMask) {
		long laneLsbs = ((incomingFacesMask & 0x3FL) * SPREAD_MULTIPLIER) & BYTE_LSB_MASK;
		long activeMask = laneLsbs * 0x3FL;
		long masked = visibilityData & activeMask;
		masked |= masked >>> 32;
		masked |= masked >>> 16;
		masked |= masked >>> 8;
		return (int) (masked & 0x3FL);
	}

	public static long fromSetVisibility(SetVisibility setVis) {
		if (setVis == null) {
			return NULL;
		}
		long encoded = NULL;
		EnumFacing[] facings = EnumFacing._VALUES;
		for (int i = 0; i < 6; ++i) {
			EnumFacing a = facings[i];
			for (int j = i; j < 6; ++j) {
				if (setVis.isVisible(a, facings[j])) {
					encoded = setVisible(encoded, i, j);
				}
			}
		}
		return encoded;
	}

	public static void applyToSetVisibility(long encoded, SetVisibility target) {
		target.setAllVisible(false);
		if (encoded == NULL) {
			return;
		}
		if (encoded == EVERYTHING) {
			target.setAllVisible(true);
			return;
		}
		EnumFacing[] facings = EnumFacing._VALUES;
		for (int i = 0; i < 6; ++i) {
			EnumFacing a = facings[i];
			for (int j = i; j < 6; ++j) {
				if (isVisible(encoded, i, j)) {
					target.setVisible(a, facings[j], true);
				}
			}
		}
	}
}
