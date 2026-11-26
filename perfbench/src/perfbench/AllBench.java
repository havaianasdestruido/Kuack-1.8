package perfbench;

/** Default target of ./perfbench/run_bench.sh: verification first, then measurements. */
public final class AllBench {
    public static void main(String[] args) throws Exception {
        String[] targets = {
            "perfbench.EmbeddedLutBench", "perfbench.VerifyGammaLut",
            "perfbench.VerifyEmbeddedLut", "perfbench.VerifyMatrixRotate",
            "perfbench.LightmapBench", "perfbench.TextureGammaBench",
            "perfbench.MatrixRotateBench"
        };
        for (String t : targets) {
            System.out.println();
            System.out.println("######## " + t + " ########");
            Class.forName(t).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        }
        System.out.println();
        System.out.println("AllBench: all targets completed.");
    }
}
