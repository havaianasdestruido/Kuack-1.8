package net.minecraft.command;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import net.lax1dude.eaglercraft.v1_8.EaglercraftRandom;
import net.lax1dude.eaglercraft.v1_8.HString;
import net.lax1dude.eaglercraft.v1_8.ThreadLocalRandom;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
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
public class CommandSpreadPlayers extends CommandBase {

	/**+
	 * Gets the name of the command
	 */
	public String getCommandName() {
		return "spreadplayers";
	}

	/**+
	 * Return the required permission level for this command.
	 */
	public int getRequiredPermissionLevel() {
		return 2;
	}

	/**+
	 * Gets the usage string for the command.
	 */
	public String getCommandUsage(ICommandSender var1) {
		return "commands.spreadplayers.usage";
	}

	/**+
	 * Callback when the command is invoked
	 */
	public void processCommand(ICommandSender parICommandSender, String[] parArrayOfString) throws CommandException {
		if (parArrayOfString.length < 6) {
			throw new WrongUsageException("commands.spreadplayers.usage", new Object[0]);
		} else {
			int i = 0;
			BlockPos blockpos = parICommandSender.getPosition();
			double d0 = parseDouble((double) blockpos.getX(), parArrayOfString[i++], true);
			double d1 = parseDouble((double) blockpos.getZ(), parArrayOfString[i++], true);
			double d2 = parseDouble(parArrayOfString[i++], 0.0D);
			double d3 = parseDouble(parArrayOfString[i++], d2 + 1.0D);
			boolean flag = parseBoolean(parArrayOfString[i++]);
			ArrayList arraylist = Lists.newArrayList();

			while (i < parArrayOfString.length) {
				String s = parArrayOfString[i++];
				if (PlayerSelector.hasArguments(s)) {
					List list = PlayerSelector.matchEntities(parICommandSender, s, Entity.class);
					if (list.size() == 0) {
						throw new EntityNotFoundException();
					}

					arraylist.addAll(list);
				} else {
					EntityPlayerMP entityplayermp = MinecraftServer.getServer().getConfigurationManager()
							.getPlayerByUsername(s);
					if (entityplayermp == null) {
						throw new PlayerNotFoundException();
					}

					arraylist.add(entityplayermp);
				}
			}

			parICommandSender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, arraylist.size());
			if (arraylist.isEmpty()) {
				throw new EntityNotFoundException();
			} else {
				parICommandSender.addChatMessage(
						new ChatComponentTranslation("commands.spreadplayers.spreading." + (flag ? "teams" : "players"),
								new Object[] { Integer.valueOf(arraylist.size()), Double.valueOf(d3),
										Double.valueOf(d0), Double.valueOf(d1), Double.valueOf(d2) }));
				this.spread(parICommandSender, arraylist, new CommandSpreadPlayers.Position(d0, d1), d2, d3,
						((Entity) arraylist.get(0)).worldObj, flag);
			}
		}
	}

	private void spread(ICommandSender worldIn, List<Entity> parList, CommandSpreadPlayers.Position parPosition,
			double parDouble1, double parDouble2, World parWorld, boolean parFlag) throws CommandException {
		EaglercraftRandom random = ThreadLocalRandom.current();
		double d0 = parPosition.x - parDouble2;
		double d1 = parPosition.z - parDouble2;
		double d2 = parPosition.x + parDouble2;
		double d3 = parPosition.z + parDouble2;
		CommandSpreadPlayers.Position[] acommandspreadplayers$position = this.createInitialPositions(random,
				parFlag ? this.getNumberOfTeams(parList) : parList.size(), d0, d1, d2, d3);
		int i = this.spreadPositions(parPosition, parDouble1, parWorld, random, d0, d1, d2, d3,
				acommandspreadplayers$position, parFlag);
		double d4 = this.setPlayerPositions(parList, parWorld, acommandspreadplayers$position, parFlag);
		notifyOperators(worldIn, this, "commands.spreadplayers.success." + (parFlag ? "teams" : "players"),
				new Object[] { Integer.valueOf(acommandspreadplayers$position.length),
						Double.valueOf(parPosition.x), Double.valueOf(parPosition.z) });
		if (acommandspreadplayers$position.length > 1) {
			worldIn.addChatMessage(new ChatComponentTranslation(
					"commands.spreadplayers.info." + (parFlag ? "teams" : "players"),
					new Object[] { HString.format("%.2f", new Object[] { Double.valueOf(d4) }), Integer.valueOf(i) }));
		}

	}

	private int getNumberOfTeams(List<Entity> parList) {
		HashSet hashset = Sets.newHashSet();

		for (int i = 0, l = parList.size(); i < l; ++i) {
			Entity entity = parList.get(i);
			if (entity instanceof EntityPlayer) {
				hashset.add(((EntityPlayer) entity).getTeam());
			} else {
				hashset.add((Object) null);
			}
		}

		return hashset.size();
	}

	private int spreadPositions(CommandSpreadPlayers.Position worldIn, double parDouble1, World parWorld,
			EaglercraftRandom parRandom, double parDouble2, double parDouble3, double parDouble4, double parDouble5,
			CommandSpreadPlayers.Position[] parArrayOfPosition, boolean parFlag) throws CommandException {
		boolean flag = true;
		double d0 = 3.4028234663852886E38D;

		int i;
		for (i = 0; i < 10000 && flag; ++i) {
			flag = false;
			d0 = 3.4028234663852886E38D;

			for (int j = 0; j < parArrayOfPosition.length; ++j) {
				CommandSpreadPlayers.Position commandspreadplayers$position = parArrayOfPosition[j];
				int k = 0;
				CommandSpreadPlayers.Position commandspreadplayers$position1 = new CommandSpreadPlayers.Position();

				for (int l = 0; l < parArrayOfPosition.length; ++l) {
					if (j != l) {
						CommandSpreadPlayers.Position commandspreadplayers$position2 = parArrayOfPosition[l];
						double d1 = commandspreadplayers$position.dist(commandspreadplayers$position2);
						d0 = Math.min(d1, d0);
						if (d1 < parDouble1) {
							++k;
							commandspreadplayers$position1.x += commandspreadplayers$position2.x
									- commandspreadplayers$position.x;
							commandspreadplayers$position1.z += commandspreadplayers$position2.z
									- commandspreadplayers$position.z;
						}
					}
				}

				if (k > 0) {
					commandspreadplayers$position1.x /= (double) k;
					commandspreadplayers$position1.z /= (double) k;
					double d2 = (double) commandspreadplayers$position1.getLength();
					if (d2 > 0.0D) {
						commandspreadplayers$position1.normalize();
						commandspreadplayers$position.moveAway(commandspreadplayers$position1);
					} else {
						commandspreadplayers$position.randomize(parRandom, parDouble2, parDouble3, parDouble4,
								parDouble5);
					}

					flag = true;
				}

				if (commandspreadplayers$position.clamp(parDouble2, parDouble3, parDouble4, parDouble5)) {
					flag = true;
				}
			}

			if (!flag) {
				for (int k = 0; k < parArrayOfPosition.length; ++k) {
					if (!parArrayOfPosition[k].isSafe(parWorld)) {
						parArrayOfPosition[k].randomize(parRandom, parDouble2, parDouble3, parDouble4, parDouble5);
						flag = true;
					}
				}
			}
		}

		if (i >= 10000) {
			throw new CommandException("commands.spreadplayers.failure." + (parFlag ? "teams" : "players"),
					new Object[] { Integer.valueOf(parArrayOfPosition.length), Double.valueOf(worldIn.x),
							Double.valueOf(worldIn.z),
							HString.format("%.2f", new Object[] { Double.valueOf(d0) }) });
		} else {
			return i;
		}
	}

	private double setPlayerPositions(List<Entity> worldIn, World parWorld,
			CommandSpreadPlayers.Position[] parArrayOfPosition, boolean parFlag) {
		double d0 = 0.0D;
		int i = 0;
		HashMap hashmap = Maps.newHashMap();

		for (int j = 0; j < worldIn.size(); ++j) {
			Entity entity = (Entity) worldIn.get(j);
			CommandSpreadPlayers.Position commandspreadplayers$position;
			if (parFlag) {
				Team team = entity instanceof EntityPlayer ? ((EntityPlayer) entity).getTeam() : null;
				if (!hashmap.containsKey(team)) {
					hashmap.put(team, parArrayOfPosition[i++]);
				}

				commandspreadplayers$position = (CommandSpreadPlayers.Position) hashmap.get(team);
			} else {
				commandspreadplayers$position = parArrayOfPosition[i++];
			}

			entity.setPositionAndUpdate(
					(double) ((float) MathHelper.floor_double(commandspreadplayers$position.x) + 0.5F),
					(double) commandspreadplayers$position.getSpawnY(parWorld),
					(double) MathHelper.floor_double(commandspreadplayers$position.z) + 0.5D);
			double d2 = Double.MAX_VALUE;

			for (int k = 0; k < parArrayOfPosition.length; ++k) {
				if (commandspreadplayers$position != parArrayOfPosition[k]) {
					double d1 = commandspreadplayers$position.dist(parArrayOfPosition[k]);
					d2 = Math.min(d1, d2);
				}
			}

			d0 += d2;
		}

		d0 = d0 / (double) worldIn.size();
		return d0;
	}

	private CommandSpreadPlayers.Position[] createInitialPositions(EaglercraftRandom parRandom, int parInt1, double parDouble1,
			double parDouble2, double parDouble3, double parDouble4) {
		CommandSpreadPlayers.Position[] acommandspreadplayers$position = new CommandSpreadPlayers.Position[parInt1];

		for (int i = 0; i < acommandspreadplayers$position.length; ++i) {
			CommandSpreadPlayers.Position commandspreadplayers$position = new CommandSpreadPlayers.Position();
			commandspreadplayers$position.randomize(parRandom, parDouble1, parDouble2, parDouble3, parDouble4);
			acommandspreadplayers$position[i] = commandspreadplayers$position;
		}

		return acommandspreadplayers$position;
	}

	/**+
	 * Return a list of options when the user types TAB
	 */
	public List<String> addTabCompletionOptions(ICommandSender var1, String[] astring, BlockPos blockpos) {
		return astring.length >= 1 && astring.length <= 2 ? getTabCompletionCoordinateXZ(astring, 0, blockpos) : null;
	}

	static class Position {
		double x;
		double z;

		Position() {
		}

		Position(double parDouble1, double parDouble2) {
			this.x = parDouble1;
			this.z = parDouble2;
		}

		double dist(CommandSpreadPlayers.Position parPosition) {
			double d0 = this.x - parPosition.x;
			double d1 = this.z - parPosition.z;
			return Math.sqrt(d0 * d0 + d1 * d1);
		}

		void normalize() {
			double d0 = (double) this.getLength();
			this.x /= d0;
			this.z /= d0;
		}

		float getLength() {
			return MathHelper
					.sqrt_double(this.x * this.x + this.z * this.z);
		}

		public void moveAway(CommandSpreadPlayers.Position parPosition) {
			this.x -= parPosition.x;
			this.z -= parPosition.z;
		}

		public boolean clamp(double parDouble1, double parDouble2, double parDouble3, double parDouble4) {
			boolean flag = false;
			if (this.x < parDouble1) {
				this.x = parDouble1;
				flag = true;
			} else if (this.x > parDouble3) {
				this.x = parDouble3;
				flag = true;
			}

			if (this.z < parDouble2) {
				this.z = parDouble2;
				flag = true;
			} else if (this.z > parDouble4) {
				this.z = parDouble4;
				flag = true;
			}

			return flag;
		}

		public int getSpawnY(World worldIn) {
			BlockPos blockpos = new BlockPos(this.x, 256.0D, this.z);

			while (blockpos.getY() > 0) {
				blockpos = blockpos.down();
				if (worldIn.getBlockState(blockpos).getBlock().getMaterial() != Material.air) {
					return blockpos.getY() + 1;
				}
			}

			return 257;
		}

		public boolean isSafe(World worldIn) {
			BlockPos blockpos = new BlockPos(this.x, 256.0D, this.z);

			while (blockpos.getY() > 0) {
				blockpos = blockpos.down();
				Material material = worldIn.getBlockState(blockpos).getBlock().getMaterial();
				if (material != Material.air) {
					return !material.isLiquid() && material != Material.fire;
				}
			}

			return false;
		}

		public void randomize(EaglercraftRandom parRandom, double parDouble1, double parDouble2, double parDouble3,
				double parDouble4) {
			this.x = MathHelper.getRandomDoubleInRange(parRandom, parDouble1, parDouble3);
			this.z = MathHelper.getRandomDoubleInRange(parRandom, parDouble2, parDouble4);
		}
	}
}