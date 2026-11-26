package net.minecraft.world.biome;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.model.color.SodiumBiomeColorCache;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;

/**+
 * This portion of EaglercraftX contains deobfuscated Minecraft 1.8 source code.
 * 
 * Minecraft 1.8.8 bytecode is (c) 2015 Mojang AB. "Do not distribute!"
 * Mod Coder Pack v9.18 deobfuscation configs are (c) Copyright by the MCP Team
 * 
 * EaglercraftX 1.8 patch files (c) 2022-2024 lax1dude, ayunami2000. All Rights Reserved.
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
public class BiomeColorHelper {
	private static final BiomeColorHelper.ColorResolver field_180291_a = new BiomeColorHelper.ColorResolver() {
		public int getColorAtPos(BiomeGenBase blockPosition, BlockPos parBlockPos) {
			return blockPosition.getGrassColorAtPos(parBlockPos);
		}
	};
	private static final BiomeColorHelper.ColorResolver field_180289_b = new BiomeColorHelper.ColorResolver() {
		public int getColorAtPos(BiomeGenBase biomegenbase, BlockPos blockpos) {
			return biomegenbase.getFoliageColorAtPos(blockpos);
		}
	};
	private static final BiomeColorHelper.ColorResolver field_180290_c = new BiomeColorHelper.ColorResolver() {
		public int getColorAtPos(BiomeGenBase biomegenbase, BlockPos var2) {
			return biomegenbase.waterColorMultiplier;
		}
	};

	private static final BlockPos.MutableBlockPos SCRATCH_POS = new BlockPos.MutableBlockPos();

	private static int func_180285_a(IBlockAccess parIBlockAccess, BlockPos parBlockPos,
			BiomeColorHelper.ColorResolver parColorResolver) {
		int radius = SodiumClientMod.options().quality.biomeBlendRadius;
		if (radius <= 0) {
			return parColorResolver.getColorAtPos(parIBlockAccess.getBiomeGenForCoords(parBlockPos), parBlockPos);
		}

		int sumR = 0;
		int sumG = 0;
		int sumB = 0;
		int px = parBlockPos.getX();
		int py = parBlockPos.getY();
		int pz = parBlockPos.getZ();
		BlockPos.MutableBlockPos mpos = SCRATCH_POS;
		for (int dz = -radius; dz <= radius; ++dz) {
			for (int dx = -radius; dx <= radius; ++dx) {
				mpos.func_181079_c(px + dx, py, pz + dz);
				int c = parColorResolver.getColorAtPos(parIBlockAccess.getBiomeGenForCoords(mpos), mpos);
				sumR += (c >> 16) & 255;
				sumG += (c >> 8) & 255;
				sumB += c & 255;
			}
		}
		int diameter = (radius << 1) + 1;
		int area = diameter * diameter;
		return ((sumR / area) & 255) << 16 | ((sumG / area) & 255) << 8 | ((sumB / area) & 255);
	}

	public static int getGrassColorAtPos(IBlockAccess parIBlockAccess, BlockPos parBlockPos) {
		if (parIBlockAccess instanceof RegionRenderCache) {
			SodiumBiomeColorCache cache = ((RegionRenderCache) parIBlockAccess).getSodiumBiomeCache();
			if (cache != null) {
				return cache.getColor(SodiumBiomeColorCache.TYPE_GRASS, parBlockPos.getX(), parBlockPos.getY(),
						parBlockPos.getZ());
			}
		}
		return func_180285_a(parIBlockAccess, parBlockPos, field_180291_a);
	}

	public static int getFoliageColorAtPos(IBlockAccess parIBlockAccess, BlockPos parBlockPos) {
		if (parIBlockAccess instanceof RegionRenderCache) {
			SodiumBiomeColorCache cache = ((RegionRenderCache) parIBlockAccess).getSodiumBiomeCache();
			if (cache != null) {
				return cache.getColor(SodiumBiomeColorCache.TYPE_FOLIAGE, parBlockPos.getX(), parBlockPos.getY(),
						parBlockPos.getZ());
			}
		}
		return func_180285_a(parIBlockAccess, parBlockPos, field_180289_b);
	}

	public static int getWaterColorAtPos(IBlockAccess parIBlockAccess, BlockPos parBlockPos) {
		if (parIBlockAccess instanceof RegionRenderCache) {
			SodiumBiomeColorCache cache = ((RegionRenderCache) parIBlockAccess).getSodiumBiomeCache();
			if (cache != null) {
				return cache.getColor(SodiumBiomeColorCache.TYPE_WATER, parBlockPos.getX(), parBlockPos.getY(),
						parBlockPos.getZ());
			}
		}
		return func_180285_a(parIBlockAccess, parBlockPos, field_180290_c);
	}

	interface ColorResolver {
		int getColorAtPos(BiomeGenBase var1, BlockPos var2);
	}
}
