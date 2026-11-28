package net.minecraft.client.gui;

import java.util.List;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;

import net.minecraft.client.Minecraft;
import net.minecraft.util.IntHashMap;

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
public class GuiPageButtonList extends GuiListExtended {
	private final List<GuiPageButtonList.GuiEntry> entries = Lists.newArrayList();
	private final IntHashMap<Gui> componentMap = new IntHashMap();
	private final List<GuiTextField> editBoxes = Lists.newArrayList();
	private final GuiPageButtonList.GuiListEntry[][] pages;
	private int page;
	private GuiPageButtonList.GuiResponder responder;
	private Gui focusedRow;

	public GuiPageButtonList(Minecraft mcIn, int widthIn, int heightIn, int topIn, int bottomIn, int slotHeightIn,
			GuiPageButtonList.GuiResponder parGuiResponder, GuiPageButtonList.GuiListEntry[]... parArrayOfarray) {
		super(mcIn, widthIn, heightIn, topIn, bottomIn, slotHeightIn);
		this.responder = parGuiResponder;
		this.pages = parArrayOfarray;
		this.centerListVertically = false;
		this.populateComponents();
		this.populateEntries();
	}

	private void populateComponents() {
		for (int n = 0; n < this.pages.length; ++n) {
			GuiPageButtonList.GuiListEntry[] aguipagebuttonlist$guilistentry = this.pages[n];
			for (int i = 0; i < aguipagebuttonlist$guilistentry.length; i += 2) {
				GuiPageButtonList.GuiListEntry guipagebuttonlist$guilistentry = aguipagebuttonlist$guilistentry[i];
				GuiPageButtonList.GuiListEntry guipagebuttonlist$guilistentry1 = i < aguipagebuttonlist$guilistentry.length
						- 1 ? aguipagebuttonlist$guilistentry[i + 1] : null;
				Gui gui = this.createEntryElement(guipagebuttonlist$guilistentry, 0,
						guipagebuttonlist$guilistentry1 == null);
				Gui gui1 = this.createEntryElement(guipagebuttonlist$guilistentry1, 160,
						guipagebuttonlist$guilistentry == null);
				GuiPageButtonList.GuiEntry guipagebuttonlist$guientry = new GuiPageButtonList.GuiEntry(gui, gui1);
				this.entries.add(guipagebuttonlist$guientry);
				if (guipagebuttonlist$guilistentry != null && gui != null) {
					this.componentMap.addKey(guipagebuttonlist$guilistentry.getId(), gui);
					if (gui instanceof GuiTextField) {
						this.editBoxes.add((GuiTextField) gui);
					}
				}

				if (guipagebuttonlist$guilistentry1 != null && gui1 != null) {
					this.componentMap.addKey(guipagebuttonlist$guilistentry1.getId(), gui1);
					if (gui1 instanceof GuiTextField) {
						this.editBoxes.add((GuiTextField) gui1);
					}
				}
			}
		}

	}

	private void populateEntries() {
		this.entries.clear();

		for (int i = 0; i < this.pages[this.page].length; i += 2) {
			GuiPageButtonList.GuiListEntry guipagebuttonlist$guilistentry = this.pages[this.page][i];
			GuiPageButtonList.GuiListEntry guipagebuttonlist$guilistentry1 = i < this.pages[this.page].length
					- 1 ? this.pages[this.page][i + 1] : null;
			Gui gui = (Gui) this.componentMap.lookup(guipagebuttonlist$guilistentry.getId());
			Gui gui1 = guipagebuttonlist$guilistentry1 != null
					? (Gui) this.componentMap.lookup(guipagebuttonlist$guilistentry1.getId())
					: null;
			GuiPageButtonList.GuiEntry guipagebuttonlist$guientry = new GuiPageButtonList.GuiEntry(gui, gui1);
			this.entries.add(guipagebuttonlist$guientry);
		}

	}

