package net.minecraft.client.gui;

import java.util.List;

import net.lax1dude.eaglercraft.v1_8.PerformancePresets;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.EnumChatFormatting;

/**+
 * This portion of EaglercraftX contains deobfuscated Minecraft 1.8 source code.
 * 
 * Minecraft 1.8.8 bytecode is (c) 2015 Mojang AB. "Do not distribute!"
 * Mod Coder Pack v9.18 deobfuscation configs are (c) Copyright by the MCP Team
 * 
 * EaglercraftX 1.8 patch files (c) 2022-2026 lax1dude, ayunami2000, Kuack contributors. All Rights Reserved.
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
public class GuiPerformancePresets extends GuiScreen {
	private static final int PRESET_BUTTON_BASE_ID = 10;

	private final GuiScreen parentGuiScreen;
	private final GameSettings guiGameSettings;
	private final GuiButton[] presetButtons = new GuiButton[PerformancePresets.PRESET_COUNT];
	protected String screenTitle = "Performance Presets";

	public GuiPerformancePresets(GuiScreen parentScreenIn, GameSettings gameSettingsIn) {
		this.parentGuiScreen = parentScreenIn;
		this.guiGameSettings = gameSettingsIn;
	}

	public void initGui() {
		this.screenTitle = I18n.format("options.performancePresets.title");
		this.buttonList.clear();

		int startY = 44;
		int rowStep = 24;
		for (int i = 0; i < PerformancePresets.PRESET_COUNT; ++i) {
			int x;
			int y = startY + (i >> 1) * rowStep;
			int w = 150;
			if (i == PerformancePresets.PRESET_COUNT - 1 && (PerformancePresets.PRESET_COUNT % 2) == 1) {
				x = this.width / 2 - 100;
				w = 200;
			} else {
				x = this.width / 2 - 155 + (i % 2) * 160;
			}
			GuiButton btn = new GuiButton(PRESET_BUTTON_BASE_ID + i, x, y, w, 20, "");
			this.presetButtons[i] = btn;
			this.buttonList.add(btn);
		}

		this.updatePresetButtons();
		this.buttonList.add(new GuiButton(200, this.width / 2 - 100, this.height - 27, I18n.format("gui.done")));
	}

	private void updatePresetButtons() {
		int active = this.guiGameSettings.performancePreset;
		for (int i = 0; i < PerformancePresets.PRESET_COUNT; ++i) {
			if (this.presetButtons[i] != null) {
				String label = PerformancePresets.getDisplayName(i);
				if (i == active) {
					this.presetButtons[i].displayString = EnumChatFormatting.GREEN + "> " + label + " <";
				} else {
					this.presetButtons[i].displayString = label;
				}
			}
		}
	}

	protected void actionPerformed(GuiButton parGuiButton) {
		if (!parGuiButton.enabled) {
			return;
		}
		if (parGuiButton.id == 200) {
			this.mc.gameSettings.saveOptions();
			this.mc.displayGuiScreen(this.parentGuiScreen);
		} else if (parGuiButton.id >= PRESET_BUTTON_BASE_ID
				&& parGuiButton.id < PRESET_BUTTON_BASE_ID + PerformancePresets.PRESET_COUNT) {
			int presetId = parGuiButton.id - PRESET_BUTTON_BASE_ID;
			this.guiGameSettings.applyPerformancePreset(presetId);
			this.updatePresetButtons();
		}
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		this.drawCenteredString(this.fontRendererObj, this.screenTitle, this.width / 2, 12, 16777215);

		String currentText = I18n.format("options.performancePresets.current",
				PerformancePresets.getDisplayName(this.guiGameSettings.performancePreset));
		this.drawCenteredString(this.fontRendererObj, EnumChatFormatting.GRAY + currentText, this.width / 2, 26,
				10526880);

		super.drawScreen(mouseX, mouseY, partialTicks);

		int previewPreset = this.guiGameSettings.performancePreset;
		for (int i = 0; i < PerformancePresets.PRESET_COUNT; ++i) {
			if (this.presetButtons[i] != null && this.presetButtons[i].isMouseOver()) {
				previewPreset = i;
				break;
			}
		}

		int boxLeft = this.width / 2 - 155;
		int boxRight = this.width / 2 + 155;
		int boxTop = 144;
		int boxBottom = this.height - 34;
		if (boxBottom - boxTop >= 46) {
			drawRect(boxLeft, boxTop, boxRight, boxBottom, -1073741824);
			this.drawHorizontalLine(boxLeft, boxRight - 1, boxTop, -6250336);
			this.drawHorizontalLine(boxLeft, boxRight - 1, boxBottom - 1, -6250336);
			this.drawVerticalLine(boxLeft, boxTop, boxBottom - 1, -6250336);
			this.drawVerticalLine(boxRight - 1, boxTop, boxBottom - 1, -6250336);

			int textX = boxLeft + 6;
			int textY = boxTop + 5;
			int wrapWidth = (boxRight - boxLeft) - 12;

			String header = EnumChatFormatting.YELLOW + PerformancePresets.getDisplayName(previewPreset)
					+ EnumChatFormatting.GRAY + " - " + EnumChatFormatting.AQUA
					+ PerformancePresets.getTierLabel(previewPreset);
			this.drawString(this.fontRendererObj, header, textX, textY, 16777215);
			textY += 12;

			List<String> lines = this.fontRendererObj.listFormattedStringToWidth(
					PerformancePresets.getDescription(previewPreset), wrapWidth);
			for (int i = 0; i < lines.size() && textY + 10 <= boxBottom - 13; ++i) {
				this.drawString(this.fontRendererObj, lines.get(i), textX, textY, 13421772);
				textY += 10;
			}

			if (PerformancePresets.isValidPreset(previewPreset) && textY + 10 <= boxBottom - 2) {
				String summary = this.buildPresetSummary(previewPreset);
				this.drawString(this.fontRendererObj, EnumChatFormatting.GRAY + summary, textX, boxBottom - 12,
						10526880);
			}
		}
	}

	private String buildPresetSummary(int presetId) {
		int chunks = PerformancePresets.getRenderDistance(presetId);
		String graphics = I18n.format(
				PerformancePresets.isFancyGraphics(presetId) ? "options.graphics.fancy" : "options.graphics.fast");
		int ao = PerformancePresets.getAmbientOcclusion(presetId);
		String aoLabel = I18n.format(ao == 0 ? "options.ao.off" : (ao == 1 ? "options.ao.min" : "options.ao.max"));
		int mipmaps = PerformancePresets.getMipmapLevels(presetId);
		int maxParticles = PerformancePresets.getMaxParticles(presetId);
		String particlesLabel = maxParticles <= 0 ? I18n.format("options.off") : String.valueOf(maxParticles);
		return I18n.format("options.performancePresets.summary", Integer.valueOf(chunks), graphics, aoLabel,
				Integer.valueOf(mipmaps), particlesLabel);
	}
}
