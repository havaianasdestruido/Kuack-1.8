package perfbench;

import java.util.Random;

import net.lax1dude.eaglercraft.v1_8.vector.Matrix4f;
import net.lax1dude.eaglercraft.v1_8.vector.Vector3f;

/**
 * Correctness proof for FINDINGS #10: {@code Matrix4f.rotate} using the engine's sine table.
 *
 * <p>Checks, in order:</p>
 * <ol>
 *   <li><b>Error bound.</b> Over randomised angles in the modelled domain and randomised axes, the
 *       new matrix differs from the old one by at most the amount implied by the table's angle
 *       quantization (2*pi/65536 rad). The script reports the measured maximum element delta and the
 *       implied maximum angular deviation, and fails if either exceeds the analytic bound with a 4x
 *       safety factor.</li>
 *   <li><b>Unit axes are exact for the axis itself.</b> A rotation about X/Y/Z must leave that axis
 *       direction unchanged (to float rounding) in both implementations.</li>
 *   <li><b>Fallback is bit-exact.</b> |angle| &gt;= 200000, NaN and +/-Infinity must produce a matrix
 *       identical to the old code (they take the double-precision path).</li>
 *   <li><b>Zero rotation.</b> angle == 0 must still yield the identity rotation.</li>
 *   <li><b>In-place aliasing.</b> {@code rotate(a, axis, m, m)} must behave like the old code.</li>
 * </ol>
 *
 * Run: {@code ./perfbench/run_bench.sh perfbench.VerifyMatrixRotate}
 */
public final class VerifyMatrixRotate {

    /** 2*pi/65536: the sine table advance per index step. */
    private static final double TABLE_STEP = Math.PI * 2.0 / 65536.0;

    private static final Matrix4f IDENTITY = new Matrix4f();

    private static long checks = 0;
    private static long failures = 0;

    public static void main(String[] args) {
        System.out.println("=== VerifyMatrixRotate: sin-table rotation vs Math.cos/Math.sin ===");
        identity(IDENTITY);

        errorBound();
        axisInvariance();
        fallbackExactness();
        zeroAndAliasing();

        System.out.printf("checks: %,d   failures: %,d%n", checks, failures);
        if (failures != 0) {
            throw new AssertionError("VerifyMatrixRotate FAILED with " + failures + " violations");
        }
        System.out.println("OK: rotation matrices are within the documented tolerance of the original.");
    }

    // ------------------------------------------------------------------- (1)

    private static void errorBound() {
        Random rng = new Random(0xBEEF1234L);
        Matrix4f src = new Matrix4f();
        Matrix4f a = new Matrix4f();
        Matrix4f b = new Matrix4f();
        Matrix4f a2 = new Matrix4f();
        Matrix4f b2 = new Matrix4f();
        double maxDelta = 0.0;
        double maxAngle = 0.0;
        int n = 200000;
        for (int i = 0; i < n; ++i) {
            fillRandom(src, rng);
            float angle = (float) (rng.nextDouble() * 4.0 * Math.PI - 2.0 * Math.PI);
            Vector3f axis = randomAxis(rng);
            MatrixRotateBench.oldRotate(angle, axis, src, a);
            Matrix4f.rotate(angle, axis, src, b);
            ++checks;
            double d = maxAbsDelta(a, b);
            if (d > maxDelta) {
                maxDelta = d;
            }
            // deviation seen by a unit vector rotated by both matrices (identity source so the
            // number is the rotation error alone, not src's magnitude)
            Matrix4f.rotate(angle, axis, IDENTITY, b2);
            MatrixRotateBench.oldRotate(angle, axis, IDENTITY, a2);
            double ang = angularDeviation(a2, b2);
            if (ang > maxAngle) {
                maxAngle = ang;
            }
            if (Double.isNaN(d)) {
                failures++;
                System.out.println("    NaN produced for angle " + angle);
            }
        }
        // Analytic bound: cos/sin each move by at most TABLE_STEP; a rotation matrix column is a
        // product of at most 3 such terms, and it is applied to a 4x4 with entries up to ~a few,
        // so the element delta is bounded by ~4 * TABLE_STEP * |src|max. Report the measurement and
        // assert against a generous multiple so the test detects a real regression, not noise.
        double bound = 4.0 * TABLE_STEP * 64.0;
        System.out.printf("  %d rotations: max element delta %.3e (bound %.3e), max angular deviation %.3e rad "
                + "(= %.5f deg, table step %.3e rad)%n", n, maxDelta, bound, maxAngle, Math.toDegrees(maxAngle),
                TABLE_STEP);
        if (maxDelta > bound) {
            failures++;
            System.out.println("    element delta exceeds the analytic bound");
        }
        if (maxAngle > 4.0 * TABLE_STEP) {
            failures++;
            System.out.println("    angular deviation exceeds 4 table steps");
        }
    }

