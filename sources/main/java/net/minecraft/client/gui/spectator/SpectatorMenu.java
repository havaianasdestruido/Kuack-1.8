package net.minecraft.client.gui.spectator;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiSpectator;
import net.minecraft.client.gui.spectator.categories.SpectatorDetails;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

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
public class SpectatorMenu {
	private static final ISpectatorMenuObject CLOSE_ITEM = new SpectatorMenu.EndSpectatorObject();
	private static final ISpectatorMenuObject SCROLL_LEFT = new SpectatorMenu.MoveMenuObject(-1, true);
	private static final ISpectatorMenuObject SCROLL_RIGHT_ENABLED = new SpectatorMenu.MoveMenuObject(1, true);
	private static final ISpectatorMenuObject SCROLL_RIGHT_DISABLED = new SpectatorMenu.MoveMenuObject(1, false);
	public static final ISpectatorMenuObject EMPTY_SLOT = new ISpectatorMenuObject() {
		public void selectItem(SpectatorMenu var1) {
		}

		public IChatComponent getSpectatorName() {
			return new ChatComponentText("");
		}

		public void renderIcon(float var1, int var2) {
		}

		public boolean isEnabled() {
			return false;
		}
	};
	private final ISpectatorMenuRecipient listener;
	private final List<SpectatorDetails> previousCategories = Lists.newArrayList();
	private ISpectatorMenuView category = new BaseSpectatorGroup();
	private int selectedSlot = -1;
	private int page;

	public SpectatorMenu(ISpectatorMenuRecipient parISpectatorMenuRecipient) {
		this.listener = parISpectatorMenuRecipient;
	}

	public ISpectatorMenuObject getItem(int parInt1) {
		int i = parInt1 + this.page * 6;
		return this.page > 0 && parInt1 == 0 ? SCROLL_LEFT
				: (parInt1 == 7 ? (i < this.category.getItems().size() ? SCROLL_RIGHT_ENABLED : SCROLL_RIGHT_DISABLED)
						: (parInt1 == 8 ? CLOSE_ITEM
								: (i >= 0 && i < this.category.getItems().size()
										? (ISpectatorMenuObject) Objects.firstNonNull(
												this.category.getItems().get(i), EMPTY_SLOT)
										: EMPTY_SLOT)));
	}

	public List<ISpectatorMenuObject> getItems() {
		ArrayList arraylist = Lists.newArrayList();

		for (int i = 0; i <= 8; ++i) {
			arraylist.add(this.getItem(i));
		}

		return arraylist;
	}

	public ISpectatorMenuObject getSelectedItem() {
		return this.getItem(this.selectedSlot);
	}

	public ISpectatorMenuView getSelectedCategory() {
		return this.category;
	}

	public void selectSlot(int parInt1) {
		ISpectatorMenuObject ispectatormenuobject = this.getItem(parInt1);
		if (ispectatormenuobject != EMPTY_SLOT) {
			if (this.selectedSlot == parInt1 && ispectatormenuobject.isEnabled()) {
				ispectatormenuobject.selectItem(this);
			} else {
				this.selectedSlot = parInt1;
			}
		}

	}

	public void exit() {
		this.listener.onSpectatorMenuClosed(this);
	}

	public int getSelectedSlot() {
		return this.selectedSlot;
	}

	public void selectCategory(ISpectatorMenuView parISpectatorMenuView) {
		this.previousCategories.add(this.getCurrentPage());
		this.category = parISpectatorMenuView;
		this.selectedSlot = -1;
		this.page = 0;
	}

	public SpectatorDetails getCurrentPage() {
		return new SpectatorDetails(this.category, this.getItems(), this.selectedSlot);
	}

	static class EndSpectatorObject implements ISpectatorMenuObject {
		private EndSpectatorObject() {
		}

		public void selectItem(SpectatorMenu spectatormenu) {
			spectatormenu.exit();
		}

		public IChatComponent getSpectatorName() {
			return new ChatComponentText("Close menu");
		}

		public void renderIcon(float var1, int var2) {
			Minecraft.getMinecraft().getTextureManager().bindTexture(GuiSpectator.SPECTATOR_WIDGETS);
			Gui.drawModalRectWithCustomSizedTexture(0, 0, 128.0F, 0.0F, 16, 16, 256.0F, 256.0F);
		}

		public boolean isEnabled() {
			return true;
		}
	}

	static class MoveMenuObject implements ISpectatorMenuObject {
		private final int direction;
		private final boolean enabled;

		public MoveMenuObject(int parInt1, boolean parFlag) {
			this.direction = parInt1;
			this.enabled = parFlag;
		}

		public void selectItem(SpectatorMenu spectatormenu) {
			spectatormenu.page = this.direction;
		}

		public IChatComponent getSpectatorName() {
			return this.direction < 0 ? new ChatComponentText("Previous Page")
					: new ChatComponentText("Next Page");
		}

		public void renderIcon(float var1, int var2) {
			Minecraft.getMinecraft().getTextureManager().bindTexture(GuiSpectator.SPECTATOR_WIDGETS);
			if (this.direction < 0) {
				Gui.drawModalRectWithCustomSizedTexture(0, 0, 144.0F, 0.0F, 16, 16, 256.0F, 256.0F);
			} else {
				Gui.drawModalRectWithCustomSizedTexture(0, 0, 160.0F, 0.0F, 16, 16, 256.0F, 256.0F);
			}

		}

		public boolean isEnabled() {
			return this.enabled;
		}
	}
}