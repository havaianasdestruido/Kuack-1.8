package perfbench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.Entity;

/**
 * Verifies EntityAINearestAttackableTarget.findNearest() is exactly equivalent to
 * Collections.sort(list, Sorter) + get(0), including tie-breaking on equal distances.
 */
public class VerifyAI {
    public static void main(String[] args) {
        Random rng = new Random(42);
        int cases = 20000;
        int failures = 0;
        int ties = 0;
        for (int c = 0; c < cases; ++c) {
            int n = 1 + rng.nextInt(40);
            final List<Entity> list = new ArrayList<>();
            // small coordinate range so exact distance ties actually occur
            int range = 1 + rng.nextInt(4);
            for (int i = 0; i < n; ++i) {
                Entity e = new StubEntity(rng.nextInt(range), rng.nextInt(range), rng.nextInt(range));
                list.add(e);
            }
            Entity reference = new StubEntity(0, 0, 0);

            List<Entity> sorted = new ArrayList<>(list);
            Collections.sort(sorted, new net.minecraft.entity.ai.EntityAINearestAttackableTarget.Sorter(reference));
            Entity expected = sorted.get(0);

            Entity actual = net.minecraft.entity.ai.EntityAINearestAttackableTarget.findNearest(list, reference);
            if (expected != actual) {
                ++failures;
                if (failures < 5) {
                    System.out.println("MISMATCH case " + c + " n=" + n + " expected idx=" + list.indexOf(expected)
                            + " actual idx=" + list.indexOf(actual) + " distExp=" + reference.getDistanceSqToEntity(expected)
                            + " distAct=" + reference.getDistanceSqToEntity(actual));
                }
            }
            // count tie situations for coverage confidence
            for (Entity e : list) {
                if (e != expected && reference.getDistanceSqToEntity(e) == reference.getDistanceSqToEntity(expected)) {
                    ++ties;
                    break;
                }
            }
        }
        System.out.println("cases=" + cases + " mismatches=" + failures + " casesWithTies=" + ties);
        if (failures != 0) {
            System.exit(1);
        }
    }

    /** Minimal Entity subclass: only positions matter for distance + comparator. */
    static class StubEntity extends Entity {
        StubEntity(double x, double y, double z) {
            super(null);
            this.setPosition(x, y, z);
        }

        protected void entityInit() {
        }

        protected void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound tagCompund) {
        }

        protected void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound tagCompound) {
        }
    }
}
