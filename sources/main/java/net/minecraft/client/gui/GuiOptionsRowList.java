package net.minecraft.client.gui;

import java.util.List;

import com.google.common.collect.Lists;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

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
public class GuiOptionsRowList extends GuiListExtended {
	private final List<GuiOptionsRowList.Row> options = Lists.newArrayList();

	public GuiOptionsRowList(Minecraft mcIn, int parInt1, int parInt2, int parInt3, int parInt4, int parInt5,
			GameSettings.Options... parArrayOfOptions) {
		super(mcIn, parInt1, parInt2, parInt3, parInt4, parInt5);
		this.centerListVertically = false;

		for (int i = 0; i < parArrayOfOptions.length; i += 2) {
			GameSettings.Options gamesettings$options = parArrayOfOptions[i];
			GameSettings.Options gamesettings$options1 = i < parArrayOfOptions.length - 1 ? parArrayOfOptions[i + 1]
					: null;
			GuiButton guibutton = this.createButton(mcIn, parInt1 / 2 - 155, 0, gamesettings$options);
			GuiButton guibutton1 = this.createButton(mcIn, parInt1 / 2 - 155 + 160, 0, gamesettings$options1);
			this.options.add(new GuiOptionsRowList.Row(guibutton, guibutton1));
		}

	}

	private GuiButton createButton(Minecraft mcIn, int parInt1, int parInt2, GameSettings.Options parOptions) {
		if (parOptions == null) {
			return null;
		} else {
			int i = parOptions.returnEnumOrdinal();
			return (GuiButton) (parOptions.getEnumFloat() ? new GuiOptionSlider(i, parInt1, parInt2, parOptions)
					: new GuiOptionButton(i, parInt1, parInt2, parOptions,
							mcIn.gameSettings.getKeyBinding(parOptions)));
		}
	}

	/**+
	 * Gets the IGuiListEntry object for the given index
	 */
	public GuiOptionsRowList.Row getListEntry(int i) {
		return (GuiOptionsRowList.Row) this.options.get(i);
	}

	protected int getSize() {
		return this.options.size();
	}

	/**+
	 * Gets the width of the list
	 */
	public int getListWidth() {
		return 400;
	}

	protected int getScrollBarX() {
		return super.getScrollBarX() + 32;
	}

	public static class Row implements GuiListExtended.IGuiListEntry {
		private final Minecraft client = Minecraft.getMinecraft();
		private final GuiButton buttonA;
		private final GuiButton buttonB;

		public Row(GuiButton parGuiButton, GuiButton parGuiButton2) {
			this.buttonA = parGuiButton;
			this.buttonB = parGuiButton2;
		}

		public void drawEntry(int var1, int var2, int i, int var4, int var5, int j, int k, boolean var8) {
			if (this.buttonA != null) {
				this.buttonA.yPosition = i;
				this.buttonA.drawButton(this.client, j, k);
			}

			if (this.buttonB != null) {
				this.buttonB.yPosition = i;
				this.buttonB.drawButton(this.client, j, k);
			}

		}

		public boolean mousePressed(int var1, int i, int j, int var4, int var5, int var6) {
			if (this.buttonA.mousePressed(this.client, i, j)) {
				if (this.buttonA instanceof GuiOptionButton) {
					this.client.gameSettings
							.setOptionValue(((GuiOptionButton) this.buttonA).returnEnumOptions(), 1);
					this.buttonA.displayString = this.client.gameSettings
							.getKeyBinding(GameSettings.Options.getEnumOptions(this.buttonA.id));
				}

				return true;
			} else if (this.buttonB != null && this.buttonB.mousePressed(this.client, i, j)) {
				if (this.buttonB instanceof GuiOptionButton) {
					this.client.gameSettings
							.setOptionValue(((GuiOptionButton) this.buttonB).returnEnumOptions(), 1);
					this.buttonB.displayString = this.client.gameSettings
							.getKeyBinding(GameSettings.Options.getEnumOptions(this.buttonB.id));
				}

				return true;
			} else {
				return false;
			}
		}

		public void mouseReleased(int var1, int i, int j, int var4, int var5, int var6) {
			if (this.buttonA != null) {
				this.buttonA.mouseReleased(i, j);
			}

			if (this.buttonB != null) {
				this.buttonB.mouseReleased(i, j);
			}

		}

		public void setSelected(int var1, int var2, int var3) {
		}
	}
}