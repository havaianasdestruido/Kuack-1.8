package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.ArrayUtils;
import net.lax1dude.eaglercraft.v1_8.EaglercraftRandom;
import net.minecraft.block.BlockPrismarine;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
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
public class StructureOceanMonumentPieces {
	public static void registerOceanMonumentPieces() {
		MapGenStructureIO.registerStructureComponent(StructureOceanMonumentPieces.MonumentBuilding.class, "OMB");
		MapGenStructureIO.registerStructureComponent(StructureOceanMonumentPieces.MonumentCoreRoom.class, "OMCR");
		MapGenStructureIO.registerStructureComponent(StructureOceanMonumentPieces.DoubleXRoom.class, "OMDXR");
		MapGenStructureIO.registerStructureComponent(StructureOceanMonumentPieces.DoubleXYRoom.class, "OMDXYR");
		MapGenStructureIO.registerStructureComponent(StructureOceanMonumentPieces.DoubleYRoom.class, "OMDYR");
		MapGenStructureIO.registerStructureComponent(StructureOceanMonumentPieces.DoubleYZRoom.class, "OMDYZR");
		MapGenStructureIO.registerStructureComponent(StructureOceanMonumentPieces.DoubleZRoom.class, "OMDZR");
		MapGenStructureIO.registerStructureComponent(StructureOceanMonumentPieces.EntryRoom.class, "OMEntry");
		MapGenStructureIO.registerStructureComponent(StructureOceanMonumentPieces.Penthouse.class, "OMPenthouse");
		MapGenStructureIO.registerStructureComponent(StructureOceanMonumentPieces.SimpleRoom.class, "OMSimple");
		MapGenStructureIO.registerStructureComponent(StructureOceanMonumentPieces.SimpleTopRoom.class, "OMSimpleT");
	}

	public static class DoubleXRoom extends StructureOceanMonumentPieces.Piece {
		public DoubleXRoom() {
		}

		public DoubleXRoom(EnumFacing parEnumFacing, StructureOceanMonumentPieces.RoomDefinition parRoomDefinition,
				EaglercraftRandom parRandom) {
			super(1, parEnumFacing, parRoomDefinition, 2, 1, 1);
		}

		public boolean addComponentParts(World world, EaglercraftRandom var2,
				StructureBoundingBox structureboundingbox) {
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition = this.roomDefinition.connections[EnumFacing.EAST
					.getIndex()];
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition1 = this.roomDefinition;
			if (this.roomDefinition.index / 25 > 0) {
				this.generateDefaultFloor(world, structureboundingbox, 8, 0,
						structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.DOWN.getIndex()]);
				this.generateDefaultFloor(world, structureboundingbox, 0, 0,
						structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.DOWN.getIndex()]);
			}

			if (structureoceanmonumentpieces$roomdefinition1.connections[EnumFacing.UP.getIndex()] == null) {
				this.generateBoxOnFillOnly(world, structureboundingbox, 1, 4, 1, 7, 4, 6, ROUGH_PRISMARINE);
			}

