package net.caffeinemc.mods.sodium.client.gui.options;

public enum OptionFlag {
	REQUIRES_RENDERER_RELOAD("Renderer Reload"),
	REQUIRES_CLOUD_RELOAD("Cloud Mesh Rebuild"),
	REQUIRES_ASSET_RELOAD("Asset Reload"),
	REQUIRES_RESOLUTION_UPDATE("Display Resize");

	private final String description;

	OptionFlag(String description) {
		this.description = description;
	}

	public String getDescription() {
		return this.description;
	}
}
