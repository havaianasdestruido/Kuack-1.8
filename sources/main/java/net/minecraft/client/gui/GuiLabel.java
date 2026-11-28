package net.minecraft.client.gui;

import static net.lax1dude.eaglercraft.v1_8.opengl.RealOpenGLEnums.*;

import java.util.List;

import com.google.common.collect.Lists;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

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
public class GuiLabel extends Gui {
	protected int width = 200;
	protected int height = 20;
	public int x;
	public int y;
	private List<String> labels;
	public int id;
	private boolean centered;
	public boolean visible = true;
	private boolean labelBgEnabled;
	private int textColor;
	private int backColor;
	private int ulColor;
	private int brColor;
	private FontRenderer fontRenderer;
	private int border;

	public GuiLabel(FontRenderer fontRendererObj, int parInt1, int parInt2, int parInt3, int parInt4, int parInt5,
			int parInt6) {
		this.fontRenderer = fontRendererObj;
		this.id = parInt1;
		this.x = parInt2;
		this.y = parInt3;
		this.width = parInt4;
		this.height = parInt5;
		this.labels = Lists.newArrayList();
		this.centered = false;
		this.labelBgEnabled = false;
		this.textColor = parInt6;
		this.backColor = -1;
		this.ulColor = -1;
		this.brColor = -1;
		this.border = 0;
	}

	public void addLine(String parString1) {
		this.labels.add(I18n.format(parString1, new Object[0]));
	}

	/**+
	 * Sets the Label to be centered
	 */
	public GuiLabel setCentered() {
		this.centered = true;
		return this;
	}

	public void drawLabel(Minecraft mc, int mouseX, int mouseY) {
		if (this.visible) {
			GlStateManager.enableBlend();
			GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0);
			this.drawLabelBackground(mc, mouseX, mouseY);
			int i = this.y + this.height / 2 + this.border / 2;
			int j = i - this.labels.size() * 10 / 2;

			for (int k = 0; k < this.labels.size(); ++k) {
				if (this.centered) {
					this.drawCenteredString(this.fontRenderer, (String) this.labels.get(k),
							this.x + this.width / 2, j + k * 10, this.textColor);
				} else {
					this.drawString(this.fontRenderer, (String) this.labels.get(k), this.x,
							j + k * 10, this.textColor);
				}
			}

		}
	}

	protected void drawLabelBackground(Minecraft mcIn, int parInt1, int parInt2) {
		if (this.labelBgEnabled) {
			int i = this.width + this.border * 2;
			int j = this.height + this.border * 2;
			int k = this.x - this.border;
			int l = this.y - this.border;
			drawRect(k, l, k + i, l + j, this.backColor);
			this.drawHorizontalLine(k, k + i, l, this.ulColor);
			this.drawHorizontalLine(k, k + i, l + j, this.brColor);
			this.drawVerticalLine(k, l, l + j, this.ulColor);
			this.drawVerticalLine(k + i, l, l + j, this.brColor);
		}

	}
}