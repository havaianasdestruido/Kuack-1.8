package probe;

/**
 * Startup-only entry point: the PRE-CHANGE runtime-generated gamma table, compiled by TeaVM.
 * TeaVM lowers the float division to a JS double (no Math.fround), then Float32Array assignment
 * rounds to float32 - the browser semantics measured by CROSS_RUNTIME.md.
 */
public final class GammaOnlyRuntime {
    private static final float[] GAMMA = new float[256];

    static {
        for (int i = 0; i < 256; ++i) {
            GAMMA[i] = (float) Math.pow((float) i / 255.0F, 2.2D);
        }
    }

    private GammaOnlyRuntime() {
    }

    public static void main(String[] args) {
        double s = 0.0D;
        for (int i = 0; i < 256; ++i) s += (double) GAMMA[i];
        System.out.println("CHECKSUM " + s);
    }
}
