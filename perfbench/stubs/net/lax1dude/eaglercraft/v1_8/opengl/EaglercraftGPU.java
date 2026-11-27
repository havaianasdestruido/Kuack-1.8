package net.lax1dude.eaglercraft.v1_8.opengl;

/**
 * Minimal JVM-side bench stub of EaglercraftGPU. The real EaglercraftGPU.java cannot compile under
 * ECJ without the TeaVM GL stack; the mesher benches only trigger TextureUtil's static texture
 * allocation, which issues these parameter/storage calls, none of which need a real GL context.
 */
public class EaglercraftGPU {

    public static final void glTexParameteri(int target, int param, int value) {
        // no GL in the bench environment
    }

    public static final void glTexParameterf(int target, int param, float value) {
        // no GL in the bench environment
    }

    public static final void glTexStorage2D(int target, int levels, int internalFormat, int w, int h) {
        // no GL in the bench environment
    }

    public static final void glTexSubImage2D(int target, int level, int x, int y, int w, int h, int format, int type,
            net.lax1dude.eaglercraft.v1_8.internal.buffer.IntBuffer pixels) {
        // no GL in the bench environment
    }

    public static final void regenerateTexture(int tex) {
        // no GL in the bench environment
    }
}
