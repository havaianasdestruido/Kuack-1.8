package net.lax1dude.eaglercraft.v1_8.opengl;

/**
 * Minimal JVM-side bench stub of GlStateManager. The real GlStateManager.java cannot compile under
 * ECJ without the TeaVM GL stack; the mesher benches only trigger texture-id bookkeeping through
 * TextureUtil's static initializers (DynamicTexture's constructor), never real GL state.
 */
public class GlStateManager {

    private static int nextTextureId = 1;
    private static int boundTexture = 0;

    public static final int generateTexture() {
        return nextTextureId++;
    }

    public static final void deleteTexture(int texture) {
        // bookkeeping only in the bench environment
    }

    public static final void bindTexture(int texture) {
        boundTexture = texture;
    }

    public static final int getBoundTexture() {
        return boundTexture;
    }
}
