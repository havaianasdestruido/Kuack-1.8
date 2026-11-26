package perfbench;

/**
 * JVM benchmark + bit-exactness check for the 256-iteration arithmetic of
 * EntityRenderer.updateLightmap: V (vanilla 1.8.8) vs CSE (shipped invariant hoisting /
 * common-subexpression elimination).
 * change). Everything after the first four expressions is byte-for-byte the same in both.
 *
 * This is benchmark-only code: the real method also writes a DynamicTexture and calls GL, which
 * cannot run in a headless JVM or in TeaVM's Node harness. The differential check therefore proves
 * equivalence of the arithmetic; it does not substitute for an in-game screenshot comparison.
 */
public final class LightmapBench {

    private LightmapBench() {
    }

    /** Parameters that are constant for one lightmap update. */
    public static final class P {
        public float sunBrightness;
        public float torchFlickerX;
        public float bossColorModifier;
        public float bossColorModifierPrev;
        public float partialTicks;
        public float gammaSetting;
        public float nightVisionBrightness;
        public boolean nightVision;
        public boolean endDimension;
        public boolean lightning;
        public float[] table = new float[16];

        public P(float sun, float flicker, float boss, float bossPrev, float partial,
                 float gamma, float nv, boolean nvOn, boolean end, boolean bolt) {
            sunBrightness = sun;
            torchFlickerX = flicker;
            bossColorModifier = boss;
            bossColorModifierPrev = bossPrev;
            partialTicks = partial;
            gammaSetting = gamma;
            nightVisionBrightness = nv;
            nightVision = nvOn;
            endDimension = end;
            lightning = bolt;
            for (int i = 0; i < 16; ++i) {
                float f1 = 1.0F - (float) i / 15.0F;
                table[i] = (1.0F - f1) / (f1 * 3.0F + 1.0F);
            }
        }
    }

    /** Vanilla loop. Writes 256 RGB triples into out (768 floats). */
    public static void original(P p, float[] out) {
        float f = p.sunBrightness;
        float f1 = f * 0.95F + 0.05F;
        for (int i = 0; i < 256; ++i) {
            float f2 = p.table[i / 16] * f1;
            float f3 = p.table[i % 16] * (p.torchFlickerX * 0.1F + 1.5F);
            if (p.lightning) {
                f2 = p.table[i / 16];
            }
            float f4 = f2 * (f * 0.65F + 0.35F);
            float f5 = f2 * (f * 0.65F + 0.35F);
            writeBoth(p, out, i, f2, f3, f4, f5);
        }
    }

    /** Shipped loop: hoisted invariants + f5 = f4 (identical expression in vanilla). */
    public static void cse(P p, float[] out) {
        float f = p.sunBrightness;
        float f1 = f * 0.95F + 0.05F;
        float[] brightnessTable = p.table;
        float torchFlickerScale = p.torchFlickerX * 0.1F + 1.5F;
        float sunScale = f * 0.65F + 0.35F;
        for (int i = 0; i < 256; ++i) {
            float f2 = brightnessTable[i / 16] * f1;
            float f3 = brightnessTable[i % 16] * torchFlickerScale;
            if (p.lightning) {
                f2 = brightnessTable[i / 16];
            }
            float f4 = f2 * sunScale;
            float f5 = f4;
            writeBoth(p, out, i, f2, f3, f4, f5);
        }
    }

