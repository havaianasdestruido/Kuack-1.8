package perfbench;

import java.util.Random;

import net.minecraft.client.renderer.texture.MipmapGammaBlend;

/**
 * Benchmark for FINDINGS #11: the mipmap box filter with and without the 256-entry gamma decode
 * table.
 *
 * <p>"before" is a verbatim copy of Minecraft 1.8.8's {@code TextureUtil.blendColors} /
 * {@code blendColorComponent} (see {@link VerifyGammaLut}, which also proves the two agree
 * bit-for-bit). "after" is the shipped {@link MipmapGammaBlend}, i.e. the code
 * {@code TextureUtil.generateMipmapData} now calls. The surrounding mipmap loop is identical in
 * both cases, so timing the filter is timing the whole difference.</p>
 *
 * <p>Workloads are sized like real sprites: a 16x16 block/item sprite with
 * {@code mipmapLevels = 4} produces 8x8 + 4x4 + 2x2 + 1x1 = 85 filtered texels, and the block
 * atlas contains roughly a thousand of them (plus one frame per frame of every animated texture).</p>
 *
 * Run: {@code ./perfbench/run_bench.sh perfbench.TextureGammaBench}
 */
public final class TextureGammaBench {

    public static void main(String[] args) {
        System.out.println("=== TextureGammaBench: mipmap gamma filter (vanilla pow vs 256-entry LUT) ===");

        benchFilterCalls();
        benchSpriteChains();
    }

    // ---------------------------------------------------------------- per call

    private static void benchFilterCalls() {
        final int n = 4096;
        int[] a = new int[n];
        int[] b = new int[n];
        int[] c = new int[n];
        int[] d = new int[n];
        Random rng = new Random(7L);
        for (int i = 0; i < n; ++i) {
            a[i] = rng.nextInt() | 0xFF000000;
            b[i] = rng.nextInt() | 0xFF000000;
            c[i] = rng.nextInt() | 0xFF000000;
            d[i] = rng.nextInt() | 0xFF000000;
        }
        // one transparent texel per quartet forces the gamma-correct branch (worst case)
        int[] at = a.clone();
        for (int i = 0; i < n; i += 4) {
            at[i] &= 0x00FFFFFF;
        }

        System.out.println("-- per filter invocation (median ns/call) --");
        long vLin = medianQuiet(200, 40, () -> {
            long s = 0;
            for (int i = 0; i < n; ++i) {
                s += VerifyGammaLut.vanillaBlend(a[i], b[i], c[i], d[i], false);
            }
            return s;
        });
        long lLin = medianQuiet(200, 40, () -> {
            long s = 0;
            for (int i = 0; i < n; ++i) {
                s += MipmapGammaBlend.blend(a[i], b[i], c[i], d[i], false);
            }
            return s;
        });
        long vGam = medianQuiet(200, 40, () -> {
            long s = 0;
            for (int i = 0; i < n; ++i) {
                s += VerifyGammaLut.vanillaBlend(at[i], b[i], c[i], d[i], true);
            }
            return s;
        });
        long lGam = medianQuiet(200, 40, () -> {
            long s = 0;
            for (int i = 0; i < n; ++i) {
                s += MipmapGammaBlend.blend(at[i], b[i], c[i], d[i], true);
            }
            return s;
        });
        System.out.printf("  %-42s vanilla %8.2f ns   LUT %8.2f ns   %5.2fx%n",
                "one texel, linear filter (4 decode + 4 encode)", vLin / (double) n, lLin / (double) n,
                (double) vLin / lLin);
        System.out.printf("  %-42s vanilla %8.2f ns   LUT %8.2f ns   %5.2fx%n",
                "one texel, gamma filter (16 decode + 4 encode)", vGam / (double) n, lGam / (double) n,
                (double) vGam / lGam);
        System.out.println();
    }

    // ------------------------------------------------------- realistic chains

    /** Mimics TextureUtil.generateMipmapData: box filter from level 0 down to `levels`. */
    private static long chainVanilla(int width, int levels, int[] level0, boolean gamma) {
        int[] prev = level0;
        long sink = 0;
        int w = width;
        for (int l = 1; l <= levels; ++l) {
            int ow = w >> 1;
            if (ow == 0) {
                break;
            }
            int[] out = new int[ow * ow];
            for (int y = 0; y < ow; ++y) {
                int row0 = (y << 1) * w;
                for (int x = 0; x < ow; ++x) {
                    int k = row0 + (x << 1);
                    out[x + y * ow] = VerifyGammaLut.vanillaBlend(prev[k], prev[k + 1], prev[k + w],
                            prev[k + w + 1], gamma);
                    sink += k;
                }
            }
            prev = out;
            w = ow;
        }
        return sink;
    }

