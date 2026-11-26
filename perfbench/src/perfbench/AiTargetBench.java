package perfbench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Benchmarks AI nearest-target selection:
 *   vanilla: copy candidates into a List, Collections.sort() with a distance comparator, take index 0.
 *   optimized: single-pass argmin, take that element.
 *
 * Mirrors EntityAINearestAttackableTarget.shouldExecute() and EntityEnderman.AIFindPlayer.
 */
public class AiTargetBench {

    static final class Target {
        final double posX, posY, posZ;

        Target(double x, double y, double z) {
            this.posX = x;
            this.posY = y;
            this.posZ = z;
        }
    }

    static double distSq(Target a, Target b) {
        double dx = a.posX - b.posX;
        double dy = a.posY - b.posY;
        double dz = a.posZ - b.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Vanilla comparator: recomputes both distances on every comparison. */
    static final class DistanceComparator implements Comparator<Target> {
        final Target self;

        DistanceComparator(Target self) {
            this.self = self;
        }

        public int compare(Target a, Target b) {
            double d0 = distSq(self, a);
            double d1 = distSq(self, b);
            return d0 < d1 ? -1 : (d0 > d1 ? 1 : 0);
        }
    }

    static List<Target> makeCandidates(Random rng, int n) {
        List<Target> out = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            out.add(new Target((rng.nextDouble() - 0.5) * 48.0, (rng.nextDouble() - 0.5) * 8.0,
                    (rng.nextDouble() - 0.5) * 48.0));
        }
        return out;
    }

    static volatile long blackhole;

    /** Fixed batch size per sample keeps every sample the same length of work. */
    static long perCallNanos(int kind, List<Target> src, Target self, Comparator<Target> cmp, int batch) {
        long t0 = System.nanoTime();
        long acc = 0;
        if (kind == 0) {
            for (int i = 0; i < batch; ++i) {
                List<Target> list = new ArrayList<>(src);
                Collections.sort(list, cmp);
                if (!list.isEmpty()) {
                    acc += Double.doubleToLongBits(distSq(self, list.get(0)));
                }
            }
        } else {
            for (int i = 0; i < batch; ++i) {
                int best = -1;
                double bestD = Double.MAX_VALUE;
                for (int j = 0, l = src.size(); j < l; ++j) {
                    double d = distSq(self, src.get(j));
                    if (d < bestD) {
                        bestD = d;
                        best = j;
                    }
                }
                if (best >= 0) {
                    acc += Double.doubleToLongBits(bestD);
                }
            }
        }
        long el = System.nanoTime() - t0;
        blackhole += acc;
        return el;
    }

    static long median(long[] a) {
        long[] b = a.clone();
        java.util.Arrays.sort(b);
        return b[b.length / 2];
    }

    public static void main(String[] args) {
        Random rng = new Random(1234L);
        int[] sizes = { 4, 8, 16, 32, 64, 128, 256, 512 };
        BenchUtil.header("AI nearest-target selection: full sort vs single-pass argmin (per call)");
        System.out.printf("  %6s %14s %14s %10s%n", "n", "sort (us)", "argmin (us)", "speedup");
        for (int n : sizes) {
            List<Target> src = makeCandidates(rng, n);
            Target self = new Target(0, 0, 0);
            Comparator<Target> cmp = new DistanceComparator(self);
            int batch = Math.max(20, 20000 / Math.max(1, n) / 8);
            // warmup
            for (int i = 0; i < 500; ++i) {
                perCallNanos(0, src, self, cmp, batch);
                perCallNanos(1, src, self, cmp, batch);
            }
            long[] vs = new long[11];
            long[] os = new long[11];
            for (int s = 0; s < 11; ++s) {
                vs[s] = perCallNanos(0, src, self, cmp, batch) / batch;
                os[s] = perCallNanos(1, src, self, cmp, batch) / batch;
            }
            long v = median(vs);
            long o = median(os);
            System.out.printf("  %6d %14.4f %14.4f %9.2fx%n", n, v / 1e3, o / 1e3, (double) v / (double) o);
        }

        BenchUtil.header("Macro: N mobs x 24 candidates, one AI target scan per mob per AI tick");
        int cands = 24;
        List<Target> candList = makeCandidates(rng, cands);
        Target self = new Target(0, 0, 0);
        Comparator<Target> cmp = new DistanceComparator(self);
        for (int m : new int[] { 10, 25, 50, 100, 200 }) {
            int batch = Math.max(5, 4000 / m);
            for (int i = 0; i < 300; ++i) {
                long sink = 0;
                for (int k = 0; k < m; ++k) {
                    // reuse per-mob scan by scaling batch of single scans
                    sink += perCallNanos(0, candList, self, cmp, 1);
                }
                blackhole += sink;
            }
            long[] vs = new long[9];
            long[] os = new long[9];
            for (int s = 0; s < 9; ++s) {
                long t0 = System.nanoTime();
                for (int k = 0; k < m; ++k) {
                    perCallNanos(0, candList, self, cmp, batch);
                }
                vs[s] = (System.nanoTime() - t0) / (long) m / batch;
                t0 = System.nanoTime();
                for (int k = 0; k < m; ++k) {
                    perCallNanos(1, candList, self, cmp, batch);
                }
                os[s] = (System.nanoTime() - t0) / (long) m / batch;
            }
            long v = median(vs);
            long o = median(os);
            System.out.printf("  mobs=%-4d sort %10.3f us   argmin %10.3f us   speedup %.2fx%n", m, v / 1e3, o / 1e3,
                    (double) v / (double) o);
        }
        System.out.println("  (blackhole " + blackhole + ")");
    }
}
