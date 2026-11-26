package net.caffeinemc.mods.sodium.client.gui;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.log4j.LogManager;
import net.lax1dude.eaglercraft.v1_8.log4j.Logger;
import net.minecraft.util.MathHelper;

public class SodiumGameOptions {

	private static final Logger LOGGER = LogManager.getLogger("SodiumConfig");
	public static final String STORAGE_KEY = "sodium";

	public final QualitySettings quality = new QualitySettings();
	public final PerformanceSettings performance = new PerformanceSettings();
	public final AdvancedSettings advanced = new AdvancedSettings();

	public static class QualitySettings {
		public GraphicsQuality weatherQuality = GraphicsQuality.DEFAULT;
		public GraphicsQuality leavesQuality = GraphicsQuality.DEFAULT;
		public boolean enableVignette = true;
		public int biomeBlendRadius = 2;
		public int cloudDistanceChunks = 16;

		public void copyFrom(QualitySettings other) {
			this.weatherQuality = other.weatherQuality;
			this.leavesQuality = other.leavesQuality;
			this.enableVignette = other.enableVignette;
			this.biomeBlendRadius = other.biomeBlendRadius;
			this.cloudDistanceChunks = other.cloudDistanceChunks;
		}
	}

	public static class PerformanceSettings {
		public ChunkBuildDeferMode chunkBuildDeferMode = ChunkBuildDeferMode.ONE_FRAME;
		public boolean useRegionBatching = true;
		public boolean useBlockFaceCulling = true;
		public boolean useFogOcclusion = true;
		public boolean useEntityCulling = true;
		public boolean animateOnlyVisibleTextures = true;
		public boolean useParticleCulling = true;
		public boolean useCloudOptimization = true;
		public boolean useTranslucentFaceSorting = true;

		public void copyFrom(PerformanceSettings other) {
			this.chunkBuildDeferMode = other.chunkBuildDeferMode;
			this.useRegionBatching = other.useRegionBatching;
			this.useBlockFaceCulling = other.useBlockFaceCulling;
			this.useFogOcclusion = other.useFogOcclusion;
			this.useEntityCulling = other.useEntityCulling;
			this.animateOnlyVisibleTextures = other.animateOnlyVisibleTextures;
			this.useParticleCulling = other.useParticleCulling;
			this.useCloudOptimization = other.useCloudOptimization;
			this.useTranslucentFaceSorting = other.useTranslucentFaceSorting;
		}
	}

	public static class AdvancedSettings {
		public int chunkUpdateBudgetMs = 6;
		public int entityDistancePercent = 100;
		public boolean useCompactRegionStaging = true;

		public void copyFrom(AdvancedSettings other) {
			this.chunkUpdateBudgetMs = other.chunkUpdateBudgetMs;
			this.entityDistancePercent = other.entityDistancePercent;
			this.useCompactRegionStaging = other.useCompactRegionStaging;
		}
	}

	public enum GraphicsQuality {
		DEFAULT("Default"),
		FAST("Fast"),
		FANCY("Fancy");

		private final String displayName;

		GraphicsQuality(String displayName) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return this.displayName;
		}

		public boolean isFancy(boolean fancyGraphicsSetting) {
			switch (this) {
			case FANCY:
				return true;
			case FAST:
				return false;
			case DEFAULT:
			default:
				return fancyGraphicsSetting;
			}
		}

