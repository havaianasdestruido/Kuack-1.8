package net.caffeinemc.mods.sodium.client.render.cloud;

import static net.lax1dude.eaglercraft.v1_8.opengl.RealOpenGLEnums.*;

import java.io.InputStream;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.lax1dude.eaglercraft.v1_8.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.ImageData;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.client.multiplayer.WorldClient;

/**
 * Sodium-style cloud mesh renderer: extracts cloud texel mask from clouds.png once per resource reload,
 * culls all interior faces between adjacent cloud texels, and caches compiled GPU geometry across frames.
 */
public final class SodiumCloudRenderer {

	private static final ResourceLocation CLOUDS_LOCATION = new ResourceLocation("textures/environment/clouds.png");
	private static final float CELL_WIDTH = 12.0F;
	private static final float CELL_HEIGHT = 4.0F;

	private int texWidth = 0;
	private int texHeight = 0;
	private int[] texels = null;
	private boolean textureLoaded = false;

	private int displayList = -1;
	private int cachedCellX = Integer.MIN_VALUE;
	private int cachedCellZ = Integer.MIN_VALUE;
	private int cachedViewBand = -1;
	private boolean cachedFancy = false;
	private int cachedRadiusCells = -1;
	private boolean hasGeometry = false;

	public void reloadTextures(IResourceManager resourceManager) {
		invalidateMesh();
		this.textureLoaded = true;
		this.texels = null;
		this.texWidth = 0;
		this.texHeight = 0;
		try {
			IResource res = resourceManager.getResource(CLOUDS_LOCATION);
			try (InputStream in = res.getInputStream()) {
				ImageData img = TextureUtil.readBufferedImage(in);
				if (img != null && img.width > 0 && img.height > 0 && img.pixels != null) {
					this.texWidth = img.width;
					this.texHeight = img.height;
					this.texels = img.pixels;
				}
			}
		} catch (Throwable t) {
			SodiumClientMod.logger().warn("Failed to load clouds.png for SodiumCloudRenderer: {}", t.toString());
		}
	}

	public void invalidateMesh() {
		this.cachedCellX = Integer.MIN_VALUE;
		this.cachedCellZ = Integer.MIN_VALUE;
		this.cachedViewBand = -1;
	}

	public boolean renderClouds(Minecraft mc, WorldClient world, int cloudTickCounter, float partialTicks, int pass,
			boolean fancyClouds) {
		if (!this.textureLoaded) {
			reloadTextures(mc.getResourceManager());
		}
		if (this.texels == null || this.texWidth <= 0 || this.texHeight <= 0) {
			return false;
		}

		Entity view = mc.getRenderViewEntity();
		if (view == null) {
			return true;
		}

		double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * (double) partialTicks;
		double cloudTime = (double) ((float) cloudTickCounter + partialTicks);
		double cloudWorldX = (view.prevPosX + (view.posX - view.prevPosX) * (double) partialTicks
				+ cloudTime * 0.029999999329447746D) / (double) CELL_WIDTH;
		double cloudWorldZ = (view.prevPosZ + (view.posZ - view.prevPosZ) * (double) partialTicks) / (double) CELL_WIDTH
				+ 0.33000001311302185D;

		float cloudRelY = world.provider.getCloudHeight() - (float) camY + 0.33F;
		int cellX = MathHelper.floor_double(cloudWorldX);
		int cellZ = MathHelper.floor_double(cloudWorldZ);
		float subX = (float) (cloudWorldX - (double) cellX);
		float subZ = (float) (cloudWorldZ - (double) cellZ);

		int viewBand;
		if (!fancyClouds) {
			viewBand = 0;
		} else if (cloudRelY > 0.1F) {
			viewBand = 0; // Below clouds
		} else if (cloudRelY < -CELL_HEIGHT - 0.1F) {
			viewBand = 2; // Above clouds
		} else {
			viewBand = 1; // Inside cloud slab
		}

		int distChunks = SodiumClientMod.options().quality.cloudDistanceChunks;
		int radiusCells = MathHelper.clamp_int(((distChunks << 4) / 12) + 2, 6, 48);

		if (this.displayList < 0 || cellX != this.cachedCellX || cellZ != this.cachedCellZ
				|| viewBand != this.cachedViewBand || fancyClouds != this.cachedFancy
				|| radiusCells != this.cachedRadiusCells) {
			rebuildDisplayList(cellX, cellZ, viewBand, fancyClouds, radiusCells);
		}

		if (!this.hasGeometry) {
			return true;
		}

		Vec3 skyCloudColor = world.getCloudColour(partialTicks);
		float r = (float) skyCloudColor.xCoord;
		float g = (float) skyCloudColor.yCoord;
		float b = (float) skyCloudColor.zCoord;
		if (pass != 2) {
			float ar = (r * 30.0F + g * 59.0F + b * 11.0F) / 100.0F;
			float ag = (r * 30.0F + g * 70.0F) / 100.0F;
			float ab = (r * 30.0F + b * 70.0F) / 100.0F;
			r = ar;
			g = ag;
			b = ab;
		}

		GlStateManager.disableTexture2D();
		GlStateManager.disableCull();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0);
		GlStateManager.color(r, g, b, 1.0F);

