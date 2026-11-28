package perfbench;

import net.minecraft.util.MathHelper;

/**
 * Verifies and benchmarks the algebraic / LUT / constant-folding optimizations added alongside
 * the exact mipmap encoder on HotSpot:
 *   1. TileEntityBeaconRenderer beam corner trig symmetry (8 trig -> 2 trig)
 *   2. RenderGuardian beam cross+diamond trig symmetry (16 trig -> 2 trig)
 *   3. EntityRenderer.updateFogColor fourth-root replacement (pow(f, 0.25) -> sqrt(sqrt(f)))
 *   4. InstancedFontRenderer EXP fog replacement (pow(2.718, -x) -> exp(-x * ln(2.718)))
 *   5. BlockNote 25-entry pitch LUT
 *   6. RenderGlobal.renderWorldBorder pow(d, 4) -> (d*d)^2
 *   7. MathHelper.func_181159_b / func_181161_i desktop preservation (Mojang fast bit-ops kept on JVM)
 *
 * Run: {@code ./perfbench/run_bench.sh perfbench.CrossRuntimeMathBench}
 */
public final class CrossRuntimeMathBench {

    private static final double LN_2_718 = 0.999896315728952D;
    private static final float[] NOTE_PITCH = {
        0.5F, 0.5297316F, 0.561231F, 0.59460354F, 0.62996054F, 0.6674199F, 0.70710677F, 0.74915355F, 0.7937005F,
        0.8408964F, 0.8908987F, 0.9438743F, 1.0F, 1.0594631F, 1.122462F, 1.1892071F, 1.2599211F, 1.3348398F,
        1.4142135F, 1.4983071F, 1.587401F, 1.6817929F, 1.7817974F, 1.8877486F, 2.0F
    };

    private static final double[] PHASES = new double[4096];
    private static final double[] XS = new double[4096];
    private static final double[] YS = new double[4096];

    static {
        int seed = 0x13579BDF;
        for (int i = 0; i < 4096; ++i) {
            seed = seed * 1664525 + 1013904223;
            PHASES[i] = ((seed >>> 8) & 0xFFFFFF) * (Math.PI * 2.0D / 16777216.0D) - 10.0D;
            seed = seed * 1664525 + 1013904223;
            XS[i] = ((seed >> 16) / 32768.0D) * 20.0D;
            seed = seed * 1664525 + 1013904223;
            YS[i] = ((seed >> 16) / 32768.0D) * 20.0D;
        }
    }

    private static volatile double vsink = 0.0D;

    public static void main(String[] args) {
        System.out.println("=== CrossRuntimeMathBench: algebraic/trig/LUT equivalence & timing ===");
        verifyAll();
        benchPair("TileEntityBeaconRenderer (8 trig -> 2 trig)", 40000, CrossRuntimeMathBench::beacon8, CrossRuntimeMathBench::beacon2);
        benchPair("RenderGuardian (16 trig -> 2 trig)", 40000, CrossRuntimeMathBench::guardian16, CrossRuntimeMathBench::guardian2);
        benchPair("EntityRenderer.updateFogColor pow(f,0.25) -> sqrt(sqrt)", 200000, CrossRuntimeMathBench::fogPow, CrossRuntimeMathBench::fogSqrt);
        benchPair("InstancedFontRenderer pow(2.718,-x) -> exp", 200000, CrossRuntimeMathBench::fontPow, CrossRuntimeMathBench::fontExp);
        benchPair("BlockNote pitch pow(2,(j-12)/12) -> 25-LUT", 200000, CrossRuntimeMathBench::notePow, CrossRuntimeMathBench::noteLut);
        benchPair("RenderGlobal worldBorder pow(d,4) -> (d*d)^2", 200000, CrossRuntimeMathBench::borderPow, CrossRuntimeMathBench::borderMul);
        benchPair("MathHelper.func_181159_b (JVM bit-ops vs Math.atan2)", 100000, CrossRuntimeMathBench::nativeAtan2, CrossRuntimeMathBench::mhAtan2);
    }

