package net.minecraft.client.gui;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

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
public class GuiScreenOptionsSounds extends GuiScreen {
	private final GuiScreen parent;
	private final GameSettings game_settings_4;
	protected String title = "Options";
	private String offDisplayString;

	public GuiScreenOptionsSounds(GuiScreen parGuiScreen, GameSettings parGameSettings) {
		this.parent = parGuiScreen;
		this.game_settings_4 = parGameSettings;
	}

	/**+
	 * Adds the buttons (and other controls) to the screen in
	 * question. Called when the GUI is displayed and when the
	 * window resizes, the buttonList is cleared beforehand.
	 */
	public void initGui() {
		int i = 0;
		this.title = I18n.format("options.sounds.title", new Object[0]);
		this.offDisplayString = I18n.format("options.off", new Object[0]);
		this.buttonList.add(new GuiScreenOptionsSounds.Button(SoundCategory.MASTER.getCategoryId(),
				this.width / 2 - 155 + i % 2 * 160, this.height / 6 - 12 + 24 * (i >> 1), SoundCategory.MASTER, true));
		i = i + 2;

		SoundCategory[] cats = SoundCategory._VALUES;
		for (int j = 0; j < cats.length; ++j) {
			SoundCategory soundcategory = cats[j];
			if (soundcategory != SoundCategory.MASTER) {
				this.buttonList.add(new GuiScreenOptionsSounds.Button(soundcategory.getCategoryId(),
						this.width / 2 - 155 + i % 2 * 160, this.height / 6 - 12 + 24 * (i >> 1), soundcategory,
						false));
				++i;
			}
		}

		this.buttonList.add(new GuiButton(200, this.width / 2 - 100, this.height / 6 + 168,
				I18n.format("gui.done", new Object[0])));
	}

	/**+
	 * Called by the controls from the buttonList when activated.
	 * (Mouse pressed for buttons)
	 */
	protected void actionPerformed(GuiButton parGuiButton) {
		if (parGuiButton.enabled) {
			if (parGuiButton.id == 200) {
				this.mc.gameSettings.saveOptions();
				this.mc.displayGuiScreen(this.parent);
			}

		}
	}

	/**+
	 * Draws the screen and all the components in it. Args : mouseX,
	 * mouseY, renderPartialTicks
	 */
	public void drawScreen(int i, int j, float f) {
		this.drawDefaultBackground();
		this.drawCenteredString(this.fontRendererObj, this.title, this.width / 2, 15, 16777215);
		super.drawScreen(i, j, f);
	}

	protected String getSoundVolume(SoundCategory parSoundCategory) {
		float f = this.game_settings_4.getSoundLevel(parSoundCategory);
		return f == 0.0F ? this.offDisplayString : (int) (f * 100.0F) + "%";
	}

	class Button extends GuiButton {
		private final SoundCategory field_146153_r;
		private final String categoryName;
		public float volume = 1.0F;
		public boolean pressed;

		public Button(int parInt1, int parInt2, int parInt3, SoundCategory parSoundCategory, boolean parFlag) {
			super(parInt1, parInt2, parInt3, parFlag ? 310 : 150, 20, "");
			this.field_146153_r = parSoundCategory;
			this.categoryName = I18n.format("soundCategory." + parSoundCategory.getCategoryName(), new Object[0]);
			this.displayString = this.categoryName + ": "
					+ GuiScreenOptionsSounds.this.getSoundVolume(parSoundCategory);
			this.volume = GuiScreenOptionsSounds.this.game_settings_4.getSoundLevel(parSoundCategory);
		}

		protected int getHoverState(boolean var1) {
			return 0;
		}

		protected void mouseDragged(Minecraft minecraft, int i, int var3) {
			if (this.visible) {
				if (this.pressed) {
					this.volume = (float) (i - (this.xPosition + 4)) / (float) (this.width - 8);
					this.volume = MathHelper.clamp_float(this.volume, 0.0F, 1.0F);
					minecraft.gameSettings.setSoundLevel(this.field_146153_r, this.volume);
					minecraft.gameSettings.saveOptions();
					this.displayString = this.categoryName + ": "
							+ GuiScreenOptionsSounds.this.getSoundVolume(this.field_146153_r);
				}

				GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
				this.drawTexturedModalRect(this.xPosition + (int) (this.volume * (float) (this.width - 8)),
						this.yPosition, 0, 66, 4, 20);
				this.drawTexturedModalRect(this.xPosition + (int) (this.volume * (float) (this.width - 8)) + 4,
						this.yPosition, 196, 66, 4, 20);
			}
		}

		public boolean mousePressed(Minecraft minecraft, int i, int j) {
			if (super.mousePressed(minecraft, i, j)) {
				this.volume = (float) (i - (this.xPosition + 4)) / (float) (this.width - 8);
				this.volume = MathHelper.clamp_float(this.volume, 0.0F, 1.0F);
				minecraft.gameSettings.setSoundLevel(this.field_146153_r, this.volume);
				minecraft.gameSettings.saveOptions();
				this.displayString = this.categoryName + ": "
						+ GuiScreenOptionsSounds.this.getSoundVolume(this.field_146153_r);
				this.pressed = true;
				return true;
			} else {
				return false;
			}
		}

		public void playPressSound(SoundHandler var1) {
		}

		public void mouseReleased(int var1, int var2) {
			if (this.pressed) {
				if (this.field_146153_r == SoundCategory.MASTER) {
					float f = 1.0F;
				} else {
					GuiScreenOptionsSounds.this.game_settings_4.getSoundLevel(this.field_146153_r);
				}

				GuiScreenOptionsSounds.this.mc.getSoundHandler()
						.playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
			}

			this.pressed = false;
		}
	}
}