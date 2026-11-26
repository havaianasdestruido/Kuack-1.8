package net.caffeinemc.mods.sodium.client.gui.options;

import net.minecraft.util.EnumChatFormatting;

public enum OptionImpact {
	LOW("Low", EnumChatFormatting.GREEN),
	MEDIUM("Medium", EnumChatFormatting.YELLOW),
	HIGH("High", EnumChatFormatting.GOLD),
	EXTREME("Extreme", EnumChatFormatting.RED),
	VARIES("Varies", EnumChatFormatting.AQUA);

	private final String label;
	private final EnumChatFormatting color;

	OptionImpact(String label, EnumChatFormatting color) {
		this.label = label;
		this.color = color;
	}

	public String getLabel() {
		return this.label;
	}

	public EnumChatFormatting getColor() {
		return this.color;
	}

	public String getFormattedText() {
		return EnumChatFormatting.GRAY + "Performance Impact: " + this.color + this.label;
	}
}
