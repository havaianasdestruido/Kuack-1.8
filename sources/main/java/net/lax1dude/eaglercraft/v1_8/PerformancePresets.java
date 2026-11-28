package net.lax1dude.eaglercraft.v1_8;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

/**
 * Copyright (c) 2022-2026 lax1dude, ayunami2000, Kuack contributors. All Rights Reserved.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 */

/**
 * Performance presets spanning ultra low-end devices up to high-end systems.
 *
 * <p>
 * Each preset configures standard {@link GameSettings} video options and
 * optionally enables deeper, gameplay-safe rendering simplifications (particle
 * caps, texture animation skipping, sky/weather culling, HUD overlay throttling,
 * tile-entity distance scaling, and per-frame chunk rebuild caps) for the
 * ultra low-end tiers.
 *
 * <p>
 * No threads or JVM-only APIs are used here, so this stays TeaVM compatible.
 */
public class PerformancePresets {

	public static final int PRESET_CUSTOM = -1;
	public static final int PRESET_BACTERIA = 0;
	public static final int PRESET_ABACUS = 1;
	public static final int PRESET_ARDUINO_UNO_R3 = 2;
	public static final int PRESET_TOASTER = 3;
	public static final int PRESET_BALANCED = 4;
	public static final int PRESET_HIGH = 5;
	public static final int PRESET_ULTRA = 6;

	public static final int PRESET_COUNT = 7;

	private static final String[] KEYS = new String[] {
		"bacteria",
		"abacus",
		"arduino_uno_r3",
		"toaster",
		"balanced",
		"high",
		"ultra"
	};

	private static final String[] LANG_KEYS = new String[] {
		"options.performancePreset.bacteria",
		"options.performancePreset.abacus",
		"options.performancePreset.arduino_uno_r3",
		"options.performancePreset.toaster",
		"options.performancePreset.balanced",
		"options.performancePreset.high",
		"options.performancePreset.ultra"
	};

	private static final String[] TIER_LANG_KEYS = new String[] {
		"options.performancePreset.bacteria.tier",
		"options.performancePreset.abacus.tier",
		"options.performancePreset.arduino_uno_r3.tier",
		"options.performancePreset.toaster.tier",
		"options.performancePreset.balanced.tier",
		"options.performancePreset.high.tier",
		"options.performancePreset.ultra.tier"
	};

	private static final String[] DESC_LANG_KEYS = new String[] {
		"options.performancePreset.bacteria.desc",
		"options.performancePreset.abacus.desc",
		"options.performancePreset.arduino_uno_r3.desc",
		"options.performancePreset.toaster.desc",
		"options.performancePreset.balanced.desc",
		"options.performancePreset.high.desc",
		"options.performancePreset.ultra.desc"
	};

	private static final int[] RENDER_DISTANCE = new int[] { 1, 2, 2, 3, 6, 10, 16 };
	private static final boolean[] FANCY_GRAPHICS = new boolean[] { false, false, false, false, false, true, true };
	private static final int[] AMBIENT_OCCLUSION = new int[] { 0, 0, 0, 0, 1, 2, 2 };
	private static final int[] CLOUDS = new int[] { 0, 0, 0, 0, 1, 2, 2 };
	private static final int[] PARTICLES = new int[] { 2, 2, 2, 1, 1, 0, 0 };
	private static final int[] FXAA = new int[] { 2, 2, 2, 2, 0, 1, 1 };
	private static final int[] MIPMAP_LEVELS = new int[] { 0, 0, 0, 0, 2, 4, 4 };
	private static final boolean[] BLOCK_ALTERNATIVES = new boolean[] { false, false, false, false, true, true, true };
	private static final boolean[] ENTITY_SHADOWS = new boolean[] { false, false, false, false, true, true, true };
	private static final boolean[] FOG = new boolean[] { false, false, false, false, true, true, true };
	private static final boolean[] DYNAMIC_LIGHTS = new boolean[] { false, false, false, false, false, true, true };
	private static final boolean[] DISABLE_BLUR = new boolean[] { true, true, true, true, false, false, false };
	private static final boolean[] VIEW_BOBBING = new boolean[] { false, false, true, true, true, true, true };
	private static final boolean[] FNAW_SKINS = new boolean[] { false, false, false, true, true, true, true };

	private static final int[] MAX_PARTICLES = new int[] { 0, 100, 250, 750, 4000, 4000, 4000 };
	private static final boolean[] TEXTURE_ANIMATIONS = new boolean[] { false, false, false, true, true, true, true };
	private static final boolean[] SKY_EXTRAS = new boolean[] { false, false, true, true, true, true, true };
	private static final boolean[] STARS = new boolean[] { false, false, false, true, true, true, true };
	private static final boolean[] WEATHER_RENDER = new boolean[] { false, false, true, true, true, true, true };
	private static final long[] OVERLAY_REFRESH_MS = new long[] { 200L, 150L, 125L, 100L, -1L, -1L, -1L };
	private static final float[] TILE_ENTITY_DIST_SCALE = new float[] { 0.25F, 0.35F, 0.50F, 0.75F, 1.0F, 1.0F, 1.0F };
	private static final int[] MAX_CHUNK_UPDATES = new int[] { 1, 1, 2, 4, Integer.MAX_VALUE, Integer.MAX_VALUE,
			Integer.MAX_VALUE };

