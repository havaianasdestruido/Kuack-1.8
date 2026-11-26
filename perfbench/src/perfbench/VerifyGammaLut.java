package perfbench;

import java.util.Random;

import net.minecraft.client.renderer.texture.MipmapGammaBlend;

/**
 * Equivalence proof for FINDINGS #11 (mipmap gamma decode lookup table).
 *
 * <p>Three layers:</p>
 * <ol>
 *   <li><b>Exhaustive table check</b>: every one of the 256 entries of the decode table must be
 *       bit-identical (floatToRawIntBits) to the exact expression the call sites used.</li>
 *   <li><b>Exhaustive one-channel sweep</b>: for every byte value in every channel position, with
 *       the three neighbour texels pinned to a set of representative values (0, 1, 127, 128, 254,
 *       255, plus transparent and opaque alpha), the patched blend must equal a verbatim copy of the
 *       vanilla code, for both filter branches (gamma-correct and linear).</li>
 *   <li><b>Randomised differential test</b>: millions of ARGB texel quadruples, mixed alpha
 *       (including fully transparent texels that select the gamma-correct branch), compared against
 *       the verbatim vanilla implementation.</li>
 * </ol>
 *
 * Run: {@code ./perfbench/run_bench.sh perfbench.VerifyGammaLut}
 */
public final class VerifyGammaLut {
    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        System.out.println("=== VerifyGammaLut: mipmap gamma decode LUT equivalence ===");

        checkTable();
        sweepSingleChannel();
        randomDifferential(Integer.parseInt(System.getProperty("verify.random", "4000000")));

