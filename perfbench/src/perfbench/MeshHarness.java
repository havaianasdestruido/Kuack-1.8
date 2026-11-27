package perfbench;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.lax1dude.eaglercraft.v1_8.opengl.ext.deferred.EaglerDeferredPipeline;
import net.lax1dude.eaglercraft.v1_8.opengl.ext.dynamiclights.DynamicLightsStateManager;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Shared fixtures for the BlockModelRenderer meshing bench and the differential verifier:
 * a real WorldStub + RegionRenderCache (same setup as RebuildProfile) and synthetic baked
 * models whose quads deliberately span the flush / inset / cross shapes that drive the
 * boundsFlags and AO-weighting branches.
 */
public final class MeshHarness {
    public static final int STRIDE = 7;
    public static final int STRIDE_NORMALS = 8;

    /** Records every WorldRenderer call the mesher makes, in order, with exact arguments. */
    public static final class RecordingWorldRenderer extends WorldRenderer {
        final List<Object> record = new ArrayList<Object>();

        public RecordingWorldRenderer() {
            super(1 << 12);
        }

        @Override
        public void addVertexData(int[] vertexData) {
            record.add("vd:" + Arrays.toString(vertexData));
        }

        @Override
        public void putBrightness4(int a, int b, int c, int d) {
            record.add("br:" + a + "," + b + "," + c + "," + d);
        }

        @Override
        public void putColorMultiplier(float red, float green, float blue, int idx) {
            record.add("col:" + Float.toString(red) + "," + Float.toString(green) + "," + Float.toString(blue) + "," + idx);
        }

        @Override
        public void putPosition(double x, double y, double z) {
            record.add("pos:" + Double.toString(x) + "," + Double.toString(y) + "," + Double.toString(z));
        }
    }

    /** No-op WorldRenderer for benchmarks: consumes the vertex stream without recording state. */
    public static final class BlackholeWorldRenderer extends WorldRenderer {
        long bh;

        public BlackholeWorldRenderer() {
            super(1 << 12);
        }

        @Override
        public void addVertexData(int[] vertexData) {
            bh += vertexData.length;
        }

        @Override
        public void putBrightness4(int a, int b, int c, int d) {
            bh += a + b + c + d;
        }

        @Override
        public void putColorMultiplier(float red, float green, float blue, int idx) {
            bh += Float.floatToIntBits(red) + Float.floatToIntBits(green) + Float.floatToIntBits(blue) + idx;
        }

        @Override
        public void putPosition(double x, double y, double z) {
            bh += Double.doubleToLongBits(x) + Double.doubleToLongBits(y) + Double.doubleToLongBits(z);
        }
    }

    public static final class SyntheticQuad extends BakedQuad {
        public SyntheticQuad(int[] data, int tint, EnumFacing face) {
            super(data, tint, face);
        }

        public SyntheticQuad(int[] data, int[] normals, int tint, EnumFacing face) {
            super(data, normals, tint, face);
        }
    }

    public static final class SyntheticModel implements IBakedModel {
        private final List<BakedQuad>[] faces;
        private final List<BakedQuad> general;

        public SyntheticModel(List<BakedQuad>[] facesIn, List<BakedQuad> generalIn) {
            this.faces = facesIn;
            this.general = generalIn;
        }

        @SuppressWarnings("unchecked")
        public static SyntheticModel build(Random rng, boolean inset, boolean tinted, boolean includeGeneral,
                boolean skipSomeFaces) {
            float lo = inset ? 0.2F : 0.0F;
            float hi = inset ? 0.8F : 1.0F;
            List<BakedQuad>[] faces = new List[EnumFacing._VALUES.length];
            EnumFacing[] facings = EnumFacing._VALUES;
            for (int f = 0; f < facings.length; ++f) {
                if (skipSomeFaces && f % 3 == 2) {
                    faces[f] = Collections.emptyList();
                    continue;
                }
                EnumFacing face = facings[f];
                int tint = tinted ? 3 + f : -1;
                int[] data = quadData(face, lo, hi);
                int[] normals = quadDataWithNormals(face, lo, hi);
                // every quad carries a normals array so the dynamic-lights path (stride 8) is usable
                // in every mode combination; the non-lights path reads the same first 4 positions
                faces[f] = Collections.<BakedQuad>singletonList(new SyntheticQuad(data, normals, tint, face));
            }
            List<BakedQuad> general;
            if (includeGeneral) {
                general = new ArrayList<BakedQuad>();
                general.add(new SyntheticQuad(quadData(EnumFacing.UP, lo, hi), quadDataWithNormals(EnumFacing.UP, lo, hi),
                        -1, EnumFacing.UP));
                general.add(new SyntheticQuad(quadData(EnumFacing.DOWN, lo, hi),
                        quadDataWithNormals(EnumFacing.DOWN, lo, hi), tinted ? 9 : -1, EnumFacing.DOWN));
                general.add(new SyntheticQuad(quadData(EnumFacing.NORTH, lo, hi),
                        quadDataWithNormals(EnumFacing.NORTH, lo, hi), -1, EnumFacing.NORTH));
            } else {
                general = Collections.emptyList();
            }
            return new SyntheticModel(faces, general);
        }

