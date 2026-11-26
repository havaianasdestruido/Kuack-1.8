package perfbench;

import java.util.Random;

/**
 * Heightmap of the biome-colour cost: vanilla calls colorMultiplier() once per QUAD, so a
 * grass block with 6 quads performs 6 independent 3x3 neighbourhood samples around the SAME
 * BlockPos. Those six results are identical by definition.
 */
public class BiomeColorPerBlock {

    static final int[] GRASS = new int[65536];
    static final Perlin noise = new Perlin(new Random(11));
    static final byte[] BIOME = new byte[256];
    static final float[] TEMP = new float[256];
    static volatile long blackhole;

    static {
        Random r = new Random(7);
        for (int i = 0; i < GRASS.length; ++i) {
            GRASS[i] = 0x800000 | r.nextInt(0x1000000);
        }
        for (int i = 0; i < 256; ++i) {
            BIOME[i] = (byte) (1 + r.nextInt(20));
            TEMP[i] = 0.3F + r.nextFloat() * 0.5F;
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

    static int sample(int x, int y, int z) {
        float temp = TEMP[(z & 15) << 4 | (x & 15)];
        float f;
        if (y > 64) {
            f = (float) (noise.noise((double) x / 8.0, (double) z / 8.0) * 4.0);
            f = temp - (f + (float) y - 64.0F) * 0.05F / 30.0F;
        } else {
            f = temp;
        }
        double d0 = Math.max(0.0F, Math.min(1.0F, f));
        return GRASS[(int) ((1.0 - 0.5 * d0) * 255.0) << 8 | (int) ((1.0 - d0) * 255.0)];
    }

    static int neighbourhood(int x, int y, int z) {
        int i = 0, j = 0, k = 0;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                int c = sample(x + dx, y, z + dz);
                i += (c & 0xFF0000) >> 16;
                j += (c & 0xFF00) >> 8;
                k += c & 255;
            }
        }
        return (i / 9 & 255) << 16 | (j / 9 & 255) << 8 | (k / 9 & 255);
    }

    public static void main(String[] args) {
        Random rng = new Random(5);
        int blocks = 1024; // grass blocks in one section
        int[] bx = new int[blocks], by = new int[blocks], bz = new int[blocks];
        for (int i = 0; i < blocks; ++i) {
            bx[i] = 1024 + (i & 15);
            by[i] = 64 + ((i >> 4) & 15);
            bz[i] = -512 + ((i >> 8) & 15);
        }
        int quadsPerBlock = 6;

        long[] s = new long[9];
        for (int w = 0; w < 100; ++w) {
            long acc = 0;
            for (int b = 0; b < blocks; ++b) {
                for (int q = 0; q < quadsPerBlock; ++q) {
                    acc += neighbourhood(bx[b], by[b], bz[b]);
                }
                acc += neighbourhood(bx[b], by[b], bz[b]);
            }
            blackhole += acc;
        }
        for (int t = 0; t < 9; ++t) {
            long t0 = System.nanoTime();
            long acc = 0;
            for (int b = 0; b < blocks; ++b) {
                for (int q = 0; q < quadsPerBlock; ++q) {
                    acc += neighbourhood(bx[b], by[b], bz[b]);
                }
            }
            s[t] = System.nanoTime() - t0;
            blackhole += acc;
        }
        long vanilla = BenchUtil.median(s);

        for (int t = 0; t < 9; ++t) {
            long t0 = System.nanoTime();
            long acc = 0;
            for (int b = 0; b < blocks; ++b) {
                int c = neighbourhood(bx[b], by[b], bz[b]);
                for (int q = 0; q < quadsPerBlock; ++q) {
                    acc += c;
                }
            }
            s[t] = System.nanoTime() - t0;
            blackhole += acc;
        }
        long perBlock = BenchUtil.median(s);

        BenchUtil.header("Biome colour: per-quad vs per-block (1024 grass blocks x 6 quads)");
        System.out.printf("  per-quad  %10.3f ms  (%,.0f ns/block)%n", vanilla / 1e6, (double) vanilla / blocks);
        System.out.printf("  per-block %10.3f ms  (%,.0f ns/block)  speedup %.2fx%n", perBlock / 1e6,
                (double) perBlock / blocks, (double) vanilla / (double) perBlock);
        System.out.println("  blackhole " + blackhole);
    }
}
