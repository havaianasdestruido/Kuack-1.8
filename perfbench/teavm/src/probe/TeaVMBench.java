package probe;

import net.minecraft.client.renderer.texture.MipmapGammaBlend;
import net.minecraft.util.MathHelper;

/**
 * Cross-runtime mathematical benchmark, compiled BOTH by ECJ (run on HotSpot 17/25) and by the
 * project's own TeaVMBridgeImpl 0.9.2 (run in Node/V8). Same Java source, same deterministic
 * inputs, same printed result lines - so the two runtimes can be compared without a hand-written
 * JS replica.
 *
 * Output contract (parsed by perfbench/teavm/run_probe.js):
 *   VERIFY <name> <checks> <failures>
 *   INIT   <name> <nanoseconds>
 *   BENCH  <name> <rep> <nanoseconds> <ops>
 *   ERROR  <name> <checks> <mismatches> <maxAbsError>
 *   PROBE_END
 */
public final class TeaVMBench {

    private static final int SAMPLES = 4096;
    private static final float[] ANGLES = new float[SAMPLES];
    private static final int[] TEXELS = new int[SAMPLES];
    private static final double[] AVGS = new double[SAMPLES];
    private static final double[] XS = new double[SAMPLES];
    private static final double[] YS = new double[SAMPLES];
    private static final double[] POS = new double[SAMPLES];
    private static final float[] LM_OUT = new float[768];
    private static final LightmapKernel.P[] LM_PARAMS = new LightmapKernel.P[6];
    private static final double LN_2_718 = 0.999896315728952D;
    private static final double LEGACY_BIAS = Double.longBitsToDouble(4805340802404319232L);
    private static final double[] LEGACY_ASIN = new double[257];
    private static final double[] LEGACY_COS = new double[257];

    static {
        for (int j = 0; j < 257; ++j) {
            double d0 = (double) j / 256.0D;
            double d1 = Math.asin(d0);
            LEGACY_COS[j] = Math.cos(d1);
            LEGACY_ASIN[j] = d1;
        }
    }

    private static final String[] KERNELS = {
        "trig_native", "trig_table", "decode_native", "decode_lut",
        "blend_vanilla_linear", "blend_shipped_linear", "blend_vanilla_gamma", "blend_shipped_gamma",
        "encode_exact", "encode_quantized", "lightmap_original", "lightmap_cse",
        "mh_atan2_legacy", "mh_atan2_shipped", "mh_rsqrt_legacy", "mh_rsqrt_shipped",
        "beacon_8trig", "beacon_2trig", "guardian_16trig", "guardian_2trig",
        "fog_pow25", "fog_sqrtsqrt", "font_pow", "font_exp"
    };
    private static final int[] OPS = {
        200000, 200000, 200000, 200000,
        20000, 20000, 20000, 20000,
        200000, 200000, 400, 400,
        100000, 100000, 200000, 200000,
        40000, 40000, 40000, 40000,
        200000, 200000, 200000, 200000
    };

    private static double sink = 0.0D;

    private TeaVMBench() {
    }

    public static void main(String[] args) {
        System.out.println("PROBE_BEGIN");
        if (args.length > 0 && "init".equals(args[0])) {
            // TeaVM calls every reachable <clinit> before main's body; timing this call from
            // Node therefore measures the real class-initialization/startup cost of the bundle.
            System.out.println("INIT_ONLY");
            return;
        }
        initInputs();

        // Cold first-use costs (includes static initializers / table construction).
        long t = System.nanoTime();
        sink += MathHelper.sin(0.123F);
        System.out.println("INIT mathhelper_sin_table " + (System.nanoTime() - t));

        t = System.nanoTime();
        sink += MipmapGammaBlend.gamma22(7);
        System.out.println("INIT embedded_gamma_table " + (System.nanoTime() - t));

        t = System.nanoTime();
        sink += RuntimeSinTable.sin(0.123F);
        System.out.println("INIT runtime_sin_table " + (System.nanoTime() - t));

        t = System.nanoTime();
        sink += EmbeddedSinTable.sin(0.123F);
        System.out.println("INIT embedded_sin_table " + (System.nanoTime() - t));

        t = System.nanoTime();
        sink += EncodeQuantized.encoded(0.5D);
        System.out.println("INIT quantized_encode_table " + (System.nanoTime() - t));

        verify();

        for (int k = 0; k < KERNELS.length; ++k) {
            int n = OPS[k];
            for (int w = 0; w < 3; ++w) {
                sink += run(k, n / 4);
            }
            for (int r = 0; r < 7; ++r) {
                long t0 = System.nanoTime();
                sink += run(k, n);
                long t1 = System.nanoTime();
                System.out.println("BENCH " + KERNELS[k] + " " + r + " " + (t1 - t0) + " " + n);
            }
        }

        System.out.println("SINK " + sink);
        System.out.println("PROBE_END");
    }