		GlStateManager.pushMatrix();
		GlStateManager.translate(-subX * CELL_WIDTH, cloudRelY, -subZ * CELL_WIDTH);

		if (fancyClouds) {
			// Two-pass depth + color using the single cached DisplayList for artifact-free translucency
			GlStateManager.colorMask(false, false, false, false);
			EaglercraftGPU.glCallList(this.displayList);

			switch (pass) {
			case 0:
				GlStateManager.colorMask(false, true, true, true);
				break;
			case 1:
				GlStateManager.colorMask(true, false, false, true);
				break;
			case 2:
			default:
				GlStateManager.colorMask(true, true, true, true);
				break;
			}
			EaglercraftGPU.glCallList(this.displayList);
		} else {
			EaglercraftGPU.glCallList(this.displayList);
		}

		GlStateManager.popMatrix();
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.disableBlend();
		GlStateManager.enableCull();
		GlStateManager.enableTexture2D();
		return true;
	}

	private void rebuildDisplayList(int centerX, int centerZ, int viewBand, boolean fancy, int radius) {
		if (this.displayList < 0) {
			this.displayList = GLAllocation.generateDisplayLists();
		}

		Tessellator tess = Tessellator.getInstance();
		WorldRenderer wr = tess.getWorldRenderer();
		wr.begin(GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

		int quadCount = 0;
		int radSq = radius * radius;
		boolean emitBottom = !fancy || viewBand <= 1;
		boolean emitTop = fancy && viewBand >= 1;

		for (int dz = -radius; dz <= radius; ++dz) {
			for (int dx = -radius; dx <= radius; ++dx) {
				if (dx * dx + dz * dz > radSq) {
					continue;
				}
				int gx = centerX + dx;
				int gz = centerZ + dz;
				int argb = sampleTexel(gx, gz);
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha < 128) {
					continue;
				}

				float baseR = ((argb >>> 16) & 0xFF) / 255.0F;
				float baseG = ((argb >>> 8) & 0xFF) / 255.0F;
				float baseB = (argb & 0xFF) / 255.0F;
				float a = 0.8F;

				float x0 = (float) dx * CELL_WIDTH;
				float x1 = x0 + CELL_WIDTH;
				float z0 = (float) dz * CELL_WIDTH;
				float z1 = z0 + CELL_WIDTH;

				if (!fancy) {
					wr.pos(x0, 0.0D, z1).color(baseR, baseG, baseB, a).endVertex();
					wr.pos(x1, 0.0D, z1).color(baseR, baseG, baseB, a).endVertex();
					wr.pos(x1, 0.0D, z0).color(baseR, baseG, baseB, a).endVertex();
					wr.pos(x0, 0.0D, z0).color(baseR, baseG, baseB, a).endVertex();
					++quadCount;
					continue;
				}

				if (emitBottom) {
					float br = baseR * 0.7F;
					float bg = baseG * 0.7F;
					float bb = baseB * 0.7F;
					wr.pos(x0, 0.0D, z1).color(br, bg, bb, a).endVertex();
					wr.pos(x1, 0.0D, z1).color(br, bg, bb, a).endVertex();
					wr.pos(x1, 0.0D, z0).color(br, bg, bb, a).endVertex();
					wr.pos(x0, 0.0D, z0).color(br, bg, bb, a).endVertex();
					++quadCount;
				}

				if (emitTop) {
					wr.pos(x0, CELL_HEIGHT, z1).color(baseR, baseG, baseB, a).endVertex();
					wr.pos(x1, CELL_HEIGHT, z1).color(baseR, baseG, baseB, a).endVertex();
					wr.pos(x1, CELL_HEIGHT, z0).color(baseR, baseG, baseB, a).endVertex();
					wr.pos(x0, CELL_HEIGHT, z0).color(baseR, baseG, baseB, a).endVertex();
					++quadCount;
				}

				// Cull interior vertical faces where adjacent texel is also opaque!
				float xr = baseR * 0.9F;
				float xg = baseG * 0.9F;
				float xb = baseB * 0.9F;
				if (dx > -1 && !isSolidTexel(gx - 1, gz)) {
					wr.pos(x0, 0.0D, z1).color(xr, xg, xb, a).endVertex();
					wr.pos(x0, CELL_HEIGHT, z1).color(xr, xg, xb, a).endVertex();
					wr.pos(x0, CELL_HEIGHT, z0).color(xr, xg, xb, a).endVertex();
					wr.pos(x0, 0.0D, z0).color(xr, xg, xb, a).endVertex();
					++quadCount;
				}
				if (dx < 1 && !isSolidTexel(gx + 1, gz)) {
					wr.pos(x1, 0.0D, z1).color(xr, xg, xb, a).endVertex();
					wr.pos(x1, CELL_HEIGHT, z1).color(xr, xg, xb, a).endVertex();
					wr.pos(x1, CELL_HEIGHT, z0).color(xr, xg, xb, a).endVertex();
					wr.pos(x1, 0.0D, z0).color(xr, xg, xb, a).endVertex();
					++quadCount;
				}

				float zr = baseR * 0.8F;
				float zg = baseG * 0.8F;
				float zb = baseB * 0.8F;
				if (dz > -1 && !isSolidTexel(gx, gz - 1)) {
					wr.pos(x0, CELL_HEIGHT, z0).color(zr, zg, zb, a).endVertex();
					wr.pos(x1, CELL_HEIGHT, z0).color(zr, zg, zb, a).endVertex();
					wr.pos(x1, 0.0D, z0).color(zr, zg, zb, a).endVertex();
					wr.pos(x0, 0.0D, z0).color(zr, zg, zb, a).endVertex();
					++quadCount;
				}
				if (dz < 1 && !isSolidTexel(gx, gz + 1)) {
					wr.pos(x0, CELL_HEIGHT, z1).color(zr, zg, zb, a).endVertex();
					wr.pos(x1, CELL_HEIGHT, z1).color(zr, zg, zb, a).endVertex();
					wr.pos(x1, 0.0D, z1).color(zr, zg, zb, a).endVertex();
					wr.pos(x0, 0.0D, z1).color(zr, zg, zb, a).endVertex();
					++quadCount;
				}
			}
		}

		this.cachedCellX = centerX;
		this.cachedCellZ = centerZ;
		this.cachedViewBand = viewBand;
		this.cachedFancy = fancy;
		this.cachedRadiusCells = radius;
		this.hasGeometry = quadCount > 0;

		if (this.hasGeometry) {
			EaglercraftGPU.glNewList(this.displayList, GL_COMPILE);
			tess.draw();
			EaglercraftGPU.glEndList();
		} else {
			wr.finishDrawing();
			wr.reset();
			EaglercraftGPU.flushDisplayList(this.displayList);
		}
	}

	private int sampleTexel(int gx, int gz) {
		int w = this.texWidth;
		int h = this.texHeight;
		int tx = gx % w;
		if (tx < 0) {
			tx += w;
		}
		int tz = gz % h;
		if (tz < 0) {
			tz += h;
		}
		return this.texels[tz * w + tx];
	}

	private boolean isSolidTexel(int gx, int gz) {
		return ((sampleTexel(gx, gz) >>> 24) & 0xFF) >= 128;
	}

	public void destroy() {
		if (this.displayList >= 0) {
			GLAllocation.deleteDisplayLists(this.displayList);
			this.displayList = -1;
		}
		this.hasGeometry = false;
		invalidateMesh();
	}
}
