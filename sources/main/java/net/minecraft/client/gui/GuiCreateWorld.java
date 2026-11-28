package net.minecraft.client.gui;

import java.util.Random;

import net.lax1dude.eaglercraft.v1_8.Keyboard;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.WorldInfo;
import org.apache.commons.lang3.StringUtils;

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
public class GuiCreateWorld extends GuiScreen {
	private GuiScreen parentScreen;
	private GuiTextField worldNameField;
	private GuiTextField worldSeedField;
	private String saveDirName;
	private String gameMode = "survival";
	private String savedGameMode;
	private boolean generateStructuresEnabled = true;
	private boolean allowCheats;
	private boolean allowCheatsWasSetByUser;
	private boolean bonusChestEnabled;
	private boolean hardCoreMode;
	private boolean alreadyGenerated;
	private boolean inMoreWorldOptionsDisplay;
	private GuiButton btnGameMode;
	private GuiButton btnMoreOptions;
	private GuiButton btnMapFeatures;
	private GuiButton btnBonusItems;
	private GuiButton btnMapType;
	private GuiButton btnAllowCommands;
	private GuiButton btnCustomizeType;
	private String gameModeDesc1;
	private String gameModeDesc2;
	private String worldSeed;
	private String worldName;
	private int selectedIndex;
	public String chunkProviderSettingsJson = "";
	/**+
	 * These filenames are known to be restricted on one or more
	 * OS's.
	 */
	private static final String[] disallowedFilenames = new String[] { "CON", "COM", "PRN", "AUX", "CLOCK$", "NUL",
			"COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4",
			"LPT5", "LPT6", "LPT7", "LPT8", "LPT9" };

	public GuiCreateWorld(GuiScreen parGuiScreen) {
		this.parentScreen = parGuiScreen;
		this.worldSeed = "";
		this.worldName = I18n.format("selectWorld.newWorld", new Object[0]);
	}

	/**+
	 * Called from the main game loop to update the screen.
	 */
	public void updateScreen() {
		this.worldNameField.updateCursorCounter();
		this.worldSeedField.updateCursorCounter();
	}

	/**+
	 * Adds the buttons (and other controls) to the screen in
	 * question. Called when the GUI is displayed and when the
	 * window resizes, the buttonList is cleared beforehand.
	 */
	public void initGui() {
		Keyboard.enableRepeatEvents(true);
		this.buttonList.clear();
		this.buttonList.add(new GuiButton(0, this.width / 2 - 155, this.height - 28, 150, 20,
				I18n.format("selectWorld.create", new Object[0])));
		this.buttonList.add(new GuiButton(1, this.width / 2 + 5, this.height - 28, 150, 20,
				I18n.format("gui.cancel", new Object[0])));
		this.buttonList.add(this.btnGameMode = new GuiButton(2, this.width / 2 - 75, 115, 150, 20,
				I18n.format("selectWorld.gameMode", new Object[0])));
		this.buttonList.add(this.btnMoreOptions = new GuiButton(3, this.width / 2 - 75, 187, 150, 20,
				I18n.format("selectWorld.moreWorldOptions", new Object[0])));
		this.buttonList.add(this.btnMapFeatures = new GuiButton(4, this.width / 2 - 155, 100, 150, 20,
				I18n.format("selectWorld.mapFeatures", new Object[0])));
		this.btnMapFeatures.visible = false;
		this.buttonList.add(this.btnBonusItems = new GuiButton(7, this.width / 2 + 5, 151, 150, 20,
				I18n.format("selectWorld.bonusItems", new Object[0])));
		this.btnBonusItems.visible = false;
		this.buttonList.add(this.btnMapType = new GuiButton(5, this.width / 2 + 5, 100, 150, 20,
				I18n.format("selectWorld.mapType", new Object[0])));
		this.btnMapType.visible = false;
		this.buttonList.add(this.btnAllowCommands = new GuiButton(6, this.width / 2 - 155, 151, 150, 20,
				I18n.format("selectWorld.allowCommands", new Object[0])));
		this.btnAllowCommands.visible = false;
		this.buttonList.add(this.btnCustomizeType = new GuiButton(8, this.width / 2 + 5, 120, 150, 20,
				I18n.format("selectWorld.customizeType", new Object[0])));
		this.btnCustomizeType.visible = false;
		this.worldNameField = new GuiTextField(9, this.fontRendererObj, this.width / 2 - 100, 60, 200, 20);
		this.worldNameField.setFocused(true);
		this.worldNameField.setText(this.worldName);
		this.worldSeedField = new GuiTextField(10, this.fontRendererObj, this.width / 2 - 100, 60, 200, 20);
		this.worldSeedField.setText(this.worldSeed);
		this.showMoreWorldOptions(this.inMoreWorldOptionsDisplay);
		this.calcSaveDirName();
		this.updateDisplayState();
	}

