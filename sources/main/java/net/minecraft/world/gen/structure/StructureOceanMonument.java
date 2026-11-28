package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.lax1dude.eaglercraft.v1_8.EaglercraftRandom;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

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
public class StructureOceanMonument extends MapGenStructure {
	private int spacing;
	private int separation;
	public static final List<BiomeGenBase> WATER_BIOMES = Arrays.asList(new BiomeGenBase[] { BiomeGenBase.ocean,
			BiomeGenBase.deepOcean, BiomeGenBase.river, BiomeGenBase.frozenOcean, BiomeGenBase.frozenRiver });
	private static final List<BiomeGenBase.SpawnListEntry> MONUMENT_ENEMIES = Lists.newArrayList();

	public StructureOceanMonument(boolean scramble) {
		super(scramble);
		this.spacing = 32;
		this.separation = 5;
	}

	public StructureOceanMonument(Map<String, String> parMap, boolean scramble) {
		this(scramble);

		for (Entry entry : parMap.entrySet()) {
			if (((String) entry.getKey()).equals("spacing")) {
				this.spacing = MathHelper.parseIntWithDefaultAndMax((String) entry.getValue(),
						this.spacing, 1);
			} else if (((String) entry.getKey()).equals("separation")) {
				this.separation = MathHelper.parseIntWithDefaultAndMax((String) entry.getValue(),
						this.separation, 1);
			}
		}

	}

	public String getStructureName() {
		return "Monument";
	}

	protected boolean canSpawnStructureAtCoords(int i, int j) {
		int k = i;
		int l = j;
		if (i < 0) {
			i -= this.spacing - 1;
		}

		if (j < 0) {
			j -= this.spacing - 1;
		}

		int i1 = i / this.spacing;
		int j1 = j / this.spacing;
		EaglercraftRandom random = this.worldObj.setRandomSeed(i1, j1, 10387313);
		i1 = i1 * this.spacing;
		j1 = j1 * this.spacing;
		i1 = i1 + (random.nextInt(this.spacing - this.separation)
				+ random.nextInt(this.spacing - this.separation)) / 2;
		j1 = j1 + (random.nextInt(this.spacing - this.separation)
				+ random.nextInt(this.spacing - this.separation)) / 2;
		if (k == i1 && l == j1) {
			if (this.worldObj.getWorldChunkManager().getBiomeGenerator(new BlockPos(k * 16 + 8, 64, l * 16 + 8),
					(BiomeGenBase) null) != BiomeGenBase.deepOcean) {
				return false;
			}

			boolean flag = this.worldObj.getWorldChunkManager().areBiomesViable(k * 16 + 8, l * 16 + 8, 29,
					WATER_BIOMES);
			if (flag) {
				return true;
			}
		}

		return false;
	}

	protected StructureStart getStructureStart(int i, int j) {
		return new StructureOceanMonument.StartMonument(this.worldObj, this.rand, i, j);
	}

	public List<BiomeGenBase.SpawnListEntry> getScatteredFeatureSpawnList() {
		return MONUMENT_ENEMIES;
	}

	static {
		MONUMENT_ENEMIES.add(new BiomeGenBase.SpawnListEntry(EntityGuardian.class, 1, 2, 4));
	}

	public static class StartMonument extends StructureStart {
		private Set<ChunkCoordIntPair> processed = Sets.newHashSet();
		private boolean wasCreated;

		public StartMonument() {
		}

		public StartMonument(World worldIn, EaglercraftRandom parRandom, int parInt1, int parInt2) {
			super(parInt1, parInt2);
			this.create(worldIn, parRandom, parInt1, parInt2);
		}

		private void create(World worldIn, EaglercraftRandom parRandom, int parInt1, int parInt2) {
			parRandom.setSeed(worldIn.getSeed());
			long i = parRandom.nextLong();
			long j = parRandom.nextLong();
			long k = (long) parInt1 * i;
			long l = (long) parInt2 * j;
			parRandom.setSeed(k ^ l ^ worldIn.getSeed());
			int i1 = parInt1 * 16 + 8 - 29;
			int j1 = parInt2 * 16 + 8 - 29;
			EnumFacing enumfacing = EnumFacing.Plane.HORIZONTAL.random(parRandom);
			this.components.add(new StructureOceanMonumentPieces.MonumentBuilding(parRandom, i1, j1, enumfacing));
			this.updateBoundingBox();
			this.wasCreated = true;
		}

		public void generateStructure(World worldIn, EaglercraftRandom rand, StructureBoundingBox structurebb) {
			if (!this.wasCreated) {
				this.components.clear();
				this.create(worldIn, rand, this.getChunkPosX(), this.getChunkPosZ());
			}

			super.generateStructure(worldIn, rand, structurebb);
		}

		public boolean isValidForPostProcess(ChunkCoordIntPair pair) {
			return this.processed.contains(pair) ? false : super.isValidForPostProcess(pair);
		}

		public void notifyPostProcessAt(ChunkCoordIntPair pair) {
			super.notifyPostProcessAt(pair);
			this.processed.add(pair);
		}

		public void writeToNBT(NBTTagCompound tagCompound) {
			super.writeToNBT(tagCompound);
			NBTTagList nbttaglist = new NBTTagList();

			for (ChunkCoordIntPair chunkcoordintpair : this.processed) {
				NBTTagCompound nbttagcompound = new NBTTagCompound();
				nbttagcompound.setInteger("X", chunkcoordintpair.chunkXPos);
				nbttagcompound.setInteger("Z", chunkcoordintpair.chunkZPos);
				nbttaglist.appendTag(nbttagcompound);
			}

			tagCompound.setTag("Processed", nbttaglist);
		}

		public void readFromNBT(NBTTagCompound tagCompound) {
			super.readFromNBT(tagCompound);
			if (tagCompound.hasKey("Processed", 9)) {
				NBTTagList nbttaglist = tagCompound.getTagList("Processed", 10);

				for (int i = 0; i < nbttaglist.tagCount(); ++i) {
					NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(i);
					this.processed
							.add(new ChunkCoordIntPair(nbttagcompound.getInteger("X"), nbttagcompound.getInteger("Z")));
				}
			}

		}
	}
}