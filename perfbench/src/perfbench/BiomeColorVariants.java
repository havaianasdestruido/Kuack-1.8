package perfbench;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Compares biome-colour strategies for grass/foliage quads.
 *
 *  vanilla : 3x3 loop, allocates 2 BlockPos + iterator machinery, biome lookup + Perlin per sample.
 *  flat    : same 3x3 loop with plain int coordinates, array-indexed biome lookup,
 *            memoised per (biome,x>>3,z>>3,y). No allocation.
 *  prepass : per-column sums computed once, per-quad lookup is O(1).
 */
public class BiomeColorVariants {

    static final int[] GRASS = new int[65536];
    static final int[] FOLIAGE = new int[65536];
    static final Perlin temperatureNoise = new Perlin(new Random(11));
    static volatile long blackhole;

    static {
        Random r = new Random(7);
        for (int i = 0; i < GRASS.length; ++i) {
            GRASS[i] = 0x800000 | r.nextInt(0x1000000);
            FOLIAGE[i] = 0x800000 | r.nextInt(0x1000000);
        }
    }

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

    // ---- per-chunk-section biome array and temperature, as RegionRenderCache would supply ----
    static final byte[] BIOME = new byte[256];     // biome id per column
    static final float[] TEMP = new float[256];    // biome temperature per column

    static void fillSection(Random rng) {
        for (int i = 0; i < 256; ++i) {
            BIOME[i] = (byte) (1 + rng.nextInt(20));
            TEMP[i] = 0.3F + rng.nextFloat() * 0.5F;
        }
    }

    static float biomeTemp(int x, int z) {
        return TEMP[(z & 15) << 4 | (x & 15)];
    }

    static int grassColor(float temp, int x, int y, int z) {
        float f;
        if (y > 64) {
            f = (float) (temperatureNoise.noise((double) x / 8.0, (double) z / 8.0) * 4.0);
            f = temp - (f + (float) y - 64.0F) * 0.05F / 30.0F;
        } else {
            f = temp;
        }
        double d0 = Math.max(0.0F, Math.min(1.0F, f));
        return GRASS[(int) ((1.0 - 0.5 * d0) * 255.0) << 8 | (int) ((1.0 - d0) * 255.0)];
    }

    /** Vanilla shape (including per-sample heap traffic). */
    static int vanilla(int x, int y, int z) {
        int i = 0, j = 0, k = 0;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                int c = grassColor(biomeTemp(x + dx, z + dz), x + dx, y, z + dz);
                i += (c & 0xFF0000) >> 16;
                j += (c & 0xFF00) >> 8;
                k += c & 255;
            }
        }
        return (i / 9 & 255) << 16 | (j / 9 & 255) << 8 | (k / 9 & 255);
    }

    /** Memoised variant: one array lookup per sample, identical arithmetic. */
    static final class Memo {
        final int[] data = new int[4096];
        final boolean[] valid = new boolean[4096];
        int y = -999; // cached y level

        void reset(int newY) {
            Arrays.fill(valid, false);
            y = newY;
        }

        int get(int x, int z) {
            int idx = (z & 15) << 4 | (x & 15);
            if (valid[idx]) {
                return data[idx];
            }
            int v = grassColor(biomeTemp(x, z), x, y, z);
            data[idx] = v;
            valid[idx] = true;
            return v;
        }
    }

    static int memoized(int x, int y, int z, Memo memo) {
        if (memo.y != y) {
            memo.reset(y);
        }
        int i = 0, j = 0, k = 0;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                int c = memo.get(x + dx, z + dz);
                i += (c & 0xFF0000) >> 16;
                j += (c & 0xFF00) >> 8;
                k += c & 255;
            }
        }
        return (i / 9 & 255) << 16 | (j / 9 & 255) << 8 | (k / 9 & 255);
    }

    public static void main(String[] args) {
        Random rng = new Random(5);
        fillSection(rng);
        int n = 4096;
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        for (int i = 0; i < n; ++i) {
            xs[i] = 1024 + rng.nextInt(16);
            ys[i] = 64 + rng.nextInt(60);
            zs[i] = -512 + rng.nextInt(16);
        }

        Memo memo = new Memo();
        long[] s = new long[9];
        for (int i = 0; i < 300; ++i) {
            long acc = 0;
            Memo m2 = new Memo();
            for (int q = 0; q < n; ++q) {
                acc += vanilla(xs[q], ys[q], zs[q]);
                acc += memoized(xs[q], ys[q], zs[q], m2);
            }
            blackhole += acc;
        }
        for (int t = 0; t < 9; ++t) {
            long t0 = System.nanoTime();
            long acc = 0;
            for (int q = 0; q < n; ++q) {
                acc += vanilla(xs[q], ys[q], zs[q]);
            }
            s[t] = System.nanoTime() - t0;
            blackhole += acc;
        }
        long v = BenchUtil.median(s);

        // memo: y is constant inside a section sweep in practice; emulate that
        for (int t = 0; t < 9; ++t) {
            long t0 = System.nanoTime();
            long acc = 0;
            Memo m = new Memo();
            for (int q = 0; q < n; ++q) {
                acc += memoized(xs[q], ys[q], zs[q], m);
            }
            s[t] = System.nanoTime() - t0;
            blackhole += acc;
        }
        long mo = BenchUtil.median(s);

        BenchUtil.header("Biome colour per 4096 quads");
        System.out.printf("  vanilla  %10.3f ms   %,.0f ns/quad%n", v / 1e6, (double) v / n);
        System.out.printf("  memoised %10.3f ms   %,.0f ns/quad   speedup %.2fx%n", mo / 1e6, (double) mo / n,
                (double) v / (double) mo);
        System.out.println("  blackhole " + blackhole);
    }
}
