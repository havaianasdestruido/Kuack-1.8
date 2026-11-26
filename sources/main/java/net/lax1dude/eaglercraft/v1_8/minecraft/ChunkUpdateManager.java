package net.lax1dude.eaglercraft.v1_8.minecraft;

import static net.lax1dude.eaglercraft.v1_8.opengl.RealOpenGLEnums.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.lax1dude.eaglercraft.v1_8.log4j.LogManager;
import net.lax1dude.eaglercraft.v1_8.log4j.Logger;
import net.lax1dude.eaglercraft.v1_8.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldVertexBufferUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.ListedRenderChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;

public class ChunkUpdateManager {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final int MAX_QUEUE_SIZE = 512;

	private final WorldVertexBufferUploader worldVertexUploader;
	private final RegionRenderCacheBuilder renderCache;

	private int chunkUpdatesTotal = 0;
	private int chunkUpdatesTotalLast = 0;
	private int chunkUpdatesTotalImmediate = 0;
	private int chunkUpdatesTotalImmediateLast = 0;
	private int chunkUpdatesQueued = 0;
	private int chunkUpdatesQueuedLast = 0;
	private long chunkUpdatesTotalLastUpdate = 0l;

	/** Pending mesh work, bounded by updateChunkLater to keep frame time predictable. */
	private final List<ChunkCompileTaskGenerator> queue = new ArrayList<>(256);
	/** O(1) per-section deduplication for queued rebuild and transparency tasks. */
	private final Map<RenderChunk, ChunkCompileTaskGenerator> queuedByChunk = new HashMap<>();

	public ChunkUpdateManager() {
		worldVertexUploader = new WorldVertexBufferUploader();
		renderCache = new RegionRenderCacheBuilder();
	}

	public static class EmptyBlockLayerException extends IllegalStateException {
	}

	private void runGenerator(ChunkCompileTaskGenerator generator, Entity entity) {
		generator.setRegionRenderCacheBuilder(renderCache);
		float f = (float) entity.posX;
		float f1 = (float) entity.posY + entity.getEyeHeight();
		float f2 = (float) entity.posZ;
		ChunkCompileTaskGenerator.Type chunkcompiletaskgenerator$type = generator.getType();
		generator.setStatus(ChunkCompileTaskGenerator.Status.COMPILING);
		if (chunkcompiletaskgenerator$type == ChunkCompileTaskGenerator.Type.REBUILD_CHUNK) {
			generator.getRenderChunk().rebuildChunk(f, f1, f2, generator);
		} else if (chunkcompiletaskgenerator$type == ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY) {
			RenderChunk r = generator.getRenderChunk();
			try {
				r.resortTransparency(f, f1, f2, generator);
				CompiledChunk ch = generator.getCompiledChunk();
				if (ch.isLayerEmpty(EnumWorldBlockLayer.TRANSLUCENT)
						&& ch.isLayerEmpty(EnumWorldBlockLayer.REALISTIC_WATER)) {
					throw new EmptyBlockLayerException();
				}
			} catch (EmptyBlockLayerException ex) {
				LOGGER.error("RenderChunk {} tried to update it's TRANSLUCENT layer with no proper initialization",
						r.getPosition());
				generator.setStatus(ChunkCompileTaskGenerator.Status.DONE);
				return;
			}
		}

		generator.setStatus(ChunkCompileTaskGenerator.Status.UPLOADING);

		final CompiledChunk compiledchunk = generator.getCompiledChunk();
		if (chunkcompiletaskgenerator$type == ChunkCompileTaskGenerator.Type.REBUILD_CHUNK) {
			EnumWorldBlockLayer[] en = EnumWorldBlockLayer._VALUES;
			for (int i = 0; i < en.length; ++i) {
				EnumWorldBlockLayer enumworldblocklayer = en[i];
				if (!compiledchunk.isLayerEmpty(enumworldblocklayer)) {
					this.uploadChunk(enumworldblocklayer,
							generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(enumworldblocklayer),
							generator.getRenderChunk(), compiledchunk);
					generator.setStatus(ChunkCompileTaskGenerator.Status.DONE);
				}
			}
			generator.getRenderChunk().setCompiledChunk(compiledchunk);
		} else if (chunkcompiletaskgenerator$type == ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY) {
			if (!compiledchunk.isLayerEmpty(EnumWorldBlockLayer.TRANSLUCENT)) {
				this.uploadChunk(EnumWorldBlockLayer.TRANSLUCENT,
						generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(EnumWorldBlockLayer.TRANSLUCENT),
						generator.getRenderChunk(), compiledchunk);
			}
			if (!compiledchunk.isLayerEmpty(EnumWorldBlockLayer.REALISTIC_WATER)) {
				this.uploadChunk(EnumWorldBlockLayer.REALISTIC_WATER,
						generator.getRegionRenderCacheBuilder()
								.getWorldRendererByLayer(EnumWorldBlockLayer.REALISTIC_WATER),
						generator.getRenderChunk(), compiledchunk);
			}
			generator.getRenderChunk().setCompiledChunk(compiledchunk);
			generator.setStatus(ChunkCompileTaskGenerator.Status.DONE);
		}
	}