    private static void verifyAll() {
        double maxBeacon = 0.0D;
        double maxGuardian = 0.0D;
        int fogDiffs = 0;
        int noteDiffs = 0;
        int fontMaxUlp = 0;
        double maxBorder = 0.0D;

        for (int i = 0; i < 4096; ++i) {
            double d5 = PHASES[i];
            // Beacon corners
            double b0 = 0.5D + Math.cos(d5 + 2.356194490192345D) * 0.2D;
            double b1 = 0.5D + Math.sin(d5 + 2.356194490192345D) * 0.2D;
            double b2 = 0.5D + Math.cos(d5 + 0.7853981633974483D) * 0.2D;
            double b3 = 0.5D + Math.sin(d5 + 0.7853981633974483D) * 0.2D;
            double b4 = 0.5D + Math.cos(d5 + 3.9269908169872414D) * 0.2D;
            double b5 = 0.5D + Math.sin(d5 + 3.9269908169872414D) * 0.2D;
            double b6 = 0.5D + Math.cos(d5 + 5.497787143782138D) * 0.2D;
            double b7 = 0.5D + Math.sin(d5 + 5.497787143782138D) * 0.2D;
            double c45 = Math.cos(d5 + 0.7853981633974483D) * 0.2D;
            double s45 = Math.sin(d5 + 0.7853981633974483D) * 0.2D;
            maxBeacon = max8(maxBeacon,
                    Math.abs(b0 - (0.5D - s45)), Math.abs(b1 - (0.5D + c45)),
                    Math.abs(b2 - (0.5D + c45)), Math.abs(b3 - (0.5D + s45)),
                    Math.abs(b4 - (0.5D - c45)), Math.abs(b5 - (0.5D - s45)),
                    Math.abs(b6 - (0.5D + s45)), Math.abs(b7 - (0.5D - c45)));

            // Guardian beam corners
            double d6 = 0.2D * 1.41D;
            double c = Math.cos(d5), s = Math.sin(d5);
            double d6s = d6 * 0.7071067811865475D;
            double gc45 = (c - s) * d6s, gs45 = (c + s) * d6s;
            maxGuardian = max8(maxGuardian,
                    Math.abs(Math.cos(d5 + 2.356194490192345D) * d6 - (-gs45)),
                    Math.abs(Math.sin(d5 + 2.356194490192345D) * d6 - gc45),
                    Math.abs(Math.cos(d5 + 0.7853981633974483D) * d6 - gc45),
                    Math.abs(Math.sin(d5 + 0.7853981633974483D) * d6 - gs45),
                    Math.abs(Math.cos(d5 + 3.9269908169872414D) * d6 - (-gc45)),
                    Math.abs(Math.sin(d5 + 3.9269908169872414D) * d6 - (-gs45)),
                    Math.abs(Math.cos(d5 + 5.497787143782138D) * d6 - gs45),
                    Math.abs(Math.sin(d5 + 5.497787143782138D) * d6 - (-gc45)));
            maxGuardian = max8(maxGuardian,
                    Math.abs(Math.cos(d5 + 3.141592653589793D) * 0.2D - (-c * 0.2D)),
                    Math.abs(Math.sin(d5 + 3.141592653589793D) * 0.2D - (-s * 0.2D)),
                    Math.abs(Math.cos(d5 + 1.5707963267948966D) * 0.2D - (-s * 0.2D)),
                    Math.abs(Math.sin(d5 + 1.5707963267948966D) * 0.2D - (c * 0.2D)),
                    Math.abs(Math.cos(d5 + 4.71238898038469D) * 0.2D - (s * 0.2D)),
                    Math.abs(Math.sin(d5 + 4.71238898038469D) * 0.2D - (-c * 0.2D)),
                    0.0D, 0.0D);

            // Font EXP fog
            float x = i * 0.0025F;
            float fp = 1.0F - (float) Math.pow(2.718D, -x);
            float fe = 1.0F - (float) Math.exp(-x * LN_2_718);
            int ulp = Math.abs(Float.floatToRawIntBits(fp) - Float.floatToRawIntBits(fe));
            if (ulp > fontMaxUlp) fontMaxUlp = ulp;

            // WorldBorder pow4
            double d = (double) i / 4096.0D;
            double dSq = d * d;
            double bdiff = Math.abs(Math.pow(d, 4.0D) - dSq * dSq);
            if (bdiff > maxBorder) maxBorder = bdiff;
        }
        for (int rd = 1; rd <= 64; ++rd) {
            float f = 0.25F + 0.75F * (float) rd / 32.0F;
            if (Float.floatToRawIntBits(1.0F - (float) Math.pow((double) f, 0.25D))
                    != Float.floatToRawIntBits(1.0F - (float) Math.sqrt(Math.sqrt((double) f)))) {
                ++fogDiffs;
            }
        }
        for (int j = 0; j <= 24; ++j) {
            if (Float.floatToRawIntBits(NOTE_PITCH[j])
                    != Float.floatToRawIntBits((float) Math.pow(2.0D, (double) (j - 12) / 12.0D))) {
                ++noteDiffs;
            }
        }
        double maxAtanDiff = 0.0D;
        for (int i = 0; i < 4096; ++i) {
            double a = Math.atan2(YS[i], XS[i]);
            double b = MathHelper.func_181159_b(YS[i], XS[i]);
            double diff = Math.abs(a - b);
            if (diff > Math.PI) diff = Math.abs(diff - 2.0D * Math.PI);
            if (diff > maxAtanDiff) maxAtanDiff = diff;
        }

        System.out.printf("  verify beaconMaxAbs=%.3e  guardianMaxAbs=%.3e  fogBitDiffs=%d  noteBitDiffs=%d  fontMaxUlp=%d  borderMaxAbs=%.3e  mhAtan2MaxErr=%.3e%n",
                maxBeacon, maxGuardian, fogDiffs, noteDiffs, fontMaxUlp, maxBorder, maxAtanDiff);
        if (maxBeacon > 1e-14 || maxGuardian > 1e-14 || fogDiffs != 0 || noteDiffs != 0 || fontMaxUlp > 1 || maxBorder > 1e-15 || maxAtanDiff > 1e-5) {
            throw new AssertionError("Equivalence check failed!");
        }
    }

