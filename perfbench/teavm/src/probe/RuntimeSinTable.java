package probe;

/** The engine's existing sine table, isolated for the "runtime-generated" experiment. */
public final class RuntimeSinTable {
    private static final float[] SIN = new float[65536];

    private RuntimeSinTable() {
    }

    public static float sin(float angle) {
        return SIN[(int) (angle * 10430.378F) & 0xFFFF];
    }

    static {
        for (int i = 0; i < 65536; ++i) {
            SIN[i] = (float) Math.sin((double) i * 3.141592653589793D * 2.0D / 65536.0D);
        }
    }
}
