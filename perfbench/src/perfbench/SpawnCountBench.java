package perfbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * World.countEntities(Class) is called by SpawnerAnimals.findChunksForSpawning once per creature
 * type (4 types) every spawn tick. Each call walks the ENTIRE loadedEntityList and runs a costly
 * isAssignableFrom (plus an instanceof EntityLiving cast chain) against every entity.
 *
 * SpawnerAnimals then does that 4 times per tick, for every dimension's WorldServer.
 */
public class SpawnCountBench {
    static volatile long blackhole;

    static class Entity { }
    static class EntityLiving extends Entity {
        boolean isNoDespawnRequired() {
            return false;
        }
    }
    static class EntityMob extends EntityLiving { }
    static class EntityZombie extends EntityMob { }
    static class EntityCreeper extends EntityMob { }
    static class EntitySkeleton extends EntityMob { }
    static class EntityItem extends Entity { }

    static int countEntities(List<Entity> list, Class<?> type) {
        int i = 0;
        for (int j = 0, l = list.size(); j < l; ++j) {
            Entity e = list.get(j);
            if ((!(e instanceof EntityLiving) || !((EntityLiving) e).isNoDespawnRequired())
                    && type.isAssignableFrom(e.getClass())) {
                ++i;
            }
        }
        return i;
    }

    public static void main(String[] args) {
        int[] sizes = { 200, 500, 1000, 2000, 4000 };
        Class<?>[] types = { EntityMob.class, EntityLiving.class, EntityItem.class, Entity.class };

        BenchUtil.header("World.countEntities: 4 types per spawn tick over the loaded entity list");
        System.out.printf("  %7s %14s %14s%n", "entities", "per spawn tick", "per second (20tps)");
        for (int n : sizes) {
            List<Entity> list = new ArrayList<>(n);
            Random rng = new Random(3);
            for (int i = 0; i < n; ++i) {
                int k = rng.nextInt(10);
                list.add(k < 4 ? new EntityZombie() : k < 6 ? new EntityCreeper() : k < 8 ? new EntitySkeleton()
                        : new EntityItem());
            }
            int ticks = 2000;
            long[] s = new long[9];
            for (int w = 0; w < 300; ++w) {
                long acc = 0;
                for (Class<?> t : types) acc += countEntities(list, t);
                blackhole += acc;
            }
            for (int t = 0; t < 9; ++t) {
                long acc = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < ticks; ++i) {
                    for (int ty = 0; ty < types.length; ++ty) acc += countEntities(list, types[ty]);
                }
                s[t] = System.nanoTime() - t0;
                blackhole += acc;
            }
            double perTick = (double) BenchUtil.median(s) / ticks;
            System.out.printf("  %7d %11.3f ms %12.1f ms%n", n, perTick / 1e6, perTick / 1e6 * 20);
        }
        System.out.println("  blackhole " + blackhole);
    }
}