	public void setPage(int parInt1) {
		if (parInt1 != this.page) {
			int i = this.page;
			this.page = parInt1;
			this.populateEntries();
			this.markVisibility(i, parInt1);
			this.amountScrolled = 0.0F;
		}
	}

	public int getPage() {
		return this.page;
	}

	public int getPageCount() {
		return this.pages.length;
	}

	public Gui getFocusedControl() {
		return this.focusedRow;
	}

	public void previousPage() {
		if (this.page > 0) {
			this.setPage(this.page - 1);
		}

	}

	public void nextPage() {
		if (this.page < this.pages.length - 1) {
			this.setPage(this.page + 1);
		}

	}

	public Gui getComponent(int parInt1) {
		return (Gui) this.componentMap.lookup(parInt1);
	}

	private void markVisibility(int parInt1, int parInt2) {

		GuiListEntry[] etr = this.pages[parInt1];
		for (int i = 0; i < etr.length; ++i) {
			if (etr[i] != null) {
				this.setComponentVisibility((Gui) this.componentMap.lookup(etr[i].getId()), false);
			}
		}

		etr = this.pages[parInt2];
		for (int i = 0; i < etr.length; ++i) {
			if (etr[i] != null) {
				this.setComponentVisibility((Gui) this.componentMap.lookup(etr[i].getId()), true);
			}
		}

	}

	private void setComponentVisibility(Gui parGui, boolean parFlag) {
		if (parGui instanceof GuiButton) {
			((GuiButton) parGui).visible = parFlag;
		} else if (parGui instanceof GuiTextField) {
			((GuiTextField) parGui).setVisible(parFlag);
		} else if (parGui instanceof GuiLabel) {
			((GuiLabel) parGui).visible = parFlag;
		}

	}

	private Gui createEntryElement(GuiPageButtonList.GuiListEntry parGuiListEntry, int parInt1, boolean parFlag) {
		return (Gui) (parGuiListEntry instanceof GuiPageButtonList.GuiSlideEntry
				? this.createSlider(this.width / 2 - 155 + parInt1, 0,
						(GuiPageButtonList.GuiSlideEntry) parGuiListEntry)
				: (parGuiListEntry instanceof GuiPageButtonList.GuiButtonEntry
						? this.createButton(this.width / 2 - 155 + parInt1, 0,
								(GuiPageButtonList.GuiButtonEntry) parGuiListEntry)
						: (parGuiListEntry instanceof GuiPageButtonList.EditBoxEntry
								? this.createTextField(this.width / 2 - 155 + parInt1, 0,
										(GuiPageButtonList.EditBoxEntry) parGuiListEntry)
								: (parGuiListEntry instanceof GuiPageButtonList.GuiLabelEntry
										? this.createLabel(this.width / 2 - 155 + parInt1, 0,
												(GuiPageButtonList.GuiLabelEntry) parGuiListEntry, parFlag)
										: null))));
	}

	public void setActive(boolean parFlag) {
		for (int i = 0, l = this.entries.size(); i < l; ++i) {
			GuiPageButtonList.GuiEntry guipagebuttonlist$guientry = this.entries.get(i);
			if (guipagebuttonlist$guientry.component1 instanceof GuiButton) {
				((GuiButton) guipagebuttonlist$guientry.component1).enabled = parFlag;
			}

			if (guipagebuttonlist$guientry.component2 instanceof GuiButton) {
				((GuiButton) guipagebuttonlist$guientry.component2).enabled = parFlag;
			}
		}

	}

	public boolean mouseClicked(int i, int j, int k) {
		boolean flag = super.mouseClicked(i, j, k);
		int l = this.getSlotIndexFromScreenCoords(i, j);
		if (l >= 0) {
			GuiPageButtonList.GuiEntry guipagebuttonlist$guientry = this.getListEntry(l);
			if (this.focusedRow != guipagebuttonlist$guientry.focusedComponent && this.focusedRow != null
					&& this.focusedRow instanceof GuiTextField) {
				((GuiTextField) this.focusedRow).setFocused(false);
			}

			this.focusedRow = guipagebuttonlist$guientry.focusedComponent;
		}

		return flag;
	}