        System.out.printf("checks: %,d   failures: %,d%n", checks, failures);
        if (failures != 0) {
            throw new AssertionError("VerifyGammaLut FAILED with " + failures + " mismatches");
        }
        System.out.println("OK: mipmap output is bit-identical to the vanilla implementation.");
    }

    // ------------------------------------------------------------------ layer 1

    private static void checkTable() {
        int bad = 0;
        for (int v = 0; v < 256; ++v) {
            float expected = (float) Math.pow((double) ((float) v / 255.0F), 2.2D);
            float actual = MipmapGammaBlend.gamma22(v);
            ++checks;
            if (Float.floatToRawIntBits(expected) != Float.floatToRawIntBits(actual)) {
                ++bad;
                failures++;
            }
        }
        System.out.printf("  decode table: 256/256 entries checked bit-exactly, %d mismatches%n", bad);
    }

    // ------------------------------------------------------------------ layer 2

    private static void sweepSingleChannel() {
        int[] pinned = { 0x00000000, 0xFF000000, 0xFFFFFFFF, 0x7F7F7F7F, 0x80808080, 0xFEFEFEFE,
                0x01010101, 0xFF00FF00, 0x00FF00FF, 0x12345678, 0x87654321, 0xFFFFFF00 };
        int bad = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            for (int v = 0; v < 256; ++v) {
                for (int p0 = 0; p0 < pinned.length; ++p0) {
                    for (int p1 = 0; p1 < pinned.length; ++p1) {
                        int a = withChannel(pinned[p0], shift, v);
                        int b = pinned[p1];
                        int c = pinned[(p0 + p1 + 3) % pinned.length];
                        int d = pinned[(p0 * 5 + p1 + 7) % pinned.length];
                        bad += compareQuartet(a, b, c, d);
                    }
                }
            }
        }
        System.out.printf("  one-channel sweep: 4 channels x 256 values x %d x %d pinned neighbours, %d mismatches%n",
                pinned.length, pinned.length, bad);
    }

    private static int withChannel(int argb, int shift, int v) {
        return (argb & ~(255 << shift)) | (v << shift);
    }

    // ------------------------------------------------------------------ layer 3

    private static void randomDifferential(int n) {
        Random rng = new Random(0x5EEDBEEFL);
        int bad = 0;
        // a mix of fully-random texels and "mostly opaque" texels, because the two filter
        // branches are selected by the presence of a transparent texel in the sprite
        for (int i = 0; i < n; ++i) {
            int a = rng.nextInt();
            int b = rng.nextInt();
            int c = rng.nextInt();
            int d = rng.nextInt();
            if ((i & 1) == 0) {
                a |= 0xFF000000;
                b |= 0xFF000000;
                c |= 0xFF000000;
                d |= 0xFF000000;
            } else if ((i & 3) == 1) {
                a &= 0x00FFFFFF;
            }
            bad += compareQuartet(a, b, c, d);
        }
        System.out.printf("  randomised differential: %,d texel quadruples, %d mismatches%n", n, bad);
    }

    private static int compareQuartet(int a, int b, int c, int d) {
        int bad = 0;
        // branch 0: linear (vanilla blendColors(..., false) -> blendColorComponent x4)
        int ref0 = vanillaBlend(a, b, c, d, false);
        int got0 = MipmapGammaBlend.blend(a, b, c, d, false);
        ++checks;
        if (ref0 != got0) {
            ++bad;
            failures++;
            if (bad < 8) {
                System.out.printf("    MISMATCH linear %08x %08x %08x %08x: vanilla %08x got %08x%n", a, b, c, d,
                        ref0, got0);
            }
        }
        // branch 1: gamma-correct (vanilla blendColors(..., true))
        int ref1 = vanillaBlend(a, b, c, d, true);
        int got1 = MipmapGammaBlend.blend(a, b, c, d, true);
        ++checks;
        if (ref1 != got1) {
            ++bad;
            failures++;
            if (bad < 8) {
                System.out.printf("    MISMATCH gamma  %08x %08x %08x %08x: vanilla %08x got %08x%n", a, b, c, d,
                        ref1, got1);
            }
        }
        return bad;
    }

    // ------------------------------------- verbatim copy of the vanilla implementation

    private static final int[] mipmapBuffer = new int[4];

    /** Verbatim Minecraft 1.8.8 TextureUtil.blendColors. */
    static int vanillaBlend(int parInt1, int parInt2, int parInt3, int parInt4, boolean parFlag) {
        if (!parFlag) {
            int i1 = vanillaBlendComponent(parInt1, parInt2, parInt3, parInt4, 24);
            int j1 = vanillaBlendComponent(parInt1, parInt2, parInt3, parInt4, 16);
            int k1 = vanillaBlendComponent(parInt1, parInt2, parInt3, parInt4, 8);
            int l1 = vanillaBlendComponent(parInt1, parInt2, parInt3, parInt4, 0);
            return i1 << 24 | j1 << 16 | k1 << 8 | l1;
        } else {
            mipmapBuffer[0] = parInt1;
            mipmapBuffer[1] = parInt2;
            mipmapBuffer[2] = parInt3;
            mipmapBuffer[3] = parInt4;
            float f = 0.0F;
            float f1 = 0.0F;
            float f2 = 0.0F;
            float f3 = 0.0F;

            for (int i = 0; i < 4; ++i) {
                if (mipmapBuffer[i] >> 24 != 0) {
                    f += (float) Math.pow((double) ((float) (mipmapBuffer[i] >> 24 & 255) / 255.0F), 2.2D);
                    f1 += (float) Math.pow((double) ((float) (mipmapBuffer[i] >> 16 & 255) / 255.0F), 2.2D);
                    f2 += (float) Math.pow((double) ((float) (mipmapBuffer[i] >> 8 & 255) / 255.0F), 2.2D);
                    f3 += (float) Math.pow((double) ((float) (mipmapBuffer[i] >> 0 & 255) / 255.0F), 2.2D);
                }
            }

            f = f / 4.0F;
            f1 = f1 / 4.0F;
            f2 = f2 / 4.0F;
            f3 = f3 / 4.0F;
            int i2 = (int) (Math.pow((double) f, 0.45454545454545453D) * 255.0D);
            int j = (int) (Math.pow((double) f1, 0.45454545454545453D) * 255.0D);
            int k = (int) (Math.pow((double) f2, 0.45454545454545453D) * 255.0D);
            int l = (int) (Math.pow((double) f3, 0.45454545454545453D) * 255.0D);
            if (i2 < 96) {
                i2 = 0;
            }

            return i2 << 24 | j << 16 | k << 8 | l;
        }
    }

    /** Verbatim Minecraft 1.8.8 TextureUtil.blendColorComponent. */
    static int vanillaBlendComponent(int parInt1, int parInt2, int parInt3, int parInt4, int parInt5) {
        float f = (float) Math.pow((double) ((float) (parInt1 >> parInt5 & 255) / 255.0F), 2.2D);
        float f1 = (float) Math.pow((double) ((float) (parInt2 >> parInt5 & 255) / 255.0F), 2.2D);
        float f2 = (float) Math.pow((double) ((float) (parInt3 >> parInt5 & 255) / 255.0F), 2.2D);
        float f3 = (float) Math.pow((double) ((float) (parInt4 >> parInt5 & 255) / 255.0F), 2.2D);
        float f4 = (float) Math.pow((double) (f + f1 + f2 + f3) * 0.25D, 0.45454545454545453D);
        return (int) ((double) f4 * 255.0D);
    }
}
