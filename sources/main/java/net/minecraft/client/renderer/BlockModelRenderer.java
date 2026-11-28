package net.minecraft.client.renderer;

import java.util.BitSet;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.lax1dude.eaglercraft.v1_8.opengl.ext.deferred.DeferredStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.ext.deferred.VertexMarkerState;
import net.lax1dude.eaglercraft.v1_8.opengl.ext.dynamiclights.DynamicLightsStateManager;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.util.Vec3i;
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
public class BlockModelRenderer {
	public boolean renderModel(IBlockAccess blockAccessIn, IBakedModel modelIn, IBlockState blockStateIn,
			BlockPos blockPosIn, WorldRenderer worldRendererIn) {
		Block block = blockStateIn.getBlock();
		block.setBlockBoundsBasedOnState(blockAccessIn, blockPosIn);
		return this.renderModel(blockAccessIn, modelIn, blockStateIn, blockPosIn, worldRendererIn, true);
	}

	public boolean renderModel(IBlockAccess blockAccessIn, IBakedModel modelIn, IBlockState blockStateIn,
			BlockPos blockPosIn, WorldRenderer worldRendererIn, boolean checkSides) {
		boolean flag = Minecraft.isAmbientOcclusionEnabled() && blockStateIn.getBlock().getLightValue() == 0
				&& modelIn.isAmbientOcclusion();

		try {
			Block block = blockStateIn.getBlock();
			return flag
					? this.renderModelAmbientOcclusion(blockAccessIn, modelIn, block, blockPosIn, worldRendererIn,
							checkSides)
					: this.renderModelStandard(blockAccessIn, modelIn, block, blockPosIn, worldRendererIn, checkSides);
		} catch (Throwable throwable) {
			CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Tesselating block model");
			CrashReportCategory crashreportcategory = crashreport.makeCategory("Block model being tesselated");
			CrashReportCategory.addBlockInfo(crashreportcategory, blockPosIn, blockStateIn);
			crashreportcategory.addCrashSection("Using AO", Boolean.valueOf(flag));
			throw new ReportedException(crashreport);
		}
	}

	public boolean renderModelAmbientOcclusion(IBlockAccess blockAccessIn, IBakedModel modelIn, Block blockIn,
			BlockPos blockPosIn, WorldRenderer worldRendererIn, boolean checkSides) {
		boolean flag = false;
		float[] afloat = new float[EnumFacing._VALUES.length * 2];
		BitSet bitset = new BitSet(3);
		BlockModelRenderer.AmbientOcclusionFace blockmodelrenderer$ambientocclusionface = new BlockModelRenderer.AmbientOcclusionFace();

		EnumFacing[] facings = EnumFacing._VALUES;
		for (int i = 0; i < facings.length; ++i) {
			EnumFacing enumfacing = facings[i];
			List list = modelIn.getFaceQuads(enumfacing);
			if (!list.isEmpty()) {
				BlockPos blockpos = blockPosIn.offset(enumfacing);
				if (!checkSides || blockIn.shouldSideBeRendered(blockAccessIn, blockpos, enumfacing)) {
					this.renderModelAmbientOcclusionQuads(blockAccessIn, blockIn, blockPosIn, worldRendererIn, list,
							afloat, bitset, blockmodelrenderer$ambientocclusionface);
					flag = true;
				}
			}
		}

		List list1 = modelIn.getGeneralQuads();
		if (list1.size() > 0) {
			this.renderModelAmbientOcclusionQuads(blockAccessIn, blockIn, blockPosIn, worldRendererIn, list1, afloat,
					bitset, blockmodelrenderer$ambientocclusionface);
			flag = true;
		}

		return flag;
	}

	public boolean renderModelStandard(IBlockAccess blockAccessIn, IBakedModel modelIn, Block blockIn,
			BlockPos blockPosIn, WorldRenderer worldRendererIn, boolean checkSides) {
		boolean isDeferred = DeferredStateManager.isDeferredRenderer();
		boolean flag = false;
		float[] afloat = isDeferred ? new float[EnumFacing._VALUES.length * 2] : null;
		BitSet bitset = new BitSet(3);

		BlockPos.MutableBlockPos pointer = new BlockPos.MutableBlockPos();
		EnumFacing[] facings = EnumFacing._VALUES;
		for (int m = 0; m < facings.length; ++m) {
			EnumFacing enumfacing = facings[m];
			List list = modelIn.getFaceQuads(enumfacing);
			if (!list.isEmpty()) {
				BlockPos blockpos = blockPosIn.offsetEvenFaster(enumfacing, pointer);
				if (!checkSides || blockIn.shouldSideBeRendered(blockAccessIn, blockpos, enumfacing)) {
					int i = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos);
					this.renderModelStandardQuads(blockAccessIn, blockIn, blockPosIn, enumfacing, i, false,
							worldRendererIn, list, bitset, afloat);
					flag = true;
				}
			}
		}

		List list1 = modelIn.getGeneralQuads();
		if (list1.size() > 0) {
			this.renderModelStandardQuads(blockAccessIn, blockIn, blockPosIn, (EnumFacing) null, -1, true,
					worldRendererIn, list1, bitset, afloat);
			flag = true;
		}

