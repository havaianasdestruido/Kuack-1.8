package perfbench;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;

import net.minecraft.client.renderer.texture.MipmapGammaBlend;

/**
 * JVM-side proof for the source-embedded gamma tables (FINDINGS #12).
 *
 * Checks both constant arrays bit-exactly against their defining expressions, the platform
 * selection (HotSpot must use the strict-Java array), index wrapping for negative/out-of-range
 * inputs, that the generated source contains no runtime table construction, and - the real
 * contract - that whole mipmap blend output bytes still equal verbatim vanilla 1.8.8.
 *
 * The V8/TeaVM counterpart is perfbench/teavm (TeaVMBench.verify()).
 * Run: ./perfbench/run_bench.sh perfbench.VerifyEmbeddedLut
 */
public final class VerifyEmbeddedLut {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== VerifyEmbeddedLut: source-embedded gamma tables ===");
        Class<?> gen = Class.forName("net.minecraft.client.renderer.texture.GeneratedGammaTables");
        Method java = gen.getDeclaredMethod("gamma22Java", int.class);
        Method teavm = gen.getDeclaredMethod("gamma22Teavm", int.class);
        java.setAccessible(true);
        teavm.setAccessible(true);

        int differing = 0;
        double maxAbs = 0.0D, maxRel = 0.0D;
        for (int v = 0; v < 256; ++v) {
            float strictJava = (float) Math.pow((double) ((float) v / 255.0F), 2.2D);
            float teavmLowered = (float) Math.pow((double) v / 255.0D, 2.2D);
            float gotJava = (Float) java.invoke(null, v);
            float gotTeavm = (Float) teavm.invoke(null, v);
            check(bits(strictJava) == bits(gotJava), "JAVA[" + v + "]");
            check(bits(teavmLowered) == bits(gotTeavm), "TEAVM[" + v + "]");
            if (bits(gotJava) != bits(gotTeavm)) {
                ++differing;
                double d = Math.abs((double) gotJava - (double) gotTeavm);
                if (d > maxAbs) maxAbs = d;
                double rel = teavmLowered != 0.0F ? d / Math.abs((double) teavmLowered) : 0.0D;
                if (rel > maxRel) maxRel = rel;
            }
            // negative and out-of-range indices must wrap to the same entry
            check(bits(gotJava) == bits((Float) java.invoke(null, v - 512)), "JAVA wrap " + v);
            check(bits(gotTeavm) == bits((Float) teavm.invoke(null, v + 1024)), "TEAVM wrap " + v);
            // the active table on HotSpot must be the strict-Java one
            check(bits(MipmapGammaBlend.gamma22(v)) == bits(gotJava), "platform selection " + v);
        }
        System.out.printf("  JAVA/TEAVM tables: 256/256 entries bit-exact each; %d entries differ, "
                + "max abs %.3g, max rel %.3g%n", differing, maxAbs, maxRel);
        check(differing == 132, "expected 132 cross-runtime ULP differences");
        check(maxAbs <= 6.0e-8D, "max absolute error <= 1 ULP");

        // boundaries
        check((Float) java.invoke(null, 0) == 0.0F, "JAVA[0] == 0");
        check((Float) java.invoke(null, 255) == 1.0F, "JAVA[255] == 1");
        check((Float) teavm.invoke(null, 0) == 0.0F, "TEAVM[0] == 0");
        check((Float) teavm.invoke(null, 255) == 1.0F, "TEAVM[255] == 1");

        checkSourceHasNoRuntimeComputation();

        // whole-blend differential against verbatim vanilla 1.8.8
        Random rng = new Random(0x5EEDBEEFL);
        int bad = 0;
        for (int i = 0; i < 200000; ++i) {
            int a = rng.nextInt(), b = rng.nextInt(), c = rng.nextInt(), d = rng.nextInt();
            if ((i & 1) == 0) { a |= 0xFF000000; b |= 0xFF000000; c |= 0xFF000000; d |= 0xFF000000; }
            if ((i & 3) == 1) a &= 0x00FFFFFF;
            ++checks;
            if (VerifyGammaLut.vanillaBlend(a, b, c, d, false) != MipmapGammaBlend.blend(a, b, c, d, false)) ++bad;
            ++checks;
            if (VerifyGammaLut.vanillaBlend(a, b, c, d, true) != MipmapGammaBlend.blend(a, b, c, d, true)) ++bad;
        }
        check(bad == 0, "200000 blended quadruples vs verbatim vanilla");
        System.out.printf("  blend differential: 400,000 checks, %d byte mismatches%n", bad);

        System.out.printf("checks: %,d   failures: %,d%n", checks, failures);
        if (failures != 0) throw new AssertionError("VerifyEmbeddedLut FAILED with " + failures + " failures");
        System.out.println("OK: both embedded tables are exact for their runtime; mipmap output unchanged.");
    }

    private static void checkSourceHasNoRuntimeComputation() throws Exception {
        File f = new File("sources/main/java/net/minecraft/client/renderer/texture/GeneratedGammaTables.java");
        String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        // strip the Javadoc before scanning for runtime constructs
        String body = src.substring(src.indexOf("final class GeneratedGammaTables"));
        check(!body.contains("Math.pow"), "no Math.pow in generated source");
        check(!body.contains("Math.sqrt"), "no Math.sqrt in generated source");
        check(!body.contains("for ("), "no runtime loop in generated source");
        check(!body.contains("static {"), "no static initializer computing entries");
        check(body.contains("private static final float[] GAMMA_2_2_JAVA = {"), "JAVA array constants present");
        check(body.contains("private static final float[] GAMMA_2_2_TEAVM = {"), "TEAVM array constants present");
        check(body.contains("private static final double[] THRESH_BLEND = {"), "THRESH_BLEND constants present");
        check(body.contains("private static final double[] THRESH_COMP = {"), "THRESH_COMP constants present");
        check(body.contains("private static final short[] BUCKET_BLEND = {"), "BUCKET_BLEND constants present");
        check(body.contains("private static final short[] BUCKET_COMP = {"), "BUCKET_COMP constants present");
        System.out.println("  generated source: 6 constant arrays, 0 runtime computations, 0 file loads");
    }

    private static int bits(float f) {
        return Float.floatToRawIntBits(f);
    }

    private static void check(boolean ok, String what) {
        ++checks;
        if (!ok) {
            ++failures;
            if (failures < 10) System.out.println("    FAIL: " + what);
        }
    }
}