    private static double max8(double m, double a, double b, double c, double d, double e, double f, double g, double h) {
        double[] xs = { m, a, b, c, d, e, f, g, h };
        double r = m;
        for (double v : xs) if (v > r) r = v;
        return r;
    }

    interface Kernel { double run(int n); }

    private static void benchPair(String label, int ops, Kernel before, Kernel after) {
        double s = 0.0D;
        for (int w = 0; w < 5; ++w) { s += before.run(ops / 2); s += after.run(ops / 2); }
        double[] tB = new double[7], tA = new double[7];
        for (int r = 0; r < 7; ++r) {
            long t0 = System.nanoTime(); s += before.run(ops); long t1 = System.nanoTime();
            tB[r] = (double) (t1 - t0) / ops;
            long t2 = System.nanoTime(); s += after.run(ops); long t3 = System.nanoTime();
            tA[r] = (double) (t3 - t2) / ops;
        }
        vsink += s;
        java.util.Arrays.sort(tB);
        java.util.Arrays.sort(tA);
        System.out.printf("  %-52s %7.2f ns -> %7.2f ns   (%5.2fx)%n", label, tB[3], tA[3], tB[3] / tA[3]);
    }

    private static double beacon8(int n) {
        double acc = 0.0D;
        for (int i = 0; i < n; ++i) {
            double d5 = PHASES[i & 4095];
            acc += (0.5D + Math.cos(d5 + 2.356194490192345D) * 0.2D)
                 + (0.5D + Math.sin(d5 + 2.356194490192345D) * 0.2D)
                 + (0.5D + Math.cos(d5 + 0.7853981633974483D) * 0.2D)
                 + (0.5D + Math.sin(d5 + 0.7853981633974483D) * 0.2D)
                 + (0.5D + Math.cos(d5 + 3.9269908169872414D) * 0.2D)
                 + (0.5D + Math.sin(d5 + 3.9269908169872414D) * 0.2D)
                 + (0.5D + Math.cos(d5 + 5.497787143782138D) * 0.2D)
                 + (0.5D + Math.sin(d5 + 5.497787143782138D) * 0.2D);
        }
        return acc;
    }

