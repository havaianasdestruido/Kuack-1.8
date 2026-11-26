package perfbench;

import java.io.File;
import java.util.Arrays;

import net.minecraft.client.renderer.texture.MipmapGammaBlend;

/**
 * JVM measurements for the source-embedded gamma tables (FINDINGS #12): class-initialization cost
 * (the pre-change runtime table ran 256 Math.pow calls at first use), steady-state lookup cost,
 * and artifact sizes. Run as its own JVM so both class initializers are genuinely cold:
 *   ./perfbench/run_bench.sh perfbench.EmbeddedLutBench
 *
 * Hot loops live in small methods so C1/C2 can compile them independently of the class-loading
 * and printf code in main (a single 600-byte main method repeatedly deoptimized on JDK 25).
 */
public final class EmbeddedLutBench {

    /** Benchmark-only replica of the PRE-CHANGE runtime-generated table. */
    public static final class RuntimeGammaTable {
        private static final float[] T = new float[256];
        static {
            for (int i = 0; i < 256; ++i) {
                T[i] = (float) Math.pow((double) ((float) i / 255.0F), 2.2D);
            }
        }
        public static float gamma22(int v) { return T[v & 255]; }
    }

    private static volatile double sink = 0.0D;

    private static double hotEmbedded(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) s += MipmapGammaBlend.gamma22(i);
        return s;
    }

    private static double hotRuntime(int n) {
        double s = 0.0D;
        for (int i = 0; i < n; ++i) s += RuntimeGammaTable.gamma22(i);
        return s;
    }

    private static double hotPow(int batches) {
        float s = 0.0F;
        for (int j = 0; j < batches; ++j) {
            for (int i = 0; i < 256; ++i) s += (float) Math.pow((double) ((float) i / 255.0F), 2.2D);
        }
        return s;
    }

    private static double median(double[] a) {
        double[] s = a.clone();
        Arrays.sort(s);
        return s[s.length / 2];
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== EmbeddedLutBench: startup + steady-state (HotSpot) ===");
        ClassLoader cl = EmbeddedLutBench.class.getClassLoader();
        // In the real game EagRuntime is loaded long before any texture code; pre-load it so
        // the measured clinit is the table's own, not first-time platform class loading.
        Class.forName("net.lax1dude.eaglercraft.v1_8.EagRuntime", true, cl);
        Class.forName("net.lax1dude.eaglercraft.v1_8.internal.EnumPlatformType", true, cl);

        long t0 = System.nanoTime();
        Class.forName("perfbench.EmbeddedLutBench$RuntimeGammaTable", true, cl);
        long t1 = System.nanoTime();
        System.out.printf("  cold clinit, runtime-generated table (256 Math.pow): %8.2f us%n", (t1 - t0) / 1000.0);

        long t2 = System.nanoTime();
        Class.forName("net.minecraft.client.renderer.texture.MipmapGammaBlend", true, cl);
        long t3 = System.nanoTime();
        System.out.printf("  cold clinit, 2 x 256 embedded constants:            %8.2f us%n", (t3 - t2) / 1000.0);
        System.out.printf("  startup saving:                                      %8.2f us%n",
                (t1 - t0 - (t3 - t2)) / 1000.0);

        // steady-state decode lookup (same code path in production)
        int n = 20000000;
        double[] embedded = new double[7];
        double[] runtime = new double[7];
        for (int rep = 0; rep < 7; ++rep) {
            long a = System.nanoTime();
            sink += hotEmbedded(n);
            embedded[rep] = (System.nanoTime() - a) / (double) n;
            long b = System.nanoTime();
            sink += hotRuntime(n);
            runtime[rep] = (System.nanoTime() - b) / (double) n;
        }
        System.out.printf("  decode lookup, embedded: %.3f ns/op   runtime-generated: %.3f ns/op%n",
                median(embedded), median(runtime));

        File root = new File("perfbench/build/main/net/minecraft/client/renderer/texture");
        for (String name : new String[] { "GeneratedGammaTables.class", "MipmapGammaBlend.class" }) {
            File f = new File(root, name);
            System.out.printf("  %-32s %d bytes%n", name, f.exists() ? f.length() : -1);
        }
        File src = new File("sources/main/java/net/minecraft/client/renderer/texture/GeneratedGammaTables.java");
        System.out.printf("  %-32s %d bytes%n", "GeneratedGammaTables.java", src.length());
        // Isolate the arithmetic that the embedded constants remove (steady-state, warm).
        hotPow(1000);
        double[] powCost = new double[7];
        for (int rep = 0; rep < 7; ++rep) {
            long a = System.nanoTime();
            sink += hotPow(20000);
            powCost[rep] = (System.nanoTime() - a) / 20000.0 / 1000.0; // us per 256 calls
        }
        System.out.printf("  256 Math.pow decode calls (warm): %.2f us - the work removed from startup%n", median(powCost));
        System.out.println("  (TeaVM JS size / parse cost is measured by perfbench/teavm/run_startup.js)");
    }
}