		return flag;
	}

	private void renderModelAmbientOcclusionQuads(IBlockAccess blockAccessIn, Block blockIn, BlockPos blockPosIn,
			WorldRenderer worldRendererIn, List<BakedQuad> listQuadsIn, float[] quadBounds, BitSet boundsFlags,
			BlockModelRenderer.AmbientOcclusionFace aoFaceIn) {
		boolean isDeferred = DeferredStateManager.isDeferredRenderer();
		boolean isDynamicLights = isDeferred || DynamicLightsStateManager.isDynamicLightsRender();
		double d0 = (double) blockPosIn.getX();
		double d1 = (double) blockPosIn.getY();
		double d2 = (double) blockPosIn.getZ();
		Block.EnumOffsetType block$enumoffsettype = blockIn.getOffsetType();
		if (block$enumoffsettype != Block.EnumOffsetType.NONE) {
			long i = MathHelper.getPositionRandom(blockPosIn);
			d0 += ((double) ((float) (i >> 16 & 15L) / 15.0F) - 0.5D) * 0.5D;
			d2 += ((double) ((float) (i >> 24 & 15L) / 15.0F) - 0.5D) * 0.5D;
			if (!isDeferred && block$enumoffsettype == Block.EnumOffsetType.XYZ) {
				d1 += ((double) ((float) (i >> 20 & 15L) / 15.0F) - 1.0D) * 0.2D;
			}
		}

		for (int i = 0, l = listQuadsIn.size(); i < l; ++i) {
			BakedQuad bakedquad = listQuadsIn.get(i);
			int[] vertData = isDynamicLights ? bakedquad.getVertexDataWithNormals() : bakedquad.getVertexData();
			this.fillQuadBounds(blockIn, vertData, bakedquad.getFace(), quadBounds, boundsFlags,
					isDynamicLights ? 8 : 7);
			aoFaceIn.updateVertexBrightness(blockAccessIn, blockIn, blockPosIn, bakedquad.getFace(), quadBounds,
					boundsFlags);
			worldRendererIn.addVertexData(vertData);
			worldRendererIn.putBrightness4(aoFaceIn.vertexBrightness[0], aoFaceIn.vertexBrightness[1],
					aoFaceIn.vertexBrightness[2], aoFaceIn.vertexBrightness[3]);
			if (bakedquad.hasTintIndex()) {
				int j = blockIn.colorMultiplier(blockAccessIn, blockPosIn, bakedquad.getTintIndex());
				if (EntityRenderer.anaglyphEnable) {
					j = TextureUtil.anaglyphColor(j);
				}

				float f = (float) (j >> 16 & 255) / 255.0F;
				float f1 = (float) (j >> 8 & 255) / 255.0F;
				float f2 = (float) (j & 255) / 255.0F;
				worldRendererIn.putColorMultiplier(aoFaceIn.vertexColorMultiplier[0] * f,
						aoFaceIn.vertexColorMultiplier[0] * f1, aoFaceIn.vertexColorMultiplier[0] * f2, 4);
				worldRendererIn.putColorMultiplier(aoFaceIn.vertexColorMultiplier[1] * f,
						aoFaceIn.vertexColorMultiplier[1] * f1, aoFaceIn.vertexColorMultiplier[1] * f2, 3);
				worldRendererIn.putColorMultiplier(aoFaceIn.vertexColorMultiplier[2] * f,
						aoFaceIn.vertexColorMultiplier[2] * f1, aoFaceIn.vertexColorMultiplier[2] * f2, 2);
				worldRendererIn.putColorMultiplier(aoFaceIn.vertexColorMultiplier[3] * f,
						aoFaceIn.vertexColorMultiplier[3] * f1, aoFaceIn.vertexColorMultiplier[3] * f2, 1);
			} else {
				worldRendererIn.putColorMultiplier(aoFaceIn.vertexColorMultiplier[0], aoFaceIn.vertexColorMultiplier[0],
						aoFaceIn.vertexColorMultiplier[0], 4);
				worldRendererIn.putColorMultiplier(aoFaceIn.vertexColorMultiplier[1], aoFaceIn.vertexColorMultiplier[1],
						aoFaceIn.vertexColorMultiplier[1], 3);
				worldRendererIn.putColorMultiplier(aoFaceIn.vertexColorMultiplier[2], aoFaceIn.vertexColorMultiplier[2],
						aoFaceIn.vertexColorMultiplier[2], 2);
				worldRendererIn.putColorMultiplier(aoFaceIn.vertexColorMultiplier[3], aoFaceIn.vertexColorMultiplier[3],
						aoFaceIn.vertexColorMultiplier[3], 1);
			}

			worldRendererIn.putPosition(d0, d1, d2);
		}

	}

	private void fillQuadBounds(Block blockIn, int[] vertexData, EnumFacing facingIn, float[] quadBounds,
			BitSet boundsFlags, int deferredStrideOverride) {
		float f = 32.0F;
		float f1 = 32.0F;
		float f2 = 32.0F;
		float f3 = -32.0F;
		float f4 = -32.0F;
		float f5 = -32.0F;

		for (int i = 0; i < 4; ++i) {
			int j = i * deferredStrideOverride;
			float f6 = Float.intBitsToFloat(vertexData[j]);
			float f7 = Float.intBitsToFloat(vertexData[j + 1]);
			float f8 = Float.intBitsToFloat(vertexData[j + 2]);
			f = Math.min(f, f6);
			f1 = Math.min(f1, f7);
			f2 = Math.min(f2, f8);
			f3 = Math.max(f3, f6);
			f4 = Math.max(f4, f7);
			f5 = Math.max(f5, f8);
		}

		if (quadBounds != null) {
			quadBounds[EnumFacing.WEST.getIndex()] = f;
			quadBounds[EnumFacing.EAST.getIndex()] = f3;
			quadBounds[EnumFacing.DOWN.getIndex()] = f1;
			quadBounds[EnumFacing.UP.getIndex()] = f4;
			quadBounds[EnumFacing.NORTH.getIndex()] = f2;
			quadBounds[EnumFacing.SOUTH.getIndex()] = f5;
			quadBounds[EnumFacing.WEST.getIndex() + EnumFacing._VALUES.length] = 1.0F - f;
			quadBounds[EnumFacing.EAST.getIndex() + EnumFacing._VALUES.length] = 1.0F - f3;
			quadBounds[EnumFacing.DOWN.getIndex() + EnumFacing._VALUES.length] = 1.0F - f1;
			quadBounds[EnumFacing.UP.getIndex() + EnumFacing._VALUES.length] = 1.0F - f4;
			quadBounds[EnumFacing.NORTH.getIndex() + EnumFacing._VALUES.length] = 1.0F - f2;
			quadBounds[EnumFacing.SOUTH.getIndex() + EnumFacing._VALUES.length] = 1.0F - f5;
		}

		float f9 = 1.0E-4F;
		float f10 = 0.9999F;
		switch (facingIn) {
		case DOWN:
			boundsFlags.set(1, f >= 1.0E-4F || f2 >= 1.0E-4F || f3 <= 0.9999F || f5 <= 0.9999F);
			boundsFlags.set(0, (f1 < 1.0E-4F || blockIn.isFullCube()) && f1 == f4);
			break;
		case UP:
			boundsFlags.set(1, f >= 1.0E-4F || f2 >= 1.0E-4F || f3 <= 0.9999F || f5 <= 0.9999F);
			boundsFlags.set(0, (f4 > 0.9999F || blockIn.isFullCube()) && f1 == f4);
			break;
		case NORTH:
			boundsFlags.set(1, f >= 1.0E-4F || f1 >= 1.0E-4F || f3 <= 0.9999F || f4 <= 0.9999F);
			boundsFlags.set(0, (f2 < 1.0E-4F || blockIn.isFullCube()) && f2 == f5);
			break;
		case SOUTH:
			boundsFlags.set(1, f >= 1.0E-4F || f1 >= 1.0E-4F || f3 <= 0.9999F || f4 <= 0.9999F);
			boundsFlags.set(0, (f5 > 0.9999F || blockIn.isFullCube()) && f2 == f5);
			break;
		case WEST:
			boundsFlags.set(1, f1 >= 1.0E-4F || f2 >= 1.0E-4F || f4 <= 0.9999F || f5 <= 0.9999F);
			boundsFlags.set(0, (f < 1.0E-4F || blockIn.isFullCube()) && f == f3);
			break;
		case EAST:
			boundsFlags.set(1, f1 >= 1.0E-4F || f2 >= 1.0E-4F || f4 <= 0.9999F || f5 <= 0.9999F);
			boundsFlags.set(0, (f3 > 0.9999F || blockIn.isFullCube()) && f == f3);
		}

	}

	private final BlockPos blockpos0 = new BlockPos(0, 0, 0);
	private final BlockPos blockpos1 = new BlockPos(0, 0, 0);
	private final BlockPos blockpos2 = new BlockPos(0, 0, 0);
	private final BlockPos blockpos3 = new BlockPos(0, 0, 0);
	private final BlockPos blockpos4 = new BlockPos(0, 0, 0);
	private final BlockPos blockpos5 = new BlockPos(0, 0, 0);

	private void renderModelStandardQuads(IBlockAccess blockAccessIn, Block blockIn, BlockPos blockPosIn,
			EnumFacing faceIn, int brightnessIn, boolean ownBrightness, WorldRenderer worldRendererIn,
			List<BakedQuad> listQuadsIn, BitSet boundsFlags, float[] quadBounds) {
		boolean isDeferred = DeferredStateManager.isDeferredRenderer();
		boolean isDynamicLights = isDeferred || DynamicLightsStateManager.isDynamicLightsRender();
		double d0 = (double) blockPosIn.getX();
		double d1 = (double) blockPosIn.getY();
		double d2 = (double) blockPosIn.getZ();
		Block.EnumOffsetType block$enumoffsettype = blockIn.getOffsetType();
		if (block$enumoffsettype != Block.EnumOffsetType.NONE) {
			int i = blockPosIn.getX();
			int j = blockPosIn.getZ();
			long k = (long) (i * 3129871) ^ (long) j * 116129781L;
			k = k * k * 42317861L + k * 11L;
			d0 += ((double) ((float) (k >> 16 & 15L) / 15.0F) - 0.5D) * 0.5D;
			d2 += ((double) ((float) (k >> 24 & 15L) / 15.0F) - 0.5D) * 0.5D;
			if (!isDeferred && block$enumoffsettype == Block.EnumOffsetType.XYZ) {
				d1 += ((double) ((float) (k >> 20 & 15L) / 15.0F) - 1.0D) * 0.2D;
			}
		}

		for (int m = 0, n = listQuadsIn.size(); m < n; ++m) {
			BakedQuad bakedquad = listQuadsIn.get(m);
			EnumFacing facingIn = bakedquad.getFace();
			int[] vertData = isDynamicLights ? bakedquad.getVertexDataWithNormals() : bakedquad.getVertexData();
			blockPosIn.offsetEvenFaster(facingIn, blockpos0);
			this.fillQuadBounds(blockIn, vertData, facingIn, quadBounds, boundsFlags, isDynamicLights ? 8 : 7);
			boolean boundsFlags0 = boundsFlags.get(0);
			if (ownBrightness) {
				brightnessIn = boundsFlags0 ? blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos0)
						: blockIn.getMixedBrightnessForBlock(blockAccessIn, blockPosIn);
			}

			worldRendererIn.addVertexData(vertData);

			if (isDeferred) {
				BlockModelRenderer.EnumNeighborInfo blockmodelrenderer$enumneighborinfo = BlockModelRenderer.EnumNeighborInfo
						.getNeighbourInfo(facingIn);
				BlockPos blockPosIn2 = boundsFlags0 ? blockpos0 : blockPosIn;
				blockPosIn2.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[0], blockpos1);
				int i = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos1);
				blockPosIn2.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[1], blockpos2);
				int j = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos2);
				blockPosIn2.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[2], blockpos3);
				int k = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos3);
				blockPosIn2.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[3], blockpos4);
				int l = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos4);

				blockpos1.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[2], blockpos5);
				int i1 = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos5);

				blockpos1.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[3], blockpos5);
				int j1 = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos5);

				blockpos2.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[2], blockpos5);
				int k1 = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos5);

				blockpos2.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[3], blockpos5);
				int l1 = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos5);

				int[] b = new int[4];

				boolean upIsOpaque = !blockAccessIn.getBlockState(blockpos0).getBlock().isOpaqueCube();
				int i3;
				if (boundsFlags0 || upIsOpaque) {
					i3 = (ownBrightness && boundsFlags0) ? brightnessIn
							: blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos0);
				} else {
					i3 = (ownBrightness && !boundsFlags0) ? brightnessIn
							: blockIn.getMixedBrightnessForBlock(blockAccessIn, blockPosIn);
				}

				BlockModelRenderer.VertexTranslations blockmodelrenderer$vertextranslations = BlockModelRenderer.VertexTranslations
						.getVertexTranslations(facingIn);
				if (boundsFlags.get(1) && blockmodelrenderer$enumneighborinfo.doNonCubicWeight) {
					float f13 = quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[0].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[1].shape];
					float f14 = quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[2].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[3].shape];
					float f15 = quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[4].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[5].shape];
					float f16 = quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[6].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[7].shape];
					float f17 = quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[0].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[1].shape];
					float f18 = quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[2].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[3].shape];
					float f19 = quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[4].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[5].shape];
					float f20 = quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[6].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[7].shape];
					float f21 = quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[0].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[1].shape];
					float f22 = quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[2].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[3].shape];
					float f23 = quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[4].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[5].shape];
					float f24 = quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[6].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[7].shape];
					float f25 = quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[0].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[1].shape];
					float f26 = quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[2].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[3].shape];
					float f27 = quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[4].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[5].shape];
					float f28 = quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[6].shape]
							* quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[7].shape];
					int i2 = getAoBrightness(l, i, j1, i3);
					int j2 = getAoBrightness(k, i, i1, i3);
					int k2 = getAoBrightness(k, j, k1, i3);
					int l2 = getAoBrightness(l, j, l1, i3);
					b[blockmodelrenderer$vertextranslations.vert0] = getVertexBrightness(i2, j2, k2, l2, f13,
							f14, f15, f16);
					b[blockmodelrenderer$vertextranslations.vert1] = getVertexBrightness(i2, j2, k2, l2, f17,
							f18, f19, f20);
					b[blockmodelrenderer$vertextranslations.vert2] = getVertexBrightness(i2, j2, k2, l2, f21,
							f22, f23, f24);
					b[blockmodelrenderer$vertextranslations.vert3] = getVertexBrightness(i2, j2, k2, l2, f25,
							f26, f27, f28);
				} else {
					b[blockmodelrenderer$vertextranslations.vert0] = getAoBrightness(l, i, j1, i3);
					b[blockmodelrenderer$vertextranslations.vert1] = getAoBrightness(k, i, i1, i3);
					b[blockmodelrenderer$vertextranslations.vert2] = getAoBrightness(k, j, k1, i3);
					b[blockmodelrenderer$vertextranslations.vert3] = getAoBrightness(l, j, l1, i3);
				}
				worldRendererIn.putBrightness4(b[0], b[1], b[2], b[3]);
			} else {
				worldRendererIn.putBrightness4(brightnessIn, brightnessIn, brightnessIn, brightnessIn);
			}

			if (bakedquad.hasTintIndex()) {
				int l = blockIn.colorMultiplier(blockAccessIn, blockPosIn, bakedquad.getTintIndex());
				if (EntityRenderer.anaglyphEnable) {
					l = TextureUtil.anaglyphColor(l);
				}

				float f = (float) (l >> 16 & 255) / 255.0F;
				float f1 = (float) (l >> 8 & 255) / 255.0F;
				float f2 = (float) (l & 255) / 255.0F;
				worldRendererIn.putColorMultiplier(f, f1, f2, 4);
				worldRendererIn.putColorMultiplier(f, f1, f2, 3);
				worldRendererIn.putColorMultiplier(f, f1, f2, 2);
				worldRendererIn.putColorMultiplier(f, f1, f2, 1);
			}

			worldRendererIn.putPosition(d0, d1, d2);
		}

	}

	private static int getAoBrightness(int parInt1, int parInt2, int parInt3, int parInt4) {
		if (parInt1 == 0) {
			parInt1 = parInt4;
		}

		if (parInt2 == 0) {
			parInt2 = parInt4;
		}

		if (parInt3 == 0) {
			parInt3 = parInt4;
		}

		return parInt1 + parInt2 + parInt3 + parInt4 >> 2 & 16711935;
	}

	public void renderModelBrightnessColor(IBakedModel bakedModel, float parFloat1, float parFloat2, float parFloat3,
			float parFloat4) {
		EnumFacing[] facings = EnumFacing._VALUES;
		for (int i = 0; i < facings.length; ++i) {
			EnumFacing enumfacing = facings[i];
			this.renderModelBrightnessColorQuads(parFloat1, parFloat2, parFloat3, parFloat4,
					bakedModel.getFaceQuads(enumfacing));
		}

		this.renderModelBrightnessColorQuads(parFloat1, parFloat2, parFloat3, parFloat4, bakedModel.getGeneralQuads());
	}

	public void renderModelBrightness(IBakedModel parIBakedModel, IBlockState parIBlockState, float parFloat1,
			boolean parFlag) {
		Block block = parIBlockState.getBlock();
		block.setBlockBoundsForItemRender();
		GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
		int i = block.getRenderColor(block.getStateForEntityRender(parIBlockState));
		if (EntityRenderer.anaglyphEnable) {
			i = TextureUtil.anaglyphColor(i);
		}

		float f = (float) (i >> 16 & 255) / 255.0F;
		float f1 = (float) (i >> 8 & 255) / 255.0F;
		float f2 = (float) (i & 255) / 255.0F;
		if (!parFlag) {
			GlStateManager.color(parFloat1, parFloat1, parFloat1, 1.0F);
		}

		this.renderModelBrightnessColor(parIBakedModel, parFloat1, f, f1, f2);
	}

	private void renderModelBrightnessColorQuads(float parFloat1, float parFloat2, float parFloat3, float parFloat4,
			List<BakedQuad> parList) {
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer worldrenderer = tessellator.getWorldRenderer();

		for (int i = 0, l = parList.size(); i < l; ++i) {
			BakedQuad bakedquad = parList.get(i);
			worldrenderer.begin(7, DefaultVertexFormats.ITEM);
			worldrenderer.addVertexData(bakedquad.getVertexData());
			if (bakedquad.hasTintIndex()) {
				worldrenderer.putColorRGB_F4(parFloat2 * parFloat1, parFloat3 * parFloat1, parFloat4 * parFloat1);
			} else {
				worldrenderer.putColorRGB_F4(parFloat1, parFloat1, parFloat1);
			}

			Vec3i vec3i = bakedquad.getFace().getDirectionVec();
			worldrenderer.putNormal((float) vec3i.getX(), (float) vec3i.getY(), (float) vec3i.getZ(),
					VertexMarkerState.markId);
			tessellator.draw();
		}

	}

	class AmbientOcclusionFace {
		private final float[] vertexColorMultiplier = new float[4];
		private final int[] vertexBrightness = new int[4];

		private final BlockPos blockpos0 = new BlockPos(0, 0, 0);
		private final BlockPos blockpos1 = new BlockPos(0, 0, 0);
		private final BlockPos blockpos2 = new BlockPos(0, 0, 0);
		private final BlockPos blockpos3 = new BlockPos(0, 0, 0);
		private final BlockPos blockpos4 = new BlockPos(0, 0, 0);
		private final BlockPos blockpos5 = new BlockPos(0, 0, 0);

		public void updateVertexBrightness(IBlockAccess blockAccessIn, Block blockIn, BlockPos blockPosIn,
				EnumFacing facingIn, float[] quadBounds, BitSet boundsFlags) {
			BlockPos blockpos = boundsFlags.get(0) ? blockPosIn.offsetEvenFaster(facingIn, blockpos0) : blockPosIn;
			BlockModelRenderer.EnumNeighborInfo blockmodelrenderer$enumneighborinfo = BlockModelRenderer.EnumNeighborInfo
					.getNeighbourInfo(facingIn);
			blockpos.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[0], blockpos1);
			int i = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos1);
			float f = blockAccessIn.getBlockState(blockpos1).getBlock().getAmbientOcclusionLightValue();
			blockpos1.offsetEvenFaster(facingIn, blockpos5);
			boolean flag = blockAccessIn.getBlockState(blockpos5).getBlock().isTranslucent();

			blockpos.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[1], blockpos2);
			int j = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos2);
			float f1 = blockAccessIn.getBlockState(blockpos2).getBlock().getAmbientOcclusionLightValue();
			blockpos2.offsetEvenFaster(facingIn, blockpos5);
			boolean flag1 = blockAccessIn.getBlockState(blockpos5).getBlock().isTranslucent();

			blockpos.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[2], blockpos3);
			int k = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos3);
			float f2 = blockAccessIn.getBlockState(blockpos3).getBlock().getAmbientOcclusionLightValue();
			blockpos3.offsetEvenFaster(facingIn, blockpos5);
			boolean flag2 = blockAccessIn.getBlockState(blockpos5).getBlock().isTranslucent();

			blockpos.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[3], blockpos4);
			int l = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos4);
			float f3 = blockAccessIn.getBlockState(blockpos4).getBlock().getAmbientOcclusionLightValue();
			blockpos4.offsetEvenFaster(facingIn, blockpos5);
			boolean flag3 = blockAccessIn.getBlockState(blockpos5).getBlock().isTranslucent();

			float f4;
			int i1;
			if (!flag2 && !flag) {
				f4 = f;
				i1 = i;
			} else {
				blockpos1.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[2], blockpos5);
				f4 = blockAccessIn.getBlockState(blockpos5).getBlock().getAmbientOcclusionLightValue();
				i1 = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos5);
			}

			float f5;
			int j1;
			if (!flag3 && !flag) {
				f5 = f;
				j1 = i;
			} else {
				blockpos1.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[3], blockpos5);
				f5 = blockAccessIn.getBlockState(blockpos5).getBlock().getAmbientOcclusionLightValue();
				j1 = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos5);
			}

			float f6;
			int k1;
			if (!flag2 && !flag1) {
				f6 = f1;
				k1 = j;
			} else {
				blockpos2.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[2], blockpos5);
				f6 = blockAccessIn.getBlockState(blockpos5).getBlock().getAmbientOcclusionLightValue();
				k1 = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos5);
			}

			float f7;
			int l1;
			if (!flag3 && !flag1) {
				f7 = f1;
				l1 = j;
			} else {
				blockpos2.offsetEvenFaster(blockmodelrenderer$enumneighborinfo.corners[3], blockpos5);
				f7 = blockAccessIn.getBlockState(blockpos5).getBlock().getAmbientOcclusionLightValue();
				l1 = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos5);
			}

			blockPosIn.offsetEvenFaster(facingIn, blockpos5);
			int i3 = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockPosIn);
			if (boundsFlags.get(0) || !blockAccessIn.getBlockState(blockpos5).getBlock().isOpaqueCube()) {
				i3 = blockIn.getMixedBrightnessForBlock(blockAccessIn, blockpos5);
			}

			float f8 = boundsFlags.get(0)
					? blockAccessIn.getBlockState(blockpos).getBlock().getAmbientOcclusionLightValue()
					: blockAccessIn.getBlockState(blockPosIn).getBlock().getAmbientOcclusionLightValue();
			BlockModelRenderer.VertexTranslations blockmodelrenderer$vertextranslations = BlockModelRenderer.VertexTranslations
					.getVertexTranslations(facingIn);
			if (boundsFlags.get(1) && blockmodelrenderer$enumneighborinfo.doNonCubicWeight) {
				float f29 = (f3 + f + f5 + f8) * 0.25F;
				float f30 = (f2 + f + f4 + f8) * 0.25F;
				float f31 = (f2 + f1 + f6 + f8) * 0.25F;
				float f32 = (f3 + f1 + f7 + f8) * 0.25F;
				float f13 = quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[0].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[1].shape];
				float f14 = quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[2].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[3].shape];
				float f15 = quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[4].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[5].shape];
				float f16 = quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[6].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert0Weights[7].shape];
				float f17 = quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[0].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[1].shape];
				float f18 = quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[2].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[3].shape];
				float f19 = quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[4].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[5].shape];
				float f20 = quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[6].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert1Weights[7].shape];
				float f21 = quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[0].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[1].shape];
				float f22 = quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[2].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[3].shape];
				float f23 = quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[4].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[5].shape];
				float f24 = quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[6].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert2Weights[7].shape];
				float f25 = quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[0].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[1].shape];
				float f26 = quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[2].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[3].shape];
				float f27 = quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[4].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[5].shape];
				float f28 = quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[6].shape]
						* quadBounds[blockmodelrenderer$enumneighborinfo.vert3Weights[7].shape];
				this.vertexColorMultiplier[blockmodelrenderer$vertextranslations.vert0] = f29 * f13 + f30 * f14
						+ f31 * f15 + f32 * f16;
				this.vertexColorMultiplier[blockmodelrenderer$vertextranslations.vert1] = f29 * f17 + f30 * f18
						+ f31 * f19 + f32 * f20;
				this.vertexColorMultiplier[blockmodelrenderer$vertextranslations.vert2] = f29 * f21 + f30 * f22
						+ f31 * f23 + f32 * f24;
				this.vertexColorMultiplier[blockmodelrenderer$vertextranslations.vert3] = f29 * f25 + f30 * f26
						+ f31 * f27 + f32 * f28;
				int i2 = getAoBrightness(l, i, j1, i3);
				int j2 = getAoBrightness(k, i, i1, i3);
				int k2 = getAoBrightness(k, j, k1, i3);
				int l2 = getAoBrightness(l, j, l1, i3);
				this.vertexBrightness[blockmodelrenderer$vertextranslations.vert0] = getVertexBrightness(i2,
						j2, k2, l2, f13, f14, f15, f16);
				this.vertexBrightness[blockmodelrenderer$vertextranslations.vert1] = getVertexBrightness(i2,
						j2, k2, l2, f17, f18, f19, f20);
				this.vertexBrightness[blockmodelrenderer$vertextranslations.vert2] = getVertexBrightness(i2,
						j2, k2, l2, f21, f22, f23, f24);
				this.vertexBrightness[blockmodelrenderer$vertextranslations.vert3] = getVertexBrightness(i2,
						j2, k2, l2, f25, f26, f27, f28);
			} else {
				float f9 = (f3 + f + f5 + f8) * 0.25F;
				float f10 = (f2 + f + f4 + f8) * 0.25F;
				float f11 = (f2 + f1 + f6 + f8) * 0.25F;
				float f12 = (f3 + f1 + f7 + f8) * 0.25F;
				this.vertexBrightness[blockmodelrenderer$vertextranslations.vert0] = getAoBrightness(l, i, j1,
						i3);
				this.vertexBrightness[blockmodelrenderer$vertextranslations.vert1] = getAoBrightness(k, i, i1,
						i3);
				this.vertexBrightness[blockmodelrenderer$vertextranslations.vert2] = getAoBrightness(k, j, k1,
						i3);
				this.vertexBrightness[blockmodelrenderer$vertextranslations.vert3] = getAoBrightness(l, j, l1,
						i3);
				this.vertexColorMultiplier[blockmodelrenderer$vertextranslations.vert0] = f9;
				this.vertexColorMultiplier[blockmodelrenderer$vertextranslations.vert1] = f10;
				this.vertexColorMultiplier[blockmodelrenderer$vertextranslations.vert2] = f11;
				this.vertexColorMultiplier[blockmodelrenderer$vertextranslations.vert3] = f12;
			}

		}
	}

	private static int getVertexBrightness(int parInt1, int parInt2, int parInt3, int parInt4, float parFloat1,
			float parFloat2, float parFloat3, float parFloat4) {
		int i = (int) ((float) (parInt1 >> 16 & 255) * parFloat1 + (float) (parInt2 >> 16 & 255) * parFloat2
				+ (float) (parInt3 >> 16 & 255) * parFloat3 + (float) (parInt4 >> 16 & 255) * parFloat4) & 255;
		int j = (int) ((float) (parInt1 & 255) * parFloat1 + (float) (parInt2 & 255) * parFloat2
				+ (float) (parInt3 & 255) * parFloat3 + (float) (parInt4 & 255) * parFloat4) & 255;
		return i << 16 | j;
	}

	public static enum EnumNeighborInfo {
		DOWN(new EnumFacing[] { EnumFacing.WEST, EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.SOUTH }, 0.5F, false,
				new BlockModelRenderer.Orientation[0], new BlockModelRenderer.Orientation[0],
				new BlockModelRenderer.Orientation[0], new BlockModelRenderer.Orientation[0]),
		UP(new EnumFacing[] { EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH }, 1.0F, false,
				new BlockModelRenderer.Orientation[0], new BlockModelRenderer.Orientation[0],
				new BlockModelRenderer.Orientation[0], new BlockModelRenderer.Orientation[0]),
		NORTH(new EnumFacing[] { EnumFacing.UP, EnumFacing.DOWN, EnumFacing.EAST, EnumFacing.WEST }, 0.8F, true,
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.FLIP_WEST, BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.WEST, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.WEST, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.FLIP_WEST },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.FLIP_EAST, BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.EAST, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.EAST, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.FLIP_EAST },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.FLIP_EAST, BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.EAST, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.EAST, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.FLIP_EAST },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.FLIP_WEST, BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.WEST, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.WEST, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.FLIP_WEST }),
		SOUTH(new EnumFacing[] { EnumFacing.WEST, EnumFacing.EAST, EnumFacing.DOWN, EnumFacing.UP }, 0.8F, true,
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.FLIP_WEST, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.FLIP_WEST, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.WEST, BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.WEST },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.FLIP_WEST, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.FLIP_WEST, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.WEST, BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.WEST },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.FLIP_EAST, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.FLIP_EAST, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.EAST, BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.EAST },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.FLIP_EAST, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.FLIP_EAST, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.EAST, BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.EAST }),
		WEST(new EnumFacing[] { EnumFacing.UP, EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH }, 0.6F, true,
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.SOUTH, BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.FLIP_SOUTH, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.FLIP_SOUTH, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.SOUTH },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.NORTH, BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.FLIP_NORTH, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.FLIP_NORTH, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.NORTH },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.NORTH, BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.FLIP_NORTH, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.FLIP_NORTH, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.NORTH },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.SOUTH, BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.FLIP_SOUTH, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.FLIP_SOUTH, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.SOUTH }),
		EAST(new EnumFacing[] { EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH }, 0.6F, true,
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.SOUTH, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.FLIP_SOUTH, BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.FLIP_SOUTH, BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.SOUTH },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.NORTH, BlockModelRenderer.Orientation.FLIP_DOWN,
						BlockModelRenderer.Orientation.FLIP_NORTH, BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.FLIP_NORTH, BlockModelRenderer.Orientation.DOWN,
						BlockModelRenderer.Orientation.NORTH },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.NORTH, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.FLIP_NORTH, BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.FLIP_NORTH, BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.NORTH },
				new BlockModelRenderer.Orientation[] { BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.SOUTH, BlockModelRenderer.Orientation.FLIP_UP,
						BlockModelRenderer.Orientation.FLIP_SOUTH, BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.FLIP_SOUTH, BlockModelRenderer.Orientation.UP,
						BlockModelRenderer.Orientation.SOUTH });

		protected final EnumFacing[] corners;
		protected final float shadeWeight;
		protected final boolean doNonCubicWeight;
		protected final BlockModelRenderer.Orientation[] vert0Weights;
		protected final BlockModelRenderer.Orientation[] vert1Weights;
		protected final BlockModelRenderer.Orientation[] vert2Weights;
		protected final BlockModelRenderer.Orientation[] vert3Weights;
		private static final BlockModelRenderer.EnumNeighborInfo[] VALUES = new BlockModelRenderer.EnumNeighborInfo[6];

		private EnumNeighborInfo(EnumFacing[] parArrayOfEnumFacing, float parFloat1, boolean parFlag,
				BlockModelRenderer.Orientation[] parArrayOfOrientation,
				BlockModelRenderer.Orientation[] parArrayOfOrientation_2,
				BlockModelRenderer.Orientation[] parArrayOfOrientation_3,
				BlockModelRenderer.Orientation[] parArrayOfOrientation_4) {
			this.corners = parArrayOfEnumFacing;
			this.shadeWeight = parFloat1;
			this.doNonCubicWeight = parFlag;
			this.vert0Weights = parArrayOfOrientation;
			this.vert1Weights = parArrayOfOrientation_2;
			this.vert2Weights = parArrayOfOrientation_3;
			this.vert3Weights = parArrayOfOrientation_4;
		}

		public static BlockModelRenderer.EnumNeighborInfo getNeighbourInfo(EnumFacing parEnumFacing) {
			return VALUES[parEnumFacing.getIndex()];
		}

		static {
			VALUES[EnumFacing.DOWN.getIndex()] = DOWN;
			VALUES[EnumFacing.UP.getIndex()] = UP;
			VALUES[EnumFacing.NORTH.getIndex()] = NORTH;
			VALUES[EnumFacing.SOUTH.getIndex()] = SOUTH;
			VALUES[EnumFacing.WEST.getIndex()] = WEST;
			VALUES[EnumFacing.EAST.getIndex()] = EAST;
		}
	}

	public static enum Orientation {
		DOWN(EnumFacing.DOWN, false), UP(EnumFacing.UP, false), NORTH(EnumFacing.NORTH, false),
		SOUTH(EnumFacing.SOUTH, false), WEST(EnumFacing.WEST, false), EAST(EnumFacing.EAST, false),
		FLIP_DOWN(EnumFacing.DOWN, true), FLIP_UP(EnumFacing.UP, true), FLIP_NORTH(EnumFacing.NORTH, true),
		FLIP_SOUTH(EnumFacing.SOUTH, true), FLIP_WEST(EnumFacing.WEST, true), FLIP_EAST(EnumFacing.EAST, true);

		protected final int shape;

		private Orientation(EnumFacing parEnumFacing, boolean parFlag) {
			this.shape = parEnumFacing.getIndex() + (parFlag ? EnumFacing._VALUES.length : 0);
		}
	}

	static enum VertexTranslations {
		DOWN(0, 1, 2, 3), UP(2, 3, 0, 1), NORTH(3, 0, 1, 2), SOUTH(0, 1, 2, 3), WEST(3, 0, 1, 2), EAST(1, 2, 3, 0);

		private final int vert0;
		private final int vert1;
		private final int vert2;
		private final int vert3;
		private static final BlockModelRenderer.VertexTranslations[] BlockModelRendererVALUES = new BlockModelRenderer.VertexTranslations[6];

		private VertexTranslations(int parInt2, int parInt3, int parInt4, int parInt5) {
			this.vert0 = parInt2;
			this.vert1 = parInt3;
			this.vert2 = parInt4;
			this.vert3 = parInt5;
		}

		public static BlockModelRenderer.VertexTranslations getVertexTranslations(EnumFacing parEnumFacing) {
			return BlockModelRendererVALUES[parEnumFacing.getIndex()];
		}

		static {
			BlockModelRendererVALUES[EnumFacing.DOWN.getIndex()] = DOWN;
			BlockModelRendererVALUES[EnumFacing.UP.getIndex()] = UP;
			BlockModelRendererVALUES[EnumFacing.NORTH.getIndex()] = NORTH;
			BlockModelRendererVALUES[EnumFacing.SOUTH.getIndex()] = SOUTH;
			BlockModelRendererVALUES[EnumFacing.WEST.getIndex()] = WEST;
			BlockModelRendererVALUES[EnumFacing.EAST.getIndex()] = EAST;
		}
	}
}