	private GuiSlider createSlider(int parInt1, int parInt2, GuiPageButtonList.GuiSlideEntry parGuiSlideEntry) {
		GuiSlider guislider = new GuiSlider(this.responder, parGuiSlideEntry.getId(), parInt1, parInt2,
				parGuiSlideEntry.getCaption(), parGuiSlideEntry.getMinValue(), parGuiSlideEntry.getMaxValue(),
				parGuiSlideEntry.getInitalValue(), parGuiSlideEntry.getFormatter());
		guislider.visible = parGuiSlideEntry.shouldStartVisible();
		return guislider;
	}

	private GuiListButton createButton(int parInt1, int parInt2, GuiPageButtonList.GuiButtonEntry parGuiButtonEntry) {
		GuiListButton guilistbutton = new GuiListButton(this.responder, parGuiButtonEntry.getId(), parInt1,
				parInt2, parGuiButtonEntry.getCaption(), parGuiButtonEntry.getInitialValue());
		guilistbutton.visible = parGuiButtonEntry.shouldStartVisible();
		return guilistbutton;
	}

	private GuiTextField createTextField(int parInt1, int parInt2, GuiPageButtonList.EditBoxEntry parEditBoxEntry) {
		GuiTextField guitextfield = new GuiTextField(parEditBoxEntry.getId(), this.mc.fontRendererObj, parInt1,
				parInt2, 150, 20);
		guitextfield.setText(parEditBoxEntry.getCaption());
		guitextfield.setGuiResponder(this.responder);
		guitextfield.setVisible(parEditBoxEntry.shouldStartVisible());
		guitextfield.setValidator(parEditBoxEntry.getFilter());
		return guitextfield;
	}

	private GuiLabel createLabel(int parInt1, int parInt2, GuiPageButtonList.GuiLabelEntry parGuiLabelEntry,
			boolean parFlag) {
		GuiLabel guilabel;
		if (parFlag) {
			guilabel = new GuiLabel(this.mc.fontRendererObj, parGuiLabelEntry.getId(), parInt1, parInt2,
					this.width - parInt1 * 2, 20, -1);
		} else {
			guilabel = new GuiLabel(this.mc.fontRendererObj, parGuiLabelEntry.getId(), parInt1, parInt2, 150,
					20, -1);
		}

		guilabel.visible = parGuiLabelEntry.shouldStartVisible();
		guilabel.addLine(parGuiLabelEntry.getCaption());
		guilabel.setCentered();
		return guilabel;
	}

	public void onKeyPressed(char parChar1, int parInt1) {
		if (this.focusedRow instanceof GuiTextField) {
			GuiTextField guitextfield = (GuiTextField) this.focusedRow;
			if (!GuiScreen.isKeyComboCtrlV(parInt1)) {
				if (parInt1 == 15) {
					guitextfield.setFocused(false);
					int k = this.editBoxes.indexOf(this.focusedRow);
					if (GuiScreen.isShiftKeyDown()) {
						if (k == 0) {
							k = this.editBoxes.size() - 1;
						} else {
							--k;
						}
					} else if (k == this.editBoxes.size() - 1) {
						k = 0;
					} else {
						++k;
					}

					this.focusedRow = (Gui) this.editBoxes.get(k);
					guitextfield = (GuiTextField) this.focusedRow;
					guitextfield.setFocused(true);
					int l = guitextfield.yPosition + this.slotHeight;
					int i1 = guitextfield.yPosition;
					if (l > this.bottom) {
						this.amountScrolled += (float) (l - this.bottom);
					} else if (i1 < this.top) {
						this.amountScrolled = (float) i1;
					}
				} else {
					guitextfield.textboxKeyTyped(parChar1, parInt1);
				}

			} else {
				String s = GuiScreen.getClipboardString();
				String[] astring = s.split(";");
				int i = this.editBoxes.indexOf(this.focusedRow);
				int j = i;

				for (int k = 0; k < astring.length; ++k) {
					((GuiTextField) this.editBoxes.get(j)).setText(astring[k]);
					if (j == this.editBoxes.size() - 1) {
						j = 0;
					} else {
						++j;
					}

					if (j == i) {
						break;
					}
				}

			}
		}
	}

