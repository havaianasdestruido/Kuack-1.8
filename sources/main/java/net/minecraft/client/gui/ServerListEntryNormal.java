package net.minecraft.client.gui;

import static net.lax1dude.eaglercraft.v1_8.opengl.RealOpenGLEnums.*;

import java.util.List;

import net.lax1dude.eaglercraft.v1_8.log4j.LogManager;
import net.lax1dude.eaglercraft.v1_8.log4j.Logger;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
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
public class ServerListEntryNormal implements GuiListExtended.IGuiListEntry {
	private static final Logger logger = LogManager.getLogger();
	private static final ResourceLocation UNKNOWN_SERVER = new ResourceLocation("textures/misc/unknown_server.png");
	private static final ResourceLocation SERVER_SELECTION_BUTTONS = new ResourceLocation(
			"textures/gui/server_selection.png");
	private final GuiMultiplayer owner;
	private final Minecraft mc;
	private final ServerData server;
	private String lastIconB64;
	long lastClickTime;

	protected ServerListEntryNormal(GuiMultiplayer parGuiMultiplayer, ServerData parServerData) {
		this.owner = parGuiMultiplayer;
		this.server = parServerData;
		this.mc = Minecraft.getMinecraft();
	}

	public void drawEntry(int i, int j, int k, int l, int var5, int i1, int j1, boolean flag) {
		if (!this.server.pinged) {
			this.server.pinged = true;
			this.server.pingToServer = -2L;
			this.server.serverMOTD = "";
			this.server.populationInfo = "";
		}

		boolean flag1 = this.server.version > 47;
		boolean flag2 = this.server.version < 47;
		boolean flag3 = flag1 || flag2;
		this.mc.fontRendererObj.drawString(this.server.serverName, j + 32 + 3, k + 1, 16777215);
		List list = this.mc.fontRendererObj.listFormattedStringToWidth(this.server.serverMOTD, l - 32 - 2);

		for (int k1 = 0; k1 < 2; ++k1) {
			if (k1 < list.size()) {
				this.mc.fontRendererObj.drawString((String) list.get(k1), j + 32 + 3,
						k + 12 + this.mc.fontRendererObj.FONT_HEIGHT * k1, 8421504);
			} else if (k1 == 1) {
				this.mc.fontRendererObj.drawString(
						this.server.hideAddress ? I18n.format("selectServer.hiddenAddress", new Object[0])
								: this.server.serverIP,
						j + 32 + 3, k + 12 + this.mc.fontRendererObj.FONT_HEIGHT * k1 + k1, 0x444444);
			}
		}

		String s2 = flag3 ? EnumChatFormatting.DARK_RED + this.server.gameVersion
				: this.server.populationInfo;
		int l1 = this.mc.fontRendererObj.getStringWidth(s2);
		this.mc.fontRendererObj.drawString(s2, j + l - l1 - 15 - 2, k + 1, 8421504);
		byte b0 = 0;
		String s = null;
		int i2;
		String s1;
		if (flag3) {
			i2 = 5;
			s1 = flag1 ? "Client out of date!" : "Server out of date!";
			s = this.server.playerList;
		} else if (this.server.pinged && this.server.pingToServer != -2L) {
			if (this.server.pingToServer < 0L) {
				i2 = 5;
			} else if (this.server.pingToServer < 150L) {
				i2 = 0;
			} else if (this.server.pingToServer < 300L) {
				i2 = 1;
			} else if (this.server.pingToServer < 600L) {
				i2 = 2;
			} else if (this.server.pingToServer < 1000L) {
				i2 = 3;
			} else {
				i2 = 4;
			}

			if (this.server.pingToServer < 0L) {
				s1 = "(no connection)";
			} else {
				s1 = this.server.pingToServer + "ms";
				s = this.server.playerList;
			}
		} else {
			b0 = 1;
			i2 = (int) (Minecraft.getSystemTime() / 100L + (long) (i * 2) & 7L);
			if (i2 > 4) {
				i2 = 8 - i2;
			}

			s1 = "Pinging...";
		}

		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		this.mc.getTextureManager().bindTexture(Gui.icons);
		Gui.drawModalRectWithCustomSizedTexture(j + l - 15, k, (float) (b0 * 10), (float) (176 + i2 * 8), 10, 8, 256.0F,
				256.0F);
		if (this.mc.gameSettings.touchscreen || flag) {
			GlStateManager.enableShaderBlendAdd();
			GlStateManager.setShaderBlendSrc(0.6f, 0.6f, 0.6f, 1.0f);
			GlStateManager.setShaderBlendAdd(0.3f, 0.3f, 0.3f, 0.0f);
		}
		if (server.iconTextureObject != null) {
			this.drawTextureAt(j, k, server.iconResourceLocation);
		} else {
			this.drawTextureAt(j, k, UNKNOWN_SERVER);
		}
		if (this.mc.gameSettings.touchscreen || flag) {
			GlStateManager.disableShaderBlendAdd();
		}

		int j2 = i1 - j;
		int k2 = j1 - k;
		if (j2 >= l - 15 && j2 <= l - 5 && k2 >= 0 && k2 <= 8) {
			this.owner.setHoveringText(s1);
		} else if (j2 >= l - l1 - 15 - 2 && j2 <= l - 15 - 2 && k2 >= 0 && k2 <= 8) {
			this.owner.setHoveringText(s);
		}

		if (this.mc.gameSettings.touchscreen || flag) {
			this.mc.getTextureManager().bindTexture(SERVER_SELECTION_BUTTONS);
			// Gui.drawRect(j, k, j + 32, k + 32, -1601138544);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			int l2 = i1 - j;
			int i3 = j1 - k;
			if (this.canJoin()) {
				if (l2 < 32 && l2 > 16) {
					Gui.drawModalRectWithCustomSizedTexture(j, k, 0.0F, 32.0F, 32, 32, 256.0F, 256.0F);
				} else {
					Gui.drawModalRectWithCustomSizedTexture(j, k, 0.0F, 0.0F, 32, 32, 256.0F, 256.0F);
				}
			}

			if (this.owner.canMoveUp(this, i)) {
				if (l2 < 16 && i3 < 16) {
					Gui.drawModalRectWithCustomSizedTexture(j, k, 96.0F, 32.0F, 32, 32, 256.0F, 256.0F);
				} else {
					Gui.drawModalRectWithCustomSizedTexture(j, k, 96.0F, 0.0F, 32, 32, 256.0F, 256.0F);
				}
			}

			if (this.owner.canMoveDown(this, i)) {
				if (l2 < 16 && i3 > 16) {
					Gui.drawModalRectWithCustomSizedTexture(j, k, 64.0F, 32.0F, 32, 32, 256.0F, 256.0F);
				} else {
					Gui.drawModalRectWithCustomSizedTexture(j, k, 64.0F, 0.0F, 32, 32, 256.0F, 256.0F);
				}
			}
		}

	}

