package perfbench;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Brings up the block/item/biome registries on a plain JVM so benchmarks and regression tests can
 * exercise real game code. Only the registration steps that do not need the rendering stack run.
 */
public final class BenchBootstrap {
    private static boolean done = false;

    private BenchBootstrap() {
    }

    public static synchronized void register() {
        if (done) {
            return;
        }
        done = true;
        setStatic("net.minecraft.init.Bootstrap", "alreadyRegistered", Boolean.TRUE);
        call("net.minecraft.block.Block", "registerBlocks");
        call("net.minecraft.init.Blocks", "doBootstrap");
        call("net.minecraft.world.biome.BiomeGenBase", "doBootstrap");
        call("net.minecraft.item.Item", "registerItems");
        call("net.minecraft.init.Items", "doBootstrap");
    }

    private static void call(String cls, String method) {
        try {
            Class<?> c = Class.forName(cls);
            Method m = c.getDeclaredMethod(method);
            m.setAccessible(true);
            m.invoke(null);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            System.out.println("[bootstrap] " + cls + "." + method + " -> " + cause);
        }
    }

    private static void setStatic(String cls, String field, Object value) {
        try {
            Class<?> c = Class.forName(cls);
            Field f = c.getDeclaredField(field);
            f.setAccessible(true);
            f.set(null, value);
        } catch (Throwable t) {
            System.out.println("[bootstrap] set " + cls + "." + field + " -> " + t);
        }
    }
}
