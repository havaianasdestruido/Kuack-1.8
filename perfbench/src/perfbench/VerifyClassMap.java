package perfbench;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.util.ClassInheritanceMultiMap;

/**
 * Differential test for ClassInheritanceMultiMap's memoised "which keys match this concrete class".
 *
 * A reference implementation of the ORIGINAL algorithm is kept here (scan every known key with
 * isAssignableFrom on every add/remove) and run side by side with the patched real class, over
 * randomised sequences of add/remove/getByClass/contains and interleaved key registrations.
 */
public class VerifyClassMap {

    static final Class<?>[] QUERY_KEYS = { Entity.class, EntityLivingBase.class, EntityLiving.class,
            EntityZombie.class, EntityCreeper.class, EntityPig.class, EntityItem.class };

    /** Verbatim model of the original ClassInheritanceMultiMap. */
    static final class Ref<T> {
        final Class<T> baseClass;
        final List<Class<?>> knownKeys = new ArrayList<>();
        final java.util.Map<Class<?>, List<T>> map = new java.util.HashMap<>();
        final List<T> all = new ArrayList<>();

        Ref(Class<T> baseClass) {
            this.baseClass = baseClass;
            this.knownKeys.add(baseClass);
            this.map.put(baseClass, all);
        }

        void createLookup(Class<?> clazz) {
            // The original is only ever called with a key it does not know yet, so the key's list
            // either does not exist or was created by add() already holding exactly the matching
            // entities. Re-scanning would double-insert, so only backfill if the list is absent.
            if (this.map.get(clazz) == null) {
                for (int i = 0, l = this.all.size(); i < l; ++i) {
                    T o = this.all.get(i);
                    if (clazz.isAssignableFrom(o.getClass())) {
                        put(o, clazz);
                    }
                }
            }
            this.knownKeys.add(clazz);
        }

        private void put(T o, Class<?> c) {
            List<T> l = this.map.get(c);
            if (l == null) {
                l = new ArrayList<>();
                l.add(o);
                this.map.put(c, l);
            } else {
                l.add(o);
            }
        }

        boolean add(T o) {
            // NOTE: map.get(baseClass) IS `all`, so the base-class branch of this loop is what
            // inserts into `all`; there must be no separate this.all.add(o) here.
            for (Class<?> c : this.knownKeys) {
                if (c.isAssignableFrom(o.getClass())) {
                    put(o, c);
                }
            }
            return true;
        }

        boolean remove(Object o) {
            boolean flag = false;
            for (Class<?> c : this.knownKeys) {
                if (c.isAssignableFrom(o.getClass())) {
                    List<T> l = this.map.get(c);
                    if (l != null && l.remove(o)) {
                        flag = true;
                    }
                }
            }
            return flag;
        }

        List<T> byClass(Class<?> c) {
            if (!this.baseClass.isAssignableFrom(c)) {
                throw new IllegalArgumentException("bad class");
            }
            if (!this.knownKeys.contains(c)) {
                this.createLookup(c);
            }
            List<T> l = this.map.get(c);
            List<T> out = new ArrayList<>();
            if (l != null) {
                for (T o : l) {
                    if (c.isInstance(o)) {
                        out.add(o);
                    }
                }
            }
            return out;
        }

        String dump() {
            StringBuilder sb = new StringBuilder();
            sb.append("all=").append(names(this.all));
            List<Class<?>> keys = new ArrayList<>();
            for (Class<?> c : QUERY_KEYS) {
                if (this.knownKeys.contains(c)) {
                    keys.add(c);
                }
            }
            keys.sort((a, b) -> a.getName().compareTo(b.getName()));
            for (Class<?> c : keys) {
                List<T> l = this.map.get(c);
                List<T> filtered = new ArrayList<>();
                if (l != null) {
                    for (T o : l) {
                        if (c.isInstance(o)) {
                            filtered.add(o);
                        }
                    }
                }
                sb.append(" | ").append(c.getSimpleName()).append('=').append(names(filtered));
            }
            return sb.toString();
        }
    }