    private static double beacon2(int n) {
        double acc = 0.0D;
        for (int i = 0; i < n; ++i) {
            double d5 = PHASES[i & 4095];
            double c45 = Math.cos(d5 + 0.7853981633974483D) * 0.2D;
            double s45 = Math.sin(d5 + 0.7853981633974483D) * 0.2D;
            acc += (0.5D - s45) + (0.5D + c45)
                 + (0.5D + c45) + (0.5D + s45)
                 + (0.5D - c45) + (0.5D - s45)
                 + (0.5D + s45) + (0.5D - c45);
        }
        return acc;
    }

    private static double guardian16(int n) {
        double acc = 0.0D;
        final double d5 = 0.2D, d6 = 0.2D * 1.41D;
        for (int i = 0; i < n; ++i) {
            double d4 = PHASES[i & 4095];
            acc += Math.cos(d4 + 2.356194490192345D) * d6 + Math.sin(d4 + 2.356194490192345D) * d6
                 + Math.cos(d4 + 0.7853981633974483D) * d6 + Math.sin(d4 + 0.7853981633974483D) * d6
                 + Math.cos(d4 + 3.9269908169872414D) * d6 + Math.sin(d4 + 3.9269908169872414D) * d6
                 + Math.cos(d4 + 5.497787143782138D) * d6 + Math.sin(d4 + 5.497787143782138D) * d6
                 + Math.cos(d4 + 3.141592653589793D) * d5 + Math.sin(d4 + 3.141592653589793D) * d5
                 + Math.cos(d4) * d5 + Math.sin(d4) * d5
                 + Math.cos(d4 + 1.5707963267948966D) * d5 + Math.sin(d4 + 1.5707963267948966D) * d5
                 + Math.cos(d4 + 4.71238898038469D) * d5 + Math.sin(d4 + 4.71238898038469D) * d5;
        }
        return acc;
    }

    private static double guardian2(int n) {
        double acc = 0.0D;
        final double d5 = 0.2D, d6s = 0.2D * 1.41D * 0.7071067811865475D;
        for (int i = 0; i < n; ++i) {
            double d4 = PHASES[i & 4095];
            double c = Math.cos(d4), s = Math.sin(d4);
            double c45 = (c - s) * d6s, s45 = (c + s) * d6s;
            double c5 = c * d5, s5 = s * d5;
            acc += (-s45) + c45 + c45 + s45 + (-c45) + (-s45) + s45 + (-c45)
                 + (-c5) + (-s5) + c5 + s5 + (-s5) + c5 + s5 + (-c5);
        }
        return acc;
    }

    private static double fogPow(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) {
            float f = 0.25F + 0.75F * (float) ((i & 31) + 1) / 32.0F;
            s += 1.0F - (float) Math.pow((double) f, 0.25D);
        }
        return s;
    }

    private static double fogSqrt(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) {
            float f = 0.25F + 0.75F * (float) ((i & 31) + 1) / 32.0F;
            s += 1.0F - (float) Math.sqrt(Math.sqrt((double) f));
        }
        return s;
    }

    private static double fontPow(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) s += 1.0F - (float) Math.pow(2.718D, -(i & 255) * 0.01D);
        return s;
    }

    private static double fontExp(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) s += 1.0F - (float) Math.exp(-(i & 255) * 0.01D * LN_2_718);
        return s;
    }

    private static double notePow(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) s += (float) Math.pow(2.0D, (double) ((i % 25) - 12) / 12.0D);
        return s;
    }

    private static double noteLut(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) s += NOTE_PITCH[i % 25];
        return s;
    }

    private static double borderPow(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) s += Math.pow((i & 255) / 256.0D, 4.0D);
        return s;
    }

    private static double borderMul(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) {
            double d = (i & 255) / 256.0D;
            double d2 = d * d;
            s += d2 * d2;
        }
        return s;
    }

    private static double nativeAtan2(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) s += Math.atan2(YS[i & 4095], XS[i & 4095]);
        return s;
    }

    private static double mhAtan2(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) s += MathHelper.func_181159_b(YS[i & 4095], XS[i & 4095]);
        return s;
    }
}
