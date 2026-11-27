package net.lax1dude.eaglercraft.v1_8.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Minimal JVM-side bench stub of WorldRenderer. The real WorldRenderer.java cannot compile under
 * ECJ without the TeaVM GL stack; the mesher benchmarks only drive addVertexData /
 * putBrightness4 / putColorMultiplier / putPosition (and benchmarks subclass this class to
 * record or discard those calls), so only those members plus buffer allocation exist here.
 */
public class WorldRenderer {

    protected final ByteBuffer byteBuffer;
    protected final IntBuffer intBuffer;
    protected final FloatBuffer floatBuffer;

    public WorldRenderer(int bufferSizeIn) {
        this.byteBuffer = ByteBuffer.allocateDirect(bufferSizeIn << 2);
        this.intBuffer = this.byteBuffer.asIntBuffer();
        this.floatBuffer = this.byteBuffer.asFloatBuffer();
    }

    public void addVertexData(int[] vertexData) {
        for (int i = 0; i < vertexData.length; ++i) {
            this.intBuffer.put(vertexData[i]);
        }
    }

    public void putBrightness4(int a, int b, int c, int d) {
        this.intBuffer.put(a);
        this.intBuffer.put(b);
        this.intBuffer.put(c);
        this.intBuffer.put(d);
    }

    public void putColorMultiplier(float red, float green, float blue, int idx) {
        this.intBuffer.put(Float.floatToIntBits(red));
        this.intBuffer.put(Float.floatToIntBits(green));
        this.intBuffer.put(Float.floatToIntBits(blue));
        this.intBuffer.put(idx);
    }

    public void putPosition(double x, double y, double z) {
        this.floatBuffer.put((float) x);
        this.floatBuffer.put((float) y);
        this.floatBuffer.put((float) z);
    }
}
