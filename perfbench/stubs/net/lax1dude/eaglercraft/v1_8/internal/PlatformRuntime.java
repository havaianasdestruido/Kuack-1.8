package net.lax1dude.eaglercraft.v1_8.internal;

/** Minimal JVM-side stub of the TeaVM platform runtime, enough to run server/world logic in tests. */
public class PlatformRuntime {

    public static EnumPlatformType getPlatformType() {
        return EnumPlatformType.DESKTOP;
    }
    public static long randomSeed() {
        return System.nanoTime() ^ (long) (Math.random() * 0x7FFFFFFFL);
    }

    public static long randomSeed(long salt) {
        return randomSeed() ^ salt;
    }

    public static long steadyTimeMillis() {
        return System.nanoTime() / 1000000L;
    }

    public static long steadyTimeNanos() {
        return System.nanoTime();
    }
}
