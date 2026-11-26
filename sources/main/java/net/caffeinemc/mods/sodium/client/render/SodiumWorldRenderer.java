package net.caffeinemc.mods.sodium.client.render;

import java.util.List;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.SodiumGameOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SodiumOcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.region.SodiumChunkRenderContainer;
import net.caffeinemc.mods.sodium.client.render.chunk.region.SodiumRenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.cloud.SodiumCloudRenderer;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.client.render.viewport.SodiumViewportFrustum;
import net.lax1dude.eaglercraft.v1_8.HString;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.client.multiplayer.WorldClient;

/**
 * Central coordinator for baked-in Sodium rendering subsystems:
 * - 8x4x8 GPU Region Arena VBO batching
 * - Zero-allocation Graph & Fog Occlusion Culler
 * - Entity / BlockEntity / Particle Frustum & Occlusion Culling
 * - Cached Face-Culled Cloud Mesh Renderer
 * - Debug (F3) & Settings VRAM telemetry
 */
public final class SodiumWorldRenderer {

	private static final SodiumWorldRenderer INSTANCE = new SodiumWorldRenderer();

	private final SodiumRenderRegionManager regionManager = new SodiumRenderRegionManager();
	private final SodiumChunkRenderContainer renderContainer = new SodiumChunkRenderContainer(this.regionManager);
	private final SodiumOcclusionCuller occlusionCuller = new SodiumOcclusionCuller();
	private final SodiumViewportFrustum viewportFrustum = new SodiumViewportFrustum();
	private final SodiumCloudRenderer cloudRenderer = new SodiumCloudRenderer();
	private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();

	private ViewFrustum activeViewFrustum;
	private int currentFrameIndex = 0;
	private int culledEntitiesLastFrame = 0;
	private int culledParticlesLastFrame = 0;
	private int entityCullCounter = 0;
	private int particleCullCounter = 0;

	private SodiumWorldRenderer() {
	}

	public static SodiumWorldRenderer instance() {
		return INSTANCE;
	}

	public SodiumRenderRegionManager getRegionManager() {
		return this.regionManager;
	}

	public SodiumChunkRenderContainer getRenderContainer() {
		return this.renderContainer;
	}

	public SodiumOcclusionCuller getOcclusionCuller() {
		return this.occlusionCuller;
	}

	public SodiumViewportFrustum getViewportFrustum() {
		return this.viewportFrustum;
	}

	public SodiumCloudRenderer getCloudRenderer() {
		return this.cloudRenderer;
	}

	public void onWorldReload(ViewFrustum newViewFrustum) {
		this.regionManager.destroyAll();
		this.occlusionCuller.invalidateEyeCache();
		this.cloudRenderer.invalidateMesh();
		this.activeViewFrustum = newViewFrustum;
	}

	public void onWorldUnload() {
		this.regionManager.destroyAll();
		this.occlusionCuller.invalidateEyeCache();
		this.cloudRenderer.destroy();
		this.activeViewFrustum = null;
	}

	public void onResourceManagerReload(IResourceManager resourceManager) {
		this.cloudRenderer.reloadTextures(resourceManager);
	}

	public void beginTerrainSetup(ICamera camera, double camX, double camY, double camZ, int frameCount) {
		this.currentFrameIndex = frameCount;
		this.culledEntitiesLastFrame = this.entityCullCounter;
		this.culledParticlesLastFrame = this.particleCullCounter;
		this.entityCullCounter = 0;
		this.particleCullCounter = 0;
		this.regionManager.beginFrame();
		this.viewportFrustum.update(camera, camX, camY, camZ);
	}

	public void setupVisibleTerrain(ViewFrustum viewFrustum, World world, double camX, double camY, double camZ,
			double eyeY, float viewVecX, float viewVecY, float viewVecZ, int renderDistanceChunks, int frameCount,
			boolean spectator, boolean useOcclusionGraph, List<RenderChunk> visibleChunks) {
		this.activeViewFrustum = viewFrustum;
		this.occlusionCuller.findVisibleChunks(viewFrustum, world, this.viewportFrustum, camX, camY, camZ, eyeY,
				viewVecX, viewVecY, viewVecZ, renderDistanceChunks, frameCount, spectator, useOcclusionGraph,
				visibleChunks);
	}

	public void markVisibleChunkAnimations(List<RenderChunk> visibleChunks) {
		if (!SodiumClientMod.options().performance.animateOnlyVisibleTextures) {
			return;
		}
		for (int i = 0, n = visibleChunks.size(); i < n; ++i) {
			CompiledChunk cc = visibleChunks.get(i).getCompiledChunk();
			if (cc != null && cc != CompiledChunk.DUMMY) {
				SpriteUtil.markSpritesActive(cc.getSodiumAnimatedSprites());
			}
		}
	}

