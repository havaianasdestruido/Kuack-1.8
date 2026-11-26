package net.lax1dude.eaglercraft.v1_8;

/** Minimal link stub: only the methods MathHelper references. Not used by the game. */
public class EaglercraftRandom {
    public int nextInt(int bound) { return bound <= 0 ? 0 : 12345 % bound; }
    public float nextFloat() { return 0.5F; }
    public double nextDouble() { return 0.5D; }
    public long nextLong() { return 0x5EEDBEEFL; }
}
