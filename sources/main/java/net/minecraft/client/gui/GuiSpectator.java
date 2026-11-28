package net.minecraft.client.gui;

import static net.lax1dude.eaglercraft.v1_8.opengl.RealOpenGLEnums.*;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.spectator.ISpectatorMenuObject;
import net.minecraft.client.gui.spectator.ISpectatorMenuRecipient;
import net.minecraft.client.gui.spectator.SpectatorMenu;
import net.minecraft.client.gui.spectator.categories.SpectatorDetails;
import net.minecraft.client.renderer.RenderHelper;
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
public class GuiSpectator extends Gui implements ISpectatorMenuRecipient {
	private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
	public static final ResourceLocation SPECTATOR_WIDGETS = new ResourceLocation("textures/gui/spectator_widgets.png");
	private final Minecraft mc;
	private long lastSelectionTime;
	private SpectatorMenu menu;

	public GuiSpectator(Minecraft mcIn) {
		this.mc = mcIn;
	}

	public void onHotbarSelected(int parInt1) {
		this.lastSelectionTime = Minecraft.getSystemTime();
		if (this.menu != null) {
			this.menu.selectSlot(parInt1);
		} else {
			this.menu = new SpectatorMenu(this);
		}

	}

	private float getHotbarAlpha() {
		long i = this.lastSelectionTime - Minecraft.getSystemTime() + 5000L;
		return MathHelper.clamp_float((float) i / 2000.0F, 0.0F, 1.0F);
	}

	public void renderTooltip(ScaledResolution parScaledResolution, float parFloat1) {
		if (this.menu != null) {
			float f = this.getHotbarAlpha();
			if (f <= 0.0F) {
				this.menu.exit();
			} else {
				int i = parScaledResolution.getScaledWidth() / 2;
				float f1 = this.zLevel;
				this.zLevel = -90.0F;
				float f2 = (float) parScaledResolution.getScaledHeight() - 22.0F * f;
				SpectatorDetails spectatordetails = this.menu.getCurrentPage();
				this.renderPage(parScaledResolution, f, i, f2, spectatordetails);
				this.zLevel = f1;
			}
		}
	}

	protected void renderPage(ScaledResolution parScaledResolution, float parFloat1, int parInt1, float parFloat2,
			SpectatorDetails parSpectatorDetails) {
		GlStateManager.enableRescaleNormal();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0);
		GlStateManager.color(1.0F, 1.0F, 1.0F, parFloat1);
		this.mc.getTextureManager().bindTexture(WIDGETS);
		this.drawTexturedModalRect((float) (parInt1 - 91), parFloat2, 0, 0, 182, 22);
		if (parSpectatorDetails.getSelectedSlot() >= 0) {
			this.drawTexturedModalRect((float) (parInt1 - 91 - 1 + parSpectatorDetails.getSelectedSlot() * 20),
					parFloat2 - 1.0F, 0, 22, 24, 22);
		}

		RenderHelper.enableGUIStandardItemLighting();

		for (int i = 0; i < 9; ++i) {
			this.renderSlot(i, parScaledResolution.getScaledWidth() / 2 - 90 + i * 20 + 2, parFloat2 + 3.0F,
					parFloat1, parSpectatorDetails.getObject(i));
		}

		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableRescaleNormal();
		GlStateManager.disableBlend();
	}

	private void renderSlot(int parInt1, int parInt2, float parFloat1, float parFloat2,
			ISpectatorMenuObject parISpectatorMenuObject) {
		this.mc.getTextureManager().bindTexture(SPECTATOR_WIDGETS);
		if (parISpectatorMenuObject != SpectatorMenu.EMPTY_SLOT) {
			int i = (int) (parFloat2 * 255.0F);
			GlStateManager.pushMatrix();
			GlStateManager.translate((float) parInt2, parFloat1, 0.0F);
			float f = parISpectatorMenuObject.isEnabled() ? 1.0F : 0.25F;
			GlStateManager.color(f, f, f, parFloat2);
			parISpectatorMenuObject.renderIcon(f, i);
			GlStateManager.popMatrix();
			String s = String.valueOf(GameSettings
					.getKeyDisplayString(this.mc.gameSettings.keyBindsHotbar[parInt1].getKeyCode()));
			if (i > 3 && parISpectatorMenuObject.isEnabled()) {
				this.mc.fontRendererObj.drawStringWithShadow(s,
						(float) (parInt2 + 19 - 2 - this.mc.fontRendererObj.getStringWidth(s)),
						parFloat1 + 6.0F + 3.0F, 16777215 + (i << 24));
			}
		}

	}

	public void renderSelectedItem(ScaledResolution parScaledResolution) {
		int i = (int) (this.getHotbarAlpha() * 255.0F);
		if (i > 3 && this.menu != null) {
			ISpectatorMenuObject ispectatormenuobject = this.menu.getSelectedItem();
			String s = ispectatormenuobject != SpectatorMenu.EMPTY_SLOT
					? ispectatormenuobject.getSpectatorName().getFormattedText()
					: this.menu.getSelectedCategory().getPrompt().getFormattedText();
			if (s != null) {
				int j = (parScaledResolution.getScaledWidth() - this.mc.fontRendererObj.getStringWidth(s))
						/ 2;
				int k = parScaledResolution.getScaledHeight() - 35;
				GlStateManager.pushMatrix();
				GlStateManager.enableBlend();
				GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0);
				this.mc.fontRendererObj.drawStringWithShadow(s, (float) j, (float) k, 16777215 + (i << 24));
				GlStateManager.disableBlend();
				GlStateManager.popMatrix();
			}
		}

	}

	public void onSpectatorMenuClosed(SpectatorMenu parSpectatorMenu) {
		this.menu = null;
		this.lastSelectionTime = 0L;
	}

	public boolean isMenuActive() {
		return this.menu != null;
	}

	public void onMouseScroll(int parInt1) {
		int i;
		for (i = this.menu.getSelectedSlot() + parInt1; i >= 0 && i <= 8
				&& (this.menu.getItem(i) == SpectatorMenu.EMPTY_SLOT
						|| !this.menu.getItem(i).isEnabled()); i += parInt1) {
			;
		}

		if (i >= 0 && i <= 8) {
			this.menu.selectSlot(i);
			this.lastSelectionTime = Minecraft.getSystemTime();
		}

	}

	public void onMiddleClick() {
		this.lastSelectionTime = Minecraft.getSystemTime();
		if (this.isMenuActive()) {
			int i = this.menu.getSelectedSlot();
			if (i != -1) {
				this.menu.selectSlot(i);
			}
		} else {
			this.menu = new SpectatorMenu(this);
		}

	}
}