package net.minecraft.block;

import java.util.List;

import com.google.common.collect.Lists;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityNote;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;

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
public class BlockNote extends BlockContainer {
	private static final List<String> INSTRUMENTS = Lists
			.newArrayList(new String[] { "harp", "bd", "snare", "hat", "bassattack" });
	private static final float[] PITCH_TABLE = {
			0.5F, 0.5297316F, 0.561231F, 0.59460354F, 0.62996054F, 0.6674199F, 0.70710677F, 0.74915355F, 0.7937005F,
			0.8408964F, 0.8908987F, 0.9438743F, 1.0F, 1.0594631F, 1.122462F, 1.1892071F, 1.2599211F, 1.3348398F,
			1.4142135F, 1.4983071F, 1.587401F, 1.6817929F, 1.7817974F, 1.8877486F, 2.0F
	};

	public BlockNote() {
		super(Material.wood);
		this.setCreativeTab(CreativeTabs.tabRedstone);
	}

	/**+
	 * Called when a neighboring block changes.
	 */
	public void onNeighborBlockChange(World world, BlockPos blockpos, IBlockState var3, Block var4) {
		boolean flag = world.isBlockPowered(blockpos);
		TileEntity tileentity = world.getTileEntity(blockpos);
		if (tileentity instanceof TileEntityNote) {
			TileEntityNote tileentitynote = (TileEntityNote) tileentity;
			if (tileentitynote.previousRedstoneState != flag) {
				if (flag) {
					tileentitynote.triggerNote(world, blockpos);
				}

				tileentitynote.previousRedstoneState = flag;
			}
		}

	}

	public boolean onBlockActivated(World world, BlockPos blockpos, IBlockState var3, EntityPlayer entityplayer,
			EnumFacing var5, float var6, float var7, float var8) {
		if (world.isRemote) {
			return true;
		} else {
			TileEntity tileentity = world.getTileEntity(blockpos);
			if (tileentity instanceof TileEntityNote) {
				TileEntityNote tileentitynote = (TileEntityNote) tileentity;
				tileentitynote.changePitch();
				tileentitynote.triggerNote(world, blockpos);
				entityplayer.triggerAchievement(StatList.field_181735_S);
			}

			return true;
		}
	}

	public void onBlockClicked(World world, BlockPos blockpos, EntityPlayer entityplayer) {
		if (!world.isRemote) {
			TileEntity tileentity = world.getTileEntity(blockpos);
			if (tileentity instanceof TileEntityNote) {
				((TileEntityNote) tileentity).triggerNote(world, blockpos);
				entityplayer.triggerAchievement(StatList.field_181734_R);
			}
		}
	}

	/**+
	 * Returns a new instance of a block's tile entity class. Called
	 * on placing the block.
	 */
	public TileEntity createNewTileEntity(World var1, int var2) {
		return new TileEntityNote();
	}

	private String getInstrument(int id) {
		if (id < 0 || id >= INSTRUMENTS.size()) {
			id = 0;
		}

		return (String) INSTRUMENTS.get(id);
	}

	/**+
	 * Called on both Client and Server when World#addBlockEvent is
	 * called
	 */
	public boolean onBlockEventReceived(World world, BlockPos blockpos, IBlockState var3, int i, int j) {
		float f = j >= 0 && j < PITCH_TABLE.length ? PITCH_TABLE[j]
				: (float) Math.pow(2.0D, (double) (j - 12) / 12.0D);
		world.playSoundEffect((double) blockpos.getX() + 0.5D, (double) blockpos.getY() + 0.5D,
				(double) blockpos.getZ() + 0.5D, "note." + this.getInstrument(i), 3.0F, f);
		world.spawnParticle(EnumParticleTypes.NOTE, (double) blockpos.getX() + 0.5D, (double) blockpos.getY() + 1.2D,
				(double) blockpos.getZ() + 0.5D, (double) j / 24.0D, 0.0D, 0.0D, new int[0]);
		return true;
	}

	/**+
	 * The type of render function called. 3 for standard block
	 * models, 2 for TESR's, 1 for liquids, -1 is no render
	 */
	public int getRenderType() {
		return 3;
	}
}