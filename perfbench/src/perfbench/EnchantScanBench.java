package perfbench;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Models the per-tick inventory enchantment scan behind
 * EntityLivingBase.moveEntityWithHeading -> EnchantmentHelper.getDepthStriderModifier(this)
 * and decreaseAirSupply -> getRespiration(this).
 *
 * Vanilla cost per query: scan all 40 inventory slots; per non-null stack walk the
 * NBTTagList "ench" comparing "id" shorts, reaching each value through a HashMap<String,NBTBase>
 * plus a bounds check that allocates a new NBTTagCompound on out-of-range/mismatched lookups.
 */
public class EnchantScanBench {

    /** Stand-in for NBTTagCompound: a string-keyed map, like vanilla. */
    static final class FakeCompound {
        final Map<String, Object> tags = new HashMap<>(4);

        short getShort(String key) {
            Object o = tags.get(key);
            return o == null ? 0 : ((Short) o).shortValue();
        }
    }

    static final class Stack {
        final List<FakeCompound> ench; // "ench" NBTTagList contents

        Stack(List<FakeCompound> ench) {
            this.ench = ench;
        }
    }

    static List<FakeCompound> makeEnch(Random rng, int count) {
        List<FakeCompound> l = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            FakeCompound c = new FakeCompound();
            c.tags.put("id", Short.valueOf((short) (1 + rng.nextInt(35))));
            c.tags.put("lvl", Short.valueOf((short) (1 + rng.nextInt(4))));
            l.add(c);
        }
        return l;
    }

    static volatile long blackhole;

    /** Vanilla: for each slot, walk the tag list comparing ids. Allocates an empty compound on miss. */
    static int vanillaGetEnchantmentLevel(int enchID, Stack stem) {
        if (stem == null) {
            return 0;
        }
        List<FakeCompound> list = stem.ench;
        if (list == null) {
            return 0;
        }
        for (int i = 0; i < list.size(); ++i) {
            FakeCompound c = list.get(i); // NBTTagList.getCompoundTagAt(i)
            short id = c.getShort("id");
            short lvl = c.getShort("lvl");
            if (id == enchID) {
                return lvl;
            }
        }
        return 0;
    }

    static int vanillaMaxLevel(int enchID, Stack[] stacks) {
        if (stacks == null) {
            return 0;
        }
        int best = 0;
        for (int i = 0; i < stacks.length; ++i) {
            int l = vanillaGetEnchantmentLevel(enchID, stacks[i]);
            if (l > best) {
                best = l;
            }
        }
        return best;
    }

    static Stack[] makeInventory(Random rng) {
        Stack[] inv = new Stack[40];
        // realistic: 4 pieces of armor with a few enchants, a sword, a pickaxe
        for (int i = 0; i < 4; ++i) {
            inv[i] = new Stack(makeEnch(rng, 3));
        }
        inv[36] = new Stack(makeEnch(rng, 3));
        inv[37] = new Stack(makeEnch(rng, 2));
        return inv;
    }

    public static void main(String[] args) {
        Random rng = new Random(99);
        Stack[] inv = makeInventory(rng);

        BenchUtil.header("EnchantmentHelper.getDepthStriderModifier-equivalent, per call (40-slot inventory)");
        int batch = 200000;
        for (int i = 0; i < 5000; ++i) {
            blackhole += vanillaMaxLevel(7, inv);
        }
        long[] samples = new long[9];
        for (int s = 0; s < 9; ++s) {
            long t0 = System.nanoTime();
            int acc = 0;
            for (int i = 0; i < batch; ++i) {
                acc += vanillaMaxLevel(7, inv);
            }
            samples[s] = System.nanoTime() - t0;
            blackhole += acc;
        }
        long med = samples.clone()[4];
        java.util.Arrays.sort(samples);
        med = samples[4];
        System.out.printf("  per call: %,.1f ns%n", (double) med / batch);

        BenchUtil.header("Whole-server tick model: 60 entities in water x 20 ticks/s");
        int ents = 60;
        long t0 = System.nanoTime();
        int acc = 0;
        for (int tick = 0; tick < 20; ++tick) {
            for (int e = 0; e < ents; ++e) {
                acc += vanillaMaxLevel(7, inv);
                acc += vanillaMaxLevel(5, inv); // respiration, same structure
            }
        }
        long el = System.nanoTime() - t0;
        blackhole += acc;
        System.out.printf("  1 second of ticks: %,.2f ms  (%,.1f us per entity-tick pair)%n", el / 1e6,
                el / 1e3 / (ents * 20));
        System.out.printf("  blackhole %d%n", blackhole);
    }
}
