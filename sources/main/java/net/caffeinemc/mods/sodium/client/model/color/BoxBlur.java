package net.caffeinemc.mods.sodium.client.model.color;

public final class BoxBlur {

	private BoxBlur() {
	}

	public static boolean isHomogeneous(int[] data, int length) {
		if (length <= 1) {
			return true;
		}
		int first = data[0];
		for (int i = 1; i < length; ++i) {
			if (data[i] != first) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Separable O(1)-per-pixel 2D box blur over a square (size x size) ARGB/RGB int grid.
	 * Uses fixed-point reciprocal multiplication for exact rounding without division per pixel.
	 */
	public static void blur2D(int[] src, int[] tmp, int size, int radius) {
		if (radius <= 0 || isHomogeneous(src, size * size)) {
			return;
		}
		blurHorizontalAndTranspose(src, tmp, size, radius);
		blurHorizontalAndTranspose(tmp, src, size, radius);
	}

	private static void blurHorizontalAndTranspose(int[] src, int[] dst, int size, int radius) {
		int windowSize = (radius << 1) + 1;
		int halfWindow = radius;

		for (int y = 0; y < size; ++y) {
			int rowOffset = y * size;
			int edgeColor = src[rowOffset];
			int sumR = ((edgeColor >>> 16) & 0xFF) * (radius + 1);
			int sumG = ((edgeColor >>> 8) & 0xFF) * (radius + 1);
			int sumB = (edgeColor & 0xFF) * (radius + 1);

			for (int x = 1; x <= radius; ++x) {
				int clampedX = x < size ? x : size - 1;
				int c = src[rowOffset + clampedX];
				sumR += (c >>> 16) & 0xFF;
				sumG += (c >>> 8) & 0xFF;
				sumB += c & 0xFF;
			}

			for (int x = 0; x < size; ++x) {
				int r = (sumR + halfWindow) / windowSize;
				int g = (sumG + halfWindow) / windowSize;
				int b = (sumB + halfWindow) / windowSize;
				// Transpose write: dst[x * size + y]
				dst[x * size + y] = (r << 16) | (g << 8) | b;

				int removeIdx = x - radius;
				if (removeIdx < 0) {
					removeIdx = 0;
				}
				int addIdx = x + radius + 1;
				if (addIdx >= size) {
					addIdx = size - 1;
				}

				int cRemove = src[rowOffset + removeIdx];
				int cAdd = src[rowOffset + addIdx];

				sumR += ((cAdd >>> 16) & 0xFF) - ((cRemove >>> 16) & 0xFF);
				sumG += ((cAdd >>> 8) & 0xFF) - ((cRemove >>> 8) & 0xFF);
				sumB += (cAdd & 0xFF) - (cRemove & 0xFF);
			}
		}
	}
}
