package net.caffeinemc.mods.sodium.client.render.texture;

import java.util.List;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.lax1dude.eaglercraft.v1_8.minecraft.EaglerTextureAtlasSprite;

/**
 * Tracks animated block/fluid texture atlas sprites visible in the current frame so
 * EaglerTextureAtlasSprite FBO mipmap blits only run for visible textures.
 */
public final class SpriteUtil {

	private static int totalAnimatedSprites = 0;
	private static int activeAnimatedSprites = 0;

	private SpriteUtil() {
	}

	public static void markSpriteActive(EaglerTextureAtlasSprite sprite) {
		if (sprite != null && sprite.hasAnimationMetadata()) {
			sprite.sodiumMarkedActive = true;
		}
	}

	public static void markSpritesActive(List<EaglerTextureAtlasSprite> sprites) {
		if (sprites == null || sprites.isEmpty()) {
			return;
		}
		for (int i = 0, n = sprites.size(); i < n; ++i) {
			EaglerTextureAtlasSprite s = sprites.get(i);
			if (s != null) {
				s.sodiumMarkedActive = true;
			}
		}
	}

	public static boolean shouldUpdateAnimation(EaglerTextureAtlasSprite sprite) {
		if (sprite == null) {
			return false;
		}
		if (!SodiumClientMod.options().performance.animateOnlyVisibleTextures) {
			sprite.sodiumMarkedActive = false;
			return true;
		}
		// Always animate compass and clock so hotbar/item frames stay accurate
		String iconName = sprite.getIconName();
		if (iconName != null && (iconName.endsWith("clock") || iconName.endsWith("compass"))) {
			sprite.sodiumMarkedActive = false;
			return true;
		}
		boolean active = sprite.sodiumMarkedActive;
		sprite.sodiumMarkedActive = false;
		return active;
	}

	public static void recordAnimationStats(int total, int updated) {
		totalAnimatedSprites = total;
		activeAnimatedSprites = updated;
	}

	public static int getTotalAnimatedSprites() {
		return totalAnimatedSprites;
	}

	public static int getActiveAnimatedSprites() {
		return activeAnimatedSprites;
	}
}