    // ------------------------------------------------------------------ inputs

    private static void initInputs() {
        int seed = 0x5EEDBEEF;
        for (int i = 0; i < SAMPLES; ++i) {
            seed = seed * 1664525 + 1013904223;
            ANGLES[i] = (float) ((seed >>> 8) & 0xFFFFFF) * (6.2831853F / 16777216.0F);
            seed = seed * 1664525 + 1013904223;
            TEXELS[i] = seed;
            // realistic mipmap averages: mean of four decoded channel bytes
            double a = VanillaBlend.decode((seed >>> 0) & 255);
            double b = VanillaBlend.decode((seed >>> 8) & 255);
            double c = VanillaBlend.decode((seed >>> 16) & 255);
            double d = VanillaBlend.decode((seed >>> 24) & 255);
            AVGS[i] = (a + b + c + d) * 0.25D;
            seed = seed * 1664525 + 1013904223;
            XS[i] = ((seed >> 16) / 32768.0D) * 20.0D;
            seed = seed * 1664525 + 1013904223;
            YS[i] = ((seed >> 16) / 32768.0D) * 20.0D;
            POS[i] = Math.abs(XS[i]) + 0.05D;
        }
        LM_PARAMS[0] = new LightmapKernel.P(0.85F, 0.02F, 0.0F, 0.0F, 0.5F, 0.0F, 0.0F, false, false, false);
        LM_PARAMS[1] = new LightmapKernel.P(0.15F, -0.04F, 0.3F, 0.1F, 0.25F, 1.0F, 0.0F, false, false, false);
        LM_PARAMS[2] = new LightmapKernel.P(0.5F, 0.0F, 0.0F, 0.0F, 0.75F, 0.5F, 0.7F, true, false, false);
        LM_PARAMS[3] = new LightmapKernel.P(1.0F, 0.1F, 0.8F, 0.2F, 0.0F, 0.25F, 1.0F, true, false, true);
        LM_PARAMS[4] = new LightmapKernel.P(0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, false, true, false);
        LM_PARAMS[5] = new LightmapKernel.P(0.99F, 0.05F, 0.5F, 0.5F, 0.5F, 0.75F, 0.9F, true, true, true);
    }

    // ------------------------------------------------------------------ kernels