	public static boolean isValidPreset(int presetId) {
		return presetId >= 0 && presetId < PRESET_COUNT;
	}

	public static String getKey(int presetId) {
		return isValidPreset(presetId) ? KEYS[presetId] : "custom";
	}

	public static int fromKey(String key) {
		if (key != null) {
			String normalized = key.trim().toLowerCase();
			for (int i = 0; i < PRESET_COUNT; ++i) {
				if (KEYS[i].equals(normalized)) {
					return i;
				}
			}
			try {
				int numeric = Integer.parseInt(normalized);
				if (isValidPreset(numeric)) {
					return numeric;
				}
			} catch (NumberFormatException ignored) {
			}
		}
		return PRESET_CUSTOM;
	}

	public static String getDisplayName(int presetId) {
		return isValidPreset(presetId) ? I18n.format(LANG_KEYS[presetId]) : I18n.format("options.performancePreset.custom");
	}

	public static String getTierLabel(int presetId) {
		return isValidPreset(presetId) ? I18n.format(TIER_LANG_KEYS[presetId])
				: I18n.format("options.performancePreset.custom.tier");
	}

	public static String getDescription(int presetId) {
		return isValidPreset(presetId) ? I18n.format(DESC_LANG_KEYS[presetId])
				: I18n.format("options.performancePreset.custom.desc");
	}

	public static void applyToSettings(int presetId, GameSettings settings) {
		if (!isValidPreset(presetId)) {
			return;
		}
		settings.performancePreset = presetId;
		settings.renderDistanceChunks = RENDER_DISTANCE[presetId];
		settings.fancyGraphics = FANCY_GRAPHICS[presetId];
		settings.ambientOcclusion = AMBIENT_OCCLUSION[presetId];
		settings.clouds = CLOUDS[presetId];
		settings.particleSetting = PARTICLES[presetId];
		settings.fxaa = FXAA[presetId];
		settings.mipmapLevels = MIPMAP_LEVELS[presetId];
		settings.allowBlockAlternatives = BLOCK_ALTERNATIVES[presetId];
		settings.field_181151_V = ENTITY_SHADOWS[presetId];
		settings.fog = FOG[presetId];
		settings.enableDynamicLights = DYNAMIC_LIGHTS[presetId];
		settings.disableBlur = DISABLE_BLUR[presetId];
		settings.viewBobbing = VIEW_BOBBING[presetId];
		settings.enableFNAWSkins = FNAW_SKINS[presetId];
		settings.chunkFix = true;
		settings.limitFramerate = 260;
		settings.shaders = false;
	}

	public static int getRenderDistance(int presetId) {
		return isValidPreset(presetId) ? RENDER_DISTANCE[presetId] : 4;
	}

	public static boolean isFancyGraphics(int presetId) {
		return isValidPreset(presetId) && FANCY_GRAPHICS[presetId];
	}

	public static int getAmbientOcclusion(int presetId) {
		return isValidPreset(presetId) ? AMBIENT_OCCLUSION[presetId] : 0;
	}

	public static int getClouds(int presetId) {
		return isValidPreset(presetId) ? CLOUDS[presetId] : 1;
	}

	public static int getParticleSetting(int presetId) {
		return isValidPreset(presetId) ? PARTICLES[presetId] : 0;
	}

	public static int getMipmapLevels(int presetId) {
		return isValidPreset(presetId) ? MIPMAP_LEVELS[presetId] : 4;
	}

	public static int getFxaa(int presetId) {
		return isValidPreset(presetId) ? FXAA[presetId] : 0;
	}

	public static boolean isDynamicLights(int presetId) {
		return isValidPreset(presetId) && DYNAMIC_LIGHTS[presetId];
	}

	public static boolean isEntityShadows(int presetId) {
		return !isValidPreset(presetId) || ENTITY_SHADOWS[presetId];
	}

	public static boolean isFog(int presetId) {
		return !isValidPreset(presetId) || FOG[presetId];
	}

	public static int getMaxParticles(int presetId) {
		return isValidPreset(presetId) ? MAX_PARTICLES[presetId] : 4000;
	}

	public static boolean areTextureAnimationsEnabled(int presetId) {
		return !isValidPreset(presetId) || TEXTURE_ANIMATIONS[presetId];
	}

	public static boolean shouldRenderSkyExtras(int presetId) {
		return !isValidPreset(presetId) || SKY_EXTRAS[presetId];
	}

	public static boolean shouldRenderStars(int presetId) {
		return !isValidPreset(presetId) || STARS[presetId];
	}

	public static boolean shouldRenderWeather(int presetId) {
		return !isValidPreset(presetId) || WEATHER_RENDER[presetId];
	}

	public static long getOverlayRefreshIntervalMs(int presetId) {
		return isValidPreset(presetId) ? OVERLAY_REFRESH_MS[presetId] : -1L;
	}

	public static float getTileEntityDistanceScale(int presetId) {
		return isValidPreset(presetId) ? TILE_ENTITY_DIST_SCALE[presetId] : 1.0F;
	}

	public static int getMaxChunkUpdatesPerFrame(int presetId) {
		return isValidPreset(presetId) ? MAX_CHUNK_UPDATES[presetId] : Integer.MAX_VALUE;
	}
}