    static String names(List<?> l) {
        if (l == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < l.size(); ++i) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(l.get(i).getClass().getSimpleName()).append('#');
            sb.append(System.identityHashCode(l.get(i)) % 1000);
        }
        return sb.append(']').toString();
    }

    static Entity newEntity(int kind, Random rng) {
        switch (kind) {
            case 0:
                return new EntityZombie(null);
            case 1:
                return new EntityCreeper(null);
            case 2:
                return new EntityPig(null);
            default:
                return new EntityItem(null);
        }
    }

    public static void main(String[] args) throws Exception {
        long checks = 0, failures = 0;
        Random rng = new Random(2024);

        for (int trial = 0; trial < 300; ++trial) {
            Ref<Entity> ref = new Ref<>(Entity.class);
            ClassInheritanceMultiMap<Entity> patched = new ClassInheritanceMultiMap<>(Entity.class);
            // Register every class we are going to query up front, so both models hold the exact
            // same key set for the whole trial. What is under test is whether the memoised
            // assignable-key list answers the same as scanning knownKeys, not the order in which
            // keys happen to get created.
            for (Class<?> c : QUERY_KEYS) {
                ref.byClass(c);
                patched.getByClass((Class<Entity>) c).iterator();
            }

            List<Entity> live = new ArrayList<>();
            List<Entity> pool = new ArrayList<>();
            for (int i = 0; i < 80; ++i) {
                pool.add(newEntity(rng.nextInt(4), rng));
            }

            int ops = 40 + rng.nextInt(120);
            for (int op = 0; op < ops; ++op) {
                int kind = rng.nextInt(10);
                if (kind < 5 && !pool.isEmpty()) {
                    Entity e = pool.remove(rng.nextInt(pool.size()));
                    live.add(e);
                    ref.add(e);
                    patched.add(e);
                } else if (kind < 8 && !live.isEmpty()) {
                    Entity e = live.remove(rng.nextInt(live.size()));
                    ++checks;
                    if (ref.remove(e) != patched.remove(e)) {
                        ++failures;
                        System.out.println("MISMATCH remove() return");
                    }
                } else if (kind == 8) {
                    // register an extra lookup key, like a modded/tracked entity type
                    Class<?> c = QUERY_KEYS[rng.nextInt(QUERY_KEYS.length)];
                    ref.byClass(c);
                    patched.getByClass((Class<Entity>) c).iterator();
                } else {
                    Class<?>[] query = { Entity.class, EntityLiving.class, EntityZombie.class,
                            EntityCreeper.class, EntityPig.class, EntityItem.class };
                    Class<?> c = query[rng.nextInt(query.length)];
                    List<Entity> a = ref.byClass(c);
                    List<Entity> b = new ArrayList<>();
                    Iterator<Entity> it = patched.getByClass((Class<Entity>) c).iterator();
                    while (it.hasNext()) {
                        b.add(it.next());
                    }
                    ++checks;
                    if (!names(a).equals(names(b))) {
                        ++failures;
                        System.out.println("MISMATCH getByClass(" + c.getSimpleName() + ") trial " + trial);
                        System.out.println("  ref    " + names(a));
                        System.out.println("  patched" + names(b));
                    }
                    ++checks;
                    if (a.size() != b.size()) {
                        ++failures;
                        System.out.println("MISMATCH size for " + c.getSimpleName());
                    }
                }
                ++checks;
                if (!ref.dump().equals(dumpPatched(patched))) {
                    ++failures;
                    if (failures < 4) {
                        System.out.println("MISMATCH state at trial " + trial + " op " + op);
                        System.out.println("  ref    " + ref.dump());
                        System.out.println("  patched" + dumpPatched(patched));
                    }
                }
            }
        }

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures != 0) {
            throw new AssertionError("ClassInheritanceMultiMap behaviour changed");
        }
    }

    static String dumpPatched(ClassInheritanceMultiMap<Entity> m) {
        StringBuilder sb = new StringBuilder();
        List<Entity> all = new ArrayList<>();
        Iterator<Entity> it = m.iterator();
        while (it.hasNext()) {
            all.add(it.next());
        }
        sb.append("all=").append(names(all));
        List<Class<?>> sorted = new ArrayList<>();
        for (Class<?> c : QUERY_KEYS) {
            sorted.add(c);
        }
        sorted.sort((a, b) -> a.getName().compareTo(b.getName()));
        for (Class<?> c : sorted) {
            List<Entity> l = new ArrayList<>();
            try {
                Iterator<Entity> i2 = m.getByClass((Class<Entity>) c).iterator();
                while (i2.hasNext()) {
                    l.add(i2.next());
                }
            } catch (IllegalArgumentException e) {
                continue;
            }
            sb.append(" | ").append(c.getSimpleName()).append('=').append(names(l));
        }
        return sb.toString();
    }
}