	public boolean updateChunks(long timeout) {
		Entity entity = Minecraft.getMinecraft().getRenderViewEntity();
		if (entity == null) {
			stopChunkUpdates();
			return false;
		}

		boolean didWork = false;
		long millis = System.currentTimeMillis();
		long minBudgetDeadline = System.nanoTime()
				+ (long) SodiumClientMod.options().advanced.chunkUpdateBudgetMs * 1000000L;
		long effectiveTimeout = Math.max(timeout, minBudgetDeadline);
		List<ChunkCompileTaskGenerator> deferred = new ArrayList<>();

		while (!queue.isEmpty()) {
			int bestIndex = -1;
			double bestDistance = Double.POSITIVE_INFINITY;

			for (int i = 0; i < queue.size();) {
				ChunkCompileTaskGenerator task = queue.get(i);
				if (task.isFinished() || task.getStatus() != ChunkCompileTaskGenerator.Status.PENDING) {
					queue.remove(i);
					removeQueuedMapping(task);
					continue;
				}

				if (!task.canExecuteYet()) {
					queue.remove(i);
					if (millis - task.goddamnFuckingTimeout < 60000L) {
						deferred.add(task);
					} else {
						removeQueuedMapping(task);
						task.finish();
					}
					continue;
				}

				BlockPos pos = task.getRenderChunk().getPosition();
				double dx = (double) pos.getX() + 8.0D - entity.posX;
				double dy = (double) pos.getY() + 8.0D - entity.posY;
				double dz = (double) pos.getZ() + 8.0D - entity.posZ;
				double distance = dx * dx + dy * dy + dz * dz;
				if (distance < bestDistance) {
					bestDistance = distance;
					bestIndex = i;
				}
				++i;
			}

			if (bestIndex < 0) {
				break;
			}

			ChunkCompileTaskGenerator task = queue.remove(bestIndex);
			removeQueuedMapping(task);
			runGenerator(task, entity);
			didWork = true;
			++chunkUpdatesTotal;

			if (effectiveTimeout < System.nanoTime()) {
				break;
			}
		}
		queue.addAll(deferred);
		return didWork;
	}

	public boolean updateChunkLater(RenderChunk chunkRenderer) {
		final ChunkCompileTaskGenerator task = chunkRenderer.makeCompileTaskChunk();
		if (queue.size() >= MAX_QUEUE_SIZE) {
			task.finish();
			return false;
		}
		enqueue(task);
		return true;
	}

	private void enqueue(final ChunkCompileTaskGenerator task) {
		task.addFinishRunnable(new Runnable() {
			@Override
			public void run() {
				if (queue.remove(task)) {
					++chunkUpdatesTotal;
				}
				removeQueuedMapping(task);
			}
		});
		queue.add(task);
		queuedByChunk.put(task.getRenderChunk(), task);
		++chunkUpdatesQueued;
	}

