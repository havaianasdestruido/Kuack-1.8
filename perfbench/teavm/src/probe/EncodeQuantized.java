package probe;

/**
 * EXPERIMENT ONLY (deliberately NOT production code): a 4096-bucket quantized step table for
 * Math.pow(avg, 1/2.2) * 255, the remaining libm call in the mipmap filter. Built with Math.pow
 * in a static initializer - which is exactly why it is a benchmark prototype and not a shipped
 * table (production LUTs must be source-embedded constants). TeaVMBench measures both its speed
 * and its byte errors against the exact expression; see CROSS_RUNTIME.md for the rejection.
 */
public final class EncodeQuantized {
    private static final int[] ENC = new int[4096];

    private EncodeQuantized() {
    }

    public static int encoded(double avg) {
        int g = (int) (avg * 4096.0D);
        if (g < 0) g = 0;
        if (g > 4095) g = 4095;
        return ENC[g];
    }

    public static int exact(double avg) {
        return (int) (Math.pow(avg, 0.45454545454545453D) * 255.0D);
    }

    static {
        for (int g = 0; g < 4096; ++g) {
            ENC[g] = (int) (Math.pow((g + 0.5D) / 4096.0D, 0.45454545454545453D) * 255.0D);
        }
    }
}
