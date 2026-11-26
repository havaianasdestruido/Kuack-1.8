package probe;

/**
 * Verbatim copy of Minecraft 1.8.8 TextureUtil.blendColors / blendColorComponent, i.e. the
 * "before" state of FINDINGS #11 and of the source-embedded LUT work. Used as the differential
 * reference by TeaVMBench (and by perfbench.VerifyGammaLut on the JVM).
 * This is benchmark-only code; it is never shipped.
 */
public final class VanillaBlend {

    private static final int[] mipmapBuffer = new int[4];

    private VanillaBlend() {
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

    public static int blendComponent(int a, int b, int c, int d, int shift) {
        float f = (float) Math.pow((double) ((float) (a >> shift & 255) / 255.0F), 2.2D);
        float f1 = (float) Math.pow((double) ((float) (b >> shift & 255) / 255.0F), 2.2D);
        float f2 = (float) Math.pow((double) ((float) (c >> shift & 255) / 255.0F), 2.2D);
        float f3 = (float) Math.pow((double) ((float) (d >> shift & 255) / 255.0F), 2.2D);
        float f4 = (float) Math.pow((double) (f + f1 + f2 + f3) * 0.25D, 0.45454545454545453D);
        return (int) ((double) f4 * 255.0D);
    }

    /** Exact decode expression, for the exhaustive table check. */
    public static float decode(int v) {
        return (float) Math.pow((double) ((float) v / 255.0F), 2.2D);
    }
}