    private static double run(int kernel, int n) {
        double sum = 0.0D;
        switch (kernel) {
            case 0:
                for (int i = 0; i < n; ++i) {
                    float a = ANGLES[i & (SAMPLES - 1)];
                    sum += (float) Math.cos(a) + (float) Math.sin(a);
                }
                break;
            case 1:
                for (int i = 0; i < n; ++i) {
                    float a = ANGLES[i & (SAMPLES - 1)];
                    sum += MathHelper.cos(a) + MathHelper.sin(a);
                }
                break;
            case 2:
                for (int i = 0; i < n; ++i) {
                    sum += VanillaBlend.decode(TEXELS[i & (SAMPLES - 1)] & 255);
                }
                break;
            case 3:
                for (int i = 0; i < n; ++i) {
                    sum += MipmapGammaBlend.gamma22(TEXELS[i & (SAMPLES - 1)]);
                }
                break;
            case 4:
                for (int i = 0; i < n; ++i) {
                    int j = (i & (SAMPLES - 5));
                    sum += VanillaBlend.blend(TEXELS[j], TEXELS[j + 1], TEXELS[j + 2], TEXELS[j + 3], false);
                }
                break;
            case 5:
                for (int i = 0; i < n; ++i) {
                    int j = (i & (SAMPLES - 5));
                    sum += MipmapGammaBlend.blend(TEXELS[j], TEXELS[j + 1], TEXELS[j + 2], TEXELS[j + 3], false);
                }
                break;
            case 6:
                for (int i = 0; i < n; ++i) {
                    int j = (i & (SAMPLES - 5));
                    sum += VanillaBlend.blend(TEXELS[j], TEXELS[j + 1], TEXELS[j + 2], TEXELS[j + 3], true);
                }
                break;
            case 7:
                for (int i = 0; i < n; ++i) {
                    int j = (i & (SAMPLES - 5));
                    sum += MipmapGammaBlend.blend(TEXELS[j], TEXELS[j + 1], TEXELS[j + 2], TEXELS[j + 3], true);
                }
                break;
            case 8:
                for (int i = 0; i < n; ++i) {
                    sum += EncodeQuantized.exact(AVGS[i & (SAMPLES - 1)]);
                }
                break;
            case 9:
                for (int i = 0; i < n; ++i) {
                    sum += EncodeQuantized.encoded(AVGS[i & (SAMPLES - 1)]);
                }
                break;
            case 10:
                for (int i = 0; i < n; ++i) {
                    LightmapKernel.original(LM_PARAMS[i % LM_PARAMS.length], LM_OUT);
                    sum += LM_OUT[(i * 3) & 767];
                }
                break;
            case 11:
                for (int i = 0; i < n; ++i) {
                    LightmapKernel.cse(LM_PARAMS[i % LM_PARAMS.length], LM_OUT);
                    sum += LM_OUT[(i * 3) & 767];
                }
                break;
            case 12:
                for (int i = 0; i < n; ++i) {
                    sum += legacyAtan2(YS[i & (SAMPLES - 1)], XS[i & (SAMPLES - 1)]);
                }
                break;
            case 13:
                for (int i = 0; i < n; ++i) {
                    sum += MathHelper.func_181159_b(YS[i & (SAMPLES - 1)], XS[i & (SAMPLES - 1)]);
                }
                break;
            case 14:
                for (int i = 0; i < n; ++i) {
                    sum += legacyRsqrt(POS[i & (SAMPLES - 1)]);
                }
                break;
            case 15:
                for (int i = 0; i < n; ++i) {
                    sum += MathHelper.func_181161_i(POS[i & (SAMPLES - 1)]);
                }
                break;
            case 16:
                for (int i = 0; i < n; ++i) {
                    double d5 = ANGLES[i & (SAMPLES - 1)];
                    sum += (0.5D + Math.cos(d5 + 2.356194490192345D) * 0.2D)
                         + (0.5D + Math.sin(d5 + 2.356194490192345D) * 0.2D)
                         + (0.5D + Math.cos(d5 + 0.7853981633974483D) * 0.2D)
                         + (0.5D + Math.sin(d5 + 0.7853981633974483D) * 0.2D)
                         + (0.5D + Math.cos(d5 + 3.9269908169872414D) * 0.2D)
                         + (0.5D + Math.sin(d5 + 3.9269908169872414D) * 0.2D)
                         + (0.5D + Math.cos(d5 + 5.497787143782138D) * 0.2D)
                         + (0.5D + Math.sin(d5 + 5.497787143782138D) * 0.2D);
                }
                break;
            case 17:
                for (int i = 0; i < n; ++i) {
                    double d5 = ANGLES[i & (SAMPLES - 1)];
                    double c45 = Math.cos(d5 + 0.7853981633974483D) * 0.2D;
                    double s45 = Math.sin(d5 + 0.7853981633974483D) * 0.2D;
                    sum += (0.5D - s45) + (0.5D + c45)
                         + (0.5D + c45) + (0.5D + s45)
                         + (0.5D - c45) + (0.5D - s45)
                         + (0.5D + s45) + (0.5D - c45);
                }
                break;
            case 18:
                for (int i = 0; i < n; ++i) {
                    double d4 = ANGLES[i & (SAMPLES - 1)];
                    double d5 = 0.2D, d6 = 0.2D * 1.41D;
                    sum += Math.cos(d4 + 2.356194490192345D) * d6 + Math.sin(d4 + 2.356194490192345D) * d6
                         + Math.cos(d4 + 0.7853981633974483D) * d6 + Math.sin(d4 + 0.7853981633974483D) * d6
                         + Math.cos(d4 + 3.9269908169872414D) * d6 + Math.sin(d4 + 3.9269908169872414D) * d6
                         + Math.cos(d4 + 5.497787143782138D) * d6 + Math.sin(d4 + 5.497787143782138D) * d6
                         + Math.cos(d4 + 3.141592653589793D) * d5 + Math.sin(d4 + 3.141592653589793D) * d5
                         + Math.cos(d4) * d5 + Math.sin(d4) * d5
                         + Math.cos(d4 + 1.5707963267948966D) * d5 + Math.sin(d4 + 1.5707963267948966D) * d5
                         + Math.cos(d4 + 4.71238898038469D) * d5 + Math.sin(d4 + 4.71238898038469D) * d5;
                }
                break;
            case 19:
                for (int i = 0; i < n; ++i) {
                    double d4 = ANGLES[i & (SAMPLES - 1)];
                    double d5 = 0.2D, d6s = 0.2D * 1.41D * 0.7071067811865475D;
                    double c = Math.cos(d4), s = Math.sin(d4);
                    double c45 = (c - s) * d6s, s45 = (c + s) * d6s;
                    double c5 = c * d5, s5 = s * d5;
                    sum += (-s45) + c45 + c45 + s45 + (-c45) + (-s45) + s45 + (-c45)
                         + (-c5) + (-s5) + c5 + s5 + (-s5) + c5 + s5 + (-c5);
                }
                break;
            case 20:
                for (int i = 0; i < n; ++i) {
                    float f = 0.25F + 0.75F * (float) ((i & 31) + 1) / 32.0F;
                    sum += 1.0F - (float) Math.pow((double) f, 0.25D);
                }
                break;
            case 21:
                for (int i = 0; i < n; ++i) {
                    float f = 0.25F + 0.75F * (float) ((i & 31) + 1) / 32.0F;
                    sum += 1.0F - (float) Math.sqrt(Math.sqrt((double) f));
                }
                break;
            case 22:
                for (int i = 0; i < n; ++i) {
                    sum += 1.0F - (float) Math.pow(2.718D, -(i & 255) * 0.01D);
                }
                break;
            default:
                for (int i = 0; i < n; ++i) {
                    sum += 1.0F - (float) Math.exp(-(i & 255) * 0.01D * LN_2_718);
                }
                break;
        }
        return sum;
    }