        @Override
        public List<BakedQuad> getFaceQuads(EnumFacing facing) {
            return faces[facing.getIndex()];
        }

        @Override
        public List<BakedQuad> getGeneralQuads() {
            return general;
        }

        @Override
        public boolean isAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public net.lax1dude.eaglercraft.v1_8.minecraft.EaglerTextureAtlasSprite getParticleTexture() {
            return null;
        }

        @Override
        public net.minecraft.client.renderer.block.model.ItemCameraTransforms getItemCameraTransforms() {
            return null;
        }
    }

    /** 4-vertex block-format quad on the given face plane, spanning lo..hi on the other two axes. */
    public static int[] quadData(EnumFacing face, float lo, float hi) {
        return quadData(face, lo, hi, STRIDE);
    }

    private static int[] quadData(EnumFacing face, float lo, float hi, int stride) {
        int[] data = new int[stride * 4];
        int dx = face.getFrontOffsetX();
        int dy = face.getFrontOffsetY();
        int dz = face.getFrontOffsetZ();
        // plane coordinate: 0 or 1 depending on which side of the cube the face sits on
        float plane = dx + dy + dz > 0 ? 1.0F : 0.0F;
        int[][] corners;
        if (dy != 0) {
            corners = new int[][] { { 0, 0, 0 }, { 0, 0, 1 }, { 1, 0, 1 }, { 1, 0, 0 } };
        } else if (dx != 0) {
            corners = new int[][] { { 0, 0, 0 }, { 0, 1, 0 }, { 0, 1, 1 }, { 0, 0, 1 } };
        } else {
            corners = new int[][] { { 0, 0, 0 }, { 1, 0, 0 }, { 1, 1, 0 }, { 0, 1, 0 } };
        }
        for (int v = 0; v < 4; ++v) {
            float x = dx != 0 ? plane : (corners[v][0] == 0 ? lo : hi);
            float y = dy != 0 ? plane : (corners[v][1] == 0 ? lo : hi);
            float z = dz != 0 ? plane : (corners[v][2] == 0 ? lo : hi);
            int b = v * stride;
            data[b] = Float.floatToIntBits(x);
            data[b + 1] = Float.floatToIntBits(y);
            data[b + 2] = Float.floatToIntBits(z);
            data[b + 3] = 0xFFFFFFFF;
            data[b + 4] = Float.floatToIntBits(0.25F * v);
            data[b + 5] = Float.floatToIntBits(0.5F * v);
            if (stride > STRIDE) {
                data[b + 6] = 0x0000007F; // normal-ish slot for the normals variant
                data[b + 7] = v;
            }
        }
        return data;
    }

    public static int[] quadDataWithNormals(EnumFacing face, float lo, float hi) {
        return quadData(face, lo, hi, STRIDE_NORMALS);
    }

    // ---------------------------------------------------------------- render-mode flags

    private static Object deferredPipelineDummy;
    private static Object bucketLoaderDummy;