	/**+
	 * Gets the IGuiListEntry object for the given index
	 */
	public GuiPageButtonList.GuiEntry getListEntry(int i) {
		return (GuiPageButtonList.GuiEntry) this.entries.get(i);
	}

	public int getSize() {
		return this.entries.size();
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

	public static class EditBoxEntry extends GuiPageButtonList.GuiListEntry {
		private final Predicate<String> filter;

		public EditBoxEntry(int parInt1, String parString1, boolean parFlag, Predicate<String> parPredicate) {
			super(parInt1, parString1, parFlag);
			this.filter = (Predicate) Objects.firstNonNull(parPredicate, Predicates.alwaysTrue());
		}

		public Predicate<String> getFilter() {
			return this.filter;
		}
	}

	public static class GuiButtonEntry extends GuiPageButtonList.GuiListEntry {
		private final boolean initialValue;

		public GuiButtonEntry(int parInt1, String parString1, boolean parFlag, boolean parFlag2) {
			super(parInt1, parString1, parFlag);
			this.initialValue = parFlag2;
		}

		public boolean getInitialValue() {
			return this.initialValue;
		}
	}

	public static class GuiEntry implements GuiListExtended.IGuiListEntry {
		private final Minecraft client = Minecraft.getMinecraft();
		private final Gui component1;
		private final Gui component2;
		private Gui focusedComponent;

		public GuiEntry(Gui parGui, Gui parGui2) {
			this.component1 = parGui;
			this.component2 = parGui2;
		}

		public Gui getComponent1() {
			return this.component1;
		}

		public Gui getComponent2() {
			return this.component2;
		}

		public void drawEntry(int var1, int var2, int i, int var4, int var5, int j, int k, boolean var8) {
			this.renderEntry(this.component1, i, j, k, false);
			this.renderEntry(this.component2, i, j, k, false);
		}

		private void renderEntry(Gui parGui, int parInt1, int parInt2, int parInt3, boolean parFlag) {
			if (parGui != null) {
				if (parGui instanceof GuiButton) {
					this.renderEntryButton((GuiButton) parGui, parInt1, parInt2, parInt3, parFlag);
				} else if (parGui instanceof GuiTextField) {
					this.renderTextField((GuiTextField) parGui, parInt1, parFlag);
				} else if (parGui instanceof GuiLabel) {
					this.renderLabel((GuiLabel) parGui, parInt1, parInt2, parInt3, parFlag);
				}

			}
		}

		private void renderEntryButton(GuiButton parGuiButton, int parInt1, int parInt2, int parInt3, boolean parFlag) {
			parGuiButton.yPosition = parInt1;
			if (!parFlag) {
				parGuiButton.drawButton(this.client, parInt2, parInt3);
			}

		}

		private void renderTextField(GuiTextField parGuiTextField, int parInt1, boolean parFlag) {
			parGuiTextField.yPosition = parInt1;
			if (!parFlag) {
				parGuiTextField.drawTextBox();
			}

		}

		private void renderLabel(GuiLabel parGuiLabel, int parInt1, int parInt2, int parInt3, boolean parFlag) {
			parGuiLabel.y = parInt1;
			if (!parFlag) {
				parGuiLabel.drawLabel(this.client, parInt2, parInt3);
			}

		}

		public void setSelected(int var1, int var2, int i) {
			this.renderEntry(this.component1, i, 0, 0, true);
			this.renderEntry(this.component2, i, 0, 0, true);
		}

		public boolean mousePressed(int var1, int i, int j, int k, int var5, int var6) {
			boolean flag = this.clickComponent(this.component1, i, j, k);
			boolean flag1 = this.clickComponent(this.component2, i, j, k);
			return flag || flag1;
		}

		private boolean clickComponent(Gui parGui, int parInt1, int parInt2, int parInt3) {
			if (parGui == null) {
				return false;
			} else if (parGui instanceof GuiButton) {
				return this.clickButton((GuiButton) parGui, parInt1, parInt2, parInt3);
			} else {
				if (parGui instanceof GuiTextField) {
					this.clickEntry((GuiTextField) parGui, parInt1, parInt2, parInt3);
				}

				return false;
			}
		}

		private boolean clickButton(GuiButton parGuiButton, int parInt1, int parInt2, int parInt3) {
			boolean flag = parGuiButton.mousePressed(this.client, parInt1, parInt2);
			if (flag) {
				this.focusedComponent = parGuiButton;
			}

			return flag;
		}

		private void clickEntry(GuiTextField parGuiTextField, int parInt1, int parInt2, int parInt3) {
			parGuiTextField.mouseClicked(parInt1, parInt2, parInt3);
			if (parGuiTextField.isFocused()) {
				this.focusedComponent = parGuiTextField;
			}

		}

		public void mouseReleased(int var1, int i, int j, int k, int var5, int var6) {
			this.releaseEntryComponent(this.component1, i, j, k);
			this.releaseEntryComponent(this.component2, i, j, k);
		}

		private void releaseEntryComponent(Gui parGui, int parInt1, int parInt2, int parInt3) {
			if (parGui != null) {
				if (parGui instanceof GuiButton) {
					this.releaseEntry((GuiButton) parGui, parInt1, parInt2, parInt3);
				}

			}
		}

		private void releaseEntry(GuiButton parGuiButton, int parInt1, int parInt2, int parInt3) {
			parGuiButton.mouseReleased(parInt1, parInt2);
		}
	}

	public static class GuiLabelEntry extends GuiPageButtonList.GuiListEntry {
		public GuiLabelEntry(int parInt1, String parString1, boolean parFlag) {
			super(parInt1, parString1, parFlag);
		}
	}

	public static class GuiListEntry {
		private final int id;
		private final String caption;
		private final boolean startVisible;

		public GuiListEntry(int parInt1, String parString1, boolean parFlag) {
			this.id = parInt1;
			this.caption = parString1;
			this.startVisible = parFlag;
		}

		public int getId() {
			return this.id;
		}

		public String getCaption() {
			return this.caption;
		}

		public boolean shouldStartVisible() {
			return this.startVisible;
		}
	}

	public interface GuiResponder {
		void setEntryValue(int var1, boolean var2);

		void onTick(int var1, float var2);

		void setEntryValue(int var1, String var2);
	}

	public static class GuiSlideEntry extends GuiPageButtonList.GuiListEntry {
		private final GuiSlider.FormatHelper formatter;
		private final float minValue;
		private final float maxValue;
		private final float initialSliderValue;

		public GuiSlideEntry(int parInt1, String parString1, boolean parFlag, GuiSlider.FormatHelper parFormatHelper,
				float parFloat1, float parFloat2, float parFloat3) {
			super(parInt1, parString1, parFlag);
			this.formatter = parFormatHelper;
			this.minValue = parFloat1;
			this.maxValue = parFloat2;
			this.initialSliderValue = parFloat3;
		}

		public GuiSlider.FormatHelper getFormatter() {
			return this.formatter;
		}

		public float getMinValue() {
			return this.minValue;
		}

		public float getMaxValue() {
			return this.maxValue;
		}

		public float getInitalValue() {
			return this.initialSliderValue;
		}
	}
}