    private static long chainLut(int width, int levels, int[] level0, boolean gamma) {
        int[] prev = level0;
        long sink = 0;
        int w = width;
        for (int l = 1; l <= levels; ++l) {
            int ow = w >> 1;
            if (ow == 0) {
                break;
            }
            int[] out = new int[ow * ow];
            for (int y = 0; y < ow; ++y) {
                int row0 = (y << 1) * w;
                for (int x = 0; x < ow; ++x) {
                    int k = row0 + (x << 1);
                    out[x + y * ow] = MipmapGammaBlend.blend(prev[k], prev[k + 1], prev[k + w],
                            prev[k + w + 1], gamma);
                    sink += k;
                }
            }
            prev = out;
            w = ow;
        }
        return sink;
    }

    private static void benchSpriteChains() {
        final int sprites = 1024;
        final int[][] texels = new int[sprites][];
        Random rng = new Random(11L);
        for (int i = 0; i < sprites; ++i) {
            int[] px = new int[16 * 16];
            for (int j = 0; j < px.length; ++j) {
                px[j] = rng.nextInt() | 0xFF000000;
            }
            texels[i] = px;
        }
        final int[][] transparentTexels = new int[sprites][];
        for (int i = 0; i < sprites; ++i) {
            int[] px = texels[i].clone();
            px[0] = 0x00123456;    // one fully transparent texel -> gamma-correct branch
            transparentTexels[i] = px;
        }

        System.out.println("-- whole-sprite mipmap chain, 16x16 sprite, 4 levels (85 filtered texels) --");
        final int[] one = texels[0];
        final int[] oneT = transparentTexels[0];
        long vanLin = medianQuiet(200, 40, () -> chainVanilla(16, 4, one, false));
        long lutLin = medianQuiet(200, 40, () -> chainLut(16, 4, one, false));
        long vanGam = medianQuiet(200, 40, () -> chainVanilla(16, 4, oneT, true));
        long lutGam = medianQuiet(200, 40, () -> chainLut(16, 4, oneT, true));
        printChainRow("linear filter", vanLin, lutLin);
        printChainRow("gamma filter ", vanGam, lutGam);

        System.out.println("-- block atlas: 1024 sprites x 16x16 x 4 mip levels --");
        long vanAtlas = medianQuiet(20, 15, () -> {
            long s = 0;
            for (int i = 0; i < sprites; ++i) {
                s += chainVanilla(16, 4, texels[i], false);
            }
            return s;
        });
        long lutAtlas = medianQuiet(20, 15, () -> {
            long s = 0;
            for (int i = 0; i < sprites; ++i) {
                s += chainLut(16, 4, texels[i], false);
            }
            return s;
        });
        System.out.printf("  %-28s vanilla %8.2f ms   LUT %8.2f ms   %6.2fx%n", "1024 sprites", vanAtlas / 1e6,
                lutAtlas / 1e6, (double) vanAtlas / lutAtlas);

        long vanAtlasG = medianQuiet(20, 15, () -> {
            long s = 0;
            for (int i = 0; i < sprites; ++i) {
                s += chainVanilla(16, 4, transparentTexels[i], true);
            }
            return s;
        });
        long lutAtlasG = medianQuiet(20, 15, () -> {
            long s = 0;
            for (int i = 0; i < sprites; ++i) {
                s += chainLut(16, 4, transparentTexels[i], true);
            }
            return s;
        });
        System.out.printf("  %-28s vanilla %8.2f ms   LUT %8.2f ms   %6.2fx%n", "1024 sprites (gamma)",
                vanAtlasG / 1e6, lutAtlasG / 1e6, (double) vanAtlasG / lutAtlasG);

        System.out.println("-- a 256x256 atlas (mip levels 5) in one go --");
        final int[] atlas = new int[256 * 256];
        Random r2 = new Random(5L);
        for (int i = 0; i < atlas.length; ++i) {
            atlas[i] = r2.nextInt() | 0xFF000000;
        }
        long vanBig = medianQuiet(20, 15, () -> chainVanilla(256, 5, atlas, false));
        long lutBig = medianQuiet(20, 15, () -> chainLut(256, 5, atlas, false));
        System.out.printf("  %-28s vanilla %8.2f ms   LUT %8.2f ms   %6.2fx%n", "256x256 atlas", vanBig / 1e6,
                lutBig / 1e6, (double) vanBig / lutBig);
    }

    // ---------------------------------------------------------------- helpers

    interface Body {
        long run();
    }

    /** Median of `trials` timed runs of `body` (ns), without the noisy per-trial echo. */
    private static long medianQuiet(int warmup, int trials, Body body) {
        for (int i = 0; i < warmup; ++i) {
            body.run();
        }
        long[] ns = new long[trials];
        for (int i = 0; i < trials; ++i) {
            long t0 = System.nanoTime();
            body.run();
            ns[i] = System.nanoTime() - t0;
        }
        java.util.Arrays.sort(ns);
        return ns[trials / 2];
    }

    private static void printChainRow(String label, long van, long lut) {
        System.out.printf("  %-14s vanilla %7.3f us   LUT %7.3f us   %5.2fx%n", label, van / 1e3, lut / 1e3,
                (double) van / lut);
    }
}