    private static sun.misc.Unsafe theUnsafe() {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (sun.misc.Unsafe) f.get(null);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void setDeferred(boolean on) {
        try {
            if (deferredPipelineDummy == null) {
                deferredPipelineDummy = theUnsafe().allocateInstance(EaglerDeferredPipeline.class);
            }
            EaglerDeferredPipeline.instance = on ? (EaglerDeferredPipeline) deferredPipelineDummy : null;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void setDynamicLights(boolean on) {
        try {
            if (bucketLoaderDummy == null) {
                Class<?> loader = Class.forName(
                        "net.lax1dude.eaglercraft.v1_8.opengl.ext.dynamiclights.DynamicLightBucketLoader");
                bucketLoaderDummy = theUnsafe().allocateInstance(loader);
            }
            Field f = DynamicLightsStateManager.class.getDeclaredField("bucketLoader");
            f.setAccessible(true);
            f.set(null, on ? bucketLoaderDummy : null);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void setAnaglyph(boolean on) {
        EntityRenderer.anaglyphEnable = on;
    }

    // ---------------------------------------------------------------- world fixtures

    private static boolean fakeMinecraftInstalled = false;

    /**
     * TextureUtil's <clinit> builds the 16x16 "missingno" DynamicTexture, whose upload path reads
     * Minecraft.getMinecraft().gameSettings.anaglyph. Install reflection-made dummy instances so
     * that class can initialize in a plain JVM (no GL, no client boot).
     */
    public static synchronized void installFakeMinecraft() {
        if (fakeMinecraftInstalled) {
            return;
        }
        try {
            sun.misc.Unsafe unsafe = theUnsafe();
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object mcInstance = mc.cast(unsafe.allocateInstance(mc));
            Class<?> gs = Class.forName("net.minecraft.client.settings.GameSettings");
            Object gsInstance = unsafe.allocateInstance(gs);
            java.lang.reflect.Field f1 = mc.getDeclaredField("theMinecraft");
            f1.setAccessible(true);
            f1.set(null, mcInstance);
            java.lang.reflect.Field f2 = mc.getDeclaredField("gameSettings");
            f2.setAccessible(true);
            f2.set(mcInstance, gsInstance);
            fakeMinecraftInstalled = true;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static World newWorld() {
        installFakeMinecraft();
        WorldStub world = new WorldStub(99L);
        ((ChunkProviderStub) world.getChunkProvider()).preload(0, 0, 2);
        return world;
    }

    public static RegionRenderCache newCache(World world) {
        return new RegionRenderCache(world, new BlockPos(0, 0, 0), new BlockPos(15, 255, 15), 1);
    }

    /** Block palette chosen to hit shouldSideBeRendered / isFullCube / isTranslucent / tint / offset variants. */
    public static final Block[] PALETTE = new Block[] { Blocks.stone, Blocks.dirt, Blocks.grass, Blocks.leaves,
            Blocks.glass, Blocks.sand, Blocks.gravel, Blocks.torch, Blocks.red_flower, Blocks.yellow_flower,
            Blocks.snow_layer, Blocks.planks };

    public static final Block[] NO_TINT_PALETTE = new Block[] { Blocks.stone, Blocks.dirt, Blocks.glass, Blocks.sand,
            Blocks.gravel, Blocks.torch, Blocks.planks };

    public static BlockPos randomPos(Random rng) {
        return new BlockPos(2 + rng.nextInt(12), 50 + rng.nextInt(20), 2 + rng.nextInt(12));
    }

    /** Runs one render of (model, block, pos, checkSides) through the given renderer class. */
    public static boolean renderWith(boolean vanilla, IBlockAccess access, IBakedModel model, Block block,
            BlockPos pos, WorldRenderer rw, boolean checkSides, boolean ambientOcclusion) {
        BlockModelRendererVanilla v = vanilla ? Holder.VANILLA : null;
        net.minecraft.client.renderer.BlockModelRenderer p = vanilla ? null : Holder.PRODUCTION;
        if (ambientOcclusion) {
            if (vanilla) {
                return v.renderModelAmbientOcclusion(access, model, block, pos, rw, checkSides);
            }
            return p.renderModelAmbientOcclusion(access, model, block, pos, rw, checkSides);
        }
        if (vanilla) {
            return v.renderModelStandard(access, model, block, pos, rw, checkSides);
        }
        return p.renderModelStandard(access, model, block, pos, rw, checkSides);
    }

    /** One BlockModelRenderer instance per mode is reused across ALL calls (the aliasing detector). */
    public static final class Holder {
        public static final BlockModelRendererVanilla VANILLA = new BlockModelRendererVanilla();
        public static final net.minecraft.client.renderer.BlockModelRenderer PRODUCTION = new net.minecraft.client.renderer.BlockModelRenderer();
    }

    private MeshHarness() {
    }
}