		public static GraphicsQuality byName(String value) {
			if (value != null) {
				for (GraphicsQuality q : values()) {
					if (q.name().equalsIgnoreCase(value.trim())) {
						return q;
					}
				}
			}
			return DEFAULT;
		}
	}

	public enum ChunkBuildDeferMode {
		ALWAYS("Always"),
		ONE_FRAME("One Frame"),
		NEVER("Never");

		private final String displayName;

		ChunkBuildDeferMode(String displayName) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return this.displayName;
		}

		public static ChunkBuildDeferMode byName(String value) {
			if (value != null) {
				for (ChunkBuildDeferMode mode : values()) {
					if (mode.name().equalsIgnoreCase(value.trim())) {
						return mode;
					}
				}
			}
			return ONE_FRAME;
		}
	}

	public double getEntityDistanceScaleSq() {
		double scale = (double) this.advanced.entityDistancePercent / 100.0D;
		return scale * scale;
	}

	public void copyFrom(SodiumGameOptions other) {
		this.quality.copyFrom(other.quality);
		this.performance.copyFrom(other.performance);
		this.advanced.copyFrom(other.advanced);
	}

	public SodiumGameOptions copy() {
		SodiumGameOptions clone = new SodiumGameOptions();
		clone.copyFrom(this);
		return clone;
	}

	public static SodiumGameOptions defaults() {
		return new SodiumGameOptions();
	}

	public static SodiumGameOptions load() {
		SodiumGameOptions opts = new SodiumGameOptions();
		try {
			byte[] raw = EagRuntime.getStorage(STORAGE_KEY);
			if (raw != null && raw.length > 0) {
				opts.readFromBytes(raw);
			}
		} catch (Throwable t) {
			LOGGER.warn("Failed to load Sodium options, using defaults: {}", t.toString());
		}
		return opts;
	}

	public void save() {
		try {
			byte[] encoded = this.writeToBytes();
			EagRuntime.setStorage(STORAGE_KEY, encoded);
		} catch (Throwable t) {
			LOGGER.warn("Failed to save Sodium options: {}", t.toString());
		}
	}

	public void readFromBytes(byte[] raw) throws Exception {
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(raw), StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.charAt(0) == '#') {
				continue;
			}
			int sep = line.indexOf('=');
			if (sep <= 0) {
				continue;
			}
			String key = line.substring(0, sep).trim();
			String val = line.substring(sep + 1).trim();
			applyKeyValue(key, val);
		}
	}

	private void applyKeyValue(String key, String val) {
		switch (key) {
		case "quality.weatherQuality":
			this.quality.weatherQuality = GraphicsQuality.byName(val);
			break;
		case "quality.leavesQuality":
			this.quality.leavesQuality = GraphicsQuality.byName(val);
			break;
		case "quality.enableVignette":
			this.quality.enableVignette = parseBool(val, true);
			break;
		case "quality.biomeBlendRadius":
			this.quality.biomeBlendRadius = MathHelper.clamp_int(parseInt(val, 2), 0, 7);
			break;
		case "quality.cloudDistanceChunks":
			this.quality.cloudDistanceChunks = MathHelper.clamp_int(parseInt(val, 16), 4, 32);
			break;
		case "performance.chunkBuildDeferMode":
			this.performance.chunkBuildDeferMode = ChunkBuildDeferMode.byName(val);
			break;
		case "performance.useRegionBatching":
			this.performance.useRegionBatching = parseBool(val, true);
			break;
		case "performance.useBlockFaceCulling":
			this.performance.useBlockFaceCulling = parseBool(val, true);
			break;
		case "performance.useFogOcclusion":
			this.performance.useFogOcclusion = parseBool(val, true);
			break;
		case "performance.useEntityCulling":
			this.performance.useEntityCulling = parseBool(val, true);
			break;
		case "performance.animateOnlyVisibleTextures":
			this.performance.animateOnlyVisibleTextures = parseBool(val, true);
			break;
		case "performance.useParticleCulling":
			this.performance.useParticleCulling = parseBool(val, true);
			break;
		case "performance.useCloudOptimization":
			this.performance.useCloudOptimization = parseBool(val, true);
			break;
		case "performance.useTranslucentFaceSorting":
			this.performance.useTranslucentFaceSorting = parseBool(val, true);
			break;
		case "advanced.chunkUpdateBudgetMs":
			this.advanced.chunkUpdateBudgetMs = MathHelper.clamp_int(parseInt(val, 6), 1, 25);
			break;
		case "advanced.entityDistancePercent":
			this.advanced.entityDistancePercent = MathHelper.clamp_int(parseInt(val, 100), 50, 500);
			break;
		case "advanced.useCompactRegionStaging":
			this.advanced.useCompactRegionStaging = parseBool(val, true);
			break;
		default:
			break;
		}
	}

	public byte[] writeToBytes() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
		pw.println("# Sodium Configuration (Kuack-1.8)");
		pw.println("quality.weatherQuality=" + this.quality.weatherQuality.name());
		pw.println("quality.leavesQuality=" + this.quality.leavesQuality.name());
		pw.println("quality.enableVignette=" + this.quality.enableVignette);
		pw.println("quality.biomeBlendRadius=" + this.quality.biomeBlendRadius);
		pw.println("quality.cloudDistanceChunks=" + this.quality.cloudDistanceChunks);
		pw.println("performance.chunkBuildDeferMode=" + this.performance.chunkBuildDeferMode.name());
		pw.println("performance.useRegionBatching=" + this.performance.useRegionBatching);
		pw.println("performance.useBlockFaceCulling=" + this.performance.useBlockFaceCulling);
		pw.println("performance.useFogOcclusion=" + this.performance.useFogOcclusion);
		pw.println("performance.useEntityCulling=" + this.performance.useEntityCulling);
		pw.println("performance.animateOnlyVisibleTextures=" + this.performance.animateOnlyVisibleTextures);
		pw.println("performance.useParticleCulling=" + this.performance.useParticleCulling);
		pw.println("performance.useCloudOptimization=" + this.performance.useCloudOptimization);
		pw.println("performance.useTranslucentFaceSorting=" + this.performance.useTranslucentFaceSorting);
		pw.println("advanced.chunkUpdateBudgetMs=" + this.advanced.chunkUpdateBudgetMs);
		pw.println("advanced.entityDistancePercent=" + this.advanced.entityDistancePercent);
		pw.println("advanced.useCompactRegionStaging=" + this.advanced.useCompactRegionStaging);
		pw.flush();
		return baos.toByteArray();
	}

	private static boolean parseBool(String val, boolean fallback) {
		if ("true".equalsIgnoreCase(val)) {
			return true;
		}
		if ("false".equalsIgnoreCase(val)) {
			return false;
		}
		return fallback;
	}

	private static int parseInt(String val, int fallback) {
		try {
			return Integer.parseInt(val);
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}
}
