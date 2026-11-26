package perfbench;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import com.google.common.collect.Sets;

/**
 * Verifies the two PlayerManager / EntityPlayerMP changes:
 *
 *  A) filterChunkLoadQueue now tests membership against a HashSet snapshot instead of scanning an
 *     ArrayList snapshot. The resulting queue must be identical (same elements, same order).
 *
 *  B) EntityPlayerMP.loadedChunks is a LinkedHashSet instead of a LinkedList: same distinct
 *     contents, same insertion-order iteration, O(1) contains.
 */
public class VerifyChunkQueue {

    static final int[] DIRS = { 0, -1, -1, 0, 0, 1, 1, 0 };

    static final class Pair {
        final int x, z;

        Pair(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public int hashCode() {
            int i = 1664525 * this.x + 1013904223;
            int j = 1664525 * (this.z ^ -559038737) + 1013904223;
            return i ^ j;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pair)) return false;
            Pair p = (Pair) o;
            return this.x == p.x && this.z == p.z;
        }

        public String toString() {
            return x + "," + z;
        }
    }

    /**
     * The shared spiral walk. `membership` answers "was this chunk already queued?" and `out`
     * receives the rebuilt queue. Both the vanilla and the optimised version call this; only the
     * membership structure differs.
     */
    static void walk(java.util.Collection<Pair> membership, List<Pair> out, int viewRadius, int cx, int cz) {
        out.clear();
        int i = 0;
        int j = viewRadius;
        int i1 = 0;
        int j1 = 0;
        Pair here = new Pair(cx, cz);
        if (membership.contains(here)) {
            out.add(here);
        }
        for (int k1 = 1; k1 <= j * 2; ++k1) {
            for (int l1 = 0; l1 < 2; ++l1) {
                int d = (i++ % 4) * 2;
                int dx = DIRS[d], dz = DIRS[d + 1];
                for (int i2 = 0; i2 < k1; ++i2) {
                    i1 += dx;
                    j1 += dz;
                    Pair p = new Pair(cx + i1, cz + j1);
                    if (membership.contains(p)) {
                        out.add(p);
                    }
                }
            }
        }
        i = i % 4;
        for (int j2 = 0; j2 < j * 2; ++j2) {
            i1 += DIRS[i * 2];
            j1 += DIRS[i * 2 + 1];
            Pair p = new Pair(cx + i1, cz + j1);
            if (membership.contains(p)) {
                out.add(p);
            }
        }
    }

    /** Vanilla: LinkedList-backed queue, ArrayList snapshot, then walk with linear contains. */
    static List<Pair> vanilla(List<Pair> queue0, int radius, int cx, int cz) {
        LinkedList<Pair> queue = new LinkedList<>(queue0);
        ArrayList<Pair> snapshot = new ArrayList<>(queue);
        List<Pair> out = new LinkedList<>();
        walk(snapshot, out, radius, cx, cz);
        return out;
    }

    /** Optimised: LinkedHashSet-backed queue, HashSet snapshot, then walk with hashed contains. */
    static List<Pair> optimized(List<Pair> queue0, int radius, int cx, int cz) {
        LinkedHashSet<Pair> queue = Sets.newLinkedHashSet();
        queue.addAll(queue0);
        java.util.Set<Pair> snapshot = Sets.newHashSet(queue);
        List<Pair> out = new ArrayList<>();
        walk(snapshot, out, radius, cx, cz);
        return out;
    }

    public static void main(String[] args) {
        long checks = 0, failures = 0;
        java.util.Random rng = new java.util.Random(12345L);

        // 1) full queues (the steady state: every chunk in view is loaded)
        for (int radius = 1; radius <= 32; ++radius) {
            for (int cx = -3; cx <= 3; ++cx) {
                for (int cz = -3; cz <= 3; ++cz) {
                    List<Pair> src = new ArrayList<>();
                    for (int dx = -radius; dx <= radius; ++dx) {
                        for (int dz = -radius; dz <= radius; ++dz) {
                            src.add(new Pair(cx + dx, cz + dz));
                        }
                    }
                    ++checks;
                    if (!vanilla(src, radius, cx, cz).equals(optimized(src, radius, cx, cz))) {
                        ++failures;
                        System.out.println("MISMATCH full r=" + radius + " c=" + cx + "," + cz);
                    }
                }
            }
        }

        // 2) partial queues, shuffled order, entries far outside the view area, and duplicates
        for (int radius = 1; radius <= 32; ++radius) {
            for (int trial = 0; trial < 20; ++trial) {
                List<Pair> src = new ArrayList<>();
                int n = rng.nextInt((2 * radius + 1) * (2 * radius + 1) * 2 + 1);
                for (int k = 0; k < n; ++k) {
                    src.add(new Pair(rng.nextInt(2 * radius * 3 + 1) - radius * 3,
                            rng.nextInt(2 * radius * 3 + 1) - radius * 3));
                }
                ++checks;
                if (!vanilla(src, radius, 0, 0).equals(optimized(src, radius, 0, 0))) {
                    ++failures;
                    System.out.println("MISMATCH partial r=" + radius + " trial=" + trial);
                }
            }
        }

        // 3) empty queue
        ++checks;
        if (!vanilla(new ArrayList<Pair>(), 10, 0, 0).equals(optimized(new ArrayList<Pair>(), 10, 0, 0))) {
            ++failures;
            System.out.println("MISMATCH empty queue");
        }

        // 4) LinkedHashSet vs LinkedList container semantics for the chunk queue
        LinkedList<Pair> list = new LinkedList<>();
        LinkedHashSet<Pair> set = Sets.newLinkedHashSet();
        for (int trial = 0; trial < 5000; ++trial) {
            int op = rng.nextInt(4);
            Pair p = new Pair(rng.nextInt(40) - 20, rng.nextInt(40) - 20);
            switch (op) {
                case 0:
                    // both add sites are duplicate-guarded in production; verify that guard makes
                    // the two containers agree even if add is attempted twice
                    if (!list.contains(p)) list.add(p);
                    if (!set.contains(p)) set.add(p);
                    break;
                case 1:
                    list.remove(p);
                    set.remove(p);
                    break;
                case 2:
                    list.clear();
                    set.clear();
                    break;
                default:
                    ++checks;
                    boolean la = list.contains(p);
                    boolean sa = set.contains(p);
                    if (la != sa) {
                        ++failures;
                        System.out.println("MISMATCH contains(" + p + ") list=" + la + " set=" + sa);
                    }
                    if (list.size() != set.size()) {
                        ++failures;
                        System.out.println("MISMATCH size " + list.size() + " vs " + set.size());
                    }
                    if (!new ArrayList<>(list).equals(new ArrayList<>(set))) {
                        ++failures;
                        System.out.println("MISMATCH iteration order/content");
                    }
                    break;
            }
        }
        // final content comparison
        ++checks;
        if (!new ArrayList<>(list).equals(new ArrayList<>(set))) {
            ++failures;
            System.out.println("MISMATCH final contents");
        }

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures != 0) {
            throw new AssertionError("loadedChunks / filterChunkLoadQueue behaviour changed");
        }
    }
}
