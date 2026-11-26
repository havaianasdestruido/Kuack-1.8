package perfbench;

import java.util.Random;

import net.lax1dude.eaglercraft.v1_8.vector.Matrix4f;
import net.lax1dude.eaglercraft.v1_8.vector.Vector3f;

/**
 * Benchmark for FINDINGS #10: {@code Matrix4f.rotate} using the engine's 65536-entry sine table
 * instead of {@code Math.cos}/{@code Math.sin}.
 *
 * <p>{@code Matrix4f.rotate} is what {@code GlStateManager.rotate} executes for every rotated model
 * part, tile entity, particle billboard, held item and GUI item. A model renderer with six parts
 * issues 2-3 rotations per part, so a frame with a hundred visible mobs is thousands of calls.</p>
 *
 * <p>"before" is a verbatim copy of the previous implementation (see {@link VerifyMatrixRotate}),
 * "after" is the shipped class. Both perform the same 40-odd float multiply-adds; only the two trig
 * evaluations differ, so the measured difference is the trig change.</p>
 *
 * Run: {@code ./perfbench/run_bench.sh perfbench.MatrixRotateBench}
 */
public final class MatrixRotateBench {

    public static void main(String[] args) {
        System.out.println("=== MatrixRotateBench: Matrix4f.rotate trig (Math.cos/sin vs sin table) ===");

        final Vector3f xAxis = new Vector3f(1.0F, 0.0F, 0.0F);
        final Vector3f yAxis = new Vector3f(0.0F, 1.0F, 0.0F);
        final Vector3f zAxis = new Vector3f(0.0F, 0.0F, 1.0F);
        Random rng = new Random(3L);
        final Vector3f[] axes = new Vector3f[] { xAxis, yAxis, zAxis, randomAxis(rng) };

        final int batch = 2400;   // a busy frame: ~120 visible entities x ~20 part rotations
        final float[] angles = new float[batch];
        final Vector3f[] batchAxes = new Vector3f[batch];
        for (int i = 0; i < batch; ++i) {
            angles[i] = (float) (rng.nextDouble() * Math.PI * 2.0 - Math.PI);
            batchAxes[i] = i < 3 ? axes[i] : axes[rng.nextInt(axes.length)];
        }

        Matrix4f src = new Matrix4f();
        Matrix4f dst = new Matrix4f();
        identity(src);

        System.out.println("-- single rotation, isolated (median ns/call) --");
        long oldNs = BenchUtil.median("", 2000, 60, () -> {
            long s = 0;
            for (int i = 0; i < 4096; ++i) {
                oldRotate(angles[i & (batch - 1)], batchAxes[i & (batch - 1)], src, dst);
                s += Float.floatToRawIntBits(dst.m00);
            }
            return s;
        });
        long newNs = BenchUtil.median("", 2000, 60, () -> {
            long s = 0;
            for (int i = 0; i < 4096; ++i) {
                Matrix4f.rotate(angles[i & (batch - 1)], batchAxes[i & (batch - 1)], src, dst);
                s += Float.floatToRawIntBits(dst.m00);
            }
            return s;
        });
        System.out.printf("  %-30s %8.2f ns/call%n", "before (Math.cos + Math.sin)", oldNs / 4096.0);
        System.out.printf("  %-30s %8.2f ns/call   %5.2fx%n", "after  (sin table)", newNs / 4096.0,
                (double) oldNs / newNs);

        System.out.println("-- trigger only (the two trig evaluations) --");
        final float[] aa = angles;
        long oldTrig = BenchUtil.median("", 2000, 60, () -> {
            float s = 0.0F;
            for (int i = 0; i < 4096; ++i) {
                s += (float) Math.cos(aa[i & (batch - 1)]) + (float) Math.sin(aa[i & (batch - 1)]);
            }
            return Float.floatToRawIntBits(s);
        });
        long newTrig = BenchUtil.median("", 2000, 60, () -> {
            float s = 0.0F;
            for (int i = 0; i < 4096; ++i) {
                s += net.minecraft.util.MathHelper.cos(aa[i & (batch - 1)])
                        + net.minecraft.util.MathHelper.sin(aa[i & (batch - 1)]);
            }
            return Float.floatToRawIntBits(s);
        });
        System.out.printf("  %-30s %8.3f ns/call%n", "before", oldTrig / 4096.0);
        System.out.printf("  %-30s %8.3f ns/call   %5.2fx%n", "after", newTrig / 4096.0,
                (double) oldTrig / newTrig);

        System.out.println("-- whole-frame batch (" + batch + " rotations, as GlStateManager issues them) --");
        long oldFrame = BenchUtil.median("", 500, 60, () -> {
            long s = 0;
            for (int i = 0; i < batch; ++i) {
                oldRotate(angles[i], batchAxes[i], src, dst);
                s += Float.floatToRawIntBits(dst.m00);
            }
            return s;
        });
        long newFrame = BenchUtil.median("", 500, 60, () -> {
            long s = 0;
            for (int i = 0; i < batch; ++i) {
                Matrix4f.rotate(angles[i], batchAxes[i], src, dst);
                s += Float.floatToRawIntBits(dst.m00);
            }
            return s;
        });
        System.out.printf("  %-30s %8.1f us/frame%n", "before", oldFrame / 1e3);
        System.out.printf("  %-30s %8.1f us/frame   saves %5.1f us (%.2f%% of a 16.67 ms frame)%n", "after",
                newFrame / 1e3, (oldFrame - newFrame) / 1e3, (oldFrame - newFrame) / 1.6667e7 * 100.0);

        for (int n : new int[] { 500, 7200, 20000 }) {
            final int rot = n;
            long o = BenchUtil.median("", 300, 40, () -> {
                long s = 0;
                for (int i = 0; i < rot; ++i) {
                    oldRotate(angles[i % batch], batchAxes[i % batch], src, dst);
                    s += Float.floatToRawIntBits(dst.m00);
                }
                return s;
            });
            long nw = BenchUtil.median("", 300, 40, () -> {
                long s = 0;
                for (int i = 0; i < rot; ++i) {
                    Matrix4f.rotate(angles[i % batch], batchAxes[i % batch], src, dst);
                    s += Float.floatToRawIntBits(dst.m00);
                }
                return s;
            });
            System.out.printf("  rotations/frame %6d: before %8.1f us  after %8.1f us  saves %6.1f us%n", rot,
                    o / 1e3, nw / 1e3, (o - nw) / 1e3);
        }
    }