	private void calcSaveDirName() {
		this.saveDirName = this.worldNameField.getText().trim();

		for (int i = 0; i < ChatAllowedCharacters.allowedCharactersArray.length; ++i) {
			this.saveDirName = this.saveDirName.replace(ChatAllowedCharacters.allowedCharactersArray[i], '_');
		}

		if (StringUtils.isEmpty(this.saveDirName)) {
			this.saveDirName = "World";
		}

		this.saveDirName = getUncollidingSaveDirName(this.mc.getSaveLoader(), this.saveDirName);
	}

	private void updateDisplayState() {
		this.btnGameMode.displayString = I18n.format("selectWorld.gameMode", new Object[0]) + ": "
				+ I18n.format("selectWorld.gameMode." + this.gameMode, new Object[0]);
		this.gameModeDesc1 = I18n.format("selectWorld.gameMode." + this.gameMode + ".line1", new Object[0]);
		this.gameModeDesc2 = I18n.format("selectWorld.gameMode." + this.gameMode + ".line2", new Object[0]);
		this.btnMapFeatures.displayString = I18n.format("selectWorld.mapFeatures", new Object[0]) + " ";
		if (this.generateStructuresEnabled) {
			this.btnMapFeatures.displayString = this.btnMapFeatures.displayString
					+ I18n.format("options.on", new Object[0]);
		} else {
			this.btnMapFeatures.displayString = this.btnMapFeatures.displayString
					+ I18n.format("options.off", new Object[0]);
		}

		this.btnBonusItems.displayString = I18n.format("selectWorld.bonusItems", new Object[0]) + " ";
		if (this.bonusChestEnabled && !this.hardCoreMode) {
			this.btnBonusItems.displayString = this.btnBonusItems.displayString
					+ I18n.format("options.on", new Object[0]);
		} else {
			this.btnBonusItems.displayString = this.btnBonusItems.displayString
					+ I18n.format("options.off", new Object[0]);
		}

		this.btnMapType.displayString = I18n.format("selectWorld.mapType", new Object[0]) + " "
				+ I18n.format(WorldType.worldTypes[this.selectedIndex].getTranslateName(), new Object[0]);
		this.btnAllowCommands.displayString = I18n.format("selectWorld.allowCommands", new Object[0]) + " ";
		if (this.allowCheats && !this.hardCoreMode) {
			this.btnAllowCommands.displayString = this.btnAllowCommands.displayString
					+ I18n.format("options.on", new Object[0]);
		} else {
			this.btnAllowCommands.displayString = this.btnAllowCommands.displayString
					+ I18n.format("options.off", new Object[0]);
		}

	}

	public static String getUncollidingSaveDirName(ISaveFormat parISaveFormat, String parString1) {
		parString1 = parString1.replaceAll("[\\./\"]", "_");

		for (int i = 0; i < disallowedFilenames.length; ++i) {
			if (parString1.equalsIgnoreCase(disallowedFilenames[i])) {
				parString1 = "_" + parString1 + "_";
			}
		}

		while (parISaveFormat.getWorldInfo(parString1) != null) {
			parString1 = parString1 + "-";
		}

		return parString1;
	}