	private void removeQueuedMapping(ChunkCompileTaskGenerator task) {
		RenderChunk chunk = task.getRenderChunk();
		if (queuedByChunk.get(chunk) == task) {
			queuedByChunk.remove(chunk);
		}
	}

	public boolean updateChunkNow(RenderChunk chunkRenderer) {
		Entity entity = Minecraft.getMinecraft().getRenderViewEntity();
		if (entity != null) {
			runGenerator(chunkRenderer.makeCompileTaskChunk(), entity);
			++chunkUpdatesTotalImmediate;
		}
		return true;
	}

	public void stopChunkUpdates() {
		List<ChunkCompileTaskGenerator> pending = new ArrayList<>(queue);
		queue.clear();
		queuedByChunk.clear();
		chunkUpdatesQueued = 0;
		for (int i = 0, l = pending.size(); i < l; ++i) {
			ChunkCompileTaskGenerator task = pending.get(i);
			if (!task.isFinished()) {
				task.finish();
			}
		}
	}

	public boolean updateTransparencyLater(RenderChunk chunkRenderer) {
		if (isAlreadyQueued(chunkRenderer)) {
			return true;
		}
		final ChunkCompileTaskGenerator task = chunkRenderer.makeCompileTaskTransparency();
		if (task == null) {
			return true;
		}
		task.goddamnFuckingTimeout = System.currentTimeMillis();
		if (queue.size() >= MAX_QUEUE_SIZE) {
			task.finish();
			return false;
		}
		enqueue(task);
		return true;
	}

	public void uploadChunk(final EnumWorldBlockLayer player, final WorldRenderer chunkRenderer,
			final RenderChunk compiledChunkIn, final CompiledChunk parCompiledChunk) {
		if (SodiumClientMod.options().performance.useRegionBatching && compiledChunkIn instanceof ListedRenderChunk) {
			ListedRenderChunk listed = (ListedRenderChunk) compiledChunkIn;
			listed.flushLayerDisplayList(player.ordinal());
			SodiumWorldRenderer.instance().getRegionManager().uploadChunkLayer(listed, player, chunkRenderer);
		} else {
			this.uploadDisplayList(chunkRenderer,
					((ListedRenderChunk) compiledChunkIn).getDisplayList(player, parCompiledChunk), compiledChunkIn);
		}
		chunkRenderer.setTranslation(0.0D, 0.0D, 0.0D);
	}

	private void uploadDisplayList(WorldRenderer chunkRenderer, int parInt1, RenderChunk parRenderChunk) {
		EaglercraftGPU.glNewList(parInt1, GL_COMPILE);
		GlStateManager.pushMatrix();
		this.worldVertexUploader.func_181679_a(chunkRenderer);
		GlStateManager.popMatrix();
		EaglercraftGPU.glEndList();
	}

	public boolean isAlreadyQueued(RenderChunk update) {
		return queuedByChunk.containsKey(update);
	}

	public String getDebugInfo() {
		long millis = System.currentTimeMillis();

		if (millis - chunkUpdatesTotalLastUpdate > 500l) {
			chunkUpdatesTotalLastUpdate = millis;
			chunkUpdatesTotalLast = chunkUpdatesTotal;
			chunkUpdatesTotalImmediateLast = chunkUpdatesTotalImmediate;
			chunkUpdatesTotalImmediate = 0;
			chunkUpdatesTotal = 0;
			chunkUpdatesQueuedLast = chunkUpdatesQueued;
			chunkUpdatesQueued -= chunkUpdatesTotalLast;
			if (chunkUpdatesQueued < 0) {
				chunkUpdatesQueued = 0;
			}
		}

		return "Uq: " + (chunkUpdatesTotalLast + chunkUpdatesTotalImmediateLast) + "/"
				+ (chunkUpdatesQueuedLast + chunkUpdatesTotalImmediateLast);
	}

}
