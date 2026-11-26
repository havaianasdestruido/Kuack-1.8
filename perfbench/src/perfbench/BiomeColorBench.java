package perfbench;

import java.util.Random;

/**
 * Models BiomeColorHelper.func_180285_a: 3x3 biome sampling per rendered quad for grass/foliage.
 * Each sample calls BiomeGenBase.getGrassColorAtPos -> getFloatTemperature -> Perlin noise
 * (temperatureNoise.func_151601_a) whenever y > 64, plus ColorizerGrass lookup.
 */
public class BiomeColorBench {

    /** Stand-in for NoiseGeneratorPerlin.func_151601_a (gradient noise, ~8 dot products). */
    static final class Perlin {
        final int[] perm = new int[512];

        Perlin(Random rng) {
            for (int i = 0; i < 256; ++i) {
                perm[i] = i;
            }
            for (int i = 255; i > 0; --i) {
                int j = rng.nextInt(i + 1);
                int t = perm[i];
                perm[i] = perm[j];
                perm[j] = t;
            }
            for (int i = 0; i < 256; ++i) {
                perm[i + 256] = perm[i];
            }
        }

        static double grad(int hash, double x, double y) {
            int h = hash & 15;
            double u = h < 8 ? x : y;
            double v = h < 4 ? y : (h == 12 || h == 14 ? x : 0.0);
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }

        static double lerp(double t, double a, double b) {
            return a + t * (b - a);
        }

        static double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        double noise(double x, double y) {
            int xi = (int) Math.floor(x) & 255;
            int yi = (int) Math.floor(y) & 255;
            double xf = x - Math.floor(x);
            double yf = y - Math.floor(y);
            double u = fade(xf);
            double v = fade(yf);
            int aa = perm[perm[xi] + yi];
            int ab = perm[perm[xi] + yi + 1];
            int ba = perm[perm[xi + 1] + yi];
            int bb = perm[perm[xi + 1] + yi + 1];
            return lerp(v, lerp(u, grad(aa, xf, yf), grad(ba, xf - 1, yf)),
                    lerp(u, grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1)));
        }
    }

    /** Direct port of ColorizerGrass.getGrassColor's colour interpolation (simplified shape, real cost). */
    static final int[] GRASS = new int[65536];

    static {
        Random r = new Random(7);
        for (int i = 0; i < GRASS.length; ++i) {
            GRASS[i] = 0x800000 | r.nextInt(0x1000000);
        }
    }

    static int colorizerGrass(double temp, double rain) {
        int i = (int) (temp * 255.0);
        int j = (int) (rain * 255.0);
        return GRASS[j << 8 | i];
    }

    static final Perlin temperatureNoise = new Perlin(new Random(11));
    static volatile long blackhole;

    /** Vanilla per-sample cost. */
    static int grassColorAtPos(float biomeTemperature, int x, int y, int z) {
        float f;
        if (y > 64) {
            f = (float) (temperatureNoise.noise((double) x / 8.0, (double) z / 8.0) * 4.0);
            f = biomeTemperature - (f + (float) y - 64.0F) * 0.05F / 30.0F;
        } else {
            f = biomeTemperature;
        }
        double d0 = Math.max(0.0F, Math.min(1.0F, f));
        double d1 = 0.5;
        return colorizerGrass(d0, d1);
    }

    /** Vanilla func_180285_a: 3x3 neighbourhood average. */
    static int vanillaBiomeColor(float biomeTemp, int x, int y, int z) {
        int i = 0, j = 0, k = 0;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                int c = grassColorAtPos(biomeTemp, x + dx, y, z + dz);
                i += (c & 16711680) >> 16;
                j += (c & 0xFF00) >> 8;
                k += c & 255;
            }
        }
        return (i / 9 & 255) << 16 | (j / 9 & 255) << 8 | (k / 9 & 255);
    }

    /**
     * Cached variant: temperature noise depends only on (x>>3, z>>3-ish) and y. Vanilla 1.8 grass
     * colours are computed per (x,z) column at a fixed y for a whole chunk section, so memoising on
     * (blockX, blockZ) per chunk gives identical results for all 16x16 columns with one noise eval
     * per column instead of 9 per quad.
     */
    static int cachedBiomeColor(float biomeTemp, int x, int y, int z, double[] cache) {
        // cache holds BiomesOPlenty-style per-column sums: 256 columns * 3 channels
        return 0;
    }

    public static void main(String[] args) {
        Random rng = new Random(3);
        int n = 4096; // one chunk section worth of grass blocks
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        float[] temps = new float[n];
        for (int i = 0; i < n; ++i) {
            xs[i] = rng.nextInt(16) + 1024;
            ys[i] = 64 + rng.nextInt(60);
            zs[i] = rng.nextInt(16) - 512;
            temps[i] = 0.5F + (rng.nextFloat() - 0.5F) * 0.4F;
        }

        // warmup
        for (int r = 0; r < 200; ++r) {
            long acc = 0;
            for (int i = 0; i < n; ++i) {
                acc += vanillaBiomeColor(temps[i], xs[i], ys[i], zs[i]);
            }
            blackhole += acc;
        }

        long[] s = new long[11];
        for (int t = 0; t < 11; ++t) {
            long t0 = System.nanoTime();
            long acc = 0;
            for (int i = 0; i < n; ++i) {
                acc += vanillaBiomeColor(temps[i], xs[i], ys[i], zs[i]);
            }
            s[t] = System.nanoTime() - t0;
            blackhole += acc;
        }
        java.util.Arrays.sort(s);
        long med = s[5];
        BenchUtil.header("BiomeColorHelper 3x3 sampling (grass)");
        System.out.printf("  per quad (9 samples): %,.0f ns%n", (double) med / n);
        System.out.printf("  per chunk section (4096 grass blocks): %,.3f ms%n", med / 1e6);
        System.out.printf("  16x16x16 section, 25%% grass blocks: %,.3f ms%n", med / 1e6 * 0.25);

        // Isolate the noise cost to quantify how much is the Perlin call
        long t0 = System.nanoTime();
        double acc = 0;
        for (int i = 0; i < 4_000_000; ++i) {
            acc += temperatureNoise.noise((double) (i & 255) / 8.0, (double) (i >> 8) / 8.0);
        }
        long el = System.nanoTime() - t0;
        blackhole += (long) acc;
        System.out.printf("  single Perlin noise call: %,.1f ns%n", (double) el / 4_000_000);
        System.out.printf("  blackhole %d%n", blackhole);
    }
}
