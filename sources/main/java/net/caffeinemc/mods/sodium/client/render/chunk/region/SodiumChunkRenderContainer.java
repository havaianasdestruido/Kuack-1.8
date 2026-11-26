package net.caffeinemc.mods.sodium.client.render.chunk.region;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.lax1dude.eaglercraft.v1_8.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.chunk.ListedRenderChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.EnumWorldBlockLayer;

/**
 * Drop-in replacement for vanilla RenderList that batches chunk sections into
 * SodiumRenderRegions (up to 256 sections per drawElements call) when Region Batching
 * is enabled, with seamless fallback to DisplayLists when disabled.
 */
public final class SodiumChunkRenderContainer extends ChunkRenderContainer {

	private final SodiumRenderRegionManager regionManager;
	private double camX;
	private double camY;
	private double camZ;
	private boolean primaryCameraPass = true;

	public SodiumChunkRenderContainer(SodiumRenderRegionManager regionManager) {
		this.regionManager = regionManager;
	}

	@Override
	public void initialize(double viewEntityXIn, double viewEntityYIn, double viewEntityZIn) {
		super.initialize(viewEntityXIn, viewEntityYIn, viewEntityZIn);
		this.camX = viewEntityXIn;
		this.camY = viewEntityYIn;
		this.camZ = viewEntityZIn;
	}

	public void setPrimaryCameraPass(boolean primaryCameraPass) {
		this.primaryCameraPass = primaryCameraPass;
	}

	@Override
	public void renderChunkLayer(EnumWorldBlockLayer layer) {
		if (!this.initialized) {
			return;
		}
		int totalChunks = this.renderChunks.size();
		if (totalChunks == 0) {
			return;
		}

		if (SodiumClientMod.options().performance.useRegionBatching) {
			int layerIdx = layer.ordinal();
			for (int i = 0; i < totalChunks; ++i) {
				RenderChunk rc = this.renderChunks.get(i);
				if (rc instanceof ListedRenderChunk) {
					this.regionManager.enqueueForDraw((ListedRenderChunk) rc, layerIdx);
				}
			}
			this.regionManager.flushLayerBatch(layer, this.camX, this.camY, this.camZ, this.primaryCameraPass);
		} else {
			for (int i = 0; i < totalChunks; ++i) {
				ListedRenderChunk listed = (ListedRenderChunk) this.renderChunks.get(i);
				GlStateManager.pushMatrix();
				this.preRenderChunk(listed, layer);
				EaglercraftGPU.glCallList(listed.getDisplayList(layer, listed.getCompiledChunk()));
				GlStateManager.popMatrix();
			}
		}

		GlStateManager.resetColor();
		this.renderChunks.clear();
	}
}