			if (structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.UP.getIndex()] == null) {
				this.generateBoxOnFillOnly(world, structureboundingbox, 8, 4, 1, 14, 4, 6, ROUGH_PRISMARINE);
			}

			this.fillWithBlocks(world, structureboundingbox, 0, 3, 0, 0, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 15, 3, 0, 15, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 3, 0, 15, 3, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 3, 7, 14, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 0, 2, 0, 0, 2, 7, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 15, 2, 0, 15, 2, 7, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 2, 0, 15, 2, 0, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 2, 7, 14, 2, 7, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 0, 1, 0, 0, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 15, 1, 0, 15, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 1, 0, 15, 1, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 1, 7, 14, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 1, 0, 10, 1, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, 2, 0, 9, 2, 3, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 3, 0, 10, 3, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.setBlockState(world, SEA_LANTERN, 6, 2, 3, structureboundingbox);
			this.setBlockState(world, SEA_LANTERN, 9, 2, 3, structureboundingbox);
			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.SOUTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 1, 0, 4, 2, 0, false);
			}

			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.NORTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 1, 7, 4, 2, 7, false);
			}

			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.WEST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 0, 1, 3, 0, 2, 4, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.SOUTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 11, 1, 0, 12, 2, 0, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.NORTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 11, 1, 7, 12, 2, 7, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.EAST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 15, 1, 3, 15, 2, 4, false);
			}

			return true;
		}
	}

	public static class DoubleXYRoom extends StructureOceanMonumentPieces.Piece {
		public DoubleXYRoom() {
		}

		public DoubleXYRoom(EnumFacing parEnumFacing, StructureOceanMonumentPieces.RoomDefinition parRoomDefinition,
				EaglercraftRandom parRandom) {
			super(1, parEnumFacing, parRoomDefinition, 2, 2, 1);
		}

		public boolean addComponentParts(World world, EaglercraftRandom var2,
				StructureBoundingBox structureboundingbox) {
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition = this.roomDefinition.connections[EnumFacing.EAST
					.getIndex()];
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition1 = this.roomDefinition;
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition2 = structureoceanmonumentpieces$roomdefinition1.connections[EnumFacing.UP
					.getIndex()];
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition3 = structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.UP
					.getIndex()];
			if (this.roomDefinition.index / 25 > 0) {
				this.generateDefaultFloor(world, structureboundingbox, 8, 0,
						structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.DOWN.getIndex()]);
				this.generateDefaultFloor(world, structureboundingbox, 0, 0,
						structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.DOWN.getIndex()]);
			}

			if (structureoceanmonumentpieces$roomdefinition2.connections[EnumFacing.UP.getIndex()] == null) {
				this.generateBoxOnFillOnly(world, structureboundingbox, 1, 8, 1, 7, 8, 6, ROUGH_PRISMARINE);
			}

			if (structureoceanmonumentpieces$roomdefinition3.connections[EnumFacing.UP.getIndex()] == null) {
				this.generateBoxOnFillOnly(world, structureboundingbox, 8, 8, 1, 14, 8, 6, ROUGH_PRISMARINE);
			}

			for (int i = 1; i <= 7; ++i) {
				IBlockState iblockstate = BRICKS_PRISMARINE;
				if (i == 2 || i == 6) {
					iblockstate = ROUGH_PRISMARINE;
				}

				this.fillWithBlocks(world, structureboundingbox, 0, i, 0, 0, i, 7, iblockstate, iblockstate, false);
				this.fillWithBlocks(world, structureboundingbox, 15, i, 0, 15, i, 7, iblockstate, iblockstate, false);
				this.fillWithBlocks(world, structureboundingbox, 1, i, 0, 15, i, 0, iblockstate, iblockstate, false);
				this.fillWithBlocks(world, structureboundingbox, 1, i, 7, 14, i, 7, iblockstate, iblockstate, false);
			}

			this.fillWithBlocks(world, structureboundingbox, 2, 1, 3, 2, 7, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 3, 1, 2, 4, 7, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 3, 1, 5, 4, 7, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 13, 1, 3, 13, 7, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 11, 1, 2, 12, 7, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 11, 1, 5, 12, 7, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 1, 3, 5, 3, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 10, 1, 3, 10, 3, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 7, 2, 10, 7, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 5, 2, 5, 7, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 10, 5, 2, 10, 7, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 5, 5, 5, 7, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 10, 5, 5, 10, 7, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.setBlockState(world, BRICKS_PRISMARINE, 6, 6, 2, structureboundingbox);
			this.setBlockState(world, BRICKS_PRISMARINE, 9, 6, 2, structureboundingbox);
			this.setBlockState(world, BRICKS_PRISMARINE, 6, 6, 5, structureboundingbox);
			this.setBlockState(world, BRICKS_PRISMARINE, 9, 6, 5, structureboundingbox);
			this.fillWithBlocks(world, structureboundingbox, 5, 4, 3, 6, 4, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 9, 4, 3, 10, 4, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.setBlockState(world, SEA_LANTERN, 5, 4, 2, structureboundingbox);
			this.setBlockState(world, SEA_LANTERN, 5, 4, 5, structureboundingbox);
			this.setBlockState(world, SEA_LANTERN, 10, 4, 2, structureboundingbox);
			this.setBlockState(world, SEA_LANTERN, 10, 4, 5, structureboundingbox);
			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.SOUTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 1, 0, 4, 2, 0, false);
			}

			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.NORTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 1, 7, 4, 2, 7, false);
			}

			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.WEST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 0, 1, 3, 0, 2, 4, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.SOUTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 11, 1, 0, 12, 2, 0, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.NORTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 11, 1, 7, 12, 2, 7, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.EAST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 15, 1, 3, 15, 2, 4, false);
			}

			if (structureoceanmonumentpieces$roomdefinition2.hasOpening[EnumFacing.SOUTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 5, 0, 4, 6, 0, false);
			}

			if (structureoceanmonumentpieces$roomdefinition2.hasOpening[EnumFacing.NORTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 5, 7, 4, 6, 7, false);
			}

			if (structureoceanmonumentpieces$roomdefinition2.hasOpening[EnumFacing.WEST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 0, 5, 3, 0, 6, 4, false);
			}

			if (structureoceanmonumentpieces$roomdefinition3.hasOpening[EnumFacing.SOUTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 11, 5, 0, 12, 6, 0, false);
			}

			if (structureoceanmonumentpieces$roomdefinition3.hasOpening[EnumFacing.NORTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 11, 5, 7, 12, 6, 7, false);
			}

			if (structureoceanmonumentpieces$roomdefinition3.hasOpening[EnumFacing.EAST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 15, 5, 3, 15, 6, 4, false);
			}

			return true;
		}
	}

	public static class DoubleYRoom extends StructureOceanMonumentPieces.Piece {
		public DoubleYRoom() {
		}

		public DoubleYRoom(EnumFacing parEnumFacing, StructureOceanMonumentPieces.RoomDefinition parRoomDefinition,
				EaglercraftRandom parRandom) {
			super(1, parEnumFacing, parRoomDefinition, 1, 2, 1);
		}

		public boolean addComponentParts(World world, EaglercraftRandom var2,
				StructureBoundingBox structureboundingbox) {
			if (this.roomDefinition.index / 25 > 0) {
				this.generateDefaultFloor(world, structureboundingbox, 0, 0,
						this.roomDefinition.hasOpening[EnumFacing.DOWN.getIndex()]);
			}

			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition = this.roomDefinition.connections[EnumFacing.UP
					.getIndex()];
			if (structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.UP.getIndex()] == null) {
				this.generateBoxOnFillOnly(world, structureboundingbox, 1, 8, 1, 6, 8, 6, ROUGH_PRISMARINE);
			}

			this.fillWithBlocks(world, structureboundingbox, 0, 4, 0, 0, 4, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 7, 4, 0, 7, 4, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 4, 0, 6, 4, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 4, 7, 6, 4, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 2, 4, 1, 2, 4, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 4, 2, 1, 4, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 4, 1, 5, 4, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, 4, 2, 6, 4, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 2, 4, 5, 2, 4, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 4, 5, 1, 4, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 4, 5, 5, 4, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, 4, 5, 6, 4, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition1 = this.roomDefinition;

			for (int i = 1; i <= 5; i += 4) {
				byte b0 = 0;
				if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.SOUTH.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, 2, i, b0, 2, i + 2, b0, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, 5, i, b0, 5, i + 2, b0, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, 3, i + 2, b0, 4, i + 2, b0, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
				} else {
					this.fillWithBlocks(world, structureboundingbox, 0, i, b0, 7, i + 2, b0, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, 0, i + 1, b0, 7, i + 1, b0, ROUGH_PRISMARINE,
							ROUGH_PRISMARINE, false);
				}

				b0 = 7;
				if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.NORTH.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, 2, i, b0, 2, i + 2, b0, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, 5, i, b0, 5, i + 2, b0, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, 3, i + 2, b0, 4, i + 2, b0, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
				} else {
					this.fillWithBlocks(world, structureboundingbox, 0, i, b0, 7, i + 2, b0, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, 0, i + 1, b0, 7, i + 1, b0, ROUGH_PRISMARINE,
							ROUGH_PRISMARINE, false);
				}

				byte b1 = 0;
				if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.WEST.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, b1, i, 2, b1, i + 2, 2, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, b1, i, 5, b1, i + 2, 5, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, b1, i + 2, 3, b1, i + 2, 4, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
				} else {
					this.fillWithBlocks(world, structureboundingbox, b1, i, 0, b1, i + 2, 7, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, b1, i + 1, 0, b1, i + 1, 7, ROUGH_PRISMARINE,
							ROUGH_PRISMARINE, false);
				}

				b1 = 7;
				if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.EAST.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, b1, i, 2, b1, i + 2, 2, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, b1, i, 5, b1, i + 2, 5, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, b1, i + 2, 3, b1, i + 2, 4, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
				} else {
					this.fillWithBlocks(world, structureboundingbox, b1, i, 0, b1, i + 2, 7, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, b1, i + 1, 0, b1, i + 1, 7, ROUGH_PRISMARINE,
							ROUGH_PRISMARINE, false);
				}

				structureoceanmonumentpieces$roomdefinition1 = structureoceanmonumentpieces$roomdefinition;
			}

			return true;
		}
	}

	public static class DoubleYZRoom extends StructureOceanMonumentPieces.Piece {
		public DoubleYZRoom() {
		}

		public DoubleYZRoom(EnumFacing parEnumFacing, StructureOceanMonumentPieces.RoomDefinition parRoomDefinition,
				EaglercraftRandom parRandom) {
			super(1, parEnumFacing, parRoomDefinition, 1, 2, 2);
		}

		public boolean addComponentParts(World world, EaglercraftRandom var2,
				StructureBoundingBox structureboundingbox) {
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition = this.roomDefinition.connections[EnumFacing.NORTH
					.getIndex()];
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition1 = this.roomDefinition;
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition2 = structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.UP
					.getIndex()];
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition3 = structureoceanmonumentpieces$roomdefinition1.connections[EnumFacing.UP
					.getIndex()];
			if (this.roomDefinition.index / 25 > 0) {
				this.generateDefaultFloor(world, structureboundingbox, 0, 8,
						structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.DOWN.getIndex()]);
				this.generateDefaultFloor(world, structureboundingbox, 0, 0,
						structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.DOWN.getIndex()]);
			}

			if (structureoceanmonumentpieces$roomdefinition3.connections[EnumFacing.UP.getIndex()] == null) {
				this.generateBoxOnFillOnly(world, structureboundingbox, 1, 8, 1, 6, 8, 7, ROUGH_PRISMARINE);
			}

			if (structureoceanmonumentpieces$roomdefinition2.connections[EnumFacing.UP.getIndex()] == null) {
				this.generateBoxOnFillOnly(world, structureboundingbox, 1, 8, 8, 6, 8, 14, ROUGH_PRISMARINE);
			}

			for (int i = 1; i <= 7; ++i) {
				IBlockState iblockstate = BRICKS_PRISMARINE;
				if (i == 2 || i == 6) {
					iblockstate = ROUGH_PRISMARINE;
				}

				this.fillWithBlocks(world, structureboundingbox, 0, i, 0, 0, i, 15, iblockstate, iblockstate, false);
				this.fillWithBlocks(world, structureboundingbox, 7, i, 0, 7, i, 15, iblockstate, iblockstate, false);
				this.fillWithBlocks(world, structureboundingbox, 1, i, 0, 6, i, 0, iblockstate, iblockstate, false);
				this.fillWithBlocks(world, structureboundingbox, 1, i, 15, 6, i, 15, iblockstate, iblockstate, false);
			}

			for (int j = 1; j <= 7; ++j) {
				IBlockState iblockstate1 = DARK_PRISMARINE;
				if (j == 2 || j == 6) {
					iblockstate1 = SEA_LANTERN;
				}

				this.fillWithBlocks(world, structureboundingbox, 3, j, 7, 4, j, 8, iblockstate1, iblockstate1, false);
			}

			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.SOUTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 1, 0, 4, 2, 0, false);
			}

			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.EAST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 7, 1, 3, 7, 2, 4, false);
			}

			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.WEST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 0, 1, 3, 0, 2, 4, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.NORTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 1, 15, 4, 2, 15, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.WEST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 0, 1, 11, 0, 2, 12, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.EAST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 7, 1, 11, 7, 2, 12, false);
			}

			if (structureoceanmonumentpieces$roomdefinition3.hasOpening[EnumFacing.SOUTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 5, 0, 4, 6, 0, false);
			}

			if (structureoceanmonumentpieces$roomdefinition3.hasOpening[EnumFacing.EAST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 7, 5, 3, 7, 6, 4, false);
				this.fillWithBlocks(world, structureboundingbox, 5, 4, 2, 6, 4, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 6, 1, 2, 6, 3, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 6, 1, 5, 6, 3, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
			}

			if (structureoceanmonumentpieces$roomdefinition3.hasOpening[EnumFacing.WEST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 0, 5, 3, 0, 6, 4, false);
				this.fillWithBlocks(world, structureboundingbox, 1, 4, 2, 2, 4, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 1, 2, 1, 3, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 1, 5, 1, 3, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
			}

			if (structureoceanmonumentpieces$roomdefinition2.hasOpening[EnumFacing.NORTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 5, 15, 4, 6, 15, false);
			}

			if (structureoceanmonumentpieces$roomdefinition2.hasOpening[EnumFacing.WEST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 0, 5, 11, 0, 6, 12, false);
				this.fillWithBlocks(world, structureboundingbox, 1, 4, 10, 2, 4, 13, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 1, 10, 1, 3, 10, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 1, 13, 1, 3, 13, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
			}

			if (structureoceanmonumentpieces$roomdefinition2.hasOpening[EnumFacing.EAST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 7, 5, 11, 7, 6, 12, false);
				this.fillWithBlocks(world, structureboundingbox, 5, 4, 10, 6, 4, 13, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 6, 1, 10, 6, 3, 10, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 6, 1, 13, 6, 3, 13, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
			}

			return true;
		}
	}

	public static class DoubleZRoom extends StructureOceanMonumentPieces.Piece {
		public DoubleZRoom() {
		}

		public DoubleZRoom(EnumFacing parEnumFacing, StructureOceanMonumentPieces.RoomDefinition parRoomDefinition,
				EaglercraftRandom parRandom) {
			super(1, parEnumFacing, parRoomDefinition, 1, 1, 2);
		}

		public boolean addComponentParts(World world, EaglercraftRandom var2,
				StructureBoundingBox structureboundingbox) {
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition = this.roomDefinition.connections[EnumFacing.NORTH
					.getIndex()];
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition1 = this.roomDefinition;
			if (this.roomDefinition.index / 25 > 0) {
				this.generateDefaultFloor(world, structureboundingbox, 0, 8,
						structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.DOWN.getIndex()]);
				this.generateDefaultFloor(world, structureboundingbox, 0, 0,
						structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.DOWN.getIndex()]);
			}

			if (structureoceanmonumentpieces$roomdefinition1.connections[EnumFacing.UP.getIndex()] == null) {
				this.generateBoxOnFillOnly(world, structureboundingbox, 1, 4, 1, 6, 4, 7, ROUGH_PRISMARINE);
			}

			if (structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.UP.getIndex()] == null) {
				this.generateBoxOnFillOnly(world, structureboundingbox, 1, 4, 8, 6, 4, 14, ROUGH_PRISMARINE);
			}

			this.fillWithBlocks(world, structureboundingbox, 0, 3, 0, 0, 3, 15, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 7, 3, 0, 7, 3, 15, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 3, 0, 7, 3, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 3, 15, 6, 3, 15, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 0, 2, 0, 0, 2, 15, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 7, 2, 0, 7, 2, 15, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 2, 0, 7, 2, 0, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 2, 15, 6, 2, 15, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 0, 1, 0, 0, 1, 15, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 7, 1, 0, 7, 1, 15, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 1, 0, 7, 1, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 1, 15, 6, 1, 15, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 1, 1, 1, 1, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, 1, 1, 6, 1, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 3, 1, 1, 3, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, 3, 1, 6, 3, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 1, 13, 1, 1, 14, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, 1, 13, 6, 1, 14, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 3, 13, 1, 3, 14, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, 3, 13, 6, 3, 14, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 2, 1, 6, 2, 3, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 1, 6, 5, 3, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 2, 1, 9, 2, 3, 9, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 1, 9, 5, 3, 9, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 3, 2, 6, 4, 2, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 3, 2, 9, 4, 2, 9, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 2, 2, 7, 2, 2, 8, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 2, 7, 5, 2, 8, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.setBlockState(world, SEA_LANTERN, 2, 2, 5, structureboundingbox);
			this.setBlockState(world, SEA_LANTERN, 5, 2, 5, structureboundingbox);
			this.setBlockState(world, SEA_LANTERN, 2, 2, 10, structureboundingbox);
			this.setBlockState(world, SEA_LANTERN, 5, 2, 10, structureboundingbox);
			this.setBlockState(world, BRICKS_PRISMARINE, 2, 3, 5, structureboundingbox);
			this.setBlockState(world, BRICKS_PRISMARINE, 5, 3, 5, structureboundingbox);
			this.setBlockState(world, BRICKS_PRISMARINE, 2, 3, 10, structureboundingbox);
			this.setBlockState(world, BRICKS_PRISMARINE, 5, 3, 10, structureboundingbox);
			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.SOUTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 1, 0, 4, 2, 0, false);
			}

			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.EAST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 7, 1, 3, 7, 2, 4, false);
			}

			if (structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.WEST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 0, 1, 3, 0, 2, 4, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.NORTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 1, 15, 4, 2, 15, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.WEST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 0, 1, 11, 0, 2, 12, false);
			}

			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.EAST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 7, 1, 11, 7, 2, 12, false);
			}

			return true;
		}
	}

	public static class EntryRoom extends StructureOceanMonumentPieces.Piece {
		public EntryRoom() {
		}

		public EntryRoom(EnumFacing parEnumFacing, StructureOceanMonumentPieces.RoomDefinition parRoomDefinition) {
			super(1, parEnumFacing, parRoomDefinition, 1, 1, 1);
		}

		public boolean addComponentParts(World world, EaglercraftRandom var2,
				StructureBoundingBox structureboundingbox) {
			this.fillWithBlocks(world, structureboundingbox, 0, 3, 0, 2, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 3, 0, 7, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 0, 2, 0, 1, 2, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, 2, 0, 7, 2, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 0, 1, 0, 0, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 7, 1, 0, 7, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 0, 1, 7, 7, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 1, 0, 2, 3, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 1, 0, 6, 3, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			if (this.roomDefinition.hasOpening[EnumFacing.NORTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 1, 7, 4, 2, 7, false);
			}

			if (this.roomDefinition.hasOpening[EnumFacing.WEST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 0, 1, 3, 1, 2, 4, false);
			}

			if (this.roomDefinition.hasOpening[EnumFacing.EAST.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 6, 1, 3, 7, 2, 4, false);
			}

			return true;
		}
	}

	static class FitSimpleRoomHelper implements StructureOceanMonumentPieces.MonumentRoomFitHelper {
		private FitSimpleRoomHelper() {
		}

		public boolean fits(StructureOceanMonumentPieces.RoomDefinition var1) {
			return true;
		}

		public StructureOceanMonumentPieces.Piece create(EnumFacing enumfacing,
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition,
				EaglercraftRandom random) {
			structureoceanmonumentpieces$roomdefinition.claimed = true;
			return new StructureOceanMonumentPieces.SimpleRoom(enumfacing, structureoceanmonumentpieces$roomdefinition,
					random);
		}
	}

	static class FitSimpleRoomTopHelper implements StructureOceanMonumentPieces.MonumentRoomFitHelper {
		private FitSimpleRoomTopHelper() {
		}

		public boolean fits(
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition) {
			return !structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.WEST.getIndex()]
					&& !structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.EAST.getIndex()]
					&& !structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.NORTH.getIndex()]
					&& !structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.SOUTH.getIndex()]
					&& !structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.UP.getIndex()];
		}

		public StructureOceanMonumentPieces.Piece create(EnumFacing enumfacing,
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition,
				EaglercraftRandom random) {
			structureoceanmonumentpieces$roomdefinition.claimed = true;
			return new StructureOceanMonumentPieces.SimpleTopRoom(enumfacing,
					structureoceanmonumentpieces$roomdefinition, random);
		}
	}

	public static class MonumentBuilding extends StructureOceanMonumentPieces.Piece {
		private StructureOceanMonumentPieces.RoomDefinition sourceRoom;
		private StructureOceanMonumentPieces.RoomDefinition coreRoom;
		private List<StructureOceanMonumentPieces.Piece> childPieces = Lists.newArrayList();

		public MonumentBuilding() {
		}

		public MonumentBuilding(EaglercraftRandom parRandom, int parInt1, int parInt2, EnumFacing parEnumFacing) {
			super(0);
			this.coordBaseMode = parEnumFacing;
			switch (this.coordBaseMode) {
			case NORTH:
			case SOUTH:
				this.boundingBox = new StructureBoundingBox(parInt1, 39, parInt2, parInt1 + 58 - 1, 61,
						parInt2 + 58 - 1);
				break;
			default:
				this.boundingBox = new StructureBoundingBox(parInt1, 39, parInt2, parInt1 + 58 - 1, 61,
						parInt2 + 58 - 1);
			}

			List list = this.generateRoomGraph(parRandom);
			this.sourceRoom.claimed = true;
			this.childPieces
					.add(new StructureOceanMonumentPieces.EntryRoom(this.coordBaseMode, this.sourceRoom));
			this.childPieces.add(new StructureOceanMonumentPieces.MonumentCoreRoom(this.coordBaseMode,
					this.coreRoom, parRandom));
			ArrayList arraylist = Lists.newArrayList();
			arraylist.add(new StructureOceanMonumentPieces.XYDoubleRoomFitHelper());
			arraylist.add(new StructureOceanMonumentPieces.YZDoubleRoomFitHelper());
			arraylist.add(new StructureOceanMonumentPieces.ZDoubleRoomFitHelper());
			arraylist.add(new StructureOceanMonumentPieces.XDoubleRoomFitHelper());
			arraylist.add(new StructureOceanMonumentPieces.YDoubleRoomFitHelper());
			arraylist.add(new StructureOceanMonumentPieces.FitSimpleRoomTopHelper());
			arraylist.add(new StructureOceanMonumentPieces.FitSimpleRoomHelper());

			label319: for (StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition : (List<StructureOceanMonumentPieces.RoomDefinition>) list) {
				if (!structureoceanmonumentpieces$roomdefinition.claimed
						&& !structureoceanmonumentpieces$roomdefinition.isSpecial()) {
					Iterator iterator = arraylist.iterator();

					StructureOceanMonumentPieces.MonumentRoomFitHelper structureoceanmonumentpieces$monumentroomfithelper;
					while (true) {
						if (!iterator.hasNext()) {
							continue label319;
						}

						structureoceanmonumentpieces$monumentroomfithelper = (StructureOceanMonumentPieces.MonumentRoomFitHelper) iterator
								.next();
						if (structureoceanmonumentpieces$monumentroomfithelper
								.fits(structureoceanmonumentpieces$roomdefinition)) {
							break;
						}
					}

					this.childPieces.add(structureoceanmonumentpieces$monumentroomfithelper
							.create(this.coordBaseMode, structureoceanmonumentpieces$roomdefinition, parRandom));
				}
			}

			int j = this.boundingBox.minY;
			int k = this.getXWithOffset(9, 22);
			int l = this.getZWithOffset(9, 22);

			for (StructureOceanMonumentPieces.Piece structureoceanmonumentpieces$piece : this.childPieces) {
				structureoceanmonumentpieces$piece.getBoundingBox().offset(k, j, l);
			}

			StructureBoundingBox structureboundingbox1 = StructureBoundingBox.createProper(this.getXWithOffset(1, 1),
					this.getYWithOffset(1), this.getZWithOffset(1, 1), this.getXWithOffset(23, 21),
					this.getYWithOffset(8), this.getZWithOffset(23, 21));
			StructureBoundingBox structureboundingbox2 = StructureBoundingBox.createProper(this.getXWithOffset(34, 1),
					this.getYWithOffset(1), this.getZWithOffset(34, 1), this.getXWithOffset(56, 21),
					this.getYWithOffset(8), this.getZWithOffset(56, 21));
			StructureBoundingBox structureboundingbox = StructureBoundingBox.createProper(this.getXWithOffset(22, 22),
					this.getYWithOffset(13), this.getZWithOffset(22, 22), this.getXWithOffset(35, 35),
					this.getYWithOffset(17), this.getZWithOffset(35, 35));
			int i = parRandom.nextInt();
			this.childPieces
					.add(new StructureOceanMonumentPieces.WingRoom(this.coordBaseMode, structureboundingbox1, i++));
			this.childPieces
					.add(new StructureOceanMonumentPieces.WingRoom(this.coordBaseMode, structureboundingbox2, i++));
			this.childPieces
					.add(new StructureOceanMonumentPieces.Penthouse(this.coordBaseMode, structureboundingbox));
		}

		private List<StructureOceanMonumentPieces.RoomDefinition> generateRoomGraph(EaglercraftRandom parRandom) {
			StructureOceanMonumentPieces.RoomDefinition[] astructureoceanmonumentpieces$roomdefinition = new StructureOceanMonumentPieces.RoomDefinition[75];

			for (int i = 0; i < 5; ++i) {
				for (int j = 0; j < 4; ++j) {
					byte b0 = 0;
					int k = getRoomIndex(i, b0, j);
					astructureoceanmonumentpieces$roomdefinition[k] = new StructureOceanMonumentPieces.RoomDefinition(
							k);
				}
			}

			for (int l1 = 0; l1 < 5; ++l1) {
				for (int k2 = 0; k2 < 4; ++k2) {
					byte b1 = 1;
					int k3 = getRoomIndex(l1, b1, k2);
					astructureoceanmonumentpieces$roomdefinition[k3] = new StructureOceanMonumentPieces.RoomDefinition(
							k3);
				}
			}

			for (int i2 = 1; i2 < 4; ++i2) {
				for (int l2 = 0; l2 < 2; ++l2) {
					byte b2 = 2;
					int l3 = getRoomIndex(i2, b2, l2);
					astructureoceanmonumentpieces$roomdefinition[l3] = new StructureOceanMonumentPieces.RoomDefinition(
							l3);
				}
			}

			this.sourceRoom = astructureoceanmonumentpieces$roomdefinition[GRIDROOM_SOURCE_INDEX];

			for (int j2 = 0; j2 < 5; ++j2) {
				for (int i3 = 0; i3 < 5; ++i3) {
					for (int j3 = 0; j3 < 3; ++j3) {
						int i4 = getRoomIndex(j2, j3, i3);
						if (astructureoceanmonumentpieces$roomdefinition[i4] != null) {
							for (EnumFacing enumfacing : EnumFacing._VALUES) {
								int l = j2 + enumfacing.getFrontOffsetX();
								int i1 = j3 + enumfacing.getFrontOffsetY();
								int j1 = i3 + enumfacing.getFrontOffsetZ();
								if (l >= 0 && l < 5 && j1 >= 0 && j1 < 5 && i1 >= 0 && i1 < 3) {
									int k1 = getRoomIndex(l, i1, j1);
									if (astructureoceanmonumentpieces$roomdefinition[k1] != null) {
										if (j1 != i3) {
											astructureoceanmonumentpieces$roomdefinition[i4].setConnection(
													enumfacing.getOpposite(),
													astructureoceanmonumentpieces$roomdefinition[k1]);
										} else {
											astructureoceanmonumentpieces$roomdefinition[i4].setConnection(enumfacing,
													astructureoceanmonumentpieces$roomdefinition[k1]);
										}
									}
								}
							}
						}
					}
				}
			}

			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition;
			astructureoceanmonumentpieces$roomdefinition[GRIDROOM_TOP_CONNECT_INDEX].setConnection(EnumFacing.UP,
					structureoceanmonumentpieces$roomdefinition = new StructureOceanMonumentPieces.RoomDefinition(
							1003));
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition1;
			astructureoceanmonumentpieces$roomdefinition[GRIDROOM_LEFTWING_CONNECT_INDEX].setConnection(EnumFacing.SOUTH,
					structureoceanmonumentpieces$roomdefinition1 = new StructureOceanMonumentPieces.RoomDefinition(
							1001));
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition2;
			astructureoceanmonumentpieces$roomdefinition[GRIDROOM_RIGHTWING_CONNECT_INDEX].setConnection(EnumFacing.SOUTH,
					structureoceanmonumentpieces$roomdefinition2 = new StructureOceanMonumentPieces.RoomDefinition(
							1002));
			structureoceanmonumentpieces$roomdefinition.claimed = true;
			structureoceanmonumentpieces$roomdefinition1.claimed = true;
			structureoceanmonumentpieces$roomdefinition2.claimed = true;
			this.sourceRoom.isSource = true;
			this.coreRoom = astructureoceanmonumentpieces$roomdefinition[getRoomIndex(parRandom.nextInt(4), 0,
					2)];
			this.coreRoom.claimed = true;
			this.coreRoom.connections[EnumFacing.EAST.getIndex()].claimed = true;
			this.coreRoom.connections[EnumFacing.NORTH.getIndex()].claimed = true;
			this.coreRoom.connections[EnumFacing.EAST.getIndex()].connections[EnumFacing.NORTH
					.getIndex()].claimed = true;
			this.coreRoom.connections[EnumFacing.UP.getIndex()].claimed = true;
			this.coreRoom.connections[EnumFacing.EAST.getIndex()].connections[EnumFacing.UP
					.getIndex()].claimed = true;
			this.coreRoom.connections[EnumFacing.NORTH.getIndex()].connections[EnumFacing.UP
					.getIndex()].claimed = true;
			this.coreRoom.connections[EnumFacing.EAST.getIndex()].connections[EnumFacing.NORTH
					.getIndex()].connections[EnumFacing.UP.getIndex()].claimed = true;
			ArrayList arraylist = Lists.newArrayList();

			for (StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition4 : astructureoceanmonumentpieces$roomdefinition) {
				if (structureoceanmonumentpieces$roomdefinition4 != null) {
					structureoceanmonumentpieces$roomdefinition4.updateOpenings();
					arraylist.add(structureoceanmonumentpieces$roomdefinition4);
				}
			}

			structureoceanmonumentpieces$roomdefinition.updateOpenings();
			ArrayUtils.eaglerShuffle(arraylist, parRandom);
			int j4 = 1;

			for (StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition3 : (ArrayList<StructureOceanMonumentPieces.RoomDefinition>) arraylist) {
				int k4 = 0;
				int l4 = 0;

				while (k4 < 2 && l4 < 5) {
					++l4;
					int i5 = parRandom.nextInt(6);
					if (structureoceanmonumentpieces$roomdefinition3.hasOpening[i5]) {
						int j5 = EnumFacing.getFront(i5).getOpposite().getIndex();
						structureoceanmonumentpieces$roomdefinition3.hasOpening[i5] = false;
						structureoceanmonumentpieces$roomdefinition3.connections[i5].hasOpening[j5] = false;
						if (structureoceanmonumentpieces$roomdefinition3.findSource(j4++)
								&& structureoceanmonumentpieces$roomdefinition3.connections[i5]
										.findSource(j4++)) {
							++k4;
						} else {
							structureoceanmonumentpieces$roomdefinition3.hasOpening[i5] = true;
							structureoceanmonumentpieces$roomdefinition3.connections[i5].hasOpening[j5] = true;
						}
					}
				}
			}

			arraylist.add(structureoceanmonumentpieces$roomdefinition);
			arraylist.add(structureoceanmonumentpieces$roomdefinition1);
			arraylist.add(structureoceanmonumentpieces$roomdefinition2);
			return arraylist;
		}

		public boolean addComponentParts(World world, EaglercraftRandom random,
				StructureBoundingBox structureboundingbox) {
			int i = Math.max(world.getSeaLevel(), 64) - this.boundingBox.minY;
			this.generateWaterBox(world, structureboundingbox, 0, 0, 0, 58, i, 58, false);
			this.generateWing(false, 0, world, random, structureboundingbox);
			this.generateWing(true, 33, world, random, structureboundingbox);
			this.generateEntranceArchs(world, random, structureboundingbox);
			this.generateEntranceWall(world, random, structureboundingbox);
			this.generateRoofPiece(world, random, structureboundingbox);
			this.generateLowerWall(world, random, structureboundingbox);
			this.generateMiddleWall(world, random, structureboundingbox);
			this.generateUpperWall(world, random, structureboundingbox);

			for (int j = 0; j < 7; ++j) {
				int k = 0;

				while (k < 7) {
					if (k == 0 && j == 3) {
						k = 6;
					}

					int l = j * 9;
					int i1 = k * 9;

					for (int j1 = 0; j1 < 4; ++j1) {
						for (int k1 = 0; k1 < 4; ++k1) {
							this.setBlockState(world, BRICKS_PRISMARINE, l + j1, 0, i1 + k1, structureboundingbox);
							this.replaceAirAndLiquidDownwards(world, BRICKS_PRISMARINE, l + j1, -1, i1 + k1,
									structureboundingbox);
						}
					}

					if (j != 0 && j != 6) {
						k += 6;
					} else {
						++k;
					}
				}
			}

			for (int l1 = 0; l1 < 5; ++l1) {
				this.generateWaterBox(world, structureboundingbox, -1 - l1, 0 + l1 * 2, -1 - l1, -1 - l1, 23, 58 + l1,
						false);
				this.generateWaterBox(world, structureboundingbox, 58 + l1, 0 + l1 * 2, -1 - l1, 58 + l1, 23, 58 + l1,
						false);
				this.generateWaterBox(world, structureboundingbox, 0 - l1, 0 + l1 * 2, -1 - l1, 57 + l1, 23, -1 - l1,
						false);
				this.generateWaterBox(world, structureboundingbox, 0 - l1, 0 + l1 * 2, 58 + l1, 57 + l1, 23, 58 + l1,
						false);
			}

			for (StructureOceanMonumentPieces.Piece structureoceanmonumentpieces$piece : this.childPieces) {
				if (structureoceanmonumentpieces$piece.getBoundingBox().intersectsWith(structureboundingbox)) {
					structureoceanmonumentpieces$piece.addComponentParts(world, random, structureboundingbox);
				}
			}

			return true;
		}

		private void generateWing(boolean worldIn, int parInt1, World parWorld, EaglercraftRandom parRandom,
				StructureBoundingBox parStructureBoundingBox) {
			boolean flag = true;
			if (this.doesChunkIntersect(parStructureBoundingBox, parInt1, 0, parInt1 + 23, 20)) {
				this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + 0, 0, 0, parInt1 + 24, 0, 20,
						ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				this.generateWaterBox(parWorld, parStructureBoundingBox, parInt1 + 0, 1, 0, parInt1 + 24, 10, 20, false);

				for (int i = 0; i < 4; ++i) {
					this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + i, i + 1, i, parInt1 + i, i + 1,
							20, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + i + 7, i + 5, i + 7,
							parInt1 + i + 7, i + 5, 20, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + 17 - i, i + 5, i + 7,
							parInt1 + 17 - i, i + 5, 20, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + 24 - i, i + 1, i, parInt1 + 24 - i,
							i + 1, 20, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + i + 1, i + 1, i, parInt1 + 23 - i,
							i + 1, i, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + i + 8, i + 5, i + 7,
							parInt1 + 16 - i, i + 5, i + 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}

				this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + 4, 4, 4, parInt1 + 6, 4, 20,
						ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + 7, 4, 4, parInt1 + 17, 4, 6,
						ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + 18, 4, 4, parInt1 + 20, 4, 20,
						ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + 11, 8, 11, parInt1 + 13, 8, 20,
						ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				this.setBlockState(parWorld, DOT_DECO_DATA, parInt1 + 12, 9, 12, parStructureBoundingBox);
				this.setBlockState(parWorld, DOT_DECO_DATA, parInt1 + 12, 9, 15, parStructureBoundingBox);
				this.setBlockState(parWorld, DOT_DECO_DATA, parInt1 + 12, 9, 18, parStructureBoundingBox);
				int i1 = worldIn ? parInt1 + 19 : parInt1 + 5;
				int j = worldIn ? parInt1 + 5 : parInt1 + 19;

				for (int k = 20; k >= 5; k -= 3) {
					this.setBlockState(parWorld, DOT_DECO_DATA, i1, 5, k, parStructureBoundingBox);
				}

				for (int j1 = 19; j1 >= 7; j1 -= 3) {
					this.setBlockState(parWorld, DOT_DECO_DATA, j, 5, j1, parStructureBoundingBox);
				}

				for (int k1 = 0; k1 < 4; ++k1) {
					int l = worldIn ? parInt1 + (24 - (17 - k1 * 3)) : parInt1 + 17 - k1 * 3;
					this.setBlockState(parWorld, DOT_DECO_DATA, l, 5, 5, parStructureBoundingBox);
				}

				this.setBlockState(parWorld, DOT_DECO_DATA, j, 5, 5, parStructureBoundingBox);
				this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + 11, 1, 12, parInt1 + 13, 7, 12,
						ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				this.fillWithBlocks(parWorld, parStructureBoundingBox, parInt1 + 12, 1, 11, parInt1 + 12, 7, 13,
						ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
			}

		}

		private void generateEntranceArchs(World worldIn, EaglercraftRandom parRandom,
				StructureBoundingBox parStructureBoundingBox) {
			if (this.doesChunkIntersect(parStructureBoundingBox, 22, 5, 35, 17)) {
				this.generateWaterBox(worldIn, parStructureBoundingBox, 25, 0, 0, 32, 8, 20, false);

				for (int i = 0; i < 4; ++i) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 24, 2, 5 + i * 4, 24, 4, 5 + i * 4,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 22, 4, 5 + i * 4, 23, 4, 5 + i * 4,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.setBlockState(worldIn, BRICKS_PRISMARINE, 25, 5, 5 + i * 4, parStructureBoundingBox);
					this.setBlockState(worldIn, BRICKS_PRISMARINE, 26, 6, 5 + i * 4, parStructureBoundingBox);
					this.setBlockState(worldIn, SEA_LANTERN, 26, 5, 5 + i * 4, parStructureBoundingBox);
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 33, 2, 5 + i * 4, 33, 4, 5 + i * 4,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 34, 4, 5 + i * 4, 35, 4, 5 + i * 4,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.setBlockState(worldIn, BRICKS_PRISMARINE, 32, 5, 5 + i * 4, parStructureBoundingBox);
					this.setBlockState(worldIn, BRICKS_PRISMARINE, 31, 6, 5 + i * 4, parStructureBoundingBox);
					this.setBlockState(worldIn, SEA_LANTERN, 31, 5, 5 + i * 4, parStructureBoundingBox);
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 27, 6, 5 + i * 4, 30, 6, 5 + i * 4,
							ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				}
			}

		}

		private void generateEntranceWall(World worldIn, EaglercraftRandom parRandom,
				StructureBoundingBox parStructureBoundingBox) {
			if (this.doesChunkIntersect(parStructureBoundingBox, 15, 20, 42, 21)) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 15, 0, 21, 42, 0, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 26, 1, 21, 31, 3, 21, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 21, 12, 21, 36, 12, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 17, 11, 21, 40, 11, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 16, 10, 21, 41, 10, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 15, 7, 21, 42, 9, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 16, 6, 21, 41, 6, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 17, 5, 21, 40, 5, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 21, 4, 21, 36, 4, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 22, 3, 21, 26, 3, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 31, 3, 21, 35, 3, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 23, 2, 21, 25, 2, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 32, 2, 21, 34, 2, 21, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 28, 4, 20, 29, 4, 21, BRICKS_PRISMARINE,
						BRICKS_PRISMARINE, false);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 27, 3, 21, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 30, 3, 21, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 26, 2, 21, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 31, 2, 21, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 25, 1, 21, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 32, 1, 21, parStructureBoundingBox);

				for (int i = 0; i < 7; ++i) {
					this.setBlockState(worldIn, DARK_PRISMARINE, 28 - i, 6 + i, 21, parStructureBoundingBox);
					this.setBlockState(worldIn, DARK_PRISMARINE, 29 + i, 6 + i, 21, parStructureBoundingBox);
				}

				for (int j = 0; j < 4; ++j) {
					this.setBlockState(worldIn, DARK_PRISMARINE, 28 - j, 9 + j, 21, parStructureBoundingBox);
					this.setBlockState(worldIn, DARK_PRISMARINE, 29 + j, 9 + j, 21, parStructureBoundingBox);
				}

				this.setBlockState(worldIn, DARK_PRISMARINE, 28, 12, 21, parStructureBoundingBox);
				this.setBlockState(worldIn, DARK_PRISMARINE, 29, 12, 21, parStructureBoundingBox);

				for (int k = 0; k < 3; ++k) {
					this.setBlockState(worldIn, DARK_PRISMARINE, 22 - k * 2, 8, 21, parStructureBoundingBox);
					this.setBlockState(worldIn, DARK_PRISMARINE, 22 - k * 2, 9, 21, parStructureBoundingBox);
					this.setBlockState(worldIn, DARK_PRISMARINE, 35 + k * 2, 8, 21, parStructureBoundingBox);
					this.setBlockState(worldIn, DARK_PRISMARINE, 35 + k * 2, 9, 21, parStructureBoundingBox);
				}

				this.generateWaterBox(worldIn, parStructureBoundingBox, 15, 13, 21, 42, 15, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 15, 1, 21, 15, 6, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 16, 1, 21, 16, 5, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 17, 1, 21, 20, 4, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 21, 1, 21, 21, 3, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 22, 1, 21, 22, 2, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 23, 1, 21, 24, 1, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 42, 1, 21, 42, 6, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 41, 1, 21, 41, 5, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 37, 1, 21, 40, 4, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 36, 1, 21, 36, 3, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 33, 1, 21, 34, 1, 21, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 35, 1, 21, 35, 2, 21, false);
			}

		}

		private void generateRoofPiece(World worldIn, EaglercraftRandom parRandom,
				StructureBoundingBox parStructureBoundingBox) {
			if (this.doesChunkIntersect(parStructureBoundingBox, 21, 21, 36, 36)) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 21, 0, 22, 36, 0, 36, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 21, 1, 22, 36, 23, 36, false);

				for (int i = 0; i < 4; ++i) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 21 + i, 13 + i, 21 + i, 36 - i, 13 + i,
							21 + i, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 21 + i, 13 + i, 36 - i, 36 - i, 13 + i,
							36 - i, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 21 + i, 13 + i, 22 + i, 21 + i, 13 + i,
							35 - i, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 36 - i, 13 + i, 22 + i, 36 - i, 13 + i,
							35 - i, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}

				this.fillWithBlocks(worldIn, parStructureBoundingBox, 25, 16, 25, 32, 16, 32, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 25, 17, 25, 25, 19, 25, BRICKS_PRISMARINE,
						BRICKS_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 32, 17, 25, 32, 19, 25, BRICKS_PRISMARINE,
						BRICKS_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 25, 17, 32, 25, 19, 32, BRICKS_PRISMARINE,
						BRICKS_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 32, 17, 32, 32, 19, 32, BRICKS_PRISMARINE,
						BRICKS_PRISMARINE, false);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 26, 20, 26, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 27, 21, 27, parStructureBoundingBox);
				this.setBlockState(worldIn, SEA_LANTERN, 27, 20, 27, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 26, 20, 31, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 27, 21, 30, parStructureBoundingBox);
				this.setBlockState(worldIn, SEA_LANTERN, 27, 20, 30, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 31, 20, 31, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 30, 21, 30, parStructureBoundingBox);
				this.setBlockState(worldIn, SEA_LANTERN, 30, 20, 30, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 31, 20, 26, parStructureBoundingBox);
				this.setBlockState(worldIn, BRICKS_PRISMARINE, 30, 21, 27, parStructureBoundingBox);
				this.setBlockState(worldIn, SEA_LANTERN, 30, 20, 27, parStructureBoundingBox);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 28, 21, 27, 29, 21, 27, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 27, 21, 28, 27, 21, 29, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 28, 21, 30, 29, 21, 30, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 30, 21, 28, 30, 21, 29, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
			}

		}

		private void generateLowerWall(World worldIn, EaglercraftRandom parRandom,
				StructureBoundingBox parStructureBoundingBox) {
			if (this.doesChunkIntersect(parStructureBoundingBox, 0, 21, 6, 58)) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 0, 0, 21, 6, 0, 57, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 0, 1, 21, 6, 7, 57, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 4, 4, 21, 6, 4, 53, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);

				for (int i = 0; i < 4; ++i) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, i, i + 1, 21, i, i + 1, 57 - i,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}

				for (int j = 23; j < 53; j += 3) {
					this.setBlockState(worldIn, DOT_DECO_DATA, 5, 5, j, parStructureBoundingBox);
				}

				this.setBlockState(worldIn, DOT_DECO_DATA, 5, 5, 52, parStructureBoundingBox);

				for (int k = 0; k < 4; ++k) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, k, k + 1, 21, k, k + 1, 57 - k,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}

				this.fillWithBlocks(worldIn, parStructureBoundingBox, 4, 1, 52, 6, 3, 52, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 5, 1, 51, 5, 3, 53, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
			}

			if (this.doesChunkIntersect(parStructureBoundingBox, 51, 21, 58, 58)) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 51, 0, 21, 57, 0, 57, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 51, 1, 21, 57, 7, 57, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 51, 4, 21, 53, 4, 53, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);

				for (int l = 0; l < 4; ++l) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 57 - l, l + 1, 21, 57 - l, l + 1, 57 - l,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}

				for (int i1 = 23; i1 < 53; i1 += 3) {
					this.setBlockState(worldIn, DOT_DECO_DATA, 52, 5, i1, parStructureBoundingBox);
				}

				this.setBlockState(worldIn, DOT_DECO_DATA, 52, 5, 52, parStructureBoundingBox);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 51, 1, 52, 53, 3, 52, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 52, 1, 51, 52, 3, 53, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
			}

			if (this.doesChunkIntersect(parStructureBoundingBox, 0, 51, 57, 57)) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 7, 0, 51, 50, 0, 57, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 7, 1, 51, 50, 10, 57, false);

				for (int j1 = 0; j1 < 4; ++j1) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, j1 + 1, j1 + 1, 57 - j1, 56 - j1, j1 + 1,
							57 - j1, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}
			}

		}

		private void generateMiddleWall(World worldIn, EaglercraftRandom parRandom,
				StructureBoundingBox parStructureBoundingBox) {
			if (this.doesChunkIntersect(parStructureBoundingBox, 7, 21, 13, 50)) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 7, 0, 21, 13, 0, 50, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 7, 1, 21, 13, 10, 50, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 11, 8, 21, 13, 8, 53, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);

				for (int i = 0; i < 4; ++i) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, i + 7, i + 5, 21, i + 7, i + 5, 54,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}

				for (int j = 21; j <= 45; j += 3) {
					this.setBlockState(worldIn, DOT_DECO_DATA, 12, 9, j, parStructureBoundingBox);
				}
			}

			if (this.doesChunkIntersect(parStructureBoundingBox, 44, 21, 50, 54)) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 44, 0, 21, 50, 0, 50, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 44, 1, 21, 50, 10, 50, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 44, 8, 21, 46, 8, 53, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);

				for (int k = 0; k < 4; ++k) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 50 - k, k + 5, 21, 50 - k, k + 5, 54,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}

				for (int l = 21; l <= 45; l += 3) {
					this.setBlockState(worldIn, DOT_DECO_DATA, 45, 9, l, parStructureBoundingBox);
				}
			}

			if (this.doesChunkIntersect(parStructureBoundingBox, 8, 44, 49, 54)) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 14, 0, 44, 43, 0, 50, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 14, 1, 44, 43, 10, 50, false);

				for (int i1 = 12; i1 <= 45; i1 += 3) {
					this.setBlockState(worldIn, DOT_DECO_DATA, i1, 9, 45, parStructureBoundingBox);
					this.setBlockState(worldIn, DOT_DECO_DATA, i1, 9, 52, parStructureBoundingBox);
					if (i1 == 12 || i1 == 18 || i1 == 24 || i1 == 33 || i1 == 39 || i1 == 45) {
						this.setBlockState(worldIn, DOT_DECO_DATA, i1, 9, 47, parStructureBoundingBox);
						this.setBlockState(worldIn, DOT_DECO_DATA, i1, 9, 50, parStructureBoundingBox);
						this.setBlockState(worldIn, DOT_DECO_DATA, i1, 10, 45, parStructureBoundingBox);
						this.setBlockState(worldIn, DOT_DECO_DATA, i1, 10, 46, parStructureBoundingBox);
						this.setBlockState(worldIn, DOT_DECO_DATA, i1, 10, 51, parStructureBoundingBox);
						this.setBlockState(worldIn, DOT_DECO_DATA, i1, 10, 52, parStructureBoundingBox);
						this.setBlockState(worldIn, DOT_DECO_DATA, i1, 11, 47, parStructureBoundingBox);
						this.setBlockState(worldIn, DOT_DECO_DATA, i1, 11, 50, parStructureBoundingBox);
						this.setBlockState(worldIn, DOT_DECO_DATA, i1, 12, 48, parStructureBoundingBox);
						this.setBlockState(worldIn, DOT_DECO_DATA, i1, 12, 49, parStructureBoundingBox);
					}
				}

				for (int j1 = 0; j1 < 3; ++j1) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 8 + j1, 5 + j1, 54, 49 - j1, 5 + j1, 54,
							ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				}

				this.fillWithBlocks(worldIn, parStructureBoundingBox, 11, 8, 54, 46, 8, 54, BRICKS_PRISMARINE,
						BRICKS_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 14, 8, 44, 43, 8, 53, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
			}

		}

		private void generateUpperWall(World worldIn, EaglercraftRandom parRandom,
				StructureBoundingBox parStructureBoundingBox) {
			if (this.doesChunkIntersect(parStructureBoundingBox, 14, 21, 20, 43)) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 14, 0, 21, 20, 0, 43, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 14, 1, 22, 20, 14, 43, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 18, 12, 22, 20, 12, 39, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 18, 12, 21, 20, 12, 21, BRICKS_PRISMARINE,
						BRICKS_PRISMARINE, false);

				for (int i = 0; i < 4; ++i) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, i + 14, i + 9, 21, i + 14, i + 9, 43 - i,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}

				for (int j = 23; j <= 39; j += 3) {
					this.setBlockState(worldIn, DOT_DECO_DATA, 19, 13, j, parStructureBoundingBox);
				}
			}

			if (this.doesChunkIntersect(parStructureBoundingBox, 37, 21, 43, 43)) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 37, 0, 21, 43, 0, 43, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 37, 1, 22, 43, 14, 43, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 37, 12, 22, 39, 12, 39, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 37, 12, 21, 39, 12, 21, BRICKS_PRISMARINE,
						BRICKS_PRISMARINE, false);

				for (int k = 0; k < 4; ++k) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 43 - k, k + 9, 21, 43 - k, k + 9, 43 - k,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}

				for (int l = 23; l <= 39; l += 3) {
					this.setBlockState(worldIn, DOT_DECO_DATA, 38, 13, l, parStructureBoundingBox);
				}
			}

			if (this.doesChunkIntersect(parStructureBoundingBox, 15, 37, 42, 43)) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 21, 0, 37, 36, 0, 43, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);
				this.generateWaterBox(worldIn, parStructureBoundingBox, 21, 1, 37, 36, 14, 43, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, 21, 12, 37, 36, 12, 39, ROUGH_PRISMARINE,
						ROUGH_PRISMARINE, false);

				for (int i1 = 0; i1 < 4; ++i1) {
					this.fillWithBlocks(worldIn, parStructureBoundingBox, 15 + i1, i1 + 9, 43 - i1, 42 - i1, i1 + 9,
							43 - i1, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}

				for (int j1 = 21; j1 <= 36; j1 += 3) {
					this.setBlockState(worldIn, DOT_DECO_DATA, j1, 13, 38, parStructureBoundingBox);
				}
			}

		}
	}

	public static class MonumentCoreRoom extends StructureOceanMonumentPieces.Piece {
		public MonumentCoreRoom() {
		}

		public MonumentCoreRoom(EnumFacing parEnumFacing, StructureOceanMonumentPieces.RoomDefinition parRoomDefinition,
				EaglercraftRandom parRandom) {
			super(1, parEnumFacing, parRoomDefinition, 2, 2, 2);
		}

		public boolean addComponentParts(World world, EaglercraftRandom var2,
				StructureBoundingBox structureboundingbox) {
			this.generateBoxOnFillOnly(world, structureboundingbox, 1, 8, 0, 14, 8, 14, ROUGH_PRISMARINE);
			int i = 7;
			IBlockState iblockstate = BRICKS_PRISMARINE;
			this.fillWithBlocks(world, structureboundingbox, 0, i, 0, 0, i, 15, iblockstate, iblockstate, false);
			this.fillWithBlocks(world, structureboundingbox, 15, i, 0, 15, i, 15, iblockstate, iblockstate, false);
			this.fillWithBlocks(world, structureboundingbox, 1, i, 0, 15, i, 0, iblockstate, iblockstate, false);
			this.fillWithBlocks(world, structureboundingbox, 1, i, 15, 14, i, 15, iblockstate, iblockstate, false);

			for (i = 1; i <= 6; ++i) {
				iblockstate = BRICKS_PRISMARINE;
				if (i == 2 || i == 6) {
					iblockstate = ROUGH_PRISMARINE;
				}

				for (int j = 0; j <= 15; j += 15) {
					this.fillWithBlocks(world, structureboundingbox, j, i, 0, j, i, 1, iblockstate, iblockstate, false);
					this.fillWithBlocks(world, structureboundingbox, j, i, 6, j, i, 9, iblockstate, iblockstate, false);
					this.fillWithBlocks(world, structureboundingbox, j, i, 14, j, i, 15, iblockstate, iblockstate,
							false);
				}

				this.fillWithBlocks(world, structureboundingbox, 1, i, 0, 1, i, 0, iblockstate, iblockstate, false);
				this.fillWithBlocks(world, structureboundingbox, 6, i, 0, 9, i, 0, iblockstate, iblockstate, false);
				this.fillWithBlocks(world, structureboundingbox, 14, i, 0, 14, i, 0, iblockstate, iblockstate, false);
				this.fillWithBlocks(world, structureboundingbox, 1, i, 15, 14, i, 15, iblockstate, iblockstate, false);
			}

			this.fillWithBlocks(world, structureboundingbox, 6, 3, 6, 9, 6, 9, DARK_PRISMARINE, DARK_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 7, 4, 7, 8, 5, 8, Blocks.gold_block.getDefaultState(),
					Blocks.gold_block.getDefaultState(), false);

			for (i = 3; i <= 6; i += 3) {
				for (int k = 6; k <= 9; k += 3) {
					this.setBlockState(world, SEA_LANTERN, k, i, 6, structureboundingbox);
					this.setBlockState(world, SEA_LANTERN, k, i, 9, structureboundingbox);
				}
			}

			this.fillWithBlocks(world, structureboundingbox, 5, 1, 6, 5, 2, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 1, 9, 5, 2, 9, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 10, 1, 6, 10, 2, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 10, 1, 9, 10, 2, 9, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, 1, 5, 6, 2, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 9, 1, 5, 9, 2, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, 1, 10, 6, 2, 10, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 9, 1, 10, 9, 2, 10, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 2, 5, 5, 6, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 2, 10, 5, 6, 10, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 10, 2, 5, 10, 6, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 10, 2, 10, 10, 6, 10, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
					false);
			this.fillWithBlocks(world, structureboundingbox, 5, 7, 1, 5, 7, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 10, 7, 1, 10, 7, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 5, 7, 9, 5, 7, 14, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 10, 7, 9, 10, 7, 14, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
					false);
			this.fillWithBlocks(world, structureboundingbox, 1, 7, 5, 6, 7, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 7, 10, 6, 7, 10, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 9, 7, 5, 14, 7, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 9, 7, 10, 14, 7, 10, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
					false);
			this.fillWithBlocks(world, structureboundingbox, 2, 1, 2, 2, 1, 3, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 3, 1, 2, 3, 1, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 13, 1, 2, 13, 1, 3, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 12, 1, 2, 12, 1, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 2, 1, 12, 2, 1, 13, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 3, 1, 13, 3, 1, 13, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 13, 1, 12, 13, 1, 13, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
					false);
			this.fillWithBlocks(world, structureboundingbox, 12, 1, 13, 12, 1, 13, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
					false);
			return true;
		}
	}

	interface MonumentRoomFitHelper {
		boolean fits(StructureOceanMonumentPieces.RoomDefinition var1);

		StructureOceanMonumentPieces.Piece create(EnumFacing var1,
				StructureOceanMonumentPieces.RoomDefinition var2, EaglercraftRandom var3);
	}

	public static class Penthouse extends StructureOceanMonumentPieces.Piece {
		public Penthouse() {
		}

		public Penthouse(EnumFacing parEnumFacing, StructureBoundingBox parStructureBoundingBox) {
			super(parEnumFacing, parStructureBoundingBox);
		}

		public boolean addComponentParts(World world, EaglercraftRandom var2,
				StructureBoundingBox structureboundingbox) {
			this.fillWithBlocks(world, structureboundingbox, 2, -1, 2, 11, -1, 11, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
					false);
			this.fillWithBlocks(world, structureboundingbox, 0, -1, 0, 1, -1, 11, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
					false);
			this.fillWithBlocks(world, structureboundingbox, 12, -1, 0, 13, -1, 11, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
					false);
			this.fillWithBlocks(world, structureboundingbox, 2, -1, 0, 11, -1, 1, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
					false);
			this.fillWithBlocks(world, structureboundingbox, 2, -1, 12, 11, -1, 13, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
					false);
			this.fillWithBlocks(world, structureboundingbox, 0, 0, 0, 0, 0, 13, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 13, 0, 0, 13, 0, 13, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
					false);
			this.fillWithBlocks(world, structureboundingbox, 1, 0, 0, 12, 0, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 0, 13, 12, 0, 13, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
					false);

			for (int i = 2; i <= 11; i += 3) {
				this.setBlockState(world, SEA_LANTERN, 0, 0, i, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 13, 0, i, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, i, 0, 0, structureboundingbox);
			}

			this.fillWithBlocks(world, structureboundingbox, 2, 0, 3, 4, 0, 9, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 9, 0, 3, 11, 0, 9, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 4, 0, 9, 9, 0, 11, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.setBlockState(world, BRICKS_PRISMARINE, 5, 0, 8, structureboundingbox);
			this.setBlockState(world, BRICKS_PRISMARINE, 8, 0, 8, structureboundingbox);
			this.setBlockState(world, BRICKS_PRISMARINE, 10, 0, 10, structureboundingbox);
			this.setBlockState(world, BRICKS_PRISMARINE, 3, 0, 10, structureboundingbox);
			this.fillWithBlocks(world, structureboundingbox, 3, 0, 3, 3, 0, 7, DARK_PRISMARINE, DARK_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 10, 0, 3, 10, 0, 7, DARK_PRISMARINE, DARK_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, 0, 10, 7, 0, 10, DARK_PRISMARINE, DARK_PRISMARINE, false);
			byte b0 = 3;

			for (int j = 0; j < 2; ++j) {
				for (int k = 2; k <= 8; k += 3) {
					this.fillWithBlocks(world, structureboundingbox, b0, 0, k, b0, 2, k, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				}

				b0 = 10;
			}

			this.fillWithBlocks(world, structureboundingbox, 5, 0, 10, 5, 2, 10, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 8, 0, 10, 8, 2, 10, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 6, -1, 7, 7, -1, 8, DARK_PRISMARINE, DARK_PRISMARINE, false);
			this.generateWaterBox(world, structureboundingbox, 6, -1, 3, 7, -1, 4, false);
			this.spawnElder(world, structureboundingbox, 6, 1, 6);
			return true;
		}
	}

	public abstract static class Piece extends StructureComponent {
		protected static final IBlockState ROUGH_PRISMARINE = Blocks.prismarine
				.getStateFromMeta(BlockPrismarine.ROUGH_META);
		protected static final IBlockState BRICKS_PRISMARINE = Blocks.prismarine
				.getStateFromMeta(BlockPrismarine.BRICKS_META);
		protected static final IBlockState DARK_PRISMARINE = Blocks.prismarine
				.getStateFromMeta(BlockPrismarine.DARK_META);
		protected static final IBlockState DOT_DECO_DATA = BRICKS_PRISMARINE;
		protected static final IBlockState SEA_LANTERN = Blocks.sea_lantern.getDefaultState();
		protected static final IBlockState WATER = Blocks.water.getDefaultState();
		protected static final int GRIDROOM_SOURCE_INDEX = getRoomIndex(2, 0, 0);
		protected static final int GRIDROOM_TOP_CONNECT_INDEX = getRoomIndex(2, 2, 0);
		protected static final int GRIDROOM_LEFTWING_CONNECT_INDEX = getRoomIndex(0, 1, 0);
		protected static final int GRIDROOM_RIGHTWING_CONNECT_INDEX = getRoomIndex(4, 1, 0);
		protected StructureOceanMonumentPieces.RoomDefinition roomDefinition;

		protected static final int getRoomIndex(int parInt1, int parInt2, int parInt3) {
			return parInt2 * 25 + parInt3 * 5 + parInt1;
		}

		public Piece() {
			super(0);
		}

		public Piece(int parInt1) {
			super(parInt1);
		}

		public Piece(EnumFacing parEnumFacing, StructureBoundingBox parStructureBoundingBox) {
			super(1);
			this.coordBaseMode = parEnumFacing;
			this.boundingBox = parStructureBoundingBox;
		}

		protected Piece(int parInt1, EnumFacing parEnumFacing,
				StructureOceanMonumentPieces.RoomDefinition parRoomDefinition, int parInt2, int parInt3, int parInt4) {
			super(parInt1);
			this.coordBaseMode = parEnumFacing;
			this.roomDefinition = parRoomDefinition;
			int i = parRoomDefinition.index;
			int j = i % 5;
			int k = i / 5 % 5;
			int l = i / 25;
			if (parEnumFacing != EnumFacing.NORTH && parEnumFacing != EnumFacing.SOUTH) {
				this.boundingBox = new StructureBoundingBox(0, 0, 0, parInt4 * 8 - 1, parInt3 * 4 - 1, parInt2 * 8 - 1);
			} else {
				this.boundingBox = new StructureBoundingBox(0, 0, 0, parInt2 * 8 - 1, parInt3 * 4 - 1, parInt4 * 8 - 1);
			}

			switch (parEnumFacing) {
			case NORTH:
				this.boundingBox.offset(j * 8, l * 4, -(k + parInt4) * 8 + 1);
				break;
			case SOUTH:
				this.boundingBox.offset(j * 8, l * 4, k * 8);
				break;
			case WEST:
				this.boundingBox.offset(-(k + parInt4) * 8 + 1, l * 4, j * 8);
				break;
			default:
				this.boundingBox.offset(k * 8, l * 4, j * 8);
			}

		}

		protected void writeStructureToNBT(NBTTagCompound var1) {
		}

		protected void readStructureFromNBT(NBTTagCompound var1) {
		}

		protected void generateWaterBox(World parWorld, StructureBoundingBox parStructureBoundingBox, int parInt1,
				int parInt2, int parInt3, int parInt4, int parInt5, int parInt6, boolean parFlag) {
			for (int i = parInt2; i <= parInt5; ++i) {
				for (int j = parInt1; j <= parInt4; ++j) {
					for (int k = parInt3; k <= parInt6; ++k) {
						if (!parFlag || this.getBlockStateFromPos(parWorld, j, i, k, parStructureBoundingBox).getBlock()
								.getMaterial() != Material.air) {
							if (this.getYWithOffset(i) >= parWorld.getSeaLevel()) {
								this.setBlockState(parWorld, Blocks.air.getDefaultState(), j, i, k,
										parStructureBoundingBox);
							} else {
								this.setBlockState(parWorld, WATER, j, i, k, parStructureBoundingBox);
							}
						}
					}
				}
			}

		}

		protected void generateDefaultFloor(World worldIn, StructureBoundingBox parStructureBoundingBox, int parInt1,
				int parInt2, boolean parFlag) {
			if (parFlag) {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, parInt1 + 0, 0, parInt2 + 0, parInt1 + 2, 0,
						parInt2 + 8 - 1, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, parInt1 + 5, 0, parInt2 + 0, parInt1 + 8 - 1, 0,
						parInt2 + 8 - 1, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, parInt1 + 3, 0, parInt2 + 0, parInt1 + 4, 0,
						parInt2 + 2, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, parInt1 + 3, 0, parInt2 + 5, parInt1 + 4, 0,
						parInt2 + 8 - 1, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, parInt1 + 3, 0, parInt2 + 2, parInt1 + 4, 0,
						parInt2 + 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, parInt1 + 3, 0, parInt2 + 5, parInt1 + 4, 0,
						parInt2 + 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, parInt1 + 2, 0, parInt2 + 3, parInt1 + 2, 0,
						parInt2 + 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				this.fillWithBlocks(worldIn, parStructureBoundingBox, parInt1 + 5, 0, parInt2 + 3, parInt1 + 5, 0,
						parInt2 + 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			} else {
				this.fillWithBlocks(worldIn, parStructureBoundingBox, parInt1 + 0, 0, parInt2 + 0, parInt1 + 8 - 1, 0,
						parInt2 + 8 - 1, ROUGH_PRISMARINE, ROUGH_PRISMARINE, false);
			}

		}

		protected void generateBoxOnFillOnly(World worldIn, StructureBoundingBox parStructureBoundingBox, int parInt1,
				int parInt2, int parInt3, int parInt4, int parInt5, int parInt6, IBlockState parIBlockState) {
			for (int i = parInt2; i <= parInt5; ++i) {
				for (int j = parInt1; j <= parInt4; ++j) {
					for (int k = parInt3; k <= parInt6; ++k) {
						if (this.getBlockStateFromPos(worldIn, j, i, k, parStructureBoundingBox) == WATER) {
							this.setBlockState(worldIn, parIBlockState, j, i, k, parStructureBoundingBox);
						}
					}
				}
			}

		}

		protected boolean doesChunkIntersect(StructureBoundingBox parStructureBoundingBox, int parInt1, int parInt2,
				int parInt3, int parInt4) {
			int i = this.getXWithOffset(parInt1, parInt2);
			int j = this.getZWithOffset(parInt1, parInt2);
			int k = this.getXWithOffset(parInt3, parInt4);
			int l = this.getZWithOffset(parInt3, parInt4);
			return parStructureBoundingBox.intersectsWith(Math.min(i, k), Math.min(j, l), Math.max(i, k),
					Math.max(j, l));
		}

		protected boolean spawnElder(World worldIn, StructureBoundingBox parStructureBoundingBox, int parInt1,
				int parInt2, int parInt3) {
			int i = this.getXWithOffset(parInt1, parInt3);
			int j = this.getYWithOffset(parInt2);
			int k = this.getZWithOffset(parInt1, parInt3);
			if (parStructureBoundingBox.isVecInside(new BlockPos(i, j, k))) {
				EntityGuardian entityguardian = new EntityGuardian(worldIn);
				entityguardian.setElder(true);
				entityguardian.heal(entityguardian.getMaxHealth());
				entityguardian.setLocationAndAngles((double) i + 0.5D, (double) j, (double) k + 0.5D, 0.0F, 0.0F);
				entityguardian.onInitialSpawn(worldIn.getDifficultyForLocation(new BlockPos(entityguardian)),
						(IEntityLivingData) null);
				worldIn.spawnEntityInWorld(entityguardian);
				return true;
			} else {
				return false;
			}
		}
	}

	static class RoomDefinition {
		int index;
		StructureOceanMonumentPieces.RoomDefinition[] connections = new StructureOceanMonumentPieces.RoomDefinition[6];
		boolean[] hasOpening = new boolean[6];
		boolean claimed;
		boolean isSource;
		int scanIndex;

		public RoomDefinition(int parInt1) {
			this.index = parInt1;
		}

		public void setConnection(EnumFacing parEnumFacing,
				StructureOceanMonumentPieces.RoomDefinition parRoomDefinition) {
			this.connections[parEnumFacing.getIndex()] = parRoomDefinition;
			parRoomDefinition.connections[parEnumFacing.getOpposite().getIndex()] = this;
		}

		public void updateOpenings() {
			for (int i = 0; i < 6; ++i) {
				this.hasOpening[i] = this.connections[i] != null;
			}

		}

		public boolean findSource(int parInt1) {
			if (this.isSource) {
				return true;
			} else {
				this.scanIndex = parInt1;

				for (int i = 0; i < 6; ++i) {
					if (this.connections[i] != null && this.hasOpening[i]
							&& this.connections[i].scanIndex != parInt1
							&& this.connections[i].findSource(parInt1)) {
						return true;
					}
				}

				return false;
			}
		}

		public boolean isSpecial() {
			return this.index >= 75;
		}

		public int countOpenings() {
			int i = 0;

			for (int j = 0; j < 6; ++j) {
				if (this.hasOpening[j]) {
					++i;
				}
			}

			return i;
		}
	}

	public static class SimpleRoom extends StructureOceanMonumentPieces.Piece {
		private int mainDesign;

		public SimpleRoom() {
		}

		public SimpleRoom(EnumFacing parEnumFacing, StructureOceanMonumentPieces.RoomDefinition parRoomDefinition,
				EaglercraftRandom parRandom) {
			super(1, parEnumFacing, parRoomDefinition, 1, 1, 1);
			this.mainDesign = parRandom.nextInt(3);
		}

		public boolean addComponentParts(World world, EaglercraftRandom random,
				StructureBoundingBox structureboundingbox) {
			if (this.roomDefinition.index / 25 > 0) {
				this.generateDefaultFloor(world, structureboundingbox, 0, 0,
						this.roomDefinition.hasOpening[EnumFacing.DOWN.getIndex()]);
			}

			if (this.roomDefinition.connections[EnumFacing.UP.getIndex()] == null) {
				this.generateBoxOnFillOnly(world, structureboundingbox, 1, 4, 1, 6, 4, 6, ROUGH_PRISMARINE);
			}

			boolean flag = this.mainDesign != 0 && random.nextBoolean()
					&& !this.roomDefinition.hasOpening[EnumFacing.DOWN.getIndex()]
					&& !this.roomDefinition.hasOpening[EnumFacing.UP.getIndex()]
					&& this.roomDefinition.countOpenings() > 1;
			if (this.mainDesign == 0) {
				this.fillWithBlocks(world, structureboundingbox, 0, 1, 0, 2, 1, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 0, 3, 0, 2, 3, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 0, 2, 0, 0, 2, 2, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 2, 0, 2, 2, 0, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
						false);
				this.setBlockState(world, SEA_LANTERN, 1, 2, 1, structureboundingbox);
				this.fillWithBlocks(world, structureboundingbox, 5, 1, 0, 7, 1, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 5, 3, 0, 7, 3, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 7, 2, 0, 7, 2, 2, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 5, 2, 0, 6, 2, 0, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
						false);
				this.setBlockState(world, SEA_LANTERN, 6, 2, 1, structureboundingbox);
				this.fillWithBlocks(world, structureboundingbox, 0, 1, 5, 2, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 0, 3, 5, 2, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 0, 2, 5, 0, 2, 7, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 2, 7, 2, 2, 7, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
						false);
				this.setBlockState(world, SEA_LANTERN, 1, 2, 6, structureboundingbox);
				this.fillWithBlocks(world, structureboundingbox, 5, 1, 5, 7, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 5, 3, 5, 7, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 7, 2, 5, 7, 2, 7, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 5, 2, 7, 6, 2, 7, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
						false);
				this.setBlockState(world, SEA_LANTERN, 6, 2, 6, structureboundingbox);
				if (this.roomDefinition.hasOpening[EnumFacing.SOUTH.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, 3, 3, 0, 4, 3, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				} else {
					this.fillWithBlocks(world, structureboundingbox, 3, 3, 0, 4, 3, 1, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 3, 2, 0, 4, 2, 0, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 3, 1, 0, 4, 1, 1, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				}

				if (this.roomDefinition.hasOpening[EnumFacing.NORTH.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, 3, 3, 7, 4, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				} else {
					this.fillWithBlocks(world, structureboundingbox, 3, 3, 6, 4, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 3, 2, 7, 4, 2, 7, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 3, 1, 6, 4, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				}

				if (this.roomDefinition.hasOpening[EnumFacing.WEST.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, 0, 3, 3, 0, 3, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				} else {
					this.fillWithBlocks(world, structureboundingbox, 0, 3, 3, 1, 3, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 0, 2, 3, 0, 2, 4, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 0, 1, 3, 1, 1, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				}

				if (this.roomDefinition.hasOpening[EnumFacing.EAST.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, 7, 3, 3, 7, 3, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				} else {
					this.fillWithBlocks(world, structureboundingbox, 6, 3, 3, 7, 3, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 7, 2, 3, 7, 2, 4, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 6, 1, 3, 7, 1, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				}
			} else if (this.mainDesign == 1) {
				this.fillWithBlocks(world, structureboundingbox, 2, 1, 2, 2, 3, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 2, 1, 5, 2, 3, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 5, 1, 5, 5, 3, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 5, 1, 2, 5, 3, 2, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.setBlockState(world, SEA_LANTERN, 2, 2, 2, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 2, 2, 5, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 5, 2, 5, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 5, 2, 2, structureboundingbox);
				this.fillWithBlocks(world, structureboundingbox, 0, 1, 0, 1, 3, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 0, 1, 1, 0, 3, 1, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 0, 1, 7, 1, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 0, 1, 6, 0, 3, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 6, 1, 7, 7, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 7, 1, 6, 7, 3, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 6, 1, 0, 7, 3, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 7, 1, 1, 7, 3, 1, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.setBlockState(world, ROUGH_PRISMARINE, 1, 2, 0, structureboundingbox);
				this.setBlockState(world, ROUGH_PRISMARINE, 0, 2, 1, structureboundingbox);
				this.setBlockState(world, ROUGH_PRISMARINE, 1, 2, 7, structureboundingbox);
				this.setBlockState(world, ROUGH_PRISMARINE, 0, 2, 6, structureboundingbox);
				this.setBlockState(world, ROUGH_PRISMARINE, 6, 2, 7, structureboundingbox);
				this.setBlockState(world, ROUGH_PRISMARINE, 7, 2, 6, structureboundingbox);
				this.setBlockState(world, ROUGH_PRISMARINE, 6, 2, 0, structureboundingbox);
				this.setBlockState(world, ROUGH_PRISMARINE, 7, 2, 1, structureboundingbox);
				if (!this.roomDefinition.hasOpening[EnumFacing.SOUTH.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, 1, 3, 0, 6, 3, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 1, 2, 0, 6, 2, 0, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 1, 1, 0, 6, 1, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				}

				if (!this.roomDefinition.hasOpening[EnumFacing.NORTH.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, 1, 3, 7, 6, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 1, 2, 7, 6, 2, 7, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 1, 1, 7, 6, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				}

				if (!this.roomDefinition.hasOpening[EnumFacing.WEST.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, 0, 3, 1, 0, 3, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 0, 2, 1, 0, 2, 6, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 0, 1, 1, 0, 1, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				}

				if (!this.roomDefinition.hasOpening[EnumFacing.EAST.getIndex()]) {
					this.fillWithBlocks(world, structureboundingbox, 7, 3, 1, 7, 3, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 7, 2, 1, 7, 2, 6, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, 7, 1, 1, 7, 1, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
				}
			} else if (this.mainDesign == 2) {
				this.fillWithBlocks(world, structureboundingbox, 0, 1, 0, 0, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 7, 1, 0, 7, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 1, 0, 6, 1, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 1, 7, 6, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 0, 2, 0, 0, 2, 7, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 7, 2, 0, 7, 2, 7, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 2, 0, 6, 2, 0, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 2, 7, 6, 2, 7, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 0, 3, 0, 0, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 7, 3, 0, 7, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 3, 0, 6, 3, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 1, 3, 7, 6, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 0, 1, 3, 0, 2, 4, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 7, 1, 3, 7, 2, 4, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 3, 1, 0, 4, 2, 0, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 3, 1, 7, 4, 2, 7, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				if (this.roomDefinition.hasOpening[EnumFacing.SOUTH.getIndex()]) {
					this.generateWaterBox(world, structureboundingbox, 3, 1, 0, 4, 2, 0, false);
				}

				if (this.roomDefinition.hasOpening[EnumFacing.NORTH.getIndex()]) {
					this.generateWaterBox(world, structureboundingbox, 3, 1, 7, 4, 2, 7, false);
				}

				if (this.roomDefinition.hasOpening[EnumFacing.WEST.getIndex()]) {
					this.generateWaterBox(world, structureboundingbox, 0, 1, 3, 0, 2, 4, false);
				}

				if (this.roomDefinition.hasOpening[EnumFacing.EAST.getIndex()]) {
					this.generateWaterBox(world, structureboundingbox, 7, 1, 3, 7, 2, 4, false);
				}
			}

			if (flag) {
				this.fillWithBlocks(world, structureboundingbox, 3, 1, 3, 4, 1, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 3, 2, 3, 4, 2, 4, ROUGH_PRISMARINE, ROUGH_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 3, 3, 3, 4, 3, 4, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
			}

			return true;
		}
	}

	public static class SimpleTopRoom extends StructureOceanMonumentPieces.Piece {
		public SimpleTopRoom() {
		}

		public SimpleTopRoom(EnumFacing parEnumFacing, StructureOceanMonumentPieces.RoomDefinition parRoomDefinition,
				EaglercraftRandom parRandom) {
			super(1, parEnumFacing, parRoomDefinition, 1, 1, 1);
		}

		public boolean addComponentParts(World world, EaglercraftRandom random,
				StructureBoundingBox structureboundingbox) {
			if (this.roomDefinition.index / 25 > 0) {
				this.generateDefaultFloor(world, structureboundingbox, 0, 0,
						this.roomDefinition.hasOpening[EnumFacing.DOWN.getIndex()]);
			}

			if (this.roomDefinition.connections[EnumFacing.UP.getIndex()] == null) {
				this.generateBoxOnFillOnly(world, structureboundingbox, 1, 4, 1, 6, 4, 6, ROUGH_PRISMARINE);
			}

			for (int i = 1; i <= 6; ++i) {
				for (int j = 1; j <= 6; ++j) {
					if (random.nextInt(3) != 0) {
						int k = 2 + (random.nextInt(4) == 0 ? 0 : 1);
						this.fillWithBlocks(world, structureboundingbox, i, k, j, i, 3, j,
								Blocks.sponge.getStateFromMeta(1), Blocks.sponge.getStateFromMeta(1), false);
					}
				}
			}

			this.fillWithBlocks(world, structureboundingbox, 0, 1, 0, 0, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 7, 1, 0, 7, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 1, 0, 6, 1, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 1, 7, 6, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 0, 2, 0, 0, 2, 7, DARK_PRISMARINE, DARK_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 7, 2, 0, 7, 2, 7, DARK_PRISMARINE, DARK_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 2, 0, 6, 2, 0, DARK_PRISMARINE, DARK_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 2, 7, 6, 2, 7, DARK_PRISMARINE, DARK_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 0, 3, 0, 0, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 7, 3, 0, 7, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 3, 0, 6, 3, 0, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 1, 3, 7, 6, 3, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 0, 1, 3, 0, 2, 4, DARK_PRISMARINE, DARK_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 7, 1, 3, 7, 2, 4, DARK_PRISMARINE, DARK_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 3, 1, 0, 4, 2, 0, DARK_PRISMARINE, DARK_PRISMARINE, false);
			this.fillWithBlocks(world, structureboundingbox, 3, 1, 7, 4, 2, 7, DARK_PRISMARINE, DARK_PRISMARINE, false);
			if (this.roomDefinition.hasOpening[EnumFacing.SOUTH.getIndex()]) {
				this.generateWaterBox(world, structureboundingbox, 3, 1, 0, 4, 2, 0, false);
			}

			return true;
		}
	}

	public static class WingRoom extends StructureOceanMonumentPieces.Piece {
		private int StructureOceanMonumentPiecesMainDesign;

		public WingRoom() {
		}

		public WingRoom(EnumFacing parEnumFacing, StructureBoundingBox parStructureBoundingBox, int parInt1) {
			super(parEnumFacing, parStructureBoundingBox);
			this.StructureOceanMonumentPiecesMainDesign = parInt1 & 1;
		}

		public boolean addComponentParts(World world, EaglercraftRandom var2,
				StructureBoundingBox structureboundingbox) {
			if (this.StructureOceanMonumentPiecesMainDesign == 0) {
				for (int i = 0; i < 4; ++i) {
					this.fillWithBlocks(world, structureboundingbox, 10 - i, 3 - i, 20 - i, 12 + i, 3 - i, 20,
							BRICKS_PRISMARINE, BRICKS_PRISMARINE, false);
				}

				this.fillWithBlocks(world, structureboundingbox, 7, 0, 6, 15, 0, 16, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 6, 0, 6, 6, 3, 20, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 16, 0, 6, 16, 3, 20, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 7, 1, 7, 7, 1, 20, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 15, 1, 7, 15, 1, 20, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 7, 1, 6, 9, 3, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 13, 1, 6, 15, 3, 6, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 8, 1, 7, 9, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 13, 1, 7, 14, 1, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 9, 0, 5, 13, 0, 5, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 10, 0, 7, 12, 0, 7, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 8, 0, 10, 8, 0, 12, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 14, 0, 10, 14, 0, 12, DARK_PRISMARINE, DARK_PRISMARINE,
						false);

				for (int k = 18; k >= 7; k -= 3) {
					this.setBlockState(world, SEA_LANTERN, 6, 3, k, structureboundingbox);
					this.setBlockState(world, SEA_LANTERN, 16, 3, k, structureboundingbox);
				}

				this.setBlockState(world, SEA_LANTERN, 10, 0, 10, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 12, 0, 10, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 10, 0, 12, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 12, 0, 12, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 8, 3, 6, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 14, 3, 6, structureboundingbox);
				this.setBlockState(world, BRICKS_PRISMARINE, 4, 2, 4, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 4, 1, 4, structureboundingbox);
				this.setBlockState(world, BRICKS_PRISMARINE, 4, 0, 4, structureboundingbox);
				this.setBlockState(world, BRICKS_PRISMARINE, 18, 2, 4, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 18, 1, 4, structureboundingbox);
				this.setBlockState(world, BRICKS_PRISMARINE, 18, 0, 4, structureboundingbox);
				this.setBlockState(world, BRICKS_PRISMARINE, 4, 2, 18, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 4, 1, 18, structureboundingbox);
				this.setBlockState(world, BRICKS_PRISMARINE, 4, 0, 18, structureboundingbox);
				this.setBlockState(world, BRICKS_PRISMARINE, 18, 2, 18, structureboundingbox);
				this.setBlockState(world, SEA_LANTERN, 18, 1, 18, structureboundingbox);
				this.setBlockState(world, BRICKS_PRISMARINE, 18, 0, 18, structureboundingbox);
				this.setBlockState(world, BRICKS_PRISMARINE, 9, 7, 20, structureboundingbox);
				this.setBlockState(world, BRICKS_PRISMARINE, 13, 7, 20, structureboundingbox);
				this.fillWithBlocks(world, structureboundingbox, 6, 0, 21, 7, 4, 21, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 15, 0, 21, 16, 4, 21, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.spawnElder(world, structureboundingbox, 11, 2, 16);
			} else if (this.StructureOceanMonumentPiecesMainDesign == 1) {
				this.fillWithBlocks(world, structureboundingbox, 9, 3, 18, 13, 3, 20, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 9, 0, 18, 9, 2, 18, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 13, 0, 18, 13, 2, 18, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				byte b2 = 9;
				byte b0 = 20;
				byte b1 = 5;

				for (int j = 0; j < 2; ++j) {
					this.setBlockState(world, BRICKS_PRISMARINE, b2, b1 + 1, b0, structureboundingbox);
					this.setBlockState(world, SEA_LANTERN, b2, b1, b0, structureboundingbox);
					this.setBlockState(world, BRICKS_PRISMARINE, b2, b1 - 1, b0, structureboundingbox);
					b2 = 13;
				}

				this.fillWithBlocks(world, structureboundingbox, 7, 3, 7, 15, 3, 14, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
						false);
				b2 = 10;

				for (int l = 0; l < 2; ++l) {
					this.fillWithBlocks(world, structureboundingbox, b2, 0, 10, b2, 6, 10, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.fillWithBlocks(world, structureboundingbox, b2, 0, 12, b2, 6, 12, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					this.setBlockState(world, SEA_LANTERN, b2, 0, 10, structureboundingbox);
					this.setBlockState(world, SEA_LANTERN, b2, 0, 12, structureboundingbox);
					this.setBlockState(world, SEA_LANTERN, b2, 4, 10, structureboundingbox);
					this.setBlockState(world, SEA_LANTERN, b2, 4, 12, structureboundingbox);
					b2 = 12;
				}

				b2 = 8;

				for (int i1 = 0; i1 < 2; ++i1) {
					this.fillWithBlocks(world, structureboundingbox, b2, 0, 7, b2, 2, 7, BRICKS_PRISMARINE, BRICKS_PRISMARINE,
							false);
					this.fillWithBlocks(world, structureboundingbox, b2, 0, 14, b2, 2, 14, BRICKS_PRISMARINE,
							BRICKS_PRISMARINE, false);
					b2 = 14;
				}

				this.fillWithBlocks(world, structureboundingbox, 8, 3, 8, 8, 3, 13, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				this.fillWithBlocks(world, structureboundingbox, 14, 3, 8, 14, 3, 13, DARK_PRISMARINE, DARK_PRISMARINE,
						false);
				this.spawnElder(world, structureboundingbox, 11, 5, 13);
			}

			return true;
		}
	}

	static class XDoubleRoomFitHelper implements StructureOceanMonumentPieces.MonumentRoomFitHelper {
		private XDoubleRoomFitHelper() {
		}

		public boolean fits(StructureOceanMonumentPieces.RoomDefinition parRoomDefinition) {
			return parRoomDefinition.hasOpening[EnumFacing.EAST.getIndex()]
					&& !parRoomDefinition.connections[EnumFacing.EAST.getIndex()].claimed;
		}

		public StructureOceanMonumentPieces.Piece create(EnumFacing parEnumFacing,
				StructureOceanMonumentPieces.RoomDefinition parRoomDefinition, EaglercraftRandom parRandom) {
			parRoomDefinition.claimed = true;
			parRoomDefinition.connections[EnumFacing.EAST.getIndex()].claimed = true;
			return new StructureOceanMonumentPieces.DoubleXRoom(parEnumFacing, parRoomDefinition, parRandom);
		}
	}

	static class XYDoubleRoomFitHelper implements StructureOceanMonumentPieces.MonumentRoomFitHelper {
		private XYDoubleRoomFitHelper() {
		}

		public boolean fits(
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition) {
			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.EAST.getIndex()]
					&& !structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.EAST
							.getIndex()].claimed
					&& structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.UP.getIndex()]
					&& !structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.UP
							.getIndex()].claimed) {
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition1 = structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.EAST
						.getIndex()];
				return structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.UP.getIndex()]
						&& !structureoceanmonumentpieces$roomdefinition1.connections[EnumFacing.UP
								.getIndex()].claimed;
			} else {
				return false;
			}
		}

		public StructureOceanMonumentPieces.Piece create(EnumFacing enumfacing,
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition,
				EaglercraftRandom random) {
			structureoceanmonumentpieces$roomdefinition.claimed = true;
			structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.EAST
					.getIndex()].claimed = true;
			structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.UP.getIndex()].claimed = true;
			structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.EAST
					.getIndex()].connections[EnumFacing.UP.getIndex()].claimed = true;
			return new StructureOceanMonumentPieces.DoubleXYRoom(enumfacing,
					structureoceanmonumentpieces$roomdefinition, random);
		}
	}

	static class YDoubleRoomFitHelper implements StructureOceanMonumentPieces.MonumentRoomFitHelper {
		private YDoubleRoomFitHelper() {
		}

		public boolean fits(
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition) {
			return structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.UP.getIndex()]
					&& !structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.UP
							.getIndex()].claimed;
		}

		public StructureOceanMonumentPieces.Piece create(EnumFacing enumfacing,
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition,
				EaglercraftRandom random) {
			structureoceanmonumentpieces$roomdefinition.claimed = true;
			structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.UP.getIndex()].claimed = true;
			return new StructureOceanMonumentPieces.DoubleYRoom(enumfacing, structureoceanmonumentpieces$roomdefinition,
					random);
		}
	}

	static class YZDoubleRoomFitHelper implements StructureOceanMonumentPieces.MonumentRoomFitHelper {
		private YZDoubleRoomFitHelper() {
		}

		public boolean fits(
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition) {
			if (structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.NORTH.getIndex()]
					&& !structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.NORTH
							.getIndex()].claimed
					&& structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.UP.getIndex()]
					&& !structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.UP
							.getIndex()].claimed) {
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition1 = structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.NORTH
						.getIndex()];
				return structureoceanmonumentpieces$roomdefinition1.hasOpening[EnumFacing.UP.getIndex()]
						&& !structureoceanmonumentpieces$roomdefinition1.connections[EnumFacing.UP
								.getIndex()].claimed;
			} else {
				return false;
			}
		}

		public StructureOceanMonumentPieces.Piece create(EnumFacing enumfacing,
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition,
				EaglercraftRandom random) {
			structureoceanmonumentpieces$roomdefinition.claimed = true;
			structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.NORTH
					.getIndex()].claimed = true;
			structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.UP.getIndex()].claimed = true;
			structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.NORTH
					.getIndex()].connections[EnumFacing.UP.getIndex()].claimed = true;
			return new StructureOceanMonumentPieces.DoubleYZRoom(enumfacing,
					structureoceanmonumentpieces$roomdefinition, random);
		}
	}

	static class ZDoubleRoomFitHelper implements StructureOceanMonumentPieces.MonumentRoomFitHelper {
		private ZDoubleRoomFitHelper() {
		}

		public boolean fits(
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition) {
			return structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.NORTH.getIndex()]
					&& !structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.NORTH
							.getIndex()].claimed;
		}

		public StructureOceanMonumentPieces.Piece create(EnumFacing enumfacing,
				StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition,
				EaglercraftRandom random) {
			StructureOceanMonumentPieces.RoomDefinition structureoceanmonumentpieces$roomdefinition1 = structureoceanmonumentpieces$roomdefinition;
			if (!structureoceanmonumentpieces$roomdefinition.hasOpening[EnumFacing.NORTH.getIndex()]
					|| structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.NORTH
							.getIndex()].claimed) {
				structureoceanmonumentpieces$roomdefinition1 = structureoceanmonumentpieces$roomdefinition.connections[EnumFacing.SOUTH
						.getIndex()];
			}

			structureoceanmonumentpieces$roomdefinition1.claimed = true;
			structureoceanmonumentpieces$roomdefinition1.connections[EnumFacing.NORTH
					.getIndex()].claimed = true;
			return new StructureOceanMonumentPieces.DoubleZRoom(enumfacing,
					structureoceanmonumentpieces$roomdefinition1, random);
		}
	}
}