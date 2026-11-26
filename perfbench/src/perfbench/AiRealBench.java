package perfbench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;

/** Times the real List.sort path vs the real findNearest() on actual Entity objects. */
public class AiRealBench {

    static class StubEntity extends Entity {
        StubEntity(double x, double y, double z) {
            super(null);
            this.setPosition(x, y, z);
        }

        protected void entityInit() {
        }

        protected void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound t) {
        }

        protected void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound t) {
        }
    }

    static volatile long blackhole;

    public static void main(String[] args) {
        Random rng = new Random(7);
        int[] sizes = { 4, 8, 16, 32, 64, 128 };
        BenchUtil.header("Real EntityAINearestAttackableTarget: sort vs findNearest (per AI scan)");
        System.out.printf("  %6s %16s %16s %10s%n", "n", "sort (us)", "findNearest (us)", "speedup");
        for (int n : sizes) {
            List<Entity> src = new ArrayList<>();
            for (int i = 0; i < n; ++i) {
                src.add(new StubEntity((rng.nextDouble() - 0.5) * 48, (rng.nextDouble() - 0.5) * 8,
                        (rng.nextDouble() - 0.5) * 48));
            }
            Entity ref = new StubEntity(0, 0, 0);
            EntityAINearestAttackableTarget.Sorter sorter = new EntityAINearestAttackableTarget.Sorter(ref);
            int batch = Math.max(20, 20000 / n);

            long[] vs = new long[9];
            long[] os = new long[9];
            for (int w = 0; w < 300; ++w) {
                runSort(src, sorter, batch);
                runMin(src, ref, batch);
            }
            for (int s = 0; s < 9; ++s) {
                vs[s] = runSort(src, sorter, batch) / batch;
                os[s] = runMin(src, ref, batch) / batch;
            }
            long v = BenchUtil.median(vs);
            long o = BenchUtil.median(os);
            System.out.printf("  %6d %16.4f %16.4f %9.2fx%n", n, v / 1e3, o / 1e3, (double) v / (double) o);
        }
        System.out.println("  blackhole " + blackhole);
    }

    static long runSort(List<Entity> src, EntityAINearestAttackableTarget.Sorter sorter, int batch) {
        long t0 = System.nanoTime();
        long acc = 0;
        for (int i = 0; i < batch; ++i) {
            List<Entity> list = new ArrayList<>(src);
            Collections.sort(list, sorter);
            acc += list.get(0).getEntityId();
        }
        long el = System.nanoTime() - t0;
        blackhole += acc;
        return el;
    }

    static long runMin(List<Entity> src, Entity ref, int batch) {
        long t0 = System.nanoTime();
        long acc = 0;
        for (int i = 0; i < batch; ++i) {
            List<Entity> list = new ArrayList<>(src);
            acc += EntityAINearestAttackableTarget.findNearest(list, ref).getEntityId();
        }
        long el = System.nanoTime() - t0;
        blackhole += acc;
        return el;
    }
}