	/**+
	 * Called when the screen is unloaded. Used to disable keyboard
	 * repeat events
	 */
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
	}

	/**+
	 * Called by the controls from the buttonList when activated.
	 * (Mouse pressed for buttons)
	 */
	protected void actionPerformed(GuiButton parGuiButton) {
		if (parGuiButton.enabled) {
			if (parGuiButton.id == 1) {
				this.mc.displayGuiScreen(this.parentScreen);
			} else if (parGuiButton.id == 0) {
				this.mc.displayGuiScreen((GuiScreen) null);
				if (this.alreadyGenerated) {
					return;
				}

				this.alreadyGenerated = true;
				long i = (new Random()).nextLong();
				String s = this.worldSeedField.getText();
				if (!StringUtils.isEmpty(s)) {
					try {
						long j = Long.parseLong(s);
						if (j != 0L) {
							i = j;
						}
					} catch (NumberFormatException var7) {
						i = (long) s.hashCode();
					}
				}

				WorldSettings.GameType worldsettings$gametype = WorldSettings.GameType.getByName(this.gameMode);
				WorldSettings worldsettings = new WorldSettings(i, worldsettings$gametype, this.generateStructuresEnabled,
						this.hardCoreMode, WorldType.worldTypes[this.selectedIndex]);
				worldsettings.setWorldName(this.chunkProviderSettingsJson);
				if (this.bonusChestEnabled && !this.hardCoreMode) {
					worldsettings.enableBonusChest();
				}

				if (this.allowCheats && !this.hardCoreMode) {
					worldsettings.enableCommands();
				}

				this.mc.launchIntegratedServer(this.saveDirName, this.worldNameField.getText().trim(),
						worldsettings);
			} else if (parGuiButton.id == 3) {
				this.toggleMoreWorldOptions();
			} else if (parGuiButton.id == 2) {
				if (this.gameMode.equals("survival")) {
					if (!this.allowCheatsWasSetByUser) {
						this.allowCheats = false;
					}

					this.hardCoreMode = false;
					this.gameMode = "hardcore";
					this.hardCoreMode = true;
					this.btnAllowCommands.enabled = false;
					this.btnBonusItems.enabled = false;
					this.updateDisplayState();
				} else if (this.gameMode.equals("hardcore")) {
					if (!this.allowCheatsWasSetByUser) {
						this.allowCheats = true;
					}

					this.hardCoreMode = false;
					this.gameMode = "creative";
					this.updateDisplayState();
					this.hardCoreMode = false;
					this.btnAllowCommands.enabled = true;
					this.btnBonusItems.enabled = true;
				} else {
					if (!this.allowCheatsWasSetByUser) {
						this.allowCheats = false;
					}

					this.gameMode = "survival";
					this.updateDisplayState();
					this.btnAllowCommands.enabled = true;
					this.btnBonusItems.enabled = true;
					this.hardCoreMode = false;
				}

				this.updateDisplayState();
			} else if (parGuiButton.id == 4) {
				this.generateStructuresEnabled = !this.generateStructuresEnabled;
				this.updateDisplayState();
			} else if (parGuiButton.id == 7) {
				this.bonusChestEnabled = !this.bonusChestEnabled;
				this.updateDisplayState();
			} else if (parGuiButton.id == 5) {
				++this.selectedIndex;
				if (this.selectedIndex >= WorldType.worldTypes.length) {
					this.selectedIndex = 0;
				}

				while (!this.canSelectCurWorldType()) {
					++this.selectedIndex;
					if (this.selectedIndex >= WorldType.worldTypes.length) {
						this.selectedIndex = 0;
					}
				}

				this.chunkProviderSettingsJson = "";
				this.updateDisplayState();
				this.showMoreWorldOptions(this.inMoreWorldOptionsDisplay);
			} else if (parGuiButton.id == 6) {
				this.allowCheatsWasSetByUser = true;
				this.allowCheats = !this.allowCheats;
				this.updateDisplayState();
			} else if (parGuiButton.id == 8) {
				if (WorldType.worldTypes[this.selectedIndex] == WorldType.FLAT) {
					this.mc.displayGuiScreen(new GuiCreateFlatWorld(this, this.chunkProviderSettingsJson));
				} else {
					this.mc.displayGuiScreen(new GuiCustomizeWorldScreen(this, this.chunkProviderSettingsJson));
				}
			}

		}
	}

	private boolean canSelectCurWorldType() {
		WorldType worldtype = WorldType.worldTypes[this.selectedIndex];
		return worldtype != null && worldtype.getCanBeCreated()
				? (worldtype == WorldType.DEBUG_WORLD ? isShiftKeyDown() : true)
				: false;
	}

	private void toggleMoreWorldOptions() {
		this.showMoreWorldOptions(!this.inMoreWorldOptionsDisplay);
	}

	private void showMoreWorldOptions(boolean parFlag) {
		this.inMoreWorldOptionsDisplay = parFlag;
		if (WorldType.worldTypes[this.selectedIndex] == WorldType.DEBUG_WORLD) {
			this.btnGameMode.visible = !this.inMoreWorldOptionsDisplay;
			this.btnGameMode.enabled = false;
			if (this.savedGameMode == null) {
				this.savedGameMode = this.gameMode;
			}

			this.gameMode = "spectator";
			this.btnMapFeatures.visible = false;
			this.btnBonusItems.visible = false;
			this.btnMapType.visible = this.inMoreWorldOptionsDisplay;
			this.btnAllowCommands.visible = false;
			this.btnCustomizeType.visible = false;
		} else {
			this.btnGameMode.visible = !this.inMoreWorldOptionsDisplay;
			this.btnGameMode.enabled = true;
			if (this.savedGameMode != null) {
				this.gameMode = this.savedGameMode;
				this.savedGameMode = null;
			}

			this.btnMapFeatures.visible = this.inMoreWorldOptionsDisplay
					&& WorldType.worldTypes[this.selectedIndex] != WorldType.CUSTOMIZED;
			this.btnBonusItems.visible = this.inMoreWorldOptionsDisplay;
			this.btnMapType.visible = this.inMoreWorldOptionsDisplay;
			this.btnAllowCommands.visible = this.inMoreWorldOptionsDisplay;
			this.btnCustomizeType.visible = this.inMoreWorldOptionsDisplay
					&& (WorldType.worldTypes[this.selectedIndex] == WorldType.FLAT
							|| WorldType.worldTypes[this.selectedIndex] == WorldType.CUSTOMIZED);
		}

		this.updateDisplayState();
		if (this.inMoreWorldOptionsDisplay) {
			this.btnMoreOptions.displayString = I18n.format("gui.done", new Object[0]);
		} else {
			this.btnMoreOptions.displayString = I18n.format("selectWorld.moreWorldOptions", new Object[0]);
		}

	}

	/**+
	 * Fired when a key is typed (except F11 which toggles full
	 * screen). This is the equivalent of
	 * KeyListener.keyTyped(KeyEvent e). Args : character (character
	 * on the key), keyCode (lwjgl Keyboard key code)
	 */
	protected void keyTyped(char parChar1, int parInt1) {
		if (this.worldNameField.isFocused() && !this.inMoreWorldOptionsDisplay) {
			this.worldNameField.textboxKeyTyped(parChar1, parInt1);
			this.worldName = this.worldNameField.getText();
		} else if (this.worldSeedField.isFocused() && this.inMoreWorldOptionsDisplay) {
			this.worldSeedField.textboxKeyTyped(parChar1, parInt1);
			this.worldSeed = this.worldSeedField.getText();
		}

		if (parInt1 == 28 || parInt1 == 156) {
			this.actionPerformed((GuiButton) this.buttonList.get(0));
		}

		((GuiButton) this.buttonList.get(0)).enabled = this.worldNameField.getText().length() > 0;
		this.calcSaveDirName();
	}

	/**+
	 * Called when the mouse is clicked. Args : mouseX, mouseY,
	 * clickedButton
	 */
	protected void mouseClicked(int parInt1, int parInt2, int parInt3) {
		super.mouseClicked(parInt1, parInt2, parInt3);
		if (this.inMoreWorldOptionsDisplay) {
			this.worldSeedField.mouseClicked(parInt1, parInt2, parInt3);
		} else {
			this.worldNameField.mouseClicked(parInt1, parInt2, parInt3);
		}

	}

	/**+
	 * Draws the screen and all the components in it. Args : mouseX,
	 * mouseY, renderPartialTicks
	 */
	public void drawScreen(int i, int j, float f) {
		this.drawDefaultBackground();
		this.drawCenteredString(this.fontRendererObj, I18n.format("selectWorld.create", new Object[0]), this.width / 2,
				20, -1);
		if (this.inMoreWorldOptionsDisplay) {
			this.drawString(this.fontRendererObj, I18n.format("selectWorld.enterSeed", new Object[0]),
					this.width / 2 - 100, 47, -6250336);
			this.drawString(this.fontRendererObj, I18n.format(
					StringUtils.isNotEmpty(worldSeedField.text) ? "createWorld.seedNote" : "selectWorld.seedInfo",
					new Object[0]), this.width / 2 - 100, 85, -6250336);
			if (this.btnMapFeatures.visible) {
				this.drawString(this.fontRendererObj, I18n.format("selectWorld.mapFeatures.info", new Object[0]),
						this.width / 2 - 150, 122, -6250336);
			}

			if (this.btnAllowCommands.visible) {
				this.drawString(this.fontRendererObj, I18n.format("selectWorld.allowCommands.info", new Object[0]),
						this.width / 2 - 150, 172, -6250336);
			}

			this.worldSeedField.drawTextBox();
			if (WorldType.worldTypes[this.selectedIndex].showWorldInfoNotice()) {
				this.fontRendererObj.drawSplitString(
						I18n.format(WorldType.worldTypes[this.selectedIndex].getTranslatedInfo(), new Object[0]),
						this.btnMapType.xPosition + 2, this.btnMapType.yPosition + 22, this.btnMapType.getButtonWidth(),
						10526880);
			}
		} else {
			this.drawString(this.fontRendererObj, I18n.format("selectWorld.enterName", new Object[0]),
					this.width / 2 - 100, 47, -6250336);
			this.drawString(this.fontRendererObj,
					I18n.format("selectWorld.resultFolder", new Object[0]) + " " + this.saveDirName,
					this.width / 2 - 100, 85, -6250336);
			this.worldNameField.drawTextBox();
			this.drawString(this.fontRendererObj, this.gameModeDesc1, this.width / 2 - 100, 137, -6250336);
			this.drawString(this.fontRendererObj, this.gameModeDesc2, this.width / 2 - 100, 149, -6250336);
		}

		super.drawScreen(i, j, f);
	}

	public void recreateFromExistingWorld(WorldInfo parWorldInfo) {
		this.worldName = I18n.format("selectWorld.newWorld.copyOf", new Object[] { parWorldInfo.getWorldName() });
		this.worldSeed = parWorldInfo.getSeed() + "";
		this.selectedIndex = parWorldInfo.getTerrainType().getWorldTypeID();
		this.chunkProviderSettingsJson = parWorldInfo.getGeneratorOptions();
		this.generateStructuresEnabled = parWorldInfo.isMapFeaturesEnabled();
		this.allowCheats = parWorldInfo.areCommandsAllowed();
		if (parWorldInfo.isHardcoreModeEnabled()) {
			this.gameMode = "hardcore";
		} else if (parWorldInfo.getGameType().isSurvivalOrAdventure()) {
			this.gameMode = "survival";
		} else if (parWorldInfo.getGameType().isCreative()) {
			this.gameMode = "creative";
		}

	}
}