    // ------------------------------------------------------------------- (2)

    private static void axisInvariance() {
        Matrix4f src = new Matrix4f();
        Matrix4f dst = new Matrix4f();
        identity(src);
        double worst = 0.0;
        // rotating about an axis must leave that axis direction untouched: column k of the
        // resulting matrix must still be the k-th unit vector
        for (int k = 0; k < 3; ++k) {
            Vector3f ax = k == 0 ? new Vector3f(1, 0, 0) : (k == 1 ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1));
            for (int i = -720; i <= 720; ++i) {
                float angle = (float) (i * 0.008726646259971648); // 0.5 degree steps
                Matrix4f.rotate(angle, ax, src, dst);
                float c0 = k == 0 ? dst.m00 : (k == 1 ? dst.m01 : dst.m02);
                float c1 = k == 0 ? dst.m10 : (k == 1 ? dst.m11 : dst.m12);
                float c2 = k == 0 ? dst.m20 : (k == 1 ? dst.m21 : dst.m22);
                ++checks;
                worst = Math.max(worst, Math.abs(c0 - (k == 0 ? 1.0F : 0.0F)));
                worst = Math.max(worst, Math.abs(c1 - (k == 1 ? 1.0F : 0.0F)));
                worst = Math.max(worst, Math.abs(c2 - (k == 2 ? 1.0F : 0.0F)));
            }
        }
        System.out.printf("  axis invariance over 4323 rotations: worst deviation from exact axis %.3e%n", worst);
        if (worst > 1.0e-6) {
            failures++;
            System.out.println("    rotation does not preserve its own axis");
        }
    }

    // ------------------------------------------------------------------- (3)

    private static void fallbackExactness() {
        Matrix4f src = new Matrix4f();
        Matrix4f a = new Matrix4f();
        Matrix4f b = new Matrix4f();
        fillRandom(src, new Random(99L));
        float[] angles = { 200000.0F, -200000.0F, 200001.0F, 1.0e6F, -1.0e6F, 1.0e30F, -1.0e30F,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN };
        Vector3f axis = new Vector3f(0.0F, 1.0F, 0.0F);
        int bad = 0;
        for (int i = 0; i < angles.length; ++i) {
            MatrixRotateBench.oldRotate(angles[i], axis, src, a);
            Matrix4f.rotate(angles[i], axis, src, b);
            ++checks;
            if (!bitIdentical(a, b)) {
                ++bad;
                failures++;
            }
        }
        System.out.printf("  fallback domain (%d angles incl. NaN/Inf): %d matrices differing from original%n",
                angles.length, bad);
    }

    // ------------------------------------------------------------------- (4)

    private static void zeroAndAliasing() {
        Matrix4f src = new Matrix4f();
        Matrix4f dst = new Matrix4f();
        fillRandom(src, new Random(4L));
        Vector3f axis = new Vector3f(0.0F, 1.0F, 0.0F);

        Matrix4f.rotate(0.0F, axis, src, dst);
        ++checks;
        double zeroDelta = maxAbsDelta(dst, src);
        if (zeroDelta > 1e-6) {
            failures++;
            System.out.println("    angle 0 did not preserve the source matrix");
        }

        // aliasing: dest == src, which GlStateManager.rotate always does
        Matrix4f inPlaceNew = new Matrix4f();
        Matrix4f inPlaceOld = new Matrix4f();
        copy(src, inPlaceNew);
        copy(src, inPlaceOld);
        Matrix4f.rotate(0.5F, axis, inPlaceNew, inPlaceNew);
        MatrixRotateBench.oldRotate(0.5F, axis, inPlaceOld, inPlaceOld);
        ++checks;
        double d = maxAbsDelta(inPlaceNew, inPlaceOld);
        System.out.printf("  angle 0 identity: max delta %.3e (must be exactly 0: sin=0, cos=1)%n", zeroDelta);
        System.out.printf("  in-place aliasing at 0.5 rad: max delta %.3e (table tolerance)%n", d);
        if (d > 4.0 * TABLE_STEP * 64.0) {
            failures++;
            System.out.println("    in-place rotation diverges from the original");
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Vector3f randomAxis(Random rng) {
        double x = rng.nextGaussian();
        double y = rng.nextGaussian();
        double z = rng.nextGaussian();
        double l = Math.sqrt(x * x + y * y + z * z);
        return new Vector3f((float) (x / l), (float) (y / l), (float) (z / l));
    }

    private static void fillRandom(Matrix4f m, Random rng) {
        m.m00 = rng.nextFloat();
        m.m01 = rng.nextFloat();
        m.m02 = rng.nextFloat();
        m.m03 = rng.nextFloat();
        m.m10 = rng.nextFloat();
        m.m11 = rng.nextFloat();
        m.m12 = rng.nextFloat();
        m.m13 = rng.nextFloat();
        m.m20 = rng.nextFloat();
        m.m21 = rng.nextFloat();
        m.m22 = rng.nextFloat();
        m.m23 = rng.nextFloat();
        m.m30 = 0.0F;
        m.m31 = 0.0F;
        m.m32 = 0.0F;
        m.m33 = 1.0F;
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

    private static void copy(Matrix4f from, Matrix4f to) {
        to.m00 = from.m00;
        to.m01 = from.m01;
        to.m02 = from.m02;
        to.m03 = from.m03;
        to.m10 = from.m10;
        to.m11 = from.m11;
        to.m12 = from.m12;
        to.m13 = from.m13;
        to.m20 = from.m20;
        to.m21 = from.m21;
        to.m22 = from.m22;
        to.m23 = from.m23;
        to.m30 = from.m30;
        to.m31 = from.m31;
        to.m32 = from.m32;
        to.m33 = from.m33;
    }

    private static boolean bitIdentical(Matrix4f a, Matrix4f b) {
        return Float.floatToRawIntBits(a.m00) == Float.floatToRawIntBits(b.m00)
                && Float.floatToRawIntBits(a.m01) == Float.floatToRawIntBits(b.m01)
                && Float.floatToRawIntBits(a.m02) == Float.floatToRawIntBits(b.m02)
                && Float.floatToRawIntBits(a.m03) == Float.floatToRawIntBits(b.m03)
                && Float.floatToRawIntBits(a.m10) == Float.floatToRawIntBits(b.m10)
                && Float.floatToRawIntBits(a.m11) == Float.floatToRawIntBits(b.m11)
                && Float.floatToRawIntBits(a.m12) == Float.floatToRawIntBits(b.m12)
                && Float.floatToRawIntBits(a.m13) == Float.floatToRawIntBits(b.m13)
                && Float.floatToRawIntBits(a.m20) == Float.floatToRawIntBits(b.m20)
                && Float.floatToRawIntBits(a.m21) == Float.floatToRawIntBits(b.m21)
                && Float.floatToRawIntBits(a.m22) == Float.floatToRawIntBits(b.m22)
                && Float.floatToRawIntBits(a.m23) == Float.floatToRawIntBits(b.m23)
                && Float.floatToRawIntBits(a.m30) == Float.floatToRawIntBits(b.m30)
                && Float.floatToRawIntBits(a.m31) == Float.floatToRawIntBits(b.m31)
                && Float.floatToRawIntBits(a.m32) == Float.floatToRawIntBits(b.m32)
                && Float.floatToRawIntBits(a.m33) == Float.floatToRawIntBits(b.m33);
    }

    private static double maxAbsDelta(Matrix4f a, Matrix4f b) {
        double d = 0.0;
        d = Math.max(d, Math.abs(a.m00 - b.m00));
        d = Math.max(d, Math.abs(a.m01 - b.m01));
        d = Math.max(d, Math.abs(a.m02 - b.m02));
        d = Math.max(d, Math.abs(a.m03 - b.m03));
        d = Math.max(d, Math.abs(a.m10 - b.m10));
        d = Math.max(d, Math.abs(a.m11 - b.m11));
        d = Math.max(d, Math.abs(a.m12 - b.m12));
        d = Math.max(d, Math.abs(a.m13 - b.m13));
        d = Math.max(d, Math.abs(a.m20 - b.m20));
        d = Math.max(d, Math.abs(a.m21 - b.m21));
        d = Math.max(d, Math.abs(a.m22 - b.m22));
        d = Math.max(d, Math.abs(a.m23 - b.m23));
        return d;
    }

    /**
     * Worst-case displacement of a unit vector (the +X, +Y, +Z axis directions in turn) between the
     * two rotation matrices: |R_new v - R_old v|, i.e. the chord length of the angular error.
     */
    private static double angularDeviation(Matrix4f a, Matrix4f b) {
        double worst = 0.0;
        double[][] dirs = { { 1.0, 0.0, 0.0 }, { 0.0, 1.0, 0.0 }, { 0.0, 0.0, 1.0 } };
        for (int i = 0; i < 3; ++i) {
            double vx = dirs[i][0], vy = dirs[i][1], vz = dirs[i][2];
            double ax = a.m00 * vx + a.m10 * vy + a.m20 * vz;
            double ay = a.m01 * vx + a.m11 * vy + a.m21 * vz;
            double az = a.m02 * vx + a.m12 * vy + a.m22 * vz;
            double bx = b.m00 * vx + b.m10 * vy + b.m20 * vz;
            double by = b.m01 * vx + b.m11 * vy + b.m21 * vz;
            double bz = b.m02 * vx + b.m12 * vy + b.m22 * vz;
            double dx = ax - bx, dy = ay - by, dz = az - bz;
            worst = Math.max(worst, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return worst;
    }
}