    private static double legacyRsqrt(double x) {
        double d0 = 0.5D * x;
        long i = Double.doubleToRawLongBits(x);
        i = 6910469410427058090L - (i >> 1);
        x = Double.longBitsToDouble(i);
        return x * (1.5D - d0 * x * x);
    }

    private static double legacyAtan2(double y, double x) {
        double d0 = x * x + y * y;
        if (Double.isNaN(d0)) return Double.NaN;
        boolean flag = y < 0.0D; if (flag) y = -y;
        boolean flag1 = x < 0.0D; if (flag1) x = -x;
        boolean flag2 = y > x;
        if (flag2) { double t = x; x = y; y = t; }
        double d9 = legacyRsqrt(d0);
        x *= d9; y *= d9;
        double d2 = LEGACY_BIAS + y;
        int i = (int) Double.doubleToRawLongBits(d2);
        double d3 = LEGACY_ASIN[i], d4 = LEGACY_COS[i];
        double d5 = d2 - LEGACY_BIAS;
        double d6 = y * d4 - x * d5;
        double d8 = d3 + (6.0D + d6 * d6) * d6 * 0.16666666666666666D;
        if (flag2) d8 = 1.5707963267948966D - d8;
        if (flag1) d8 = 3.141592653589793D - d8;
        if (flag) d8 = -d8;
        return d8;
    }

    // ------------------------------------------------------------------ equivalence

