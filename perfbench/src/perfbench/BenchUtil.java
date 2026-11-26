package perfbench;

import java.util.List;
import java.util.Random;

/** Shared timing helpers: warmup, repeated trials, median-of-N reporting. */
public final class BenchUtil {
    public static final Random RNG = new Random(1234L);

    private BenchUtil() {
    }

    public interface Body {
        long run();
    }

    /** Runs body warmup+trials, returns median nanos. */
    public static long median(String label, int warmup, int trials, Body body) {
        for (int i = 0; i < warmup; ++i) {
            body.run();
        }
        long[] ns = new long[trials];
        long checksum = 0;
        for (int i = 0; i < trials; ++i) {
            long t0 = System.nanoTime();
            checksum ^= body.run();
            ns[i] = System.nanoTime() - t0;
        }
        java.util.Arrays.sort(ns);
        long med = ns[trials / 2];
        System.out.printf("  %-46s median %,12.3f ms   (checksum %d)%n", label, med / 1e6, checksum);
        return med;
    }

    /** Median of a sample array (does not mutate the input). */
    public static long median(long[] samples) {
        long[] copy = samples.clone();
        java.util.Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    public static void header(String s) {
        System.out.println("=== " + s + " ===");
    }

    public static void row(String label, double value, String unit) {
        System.out.printf("  %-46s %,14.3f %s%n", label, value, unit);
    }
}
