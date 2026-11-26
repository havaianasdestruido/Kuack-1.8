package probe;

/**
 * The PRE-CHANGE runtime-generated gamma table, kept as a differential reference.
 *
 * In TeaVM 0.9.2 the Java float division in the original static initializer is lowered as a JS
 * double division, so this class's table is f32(pow(v / 255.0, 2.2)) in the browser - 132 entries
 * differ by <= 1 ULP from the source-embedded constants in the shipped GeneratedGammaTables.
 * TeaVMBench compares whole-blend OUTPUT BYTES between the two to prove the embedding is a
 * browser no-op observably. Benchmark-only code; never shipped.
 */
public final class LegacyGammaBlend {

    private static final float[] GAMMA = new float[256];
    private static final int[] mipmapBuffer = new int[4];

    private LegacyGammaBlend() {
    }

    static {
        for (int i = 0; i < 256; ++i) {
            GAMMA[i] = (float) Math.pow((double) i / 255.0D, 2.2D);
        }
    }

    public static float gamma22(int byteValue) {
        return GAMMA[byteValue & 255];
    }

    public static int blend(int a, int b, int c, int d, boolean gammaCorrect) {
        if (!gammaCorrect) {
            return blendComponent(a, b, c, d, 24) << 24 | blendComponent(a, b, c, d, 16) << 16
                    | blendComponent(a, b, c, d, 8) << 8 | blendComponent(a, b, c, d, 0);
        }
        mipmapBuffer[0] = a;
        mipmapBuffer[1] = b;
        mipmapBuffer[2] = c;
        mipmapBuffer[3] = d;
        float f = 0.0F, f1 = 0.0F, f2 = 0.0F, f3 = 0.0F;
        for (int i = 0; i < 4; ++i) {
            if (mipmapBuffer[i] >> 24 != 0) {
                f += GAMMA[mipmapBuffer[i] >> 24 & 255];
                f1 += GAMMA[mipmapBuffer[i] >> 16 & 255];
                f2 += GAMMA[mipmapBuffer[i] >> 8 & 255];
                f3 += GAMMA[mipmapBuffer[i] >> 0 & 255];
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

    public static int blendComponent(int a, int b, int c, int d, int shift) {
        float f = GAMMA[a >> shift & 255];
        float f1 = GAMMA[b >> shift & 255];
        float f2 = GAMMA[c >> shift & 255];
        float f3 = GAMMA[d >> shift & 255];
        float f4 = (float) Math.pow((double) (f + f1 + f2 + f3) * 0.25D, 0.45454545454545453D);
        return (int) ((double) f4 * 255.0D);
    }
}
