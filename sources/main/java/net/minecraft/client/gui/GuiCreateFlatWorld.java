package net.minecraft.client.gui;

import java.io.IOException;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.gen.FlatGeneratorInfo;
import net.minecraft.world.gen.FlatLayerInfo;

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
public class GuiCreateFlatWorld extends GuiScreen {
	private final GuiCreateWorld createWorldGui;
	private FlatGeneratorInfo theFlatGeneratorInfo = FlatGeneratorInfo.getDefaultFlatGenerator();
	private String flatWorldTitle;
	private String materialText;
	private String heightText;
	private GuiCreateFlatWorld.Details createFlatWorldListSlotGui;
	private GuiButton addLayerButton;
	private GuiButton editLayerButton;
	private GuiButton removeLayerButton;

	public GuiCreateFlatWorld(GuiCreateWorld createWorldGuiIn, String parString1) {
		this.createWorldGui = createWorldGuiIn;
		this.setPreset(parString1);
	}

	public String getPreset() {
		return this.theFlatGeneratorInfo.toString();
	}

	public void setPreset(String parString1) {
		this.theFlatGeneratorInfo = FlatGeneratorInfo.createFlatGeneratorFromString(parString1);
	}

	/**+
	 * Adds the buttons (and other controls) to the screen in
	 * question. Called when the GUI is displayed and when the
	 * window resizes, the buttonList is cleared beforehand.
	 */
	public void initGui() {
		this.buttonList.clear();
		this.flatWorldTitle = I18n.format("createWorld.customize.flat.title", new Object[0]);
		this.materialText = I18n.format("createWorld.customize.flat.tile", new Object[0]);
		this.heightText = I18n.format("createWorld.customize.flat.height", new Object[0]);
		this.createFlatWorldListSlotGui = new GuiCreateFlatWorld.Details();
		this.buttonList.add(this.addLayerButton = new GuiButton(2, this.width / 2 - 154, this.height - 52, 100, 20,
				I18n.format("createWorld.customize.flat.addLayer", new Object[0]) + " (NYI)"));
		this.buttonList.add(this.editLayerButton = new GuiButton(3, this.width / 2 - 50, this.height - 52, 100, 20,
				I18n.format("createWorld.customize.flat.editLayer", new Object[0]) + " (NYI)"));
		this.buttonList.add(this.removeLayerButton = new GuiButton(4, this.width / 2 - 155, this.height - 52, 150, 20,
				I18n.format("createWorld.customize.flat.removeLayer", new Object[0])));
		this.buttonList.add(new GuiButton(0, this.width / 2 - 155, this.height - 28, 150, 20,
				I18n.format("gui.done", new Object[0])));
		this.buttonList.add(new GuiButton(5, this.width / 2 + 5, this.height - 52, 150, 20,
				I18n.format("createWorld.customize.presets", new Object[0])));
		this.buttonList.add(new GuiButton(1, this.width / 2 + 5, this.height - 28, 150, 20,
				I18n.format("gui.cancel", new Object[0])));
		this.addLayerButton.visible = this.editLayerButton.visible = false;
		this.theFlatGeneratorInfo.updateLayers();
		this.onLayersChanged();
	}

