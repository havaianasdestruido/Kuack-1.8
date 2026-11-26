package probe;

import net.minecraft.client.renderer.texture.MipmapGammaBlend;

/** Startup-only entry point: the shipped source-embedded, platform-selected gamma tables. */
public final class GammaOnlyEmbedded {
    private GammaOnlyEmbedded() {
    }

    public static void main(String[] args) {
        double s = 0.0D;
        for (int i = 0; i < 256; ++i) s += (double) MipmapGammaBlend.gamma22(i);
        System.out.println("CHECKSUM " + s);
    }
}