	protected void drawTextureAt(int parInt1, int parInt2, ResourceLocation parResourceLocation) {
		this.mc.getTextureManager().bindTexture(parResourceLocation);
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		Gui.drawModalRectWithCustomSizedTexture(parInt1, parInt2, 0.0F, 0.0F, 32, 32, 32.0F, 32.0F);
		GlStateManager.disableBlend();
	}

	private boolean canJoin() {
		return true;
	}

	/**+
	 * Returns true if the mouse has been pressed on this control.
	 */
	public boolean mousePressed(int i, int var2, int var3, int var4, int j, int k) {
		if (j <= 32) {
			if (j < 32 && j > 16 && this.canJoin()) {
				this.owner.selectServer(i);
				this.owner.connectToSelected();
				return true;
			}

			if (j < 16 && k < 16 && this.owner.canMoveUp(this, i)) {
				this.owner.moveServerUp(this, i, GuiScreen.isShiftKeyDown());
				return true;
			}

			if (j < 16 && k > 16 && this.owner.canMoveDown(this, i)) {
				this.owner.moveServerDown(this, i, GuiScreen.isShiftKeyDown());
				return true;
			}
		}

		this.owner.selectServer(i);
		if (Minecraft.getSystemTime() - this.lastClickTime < 250L) {
			this.owner.connectToSelected();
		}

		this.lastClickTime = Minecraft.getSystemTime();
		return false;
	}

	public void setSelected(int var1, int var2, int var3) {
	}

	/**+
	 * Fired when the mouse button is released. Arguments: index, x,
	 * y, mouseEvent, relativeX, relativeY
	 */
	public void mouseReleased(int var1, int var2, int var3, int var4, int var5, int var6) {
	}

	public ServerData getServerData() {
		return this.server;
	}
}