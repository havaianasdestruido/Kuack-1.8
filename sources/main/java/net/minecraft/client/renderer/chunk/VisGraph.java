package net.minecraft.client.renderer.chunk;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Set;

import com.google.common.collect.Lists;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IntegerCache;

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
public class VisGraph {
	private static final int DX = (int) Math.pow(16.0D, 0.0D);
	private static final int DZ = (int) Math.pow(16.0D, 1.0D);
	private static final int DY = (int) Math.pow(16.0D, 2.0D);
	private final BitSet bitSet = new BitSet(4096);
	private static final int[] INDEX_OF_EDGES = new int[1352];
	private int empty = 4096;

	public void setOpaqueCube(BlockPos pos) {
		this.bitSet.set(getIndex(pos), true);
		--this.empty;
	}

	private static int getIndex(BlockPos pos) {
		return getIndex(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
	}

	private static int getIndex(int x, int y, int z) {
		return x << 0 | y << 8 | z << 4;
	}

	public SetVisibility computeVisibility() {
		SetVisibility setvisibility = new SetVisibility();
		if (4096 - this.empty < 256) {
			setvisibility.setAllVisible(true);
		} else if (this.empty == 0) {
			setvisibility.setAllVisible(false);
		} else {
			for (int i = 0; i < INDEX_OF_EDGES.length; ++i) {
				if (!this.bitSet.get(INDEX_OF_EDGES[i])) {
					setvisibility.setManyVisible(this.floodFill(INDEX_OF_EDGES[i]));
				}
			}
		}

		return setvisibility;
	}

	public Set<EnumFacing> getVisibleFacings(BlockPos pos) {
		return this.floodFill(getIndex(pos));
	}

	private Set<EnumFacing> floodFill(int parInt1) {
		EnumSet enumset = EnumSet.noneOf(EnumFacing.class);
		LinkedList linkedlist = Lists.newLinkedList();
		linkedlist.add(IntegerCache.getInteger(parInt1));
		this.bitSet.set(parInt1, true);

		while (!linkedlist.isEmpty()) {
			int i = ((Integer) linkedlist.poll()).intValue();
			this.addEdges(i, enumset);

			EnumFacing[] facings = EnumFacing._VALUES;
			for (int k = 0; k < facings.length; ++k) {
				EnumFacing enumfacing = facings[k];
				int j = this.getNeighborIndexAtFace(i, enumfacing);
				if (j >= 0 && !this.bitSet.get(j)) {
					this.bitSet.set(j, true);
					linkedlist.add(IntegerCache.getInteger(j));
				}
			}
		}

		return enumset;
	}

	private void addEdges(int parInt1, Set<EnumFacing> parSet) {
		int i = parInt1 >> 0 & 15;
		if (i == 0) {
			parSet.add(EnumFacing.WEST);
		} else if (i == 15) {
			parSet.add(EnumFacing.EAST);
		}

		int j = parInt1 >> 8 & 15;
		if (j == 0) {
			parSet.add(EnumFacing.DOWN);
		} else if (j == 15) {
			parSet.add(EnumFacing.UP);
		}

		int k = parInt1 >> 4 & 15;
		if (k == 0) {
			parSet.add(EnumFacing.NORTH);
		} else if (k == 15) {
			parSet.add(EnumFacing.SOUTH);
		}

	}

	private int getNeighborIndexAtFace(int parInt1, EnumFacing parEnumFacing) {
		switch (parEnumFacing) {
		case DOWN:
			if ((parInt1 >> 8 & 15) == 0) {
				return -1;
			}

			return parInt1 - DY;
		case UP:
			if ((parInt1 >> 8 & 15) == 15) {
				return -1;
			}

			return parInt1 + DY;
		case NORTH:
			if ((parInt1 >> 4 & 15) == 0) {
				return -1;
			}

			return parInt1 - DZ;
		case SOUTH:
			if ((parInt1 >> 4 & 15) == 15) {
				return -1;
			}

			return parInt1 + DZ;
		case WEST:
			if ((parInt1 >> 0 & 15) == 0) {
				return -1;
			}

			return parInt1 - DX;
		case EAST:
			if ((parInt1 >> 0 & 15) == 15) {
				return -1;
			}

			return parInt1 + DX;
		default:
			return -1;
		}
	}

	static {
		boolean flag = false;
		boolean flag1 = true;
		int i = 0;

		for (int j = 0; j < 16; ++j) {
			for (int k = 0; k < 16; ++k) {
				for (int l = 0; l < 16; ++l) {
					if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
						INDEX_OF_EDGES[i++] = getIndex(j, k, l);
					}
				}
			}
		}

	}
}