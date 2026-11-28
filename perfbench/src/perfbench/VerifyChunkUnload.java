package perfbench;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Differential test for ChunkProviderServer.loadedChunks becoming a LinkedHashSet:
 * same contents, same iteration order, and the same result for remove()/add() over the operation
 * sequences the chunk provider performs (add on load, remove on unload, full iteration/snapshot).
 */
public class VerifyChunkUnload {

    static final class Chunk {
        final int x, z;

        Chunk(int x, int z) {
            this.x = x;
            this.z = z;
        }

        // deliberately no equals/hashCode: identity semantics, exactly like the real Chunk
        public String toString() {
            return x + "," + z;
        }
    }

    public static void main(String[] args) {
        long checks = 0, failures = 0;
        Random rng = new Random(424242L);

        for (int trial = 0; trial < 4000; ++trial) {
            LinkedList<Chunk> list = new LinkedList<>();
            LinkedHashSet<Chunk> set = new LinkedHashSet<>();
            List<Chunk> pool = new ArrayList<>();
            for (int i = 0; i < 64; ++i) {
                pool.add(new Chunk(i, -i));
            }

            int ops = 1 + rng.nextInt(120);
            for (int op = 0; op < ops; ++op) {
                int kind = rng.nextInt(5);
                Chunk c = pool.get(rng.nextInt(pool.size()));
                switch (kind) {
                    case 0: // load: only added if not already present (id2ChunkMap guards this)
                        if (!list.contains(c)) {
                            list.add(c);
                            set.add(c);
                        }
                        break;
                    case 1: // unload
                        ++checks;
                        if (list.remove(c) != set.remove(c)) {
                            ++failures;
                            System.out.println("MISMATCH remove result for " + c);
                        }
                        break;
                    case 2: // unloadAllChunks-style full iteration
                        ++checks;
                        List<Chunk> a = new ArrayList<>(list);
                        List<Chunk> b = new ArrayList<>(set);
                        if (!a.equals(b)) {
                            ++failures;
                            System.out.println("MISMATCH snapshot order at trial " + trial);
                        }
                        break;
                    case 3: // unloadAllChunks-style copy
                        ++checks;
                        if (list.size() != set.size()) {
                            ++failures;
                            System.out.println("MISMATCH size");
                        }
                        break;
                    default: // random membership probe
                        ++checks;
                        if (list.contains(c) != set.contains(c)) {
                            ++failures;
                            System.out.println("MISMATCH contains for " + c);
                        }
                        break;
                }
            }
            ++checks;
            Collection<Chunk> a = list, b = set;
            if (!new ArrayList<>(a).equals(new ArrayList<>(b))) {
                ++failures;
                System.out.println("MISMATCH final contents/order at trial " + trial);
            }
        }

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures != 0) {
            throw new AssertionError("ChunkProviderServer.loadedChunks behaviour changed");
        }
    }
}