	/**+
	 * Handles mouse input.
	 */
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		this.createFlatWorldListSlotGui.handleMouseInput();
	}

	/**+
	 * Called by the controls from the buttonList when activated.
	 * (Mouse pressed for buttons)
	 */
	protected void actionPerformed(GuiButton parGuiButton) {
		int i = this.theFlatGeneratorInfo.getFlatLayers().size() - this.createFlatWorldListSlotGui.selectedLayer - 1;
		if (parGuiButton.id == 1) {
			this.mc.displayGuiScreen(this.createWorldGui);
		} else if (parGuiButton.id == 0) {
			this.createWorldGui.chunkProviderSettingsJson = this.getPreset();
			this.mc.displayGuiScreen(this.createWorldGui);
		} else if (parGuiButton.id == 5) {
			this.mc.displayGuiScreen(new GuiFlatPresets(this));
		} else if (parGuiButton.id == 4 && this.hasSelectedLayer()) {
			this.theFlatGeneratorInfo.getFlatLayers().remove(i);
			this.createFlatWorldListSlotGui.selectedLayer = Math.min(this.createFlatWorldListSlotGui.selectedLayer,
					this.theFlatGeneratorInfo.getFlatLayers().size() - 1);
		}

		this.theFlatGeneratorInfo.updateLayers();
		this.onLayersChanged();
	}

	public void onLayersChanged() {
		boolean flag = this.hasSelectedLayer();
		this.removeLayerButton.enabled = flag;
		this.editLayerButton.enabled = flag;
		this.editLayerButton.enabled = false;
		this.addLayerButton.enabled = false;
	}

	private boolean hasSelectedLayer() {
		return this.createFlatWorldListSlotGui.selectedLayer > -1
				&& this.createFlatWorldListSlotGui.selectedLayer < this.theFlatGeneratorInfo.getFlatLayers().size();
	}

	/**+
	 * Draws the screen and all the components in it. Args : mouseX,
	 * mouseY, renderPartialTicks
	 */
	public void drawScreen(int i, int j, float f) {
		this.drawDefaultBackground();
		this.createFlatWorldListSlotGui.drawScreen(i, j, f);
		this.drawCenteredString(this.fontRendererObj, this.flatWorldTitle, this.width / 2, 8, 16777215);
		int k = this.width / 2 - 92 - 16;
		this.drawString(this.fontRendererObj, this.materialText, k, 32, 16777215);
		this.drawString(this.fontRendererObj, this.heightText,
				k + 2 + 213 - this.fontRendererObj.getStringWidth(this.heightText), 32, 16777215);
		super.drawScreen(i, j, f);
	}

	class Details extends GuiSlot {
		public int selectedLayer = -1;

		public Details() {
			super(GuiCreateFlatWorld.this.mc, GuiCreateFlatWorld.this.width, GuiCreateFlatWorld.this.height, 43,
					GuiCreateFlatWorld.this.height - 60, 24);
		}

		private void drawItem(int parInt1, int parInt2, ItemStack parItemStack) {
			this.drawItemBackground(parInt1 + 1, parInt2 + 1);
			GlStateManager.enableRescaleNormal();
			if (parItemStack != null && parItemStack.getItem() != null) {
				RenderHelper.enableGUIStandardItemLighting();
				GuiCreateFlatWorld.this.itemRender.renderItemIntoGUI(parItemStack, parInt1 + 2, parInt2 + 2);
				RenderHelper.disableStandardItemLighting();
			}

			GlStateManager.disableRescaleNormal();
		}

		private void drawItemBackground(int parInt1, int parInt2) {
			this.GuiCreateFlatWorldDrawItemBackground(parInt1, parInt2, 0, 0);
		}

		private void GuiCreateFlatWorldDrawItemBackground(int parInt1, int parInt2, int parInt3, int parInt4) {
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			this.mc.getTextureManager().bindTexture(Gui.statIcons);
			float f = 0.0078125F;
			float f1 = 0.0078125F;
			boolean flag = true;
			boolean flag1 = true;
			Tessellator tessellator = Tessellator.getInstance();
			WorldRenderer worldrenderer = tessellator.getWorldRenderer();
			worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
			worldrenderer.pos((double) (parInt1 + 0), (double) (parInt2 + 18), (double) GuiCreateFlatWorld.this.zLevel)
					.tex((double) ((float) (parInt3 + 0) * 0.0078125F), (double) ((float) (parInt4 + 18) * 0.0078125F))
					.endVertex();
			worldrenderer.pos((double) (parInt1 + 18), (double) (parInt2 + 18), (double) GuiCreateFlatWorld.this.zLevel)
					.tex((double) ((float) (parInt3 + 18) * 0.0078125F), (double) ((float) (parInt4 + 18) * 0.0078125F))
					.endVertex();
			worldrenderer.pos((double) (parInt1 + 18), (double) (parInt2 + 0), (double) GuiCreateFlatWorld.this.zLevel)
					.tex((double) ((float) (parInt3 + 18) * 0.0078125F), (double) ((float) (parInt4 + 0) * 0.0078125F))
					.endVertex();
			worldrenderer.pos((double) (parInt1 + 0), (double) (parInt2 + 0), (double) GuiCreateFlatWorld.this.zLevel)
					.tex((double) ((float) (parInt3 + 0) * 0.0078125F), (double) ((float) (parInt4 + 0) * 0.0078125F))
					.endVertex();
			tessellator.draw();
		}

		protected int getSize() {
			return GuiCreateFlatWorld.this.theFlatGeneratorInfo.getFlatLayers().size();
		}

		protected void elementClicked(int i, boolean var2, int var3, int var4) {
			this.selectedLayer = i;
			GuiCreateFlatWorld.this.onLayersChanged();
		}

		protected boolean isSelected(int i) {
			return i == this.selectedLayer;
		}

		protected void drawBackground() {
		}

		protected void drawSlot(int i, int j, int k, int var4, int var5, int var6) {
			FlatLayerInfo flatlayerinfo = (FlatLayerInfo) GuiCreateFlatWorld.this.theFlatGeneratorInfo.getFlatLayers()
					.get(GuiCreateFlatWorld.this.theFlatGeneratorInfo.getFlatLayers().size() - i - 1);
			IBlockState iblockstate = flatlayerinfo.getLayerMaterial();
			Block block = iblockstate.getBlock();
			Item item = Item.getItemFromBlock(block);
			ItemStack itemstack = block != Blocks.air && item != null
					? new ItemStack(item, 1, block.getMetaFromState(iblockstate))
					: null;
			String s = itemstack == null ? "Air" : item.getItemStackDisplayName(itemstack);
			if (item == null) {
				if (block != Blocks.water && block != Blocks.flowing_water) {
					if (block == Blocks.lava || block == Blocks.flowing_lava) {
						item = Items.lava_bucket;
					}
				} else {
					item = Items.water_bucket;
				}

				if (item != null) {
					itemstack = new ItemStack(item, 1, block.getMetaFromState(iblockstate));
					s = block.getLocalizedName();
				}
			}

			this.drawItem(j, k, itemstack);
			GuiCreateFlatWorld.this.fontRendererObj.drawString(s, j + 18 + 5, k + 3, 16777215);
			String s1;
			if (i == 0) {
				s1 = I18n.format("createWorld.customize.flat.layer.top",
						new Object[] { Integer.valueOf(flatlayerinfo.getLayerCount()) });
			} else if (i == GuiCreateFlatWorld.this.theFlatGeneratorInfo.getFlatLayers().size() - 1) {
				s1 = I18n.format("createWorld.customize.flat.layer.bottom",
						new Object[] { Integer.valueOf(flatlayerinfo.getLayerCount()) });
			} else {
				s1 = I18n.format("createWorld.customize.flat.layer",
						new Object[] { Integer.valueOf(flatlayerinfo.getLayerCount()) });
			}

			GuiCreateFlatWorld.this.fontRendererObj.drawString(s1,
					j + 2 + 213 - GuiCreateFlatWorld.this.fontRendererObj.getStringWidth(s1), k + 3, 16777215);
		}

		protected int getScrollBarX() {
			return this.width - 70;
		}
	}
}