    private static Vector3f randomAxis(Random rng) {
        double x = rng.nextGaussian();
        double y = rng.nextGaussian();
        double z = rng.nextGaussian();
        double l = Math.sqrt(x * x + y * y + z * z);
        return new Vector3f((float) (x / l), (float) (y / l), (float) (z / l));
    }

    private static void identity(Matrix4f m) {
        m.m00 = 1.0F;
        m.m01 = 0.0F;
        m.m02 = 0.0F;
        m.m03 = 0.0F;
        m.m10 = 0.0F;
        m.m11 = 1.0F;
        m.m12 = 0.0F;
        m.m13 = 0.0F;
        m.m20 = 0.0F;
        m.m21 = 0.0F;
        m.m22 = 1.0F;
        m.m23 = 0.0F;
        m.m30 = 0.0F;
        m.m31 = 0.0F;
        m.m32 = 0.0F;
        m.m33 = 1.0F;
    }

    /** Verbatim copy of the pre-change Matrix4f.rotate (see VerifyMatrixRotate). */
    static Matrix4f oldRotate(float angle, Vector3f axis, Matrix4f src, Matrix4f dest) {
        if (dest == null)
            dest = new Matrix4f();
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        float oneminusc = 1.0f - c;
        float xy = axis.x * axis.y;
        float yz = axis.y * axis.z;
        float xz = axis.x * axis.z;
        float xs = axis.x * s;
        float ys = axis.y * s;
        float zs = axis.z * s;

        float f00 = axis.x * axis.x * oneminusc + c;
        float f01 = xy * oneminusc + zs;
        float f02 = xz * oneminusc - ys;
        float f10 = xy * oneminusc - zs;
        float f11 = axis.y * axis.y * oneminusc + c;
        float f12 = yz * oneminusc + xs;
        float f20 = xz * oneminusc + ys;
        float f21 = yz * oneminusc - xs;
        float f22 = axis.z * axis.z * oneminusc + c;

        float t00 = src.m00 * f00 + src.m10 * f01 + src.m20 * f02;
        float t01 = src.m01 * f00 + src.m11 * f01 + src.m21 * f02;
        float t02 = src.m02 * f00 + src.m12 * f01 + src.m22 * f02;
        float t03 = src.m03 * f00 + src.m13 * f01 + src.m23 * f02;
        float t10 = src.m00 * f10 + src.m10 * f11 + src.m20 * f12;
        float t11 = src.m01 * f10 + src.m11 * f11 + src.m21 * f12;
        float t12 = src.m02 * f10 + src.m12 * f11 + src.m22 * f12;
        float t13 = src.m03 * f10 + src.m13 * f11 + src.m23 * f12;
        dest.m20 = src.m00 * f20 + src.m10 * f21 + src.m20 * f22;
        dest.m21 = src.m01 * f20 + src.m11 * f21 + src.m21 * f22;
        dest.m22 = src.m02 * f20 + src.m12 * f21 + src.m22 * f22;
        dest.m23 = src.m03 * f20 + src.m13 * f21 + src.m23 * f22;
        dest.m00 = t00;
        dest.m01 = t01;
        dest.m02 = t02;
        dest.m03 = t03;
        dest.m10 = t10;
        dest.m11 = t11;
        dest.m12 = t12;
        dest.m13 = t13;
        return dest;
    }
}