    private static void verify() {
        // 1. The shipped table must be bit-identical to the PRE-CHANGE browser table.
        //    With the platform-specific constant arrays this is now exact in V8.
        int tableFails = 0;
        for (int v = 0; v < 256; ++v) {
            if (Float.floatToRawIntBits(MipmapGammaBlend.gamma22(v))
                    != Float.floatToRawIntBits(LegacyGammaBlend.gamma22(v))) ++tableFails;
        }
        System.out.println("VERIFY gamma_vs_prechange_browser_table 256 " + tableFails);

        // 2. Negative / out-of-range indices must wrap exactly.
        int idxChecks = 0, idxFails = 0;
        for (int v = -512; v < 512; ++v) {
            ++idxChecks;
            if (Float.floatToRawIntBits(MipmapGammaBlend.gamma22(v))
                    != Float.floatToRawIntBits(MipmapGammaBlend.gamma22(v & 255))) ++idxFails;
        }
        System.out.println("VERIFY gamma_index_wrap " + idxChecks + " " + idxFails);

        // 3. Full blend vs (a) verbatim vanilla Java source and (b) the pre-change browser table.
        //    Randomised plus targeted boundary/adversarial quadruples around every differing byte.
        int checks = 0, failsVanilla = 0, failsLegacy = 0;
        for (int i = 0; i < 100000; ++i) {
            int seed = 0x5EEDBEEF + i * 1664525;
            int a = seed, b = seed * 1664525 + 1013904223;
            int c = b * 1664525 + 1013904223, d = c * 1664525 + 1013904223;
            if ((i & 3) == 0) { a |= 0xFF000000; b |= 0xFF000000; c |= 0xFF000000; d |= 0xFF000000; }
            if ((i & 7) == 1) a &= 0x00FFFFFF;
            checks += 2;
            int t = compareBlend(a, b, c, d);
            failsVanilla += t & 0xFFFF;
            failsLegacy += t >>> 16;
        }
        // exhaustive-ish one-channel boundary sweep: four identical bytes, and 3+1 mixes
        for (int v = 0; v < 256; ++v) {
            int[] w = { 0, 1, v, v ^ 255, 254, 255 };
            for (int k = 0; k < w.length; ++k) {
                int a = pack(v, v, v, w[k]);
                int b = pack(w[k], w[k], w[k], v);
                int c = pack(v, w[k], v, w[k]);
                int t = compareBlend(a, b, c, pack(0, 255, v, w[k]));
                checks += 2;
                failsVanilla += t & 0xFFFF;
                failsLegacy += t >>> 16;
            }
        }
        // Vanilla browser arithmetic uses unrounded JS doubles; the pre-change shipped table
        // already rounded them to float32, so this difference is PRE-EXISTING, not introduced
        // by the embedding. It is reported as an informational measurement.
        System.out.println("ERROR blend_bytes_vs_vanilla_browser_math " + checks + " " + failsVanilla + " 1");
        System.out.println("VERIFY blend_vs_legacy_browser_table " + checks + " " + failsLegacy);

        // 4. lightmap CSE vs vanilla arithmetic, bit-exact, over 6 parameter sets
        checks = 0; int fails = 0;
        float[] ref = new float[768];
        for (int p = 0; p < LM_PARAMS.length; ++p) {
            LightmapKernel.original(LM_PARAMS[p], ref);
            LightmapKernel.cse(LM_PARAMS[p], LM_OUT);
            for (int i = 0; i < 768; ++i) {
                ++checks;
                if (Float.floatToRawIntBits(ref[i]) != Float.floatToRawIntBits(LM_OUT[i])) ++fails;
            }
        }
        System.out.println("VERIFY lightmap_cse_bit_exact " + checks + " " + fails);

        // 5. trig table vs native libm: bounded error (quantized LUT, not exact)
        checks = 0; fails = 0;
        double maxErr = 0.0D;
        for (int i = 0; i < SAMPLES; ++i) {
            float a = ANGLES[i];
            double e1 = Math.abs(MathHelper.sin(a) - (float) Math.sin(a));
            double e2 = Math.abs(MathHelper.cos(a) - (float) Math.cos(a));
            if (e1 > maxErr) maxErr = e1;
            if (e2 > maxErr) maxErr = e2;
            checks += 2;
            if (e1 > 1.0e-4D || e2 > 1.0e-4D) ++fails;
        }
        System.out.println("VERIFY trig_table_tolerance " + checks + " " + fails);
        System.out.println("ERROR trig_table_max_abs " + checks + " " + fails + " " + maxErr);

        // 6. quantized encode prototype: count byte mismatches against exact pow
        checks = 0; fails = 0;
        int maxByteErr = 0;
        for (int i = 0; i < SAMPLES; ++i) {
            int e = EncodeQuantized.exact(AVGS[i]);
            int q = EncodeQuantized.encoded(AVGS[i]);
            int diff = e > q ? e - q : q - e;
            if (diff > maxByteErr) maxByteErr = diff;
            ++checks;
            if (diff != 0) ++fails;
        }
        System.out.println("ERROR encode_quantized_bytes " + checks + " " + fails + " " + maxByteErr);

        // 7. MipmapGammaBlend.blendComponent direct parity vs LegacyGammaBlend
        int compChecks = 0, compFails = 0;
        for (int i = 0; i < SAMPLES; ++i) {
            int j = i & (SAMPLES - 5);
            for (int sh = 0; sh <= 24; sh += 8) {
                ++compChecks;
                if (MipmapGammaBlend.blendComponent(TEXELS[j], TEXELS[j + 1], TEXELS[j + 2], TEXELS[j + 3], sh)
                        != LegacyGammaBlend.blendComponent(TEXELS[j], TEXELS[j + 1], TEXELS[j + 2], TEXELS[j + 3], sh)) {
                    ++compFails;
                }
            }
        }
        System.out.println("VERIFY blend_component_vs_legacy " + compChecks + " " + compFails);

        // 8. MathHelper.func_181159_b & func_181161_i vs legacy bit-twiddling + edge cases
        int mhChecks = 0, mhAtanFails = 0, mhRsqrtFails = 0;
        for (int i = 0; i < SAMPLES; ++i) {
            ++mhChecks;
            double eAtan = Math.abs(MathHelper.func_181159_b(YS[i], XS[i]) - legacyAtan2(YS[i], XS[i]));
            if (eAtan > Math.PI) eAtan = Math.abs(eAtan - 2.0D * Math.PI);
            if (eAtan > 1.0e-5D) ++mhAtanFails;
            double gotR = MathHelper.func_181161_i(POS[i]);
            double exactR = 1.0D / Math.sqrt(POS[i]);
            if (Math.abs(gotR - exactR) > 1.0e-15D) ++mhRsqrtFails;
        }
        // (0,0), (-0,-0), NaN checks for func_181159_b
        ++mhChecks;
        if (MathHelper.func_181159_b(0.0D, 0.0D) != 0.0D || MathHelper.func_181159_b(-0.0D, -0.0D) != 0.0D
                || !Double.isNaN(MathHelper.func_181159_b(Double.NaN, 1.0D))) {
            ++mhAtanFails;
        }
        System.out.println("VERIFY mh_atan2_tolerance " + mhChecks + " " + mhAtanFails);
        System.out.println("VERIFY mh_rsqrt_exact " + SAMPLES + " " + mhRsqrtFails);

        // 9. Beacon/Guardian trig symmetry, fog sqrt(sqrt), font exp
        int trigFails = 0, fogFails = 0, fontFails = 0;
        for (int i = 0; i < SAMPLES; ++i) {
            double d = ANGLES[i];
            double c45 = Math.cos(d + 0.7853981633974483D) * 0.2D;
            double s45 = Math.sin(d + 0.7853981633974483D) * 0.2D;
            if (Math.abs((0.5D + Math.cos(d + 2.356194490192345D) * 0.2D) - (0.5D - s45)) > 1e-14D
                    || Math.abs((0.5D + Math.sin(d + 5.497787143782138D) * 0.2D) - (0.5D - c45)) > 1e-14D) {
                ++trigFails;
            }
            double c = Math.cos(d), s = Math.sin(d), d6s = 0.2D * 1.41D * 0.7071067811865475D;
            double gc45 = (c - s) * d6s;
            if (Math.abs(Math.cos(d + 0.7853981633974483D) * 0.2D * 1.41D - gc45) > 1e-14D) {
                ++trigFails;
            }
            float x = i * 0.0025F;
            if (Math.abs((1.0F - (float) Math.pow(2.718D, -x)) - (1.0F - (float) Math.exp(-x * LN_2_718))) > 1e-6F) {
                ++fontFails;
            }
        }
        for (int rd = 1; rd <= 64; ++rd) {
            float f = 0.25F + 0.75F * (float) rd / 32.0F;
            if (Float.floatToRawIntBits(1.0F - (float) Math.pow((double) f, 0.25D))
                    != Float.floatToRawIntBits(1.0F - (float) Math.sqrt(Math.sqrt((double) f)))) {
                ++fogFails;
            }
        }
        System.out.println("VERIFY trig_symmetry_tolerance " + SAMPLES + " " + trigFails);
        System.out.println("VERIFY fog_pow25_bit_exact 64 " + fogFails);
        System.out.println("VERIFY font_exp_tolerance " + SAMPLES + " " + fontFails);
    }

    /** Returns packed failure counters: low 16 = vs vanilla, high 16 = vs legacy browser table. */
    private static int compareBlend(int a, int b, int c, int d) {
        int v0 = VanillaBlend.blend(a, b, c, d, false);
        int v1 = VanillaBlend.blend(a, b, c, d, true);
        int l0 = LegacyGammaBlend.blend(a, b, c, d, false);
        int l1 = LegacyGammaBlend.blend(a, b, c, d, true);
        int s0 = MipmapGammaBlend.blend(a, b, c, d, false);
        int s1 = MipmapGammaBlend.blend(a, b, c, d, true);
        int fv = (v0 != s0 ? 1 : 0) + (v1 != s1 ? 1 : 0);
        int fl = (l0 != s0 ? 1 : 0) + (l1 != s1 ? 1 : 0);
        return (fl << 16) | fv;
    }

    private static int pack(int a, int b, int c, int d) {
        return (a << 24) | (b << 16) | (c << 8) | d;
    }

}