    private static void writeBoth(P p, float[] out, int i, float f2, float f3, float f4, float f5) {
        float f6 = f3 * ((f3 * 0.6F + 0.4F) * 0.6F + 0.4F);
        float f7 = f3 * (f3 * f3 * 0.6F + 0.4F);
        float f8 = f4 + f3;
        float f9 = f5 + f6;
        float f10 = f2 + f7;
        f8 = f8 * 0.96F + 0.03F;
        f9 = f9 * 0.96F + 0.03F;
        f10 = f10 * 0.96F + 0.03F;
        if (p.bossColorModifier > 0.0F) {
            float f11 = p.bossColorModifierPrev
                    + (p.bossColorModifier - p.bossColorModifierPrev) * p.partialTicks;
            f8 = f8 * (1.0F - f11) + f8 * 0.7F * f11;
            f9 = f9 * (1.0F - f11) + f9 * 0.6F * f11;
            f10 = f10 * (1.0F - f11) + f10 * 0.6F * f11;
        }
        if (p.endDimension) {
            f8 = 0.22F + f3 * 0.75F;
            f9 = 0.28F + f6 * 0.75F;
            f10 = 0.25F + f7 * 0.75F;
        }
        if (p.nightVision) {
            float f15 = p.nightVisionBrightness;
            float f12 = 1.0F / f8;
            if (f12 > 1.0F / f9) {
                f12 = 1.0F / f9;
            }
            if (f12 > 1.0F / f10) {
                f12 = 1.0F / f10;
            }
            f8 = f8 * (1.0F - f15) + f8 * f12 * f15;
            f9 = f9 * (1.0F - f15) + f9 * f12 * f15;
            f10 = f10 * (1.0F - f15) + f10 * f12 * f15;
        }
        f8 = clampHi(f8);
        f9 = clampHi(f9);
        f10 = clampHi(f10);
        float f16 = p.gammaSetting;
        float f17 = 1.0F - f8;
        float f13 = 1.0F - f9;
        float f14 = 1.0F - f10;
        f17 = 1.0F - f17 * f17 * f17 * f17;
        f13 = 1.0F - f13 * f13 * f13 * f13;
        f14 = 1.0F - f14 * f14 * f14 * f14;
        f8 = f8 * (1.0F - f16) + f17 * f16;
        f9 = f9 * (1.0F - f16) + f13 * f16;
        f10 = f10 * (1.0F - f16) + f14 * f16;
        f8 = f8 * 0.96F + 0.03F;
        f9 = f9 * 0.96F + 0.03F;
        f10 = f10 * 0.96F + 0.03F;
        out[i * 3] = clampLo(clampHi(f8));
        out[i * 3 + 1] = clampLo(clampHi(f9));
        out[i * 3 + 2] = clampLo(clampHi(f10));
    }

    private static float clampHi(float v) {
        return v > 1.0F ? 1.0F : v;
    }

    private static float clampLo(float v) {
        return v < 0.0F ? 0.0F : v;
    }

    public static void main(String[] args) {
        System.out.println("=== LightmapBench: updateLightmap invariant hoisting ===");
        P[] params = new P[6];
        params[0] = new P(0.85F, 0.02F, 0.0F, 0.0F, 0.5F, 0.0F, 0.0F, false, false, false);
        params[1] = new P(0.15F, -0.04F, 0.3F, 0.1F, 0.25F, 1.0F, 0.0F, false, false, false);
        params[2] = new P(0.5F, 0.0F, 0.0F, 0.0F, 0.75F, 0.5F, 0.7F, true, false, false);
        params[3] = new P(1.0F, 0.1F, 0.8F, 0.2F, 0.0F, 0.25F, 1.0F, true, false, true);
        params[4] = new P(0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, false, true, false);
        params[5] = new P(0.99F, 0.05F, 0.5F, 0.5F, 0.5F, 0.75F, 0.9F, true, true, true);
        float[] a = new float[768], b = new float[768];
        int checks = 0, fails = 0;
        for (P p : params) {
            original(p, a);
            cse(p, b);
            for (int i = 0; i < 768; ++i) {
                ++checks;
                if (Float.floatToRawIntBits(a[i]) != Float.floatToRawIntBits(b[i])) ++fails;
            }
        }
        System.out.printf("  bit-exact differential: %,d float comparisons, %d mismatches%n", checks, fails);
        if (fails != 0) throw new AssertionError("lightmap CSE is not bit-exact");

        int n = 40000;
        double[] orig = new double[7], opt = new double[7];
        for (int rep = 0; rep < 7; ++rep) {
            long t0 = System.nanoTime();
            double s0 = 0.0D;
            for (int i = 0; i < n; ++i) {
                original(params[i % params.length], a);
                s0 += a[(i * 3) & 767];
            }
            orig[rep] = (System.nanoTime() - t0) / (double) n;
            long t1 = System.nanoTime();
            double s1 = 0.0D;
            for (int i = 0; i < n; ++i) {
                cse(params[i % params.length], b);
                s1 += b[(i * 3) & 767];
            }
            opt[rep] = (System.nanoTime() - t1) / (double) n;
            if (s0 == 0.0D || s1 == 0.0D) throw new AssertionError("dead code elimination");
        }
        java.util.Arrays.sort(orig);
        java.util.Arrays.sort(opt);
        System.out.printf("  one 256-texel lightmap update: vanilla %.3f us   CSE %.3f us   %.2fx%n",
                orig[3] / 1000.0, opt[3] / 1000.0, orig[3] / opt[3]);
        System.out.println("  (20 lightmap updates/s => multiply the per-update saving by 20 for CPU/s)");
    }

}