	public boolean isEntityVisible(Entity entity, double camX, double camY, double camZ) {
		if (entity == null || entity.ignoreFrustumCheck) {
			return true;
		}
		SodiumGameOptions opts = SodiumClientMod.options();
		double scaleSq = opts.getEntityDistanceScaleSq();
		if (scaleSq != 1.0D) {
			double dx = entity.posX - camX;
			double dy = entity.posY - camY;
			double dz = entity.posZ - camZ;
			double distSq = (dx * dx + dy * dy + dz * dz) / scaleSq;
			if (!entity.isInRangeToRenderDist(distSq)) {
				++this.entityCullCounter;
				return false;
			}
		}

		if (!opts.performance.useEntityCulling || this.activeViewFrustum == null) {
			return true;
		}

		AxisAlignedBB box = entity.getEntityBoundingBox();
		if (box == null) {
			return true;
		}

		int minCX = MathHelper.floor_double(box.minX - 0.5D) >> 4;
		int maxCX = MathHelper.floor_double(box.maxX + 0.5D) >> 4;
		int minCY = MathHelper.clamp_int(MathHelper.floor_double(box.minY - 0.5D) >> 4, 0, 15);
		int maxCY = MathHelper.clamp_int(MathHelper.floor_double(box.maxY + 0.5D) >> 4, 0, 15);
		int minCZ = MathHelper.floor_double(box.minZ - 0.5D) >> 4;
		int maxCZ = MathHelper.floor_double(box.maxZ + 0.5D) >> 4;

		for (int cy = minCY; cy <= maxCY; ++cy) {
			for (int cz = minCZ; cz <= maxCZ; ++cz) {
				for (int cx = minCX; cx <= maxCX; ++cx) {
					this.scratchPos.func_181079_c(cx << 4, cy << 4, cz << 4);
					RenderChunk rc = this.activeViewFrustum.sodiumGetRenderChunk(this.scratchPos);
					if (rc == null || rc.sodiumVisibleFrame == this.currentFrameIndex) {
						return true;
					}
				}
			}
		}
		++this.entityCullCounter;
		return false;
	}

	public boolean isTileEntityVisible(TileEntity te, double camX, double camY, double camZ) {
		if (te == null) {
			return false;
		}
		SodiumGameOptions opts = SodiumClientMod.options();
		double maxDistSq = te.getMaxRenderDistanceSquared() * opts.getEntityDistanceScaleSq();
		if (te.getDistanceSq(camX, camY, camZ) >= maxDistSq) {
			return false;
		}
		if (te instanceof TileEntityBeacon) {
			return true;
		}
		if (!opts.performance.useEntityCulling || !this.viewportFrustum.isValid()) {
			return true;
		}
		BlockPos p = te.getPos();
		// Expand by 1 block to safely accommodate double chests, signs, banners, pistons
		return this.viewportFrustum.testBox(p.getX() - 1.0D, p.getY() - 1.0D, p.getZ() - 1.0D, p.getX() + 2.0D,
				p.getY() + 2.0D, p.getZ() + 2.0D);
	}

	public boolean shouldRenderParticle(EntityFX fx) {
		if (fx == null) {
			return false;
		}
		if (!SodiumClientMod.options().performance.useParticleCulling || !this.viewportFrustum.isValid()) {
			return true;
		}
		if (!this.viewportFrustum.testSphere(fx.posX, fx.posY, fx.posZ, 1.5F)) {
			++this.particleCullCounter;
			return false;
		}
		if (this.activeViewFrustum != null && fx.posY >= 0.0D && fx.posY < 256.0D) {
			this.scratchPos.func_181079_c(MathHelper.floor_double(fx.posX), MathHelper.floor_double(fx.posY),
					MathHelper.floor_double(fx.posZ));
			RenderChunk rc = this.activeViewFrustum.sodiumGetRenderChunk(this.scratchPos);
			if (rc != null && rc.sodiumVisibleFrame != this.currentFrameIndex) {
				++this.particleCullCounter;
				return false;
			}
		}
		return true;
	}

	public boolean renderClouds(Minecraft mc, WorldClient world, int cloudTickCounter, float partialTicks, int pass,
			boolean fancyClouds) {
		if (!SodiumClientMod.options().performance.useCloudOptimization) {
			return false;
		}
		return this.cloudRenderer.renderClouds(mc, world, cloudTickCounter, partialTicks, pass, fancyClouds);
	}

	public void appendDebugLines(List<String> rightColumnLines) {
		SodiumGameOptions opts = SodiumClientMod.options();
		rightColumnLines.add("");
		rightColumnLines.add(EnumChatFormatting.GREEN + SodiumClientMod.MOD_NAME + " Renderer "
				+ SodiumClientMod.MOD_VERSION);

		if (opts.performance.useRegionBatching) {
			long usedMB = this.regionManager.getTotalUsedBytes() / (1024L * 1024L);
			long capMB = this.regionManager.getTotalCapacityBytes() / (1024L * 1024L);
			long usedKB = (this.regionManager.getTotalUsedBytes() / 1024L);
			String memText = capMB > 0 ? (usedMB + " / " + capMB + " MB") : (usedKB + " KB");
			rightColumnLines.add(HString.format("Chunk Arena: %s (%d regions)",
					new Object[] { memText, Integer.valueOf(this.regionManager.getRegionCount()) }));
			rightColumnLines.add(HString.format("Region Batches: %d draws (%d EBO hits, %d sections)",
					new Object[] { Integer.valueOf(this.regionManager.getLastFrameDrawCalls()),
							Integer.valueOf(this.regionManager.getLastFrameEboHits()),
							Integer.valueOf(this.regionManager.getLastFrameSections()) }));
		} else {
			rightColumnLines.add("Chunk Backend: Legacy DisplayLists");
		}

		rightColumnLines.add(HString.format("Culling: -%d fog, -%d ent, -%d part | Anim: %d/%d",
				new Object[] { Integer.valueOf(this.occlusionCuller.getLastFogCulledSections()),
						Integer.valueOf(this.culledEntitiesLastFrame), Integer.valueOf(this.culledParticlesLastFrame),
						Integer.valueOf(SpriteUtil.getActiveAnimatedSprites()),
						Integer.valueOf(SpriteUtil.getTotalAnimatedSprites()) }));
	}
}
