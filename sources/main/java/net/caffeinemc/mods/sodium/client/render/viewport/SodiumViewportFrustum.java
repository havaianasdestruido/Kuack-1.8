package net.caffeinemc.mods.sodium.client.render.viewport;

import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.util.AxisAlignedBB;

/**
 * Unboxed 6-plane frustum evaluator using positive-vertex p-vertex testing (6 dot products
 * instead of 24 per AABB) and sphere culling for particles.
 */
public final class SodiumViewportFrustum {

	private final float[] nx = new float[6];
	private final float[] ny = new float[6];
	private final float[] nz = new float[6];
	private final float[] dist = new float[6];
	private double camX;
	private double camY;
	private double camZ;
	private boolean valid = false;
	private ICamera fallbackCamera;

	public void update(ICamera camera, double camX, double camY, double camZ) {
		this.camX = camX;
		this.camY = camY;
		this.camZ = camZ;
		this.fallbackCamera = camera;

		if (camera instanceof Frustum) {
			Frustum f = (Frustum) camera;
			ClippingHelper ch = f.sodiumGetClippingHelper();
			if (ch != null && ch.frustum != null) {
				this.camX = f.sodiumGetX();
				this.camY = f.sodiumGetY();
				this.camZ = f.sodiumGetZ();
				for (int i = 0; i < 6; ++i) {
					float[] plane = ch.frustum[i];
					float a = plane[0];
					float b = plane[1];
					float c = plane[2];
					float d = plane[3];
					float invLen = 1.0F / (float) Math.sqrt(a * a + b * b + c * c);
					this.nx[i] = a * invLen;
					this.ny[i] = b * invLen;
					this.nz[i] = c * invLen;
					this.dist[i] = d * invLen;
				}
				this.valid = true;
				return;
			}
		}
		this.valid = false;
	}

	public boolean isValid() {
		return this.valid;
	}

	public boolean testBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		if (!this.valid) {
			return this.fallbackCamera == null
					|| this.fallbackCamera.isBoundingBoxInFrustum(new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ));
		}

		float x0 = (float) (minX - this.camX);
		float y0 = (float) (minY - this.camY);
		float z0 = (float) (minZ - this.camZ);
		float x1 = (float) (maxX - this.camX);
		float y1 = (float) (maxY - this.camY);
		float z1 = (float) (maxZ - this.camZ);

		for (int i = 0; i < 6; ++i) {
			float px = this.nx[i] >= 0.0F ? x1 : x0;
			float py = this.ny[i] >= 0.0F ? y1 : y0;
			float pz = this.nz[i] >= 0.0F ? z1 : z0;
			if (this.nx[i] * px + this.ny[i] * py + this.nz[i] * pz + this.dist[i] < 0.0F) {
				return false;
			}
		}
		return true;
	}

	public boolean testBox(AxisAlignedBB box) {
		if (!this.valid) {
			return this.fallbackCamera == null || this.fallbackCamera.isBoundingBoxInFrustum(box);
		}
		return testBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
	}

	public boolean testSphere(double worldX, double worldY, double worldZ, float radius) {
		if (!this.valid) {
			return true;
		}
		float x = (float) (worldX - this.camX);
		float y = (float) (worldY - this.camY);
		float z = (float) (worldZ - this.camZ);
		for (int i = 0; i < 6; ++i) {
			if (this.nx[i] * x + this.ny[i] * y + this.nz[i] * z + this.dist[i] < -radius) {
				return false;
			}
		}
		return true;
	}
}
