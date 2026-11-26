package net.caffeinemc.mods.sodium.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.SodiumGameOptions.ChunkBuildDeferMode;
import net.caffeinemc.mods.sodium.client.gui.SodiumGameOptions.GraphicsQuality;
import net.caffeinemc.mods.sodium.client.gui.options.OptionFlag;
import net.caffeinemc.mods.sodium.client.gui.options.OptionImpact;
import net.caffeinemc.mods.sodium.client.gui.options.SodiumOptionEntry;
import net.caffeinemc.mods.sodium.client.gui.options.SodiumOptionPage;
import net.caffeinemc.mods.sodium.client.gui.options.SodiumOptionPage.OptionGroup;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.region.SodiumRenderRegionManager;
import net.lax1dude.eaglercraft.v1_8.Display;
import net.lax1dude.eaglercraft.v1_8.Mouse;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

/**
 * Modern Sodium tabbed Video Settings GUI with General, Quality, Performance, and Advanced tabs,
 * live GPU Arena status, option impact badges, hover documentation, Undo/Apply/Reset, and a
 * 1-click bridge to Classic Eaglercraft Video Settings.
 */
public class SodiumOptionsGUI extends GuiScreen {

	private static final int HEADER_HEIGHT = 34;
	private static final int FOOTER_HEIGHT = 32;
	private static final int ROW_HEIGHT = 20;
	private static final int GROUP_GAP = 6;
	private static final int CONTROL_WIDTH = 114;

	private final GuiScreen parentScreen;
	private final GameSettings gameSettings;
	private final List<SodiumOptionPage> pages = new ArrayList<SodiumOptionPage>();
	private int currentPageIndex = 0;
	private int scrollOffset = 0;
	private int maxScrollOffset = 0;

	private SodiumOptionEntry draggingSlider = null;
	private int dragControlLeft = 0;
	private int dragControlWidth = 1;

	public SodiumOptionsGUI(GuiScreen parentScreen, GameSettings gameSettings) {
		this.parentScreen = parentScreen;
		this.gameSettings = gameSettings;
		buildPages();
	}

	private void buildPages() {
		this.pages.clear();
		final GameSettings gs = this.gameSettings;
		final SodiumGameOptions sodium = SodiumClientMod.options();

		// 1. GENERAL PAGE
		SodiumOptionPage general = new SodiumOptionPage("General");
		OptionGroup renderingGroup = new OptionGroup("Rendering")
				.add(new SodiumOptionEntry.SliderOption(
						"Render Distance",
						"Controls how far terrain chunks are visible around the player. Sodium's region arena batching and occlusion culler allow significantly higher distances.",
						OptionImpact.HIGH, 2, 24, 1,
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return gs.renderDistanceChunks;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								gs.renderDistanceChunks = v;
							}
						},
						new SodiumOptionEntry.ValueFormatter() {
							public String format(int v) {
								return v + " chunks";
							}
						},
						OptionFlag.REQUIRES_RENDERER_RELOAD))
				.add(new SodiumOptionEntry.SliderOption(
						"Brightness",
						"Adjusts overall world lightmap brightness from Moody to Bright.",
						OptionImpact.LOW, 0, 100, 1,
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return Math.round(gs.gammaSetting * 100.0F);
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								gs.gammaSetting = (float) v / 100.0F;
							}
						},
						new SodiumOptionEntry.ValueFormatter() {
							public String format(int v) {
								return v == 0 ? "Moody" : (v == 100 ? "Bright" : ("+" + v + "%"));
							}
						}));

		OptionGroup windowGroup = new OptionGroup("Window & Display")
				.add(new SodiumOptionEntry.CycleOption(
						"GUI Scale",
						"Sets the user interface scale factor.",
						OptionImpact.LOW,
						new String[] { "Auto", "Small", "Normal", "Large" },
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return gs.guiScale & 3;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								gs.guiScale = v;
							}
						},
						OptionFlag.REQUIRES_RESOLUTION_UPDATE))
				.add(new SodiumOptionEntry.BooleanOption(
						"Fullscreen",
						"Toggles fullscreen browser/desktop display mode.",
						OptionImpact.LOW,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return Display.isFullscreen();
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								if (Display.isFullscreen() != v) {
									Display.toggleFullscreen();
								}
							}
						}))
				.add(new SodiumOptionEntry.BooleanOption(
						"VSync",
						"Synchronizes frame presentation to monitor refresh rate to prevent tearing.",
						OptionImpact.VARIES,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return gs.enableVsync;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								gs.enableVsync = v;
							}
						}))
				.add(new SodiumOptionEntry.SliderOption(
						"Max Framerate",
						"Caps frames per second to reduce power usage and heat, or set to 260 for Unlimited.",
						OptionImpact.LOW, 10, 260, 10,
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return gs.limitFramerate;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								gs.limitFramerate = v;
							}
						},
						new SodiumOptionEntry.ValueFormatter() {
							public String format(int v) {
								return v >= 260 ? "Unlimited" : (v + " fps");
							}
						}));

		OptionGroup viewGroup = new OptionGroup("Camera & Lighting")
				.add(new SodiumOptionEntry.BooleanOption(
						"View Bobbing",
						"Sways camera gently when walking.",
						OptionImpact.LOW,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return gs.viewBobbing;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								gs.viewBobbing = v;
							}
						}))
				.add(new SodiumOptionEntry.BooleanOption(
						"Dynamic Lights",
						"Illuminates surrounding terrain dynamically when holding torches or light-emitting items.",
						OptionImpact.MEDIUM,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return gs.enableDynamicLights;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								gs.enableDynamicLights = v;
							}
						},
						OptionFlag.REQUIRES_RENDERER_RELOAD));

		general.addGroup(renderingGroup).addGroup(windowGroup).addGroup(viewGroup);
		this.pages.add(general);

		// 2. QUALITY PAGE
		SodiumOptionPage quality = new SodiumOptionPage("Quality");
		OptionGroup graphicsGroup = new OptionGroup("World Quality")
				.add(new SodiumOptionEntry.CycleOption(
						"Graphics",
						"Controls baseline Fast vs Fancy transparency, shading, and sky effects.",
						OptionImpact.HIGH,
						new String[] { "Fast", "Fancy" },
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return gs.fancyGraphics ? 1 : 0;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								gs.fancyGraphics = (v == 1);
							}
						},
						OptionFlag.REQUIRES_RENDERER_RELOAD))
				.add(new SodiumOptionEntry.CycleOption(
						"Smooth Lighting",
						"Controls per-vertex ambient occlusion and smooth light interpolation across block faces.",
						OptionImpact.MEDIUM,
						new String[] { "Off", "Minimum", "Maximum" },
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return gs.ambientOcclusion;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								gs.ambientOcclusion = v;
							}
						},
						OptionFlag.REQUIRES_RENDERER_RELOAD))
				.add(new SodiumOptionEntry.SliderOption(
						"Biome Blend",
						"Smoothly blends grass, foliage, and water colors across biome borders using Sodium's O(1) separable box blur.",
						OptionImpact.LOW, 0, 7, 1,
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return sodium.quality.biomeBlendRadius;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								sodium.quality.biomeBlendRadius = v;
							}
						},
						new SodiumOptionEntry.ValueFormatter() {
							public String format(int v) {
								if (v <= 0) {
									return "Off";
								}
								int dim = (v << 1) + 1;
								return dim + "x" + dim;
							}
						},
						OptionFlag.REQUIRES_RENDERER_RELOAD))
				.add(new SodiumOptionEntry.CycleOption(
						"Leaves Quality",
						"Overrides leaf block transparency independently of global Graphics preset.",
						OptionImpact.MEDIUM,
						new String[] { "Default", "Fast", "Fancy" },
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return sodium.quality.leavesQuality.ordinal();
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								sodium.quality.leavesQuality = GraphicsQuality.values()[v];
							}
						},
						OptionFlag.REQUIRES_RENDERER_RELOAD));

		OptionGroup atmosphereGroup = new OptionGroup("Atmosphere & Details")
				.add(new SodiumOptionEntry.CycleOption(
						"Clouds",
						"Controls cloud rendering mode (Off, Fast 2D, or Fancy 3D).",
						OptionImpact.LOW,
						new String[] { "Off", "Fast", "Fancy" },
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return gs.clouds;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								gs.clouds = v;
							}
						},
						OptionFlag.REQUIRES_CLOUD_RELOAD))
				.add(new SodiumOptionEntry.SliderOption(
						"Cloud Distance",
						"Controls horizontal render radius of Sodium's cached cloud mesh.",
						OptionImpact.LOW, 4, 32, 2,
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return sodium.quality.cloudDistanceChunks;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								sodium.quality.cloudDistanceChunks = v;
							}
						},
						new SodiumOptionEntry.ValueFormatter() {
							public String format(int v) {
								return v + " chunks";
							}
						},
						OptionFlag.REQUIRES_CLOUD_RELOAD))
				.add(new SodiumOptionEntry.CycleOption(
						"Weather",
						"Controls rain and snow rendering density independently of Graphics preset.",
						OptionImpact.MEDIUM,
						new String[] { "Default", "Fast", "Fancy" },
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return sodium.quality.weatherQuality.ordinal();
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								sodium.quality.weatherQuality = GraphicsQuality.values()[v];
							}
						}))
				.add(new SodiumOptionEntry.CycleOption(
						"Particles",
						"Controls spawned particle density.",
						OptionImpact.MEDIUM,
						new String[] { "All", "Decreased", "Minimal" },
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return gs.particleSetting;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								gs.particleSetting = v;
							}
						}))
				.add(new SodiumOptionEntry.SliderOption(
						"Entity Distance",
						"Scales maximum render distance of entities and block entities (chests, signs, skulls).",
						OptionImpact.MEDIUM, 50, 500, 25,
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return sodium.advanced.entityDistancePercent;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								sodium.advanced.entityDistancePercent = v;
							}
						},
						new SodiumOptionEntry.ValueFormatter() {
							public String format(int v) {
								return v + "%";
							}
						}))
				.add(new SodiumOptionEntry.BooleanOption(
						"Vignette",
						"Renders subtle screen-corner darkening overlay based on sky light.",
						OptionImpact.LOW,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return sodium.quality.enableVignette;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								sodium.quality.enableVignette = v;
							}
						}))
				.add(new SodiumOptionEntry.BooleanOption(
						"Fog",
						"Enables distance horizon fog.",
						OptionImpact.LOW,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return gs.fog;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								gs.fog = v;
							}
						}))
				.add(new SodiumOptionEntry.SliderOption(
						"Mipmap Levels",
						"Controls texture smoothing at far distances to reduce shimmering.",
						OptionImpact.MEDIUM, 0, 4, 1,
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return gs.mipmapLevels;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								gs.mipmapLevels = v;
							}
						},
						new SodiumOptionEntry.ValueFormatter() {
							public String format(int v) {
								return v == 0 ? "Off" : (v + "x");
							}
						},
						OptionFlag.REQUIRES_ASSET_RELOAD));

		quality.addGroup(graphicsGroup).addGroup(atmosphereGroup);
		this.pages.add(quality);

		// 3. PERFORMANCE PAGE
		SodiumOptionPage performance = new SodiumOptionPage("Performance");
		OptionGroup chunkEngineGroup = new OptionGroup("Chunk Rendering Engine")
				.add(new SodiumOptionEntry.BooleanOption(
						"Use Region Batching",
						"Batches up to 256 chunk sections into shared 8x4x8 GPU vertex buffer arenas with cached element index buffers, cutting draw calls by 10x-30x.",
						OptionImpact.EXTREME,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return sodium.performance.useRegionBatching;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								sodium.performance.useRegionBatching = v;
							}
						},
						OptionFlag.REQUIRES_RENDERER_RELOAD))
				.add(new SodiumOptionEntry.CycleOption(
						"Defer Chunk Updates",
						"Controls whether nearby block updates stall the frame to rebuild synchronously (Never) or defer smoothly to the chunk scheduler (One Frame / Always).",
						OptionImpact.HIGH,
						new String[] { "Always", "One Frame", "Never" },
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return sodium.performance.chunkBuildDeferMode.ordinal();
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								sodium.performance.chunkBuildDeferMode = ChunkBuildDeferMode.values()[v];
							}
						}))
				.add(new SodiumOptionEntry.BooleanOption(
						"Use Block Face Culling",
						"Skips interior and un-annotated block/fluid model quads that lie flush against adjacent opaque full cubes.",
						OptionImpact.MEDIUM,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return sodium.performance.useBlockFaceCulling;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								sodium.performance.useBlockFaceCulling = v;
							}
						},
						OptionFlag.REQUIRES_RENDERER_RELOAD))
				.add(new SodiumOptionEntry.BooleanOption(
						"Fast Translucent Sorting",
						"Uses Sodium's zero-allocation radix/insertion quad sorter for water and stained glass instead of boxed Integer comparators.",
						OptionImpact.LOW,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return sodium.performance.useTranslucentFaceSorting;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								sodium.performance.useTranslucentFaceSorting = v;
							}
						}));

		OptionGroup cullingGroup = new OptionGroup("Culling & Animations")
				.add(new SodiumOptionEntry.BooleanOption(
						"Use Fog Occlusion",
						"Culls chunk sections hidden beyond dense fog (underwater, lava, Nether, horizon) or outside cylindrical render distance.",
						OptionImpact.MEDIUM,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return sodium.performance.useFogOcclusion;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								sodium.performance.useFogOcclusion = v;
							}
						}))
				.add(new SodiumOptionEntry.BooleanOption(
						"Use Entity Culling",
						"Skips rendering entities and block entities inside occluded chunk sections.",
						OptionImpact.MEDIUM,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return sodium.performance.useEntityCulling;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								sodium.performance.useEntityCulling = v;
							}
						}))
				.add(new SodiumOptionEntry.BooleanOption(
						"Use Particle Culling",
						"Culls particles outside the view frustum or inside hidden chunk sections before GPU upload.",
						OptionImpact.MEDIUM,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return sodium.performance.useParticleCulling;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								sodium.performance.useParticleCulling = v;
							}
						}))
				.add(new SodiumOptionEntry.BooleanOption(
						"Animate Only Visible Textures",
						"Updates FBO mipmap animations only for block/fluid sprites visible on screen in the current frame.",
						OptionImpact.HIGH,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return sodium.performance.animateOnlyVisibleTextures;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								sodium.performance.animateOnlyVisibleTextures = v;
							}
						}))
				.add(new SodiumOptionEntry.BooleanOption(
						"Cloud Mesh Caching",
						"Caches interior-culled 3D/2D cloud meshes on the GPU instead of rebuilding 128 cloud batches every frame.",
						OptionImpact.MEDIUM,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return sodium.performance.useCloudOptimization;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								sodium.performance.useCloudOptimization = v;
							}
						},
						OptionFlag.REQUIRES_CLOUD_RELOAD));

		performance.addGroup(chunkEngineGroup).addGroup(cullingGroup);
		this.pages.add(performance);

		// 4. ADVANCED PAGE
		SodiumOptionPage advanced = new SodiumOptionPage("Advanced");
		OptionGroup schedulerGroup = new OptionGroup("Chunk Builder & Memory")
				.add(new SodiumOptionEntry.SliderOption(
						"Chunk Update Budget",
						"Maximum milliseconds per frame spent compiling and uploading background chunk meshes.",
						OptionImpact.VARIES, 1, 25, 1,
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return sodium.advanced.chunkUpdateBudgetMs;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								sodium.advanced.chunkUpdateBudgetMs = v;
							}
						},
						new SodiumOptionEntry.ValueFormatter() {
							public String format(int v) {
								return v + " ms";
							}
						}))
				.add(new SodiumOptionEntry.BooleanOption(
						"Direct Staging Buffer",
						"Reuses a single native staging buffer when transforming chunk vertices into region coordinates.",
						OptionImpact.LOW,
						new SodiumOptionEntry.BooleanGetter() {
							public boolean get() {
								return sodium.advanced.useCompactRegionStaging;
							}
						},
						new SodiumOptionEntry.BooleanSetter() {
							public void set(boolean v) {
								sodium.advanced.useCompactRegionStaging = v;
							}
						}))
				.add(new SodiumOptionEntry.CycleOption(
						"FXAA Antialiasing",
						"Controls post-processing fast approximate antialiasing.",
						OptionImpact.LOW,
						new String[] { "Auto", "On", "Off" },
						new SodiumOptionEntry.IntGetter() {
							public int get() {
								return gs.fxaa % 3;
							}
						},
						new SodiumOptionEntry.IntSetter() {
							public void set(int v) {
								gs.fxaa = v;
							}
						}));

		advanced.addGroup(schedulerGroup);
		this.pages.add(advanced);
	}

	@Override
	public void initGui() {
		this.scrollOffset = MathHelper.clamp_int(this.scrollOffset, 0, computeMaxScroll());
	}

	private boolean hasAnyPendingChanges() {
		for (int i = 0, n = this.pages.size(); i < n; ++i) {
			if (this.pages.get(i).hasPendingChanges()) {
				return true;
			}
		}
		return false;
	}

	private void applyAllChanges() {
		EnumSet<OptionFlag> triggeredFlags = EnumSet.noneOf(OptionFlag.class);
		int prevMipmap = this.gameSettings.mipmapLevels;

		for (int p = 0; p < this.pages.size(); ++p) {
			List<OptionGroup> groups = this.pages.get(p).getGroups();
			for (int g = 0; g < groups.size(); ++g) {
				List<SodiumOptionEntry> opts = groups.get(g).getOptions();
				for (int o = 0; o < opts.size(); ++o) {
					SodiumOptionEntry entry = opts.get(o);
					if (entry.isModified()) {
						triggeredFlags.addAll(entry.getFlags());
						entry.apply();
					}
				}
			}
		}

		this.gameSettings.saveOptions();
		SodiumClientMod.saveOptions();

		boolean leavesFancy = this.gameSettings.shaders
				|| SodiumClientMod.options().quality.leavesQuality.isFancy(this.gameSettings.fancyGraphics);
		Blocks.leaves.setGraphicsLevel(leavesFancy);
		Blocks.leaves2.setGraphicsLevel(leavesFancy);

		if (triggeredFlags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)
				|| this.gameSettings.mipmapLevels != prevMipmap) {
			this.mc.getTextureMapBlocks().setMipmapLevels(this.gameSettings.mipmapLevels);
			this.mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
			this.mc.getTextureMapBlocks().setBlurMipmapDirect(false, this.gameSettings.mipmapLevels > 0);
			this.mc.scheduleResourcesRefresh();
		} else if (triggeredFlags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
			if (this.mc.renderGlobal != null) {
				this.mc.renderGlobal.loadRenderers();
			}
		}

		if (triggeredFlags.contains(OptionFlag.REQUIRES_CLOUD_RELOAD)) {
			SodiumWorldRenderer.instance().getCloudRenderer().invalidateMesh();
		}

		if (triggeredFlags.contains(OptionFlag.REQUIRES_RESOLUTION_UPDATE)) {
			ScaledResolution sr = new ScaledResolution(this.mc);
			int sw = sr.getScaledWidth();
			int sh = sr.getScaledHeight();
			this.setWorldAndResolution(this.mc, sw, sh);
			if (this.mc.voiceOverlay != null) {
				this.mc.voiceOverlay.setResolution(sw, sh);
			}
		}
	}

	private void undoAllChanges() {
		for (int p = 0; p < this.pages.size(); ++p) {
			List<OptionGroup> groups = this.pages.get(p).getGroups();
			for (int g = 0; g < groups.size(); ++g) {
				List<SodiumOptionEntry> opts = groups.get(g).getOptions();
				for (int o = 0; o < opts.size(); ++o) {
					opts.get(o).undo();
				}
			}
		}
	}

	private int getListLeft() {
		return 14;
	}

	private int getListWidth() {
		return this.width >= 420 ? (this.width - 186) : (this.width - 28);
	}

	private int computeContentHeight() {
		SodiumOptionPage page = this.pages.get(this.currentPageIndex);
		List<OptionGroup> groups = page.getGroups();
		int h = 6;
		for (int g = 0; g < groups.size(); ++g) {
			OptionGroup grp = groups.get(g);
			h += 14; // group header
			h += grp.getOptions().size() * (ROW_HEIGHT + 2);
			h += GROUP_GAP;
		}
		return h;
	}

	private int computeMaxScroll() {
		int viewHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
		return Math.max(0, computeContentHeight() - viewHeight);
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			int step = wheel > 0 ? -18 : 18;
			this.scrollOffset = MathHelper.clamp_int(this.scrollOffset + step, 0, computeMaxScroll());
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		this.maxScrollOffset = computeMaxScroll();
		this.scrollOffset = MathHelper.clamp_int(this.scrollOffset, 0, this.maxScrollOffset);

		// Dark translucent backdrop
		drawRect(0, 0, this.width, this.height, 0x90101215);
		drawRect(0, 0, this.width, HEADER_HEIGHT, 0xD814181D);
		drawRect(0, HEADER_HEIGHT - 1, this.width, HEADER_HEIGHT, 0xFF28323C);

		// Draw tabs
		int tabX = 14;
		for (int i = 0; i < this.pages.size(); ++i) {
			SodiumOptionPage page = this.pages.get(i);
			String label = page.getName() + (page.hasPendingChanges() ? " *" : "");
			int tabW = this.fontRendererObj.getStringWidth(label) + 16;
			boolean selected = (i == this.currentPageIndex);
			boolean hovered = mouseX >= tabX && mouseX < tabX + tabW && mouseY >= 6 && mouseY < HEADER_HEIGHT - 2;
			int bg = selected ? 0xFF222B35 : (hovered ? 0x991E262F : 0x44181E25);
			drawRect(tabX, 6, tabX + tabW, HEADER_HEIGHT - 2, bg);
			if (selected) {
				drawRect(tabX, HEADER_HEIGHT - 4, tabX + tabW, HEADER_HEIGHT - 2, 0xFF56D364);
			}
			int textColor = selected ? 0xFFFFFFFF : (hovered ? 0xFFE0E6ED : 0xFFAAB4BE);
			this.drawCenteredString(this.fontRendererObj, label, tabX + (tabW >> 1), 13, textColor);
			tabX += tabW + 4;
		}

		// Classic Video Settings button in header right
		String classicLabel = "Classic Video...";
		int classicW = this.fontRendererObj.getStringWidth(classicLabel) + 14;
		int classicX = this.width - classicW - 14;
		boolean classicHover = mouseX >= classicX && mouseX < classicX + classicW && mouseY >= 7 && mouseY < 27;
		drawRect(classicX, 7, classicX + classicW, 27, classicHover ? 0xFF2E3B4A : 0xFF202832);
		drawOutline(classicX, 7, classicX + classicW, 27, classicHover ? 0xFF6CB6FF : 0xFF3A4756);
		this.drawCenteredString(this.fontRendererObj, classicLabel, classicX + (classicW >> 1), 13,
				classicHover ? 0xFFFFFFFF : 0xFFD0D7DE);

		// Draw scrollable options list
		int listLeft = getListLeft();
		int listWidth = getListWidth();
		int listTop = HEADER_HEIGHT;
		int listBottom = this.height - FOOTER_HEIGHT;
		SodiumOptionEntry hoveredEntry = null;

		int curY = listTop + 6 - this.scrollOffset;
		SodiumOptionPage activePage = this.pages.get(this.currentPageIndex);
		List<OptionGroup> groups = activePage.getGroups();

		for (int g = 0; g < groups.size(); ++g) {
			OptionGroup grp = groups.get(g);
			if (curY + 12 >= listTop && curY <= listBottom) {
				this.fontRendererObj.drawStringWithShadow(EnumChatFormatting.AQUA + grp.getTitle(), listLeft + 2,
						curY + 2, 0xFF58A6FF);
			}
			curY += 14;

			List<SodiumOptionEntry> opts = grp.getOptions();
			for (int o = 0; o < opts.size(); ++o) {
				SodiumOptionEntry entry = opts.get(o);
				int rowBottom = curY + ROW_HEIGHT;
				if (rowBottom >= listTop && curY <= listBottom) {
					boolean rowHover = mouseX >= listLeft && mouseX < listLeft + listWidth
							&& mouseY >= Math.max(curY, listTop) && mouseY < Math.min(rowBottom, listBottom);
					if (rowHover) {
						hoveredEntry = entry;
					}
					drawOptionRow(entry, listLeft, curY, listWidth, ROW_HEIGHT, rowHover, mouseX, mouseY);
				}
				curY += ROW_HEIGHT + 2;
			}
			curY += GROUP_GAP;
		}

		// Scrollbar if needed
		if (this.maxScrollOffset > 0) {
			int trackX = listLeft + listWidth + 3;
			int trackH = listBottom - listTop - 8;
			int contentH = computeContentHeight();
			int thumbH = Math.max(18, (trackH * (listBottom - listTop)) / Math.max(1, contentH));
			int thumbY = listTop + 4 + ((trackH - thumbH) * this.scrollOffset) / this.maxScrollOffset;
			drawRect(trackX, listTop + 4, trackX + 3, listBottom - 4, 0x5528323C);
			drawRect(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xAA6E7681);
		}

		// Right-hand info & GPU Arena panel (or floating tooltip on narrow screens)
		if (this.width >= 420) {
			int sideLeft = listLeft + listWidth + 12;
			int sideRight = this.width - 12;
			drawSidePanel(sideLeft, listTop + 6, sideRight, listBottom - 6, hoveredEntry);
		}

		// Re-draw header/footer masks over scrolled overflow
		drawRect(0, 0, this.width, HEADER_HEIGHT, 0x00000000);
		drawRect(0, this.height - FOOTER_HEIGHT, this.width, this.height, 0xE014181D);
		drawRect(0, this.height - FOOTER_HEIGHT, this.width, this.height - FOOTER_HEIGHT + 1, 0xFF28323C);

		// Brand badge on bottom left
		this.fontRendererObj.drawStringWithShadow(
				EnumChatFormatting.GREEN + SodiumClientMod.MOD_NAME + EnumChatFormatting.GRAY + " "
						+ SodiumClientMod.MOD_VERSION,
				14, this.height - 20, 0xFFFFFFFF);

		// Footer buttons: Undo, Apply, Done
		boolean dirty = hasAnyPendingChanges();
		int btnY = this.height - 26;
		int doneX = this.width - 78;
		int applyX = doneX - 72;
		int undoX = applyX - 72;

		drawFooterButton("Undo", undoX, btnY, 66, 20, dirty, false, mouseX, mouseY);
		drawFooterButton("Apply", applyX, btnY, 66, 20, dirty, true, mouseX, mouseY);
		drawFooterButton("Done", doneX, btnY, 66, 20, true, false, mouseX, mouseY);

		if (this.width < 420 && hoveredEntry != null) {
			List<String> tip = new ArrayList<String>();
			tip.add(EnumChatFormatting.WHITE + hoveredEntry.getName());
			tip.addAll(this.fontRendererObj.listFormattedStringToWidth(hoveredEntry.getTooltip(), 190));
			if (hoveredEntry.getImpact() != null) {
				tip.add(hoveredEntry.getImpact().getFormattedText());
			}
			this.drawHoveringText(tip, mouseX, mouseY);
		}

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private void drawOptionRow(SodiumOptionEntry entry, int x, int y, int w, int h, boolean hovered, int mouseX,
			int mouseY) {
		int rowBg = hovered ? 0xCC222A34 : 0x99181E26;
		drawRect(x, y, x + w, y + h, rowBg);

		boolean modified = entry.isModified();
		int nameColor = modified ? 0xFFFFDF5D : (hovered ? 0xFFFFFFFF : 0xFFD8DEE4);
		String title = entry.getName() + (modified ? " *" : "");
		this.fontRendererObj.drawStringWithShadow(title, x + 8, y + 6, nameColor);

		int ctrlW = Math.min(CONTROL_WIDTH, w / 2 - 8);
		int ctrlX = x + w - ctrlW - 6;
		int ctrlY = y + 2;
		int ctrlH = h - 4;

		switch (entry.getControlType()) {
		case CHECKBOX: {
			boolean on = entry.getBooleanValue();
			int pillW = 46;
			int pillX = x + w - pillW - 6;
			int bg = on ? 0xFF238636 : 0xFF30363D;
			int border = on ? 0xFF3FB950 : 0xFF484F58;
			drawRect(pillX, ctrlY, pillX + pillW, ctrlY + ctrlH, bg);
			drawOutline(pillX, ctrlY, pillX + pillW, ctrlY + ctrlH, border);
			this.drawCenteredString(this.fontRendererObj, entry.getFormattedValue(), pillX + (pillW >> 1), y + 6,
					0xFFFFFFFF);
			break;
		}
		case SLIDER: {
			drawRect(ctrlX, ctrlY, ctrlX + ctrlW, ctrlY + ctrlH, 0xFF12161C);
			int fillW = (int) Math.round(entry.getSliderNormalized() * (double) (ctrlW - 2));
			if (fillW > 0) {
				drawRect(ctrlX + 1, ctrlY + 1, ctrlX + 1 + fillW, ctrlY + ctrlH - 1, 0xFF2EA043);
			}
			drawOutline(ctrlX, ctrlY, ctrlX + ctrlW, ctrlY + ctrlH, hovered ? 0xFF56D364 : 0xFF30363D);
			this.drawCenteredString(this.fontRendererObj, entry.getFormattedValue(), ctrlX + (ctrlW >> 1), y + 6,
					0xFFFFFFFF);
			break;
		}
		case CYCLER:
		default: {
			drawRect(ctrlX, ctrlY, ctrlX + ctrlW, ctrlY + ctrlH, hovered ? 0xFF29323E : 0xFF21262D);
			drawOutline(ctrlX, ctrlY, ctrlX + ctrlW, ctrlY + ctrlH, hovered ? 0xFF58A6FF : 0xFF363E48);
			this.drawCenteredString(this.fontRendererObj, entry.getFormattedValue(), ctrlX + (ctrlW >> 1), y + 6,
					0xFFFFFFFF);
			break;
		}
		}
	}

	private void drawSidePanel(int left, int top, int right, int bottom, SodiumOptionEntry hovered) {
		drawRect(left, top, right, bottom, 0xAA141920);
		drawOutline(left, top, right, bottom, 0xFF28323C);
		int innerW = Math.max(60, (right - left) - 14);
		int textY = top + 8;

		if (hovered != null) {
			this.fontRendererObj.drawStringWithShadow(EnumChatFormatting.BOLD + hovered.getName(), left + 7, textY,
					0xFFFFFFFF);
			textY += 13;

			if (hovered.getImpact() != null) {
				this.fontRendererObj.drawStringWithShadow(hovered.getImpact().getFormattedText(), left + 7, textY,
						0xFFFFFFFF);
				textY += 12;
			}

			List<String> lines = this.fontRendererObj.listFormattedStringToWidth(hovered.getTooltip(), innerW);
			for (int i = 0; i < lines.size() && textY + 10 < bottom - 44; ++i) {
				this.fontRendererObj.drawStringWithShadow(lines.get(i), left + 7, textY, 0xFFB8C2CC);
				textY += 10;
			}

			if (!hovered.getFlags().isEmpty() && textY + 12 < bottom - 36) {
				textY += 4;
				for (OptionFlag flag : hovered.getFlags()) {
					this.fontRendererObj.drawStringWithShadow(
							EnumChatFormatting.GOLD + "Requires: " + flag.getDescription(), left + 7, textY,
							0xFFFFFFFF);
					textY += 10;
				}
			}
		} else {
			this.fontRendererObj.drawStringWithShadow(EnumChatFormatting.GREEN + "Sodium Engine", left + 7, textY,
					0xFFFFFFFF);
			textY += 13;
			List<String> intro = this.fontRendererObj.listFormattedStringToWidth(
					"Hover over any setting to inspect its performance impact and behavior.", innerW);
			for (int i = 0; i < intro.size(); ++i) {
				this.fontRendererObj.drawStringWithShadow(intro.get(i), left + 7, textY, 0xFF8B949E);
				textY += 10;
			}
		}

		// Live GPU Arena Memory Bar at bottom of side panel
		SodiumRenderRegionManager rm = SodiumWorldRenderer.instance().getRegionManager();
		int boxTop = bottom - 36;
		if (boxTop > top + 40) {
			drawRect(left + 5, boxTop, right - 5, bottom - 5, 0xCC0D1117);
			long used = rm.getTotalUsedBytes();
			long cap = rm.getTotalCapacityBytes();
			String label = cap > 0
					? ("Arena VRAM: " + (used / 1024L) + " / " + (cap / 1024L) + " KB")
					: "Arena VRAM: Idle";
			this.fontRendererObj.drawStringWithShadow(label, left + 9, boxTop + 5, 0xFFC9D1D9);
			int barLeft = left + 9;
			int barRight = right - 9;
			int barW = Math.max(10, barRight - barLeft);
			drawRect(barLeft, boxTop + 18, barRight, boxTop + 25, 0xFF21262D);
			if (cap > 0) {
				int usedW = (int) Math.min(barW, (used * (long) barW) / cap);
				drawRect(barLeft, boxTop + 18, barLeft + usedW, boxTop + 25, 0xFF3FB950);
			}
		}
	}

	private void drawFooterButton(String label, int x, int y, int w, int h, boolean enabled, boolean accent,
			int mouseX, int mouseY) {
		boolean hovered = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		int bg;
		int border;
		int textCol;
		if (!enabled) {
			bg = 0x66161B22;
			border = 0x6630363D;
			textCol = 0xFF6E7681;
		} else if (accent) {
			bg = hovered ? 0xFF2EA043 : 0xFF238636;
			border = 0xFF56D364;
			textCol = 0xFFFFFFFF;
		} else {
			bg = hovered ? 0xFF303946 : 0xFF21262D;
			border = hovered ? 0xFF8B949E : 0xFF3A424C;
			textCol = 0xFFFFFFFF;
		}
		drawRect(x, y, x + w, y + h, bg);
		drawOutline(x, y, x + w, y + h, border);
		this.drawCenteredString(this.fontRendererObj, label, x + (w >> 1), y + 6, textCol);
	}

	private static void drawOutline(int left, int top, int right, int bottom, int color) {
		drawRect(left, top, right, top + 1, color);
		drawRect(left, bottom - 1, right, bottom, color);
		drawRect(left, top, left + 1, bottom, color);
		drawRect(right - 1, top, right, bottom, color);
	}

	private void playClickSound() {
		this.mc.getSoundHandler()
				.playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		if (mouseButton != 0) {
			return;
		}

		// Header tabs
		int tabX = 14;
		for (int i = 0; i < this.pages.size(); ++i) {
			SodiumOptionPage page = this.pages.get(i);
			String label = page.getName() + (page.hasPendingChanges() ? " *" : "");
			int tabW = this.fontRendererObj.getStringWidth(label) + 16;
			if (mouseX >= tabX && mouseX < tabX + tabW && mouseY >= 6 && mouseY < HEADER_HEIGHT - 2) {
				this.currentPageIndex = i;
				this.scrollOffset = 0;
				playClickSound();
				return;
			}
			tabX += tabW + 4;
		}

		// Classic Video button
		String classicLabel = "Classic Video...";
		int classicW = this.fontRendererObj.getStringWidth(classicLabel) + 14;
		int classicX = this.width - classicW - 14;
		if (mouseX >= classicX && mouseX < classicX + classicW && mouseY >= 7 && mouseY < 27) {
			playClickSound();
			if (hasAnyPendingChanges()) {
				applyAllChanges();
			}
			this.mc.displayGuiScreen(new GuiVideoSettings(this, this.gameSettings));
			return;
		}

		// Footer buttons
		boolean dirty = hasAnyPendingChanges();
		int btnY = this.height - 26;
		int doneX = this.width - 78;
		int applyX = doneX - 72;
		int undoX = applyX - 72;

		if (dirty && mouseX >= undoX && mouseX < undoX + 66 && mouseY >= btnY && mouseY < btnY + 20) {
			undoAllChanges();
			playClickSound();
			return;
		}
		if (dirty && mouseX >= applyX && mouseX < applyX + 66 && mouseY >= btnY && mouseY < btnY + 20) {
			applyAllChanges();
			playClickSound();
			return;
		}
		if (mouseX >= doneX && mouseX < doneX + 66 && mouseY >= btnY && mouseY < btnY + 20) {
			if (dirty) {
				applyAllChanges();
			}
			playClickSound();
			this.mc.displayGuiScreen(this.parentScreen);
			return;
		}

		// Option rows
		int listLeft = getListLeft();
		int listWidth = getListWidth();
		int listTop = HEADER_HEIGHT;
		int listBottom = this.height - FOOTER_HEIGHT;
		if (mouseY < listTop || mouseY >= listBottom || mouseX < listLeft || mouseX >= listLeft + listWidth) {
			return;
		}

		int curY = listTop + 6 - this.scrollOffset;
		List<OptionGroup> groups = this.pages.get(this.currentPageIndex).getGroups();
		int ctrlW = Math.min(CONTROL_WIDTH, listWidth / 2 - 8);
		int ctrlX = listLeft + listWidth - ctrlW - 6;

		for (int g = 0; g < groups.size(); ++g) {
			curY += 14;
			List<SodiumOptionEntry> opts = groups.get(g).getOptions();
			for (int o = 0; o < opts.size(); ++o) {
				SodiumOptionEntry entry = opts.get(o);
				if (mouseY >= curY && mouseY < curY + ROW_HEIGHT) {
					if (entry.getControlType() == SodiumOptionEntry.ControlType.SLIDER) {
						if (mouseX >= ctrlX - 4 && mouseX <= ctrlX + ctrlW + 4) {
							double norm = (double) (mouseX - ctrlX) / (double) Math.max(1, ctrlW);
							entry.onClick(norm);
							this.draggingSlider = entry;
							this.dragControlLeft = ctrlX;
							this.dragControlWidth = Math.max(1, ctrlW);
							playClickSound();
						}
					} else {
						entry.onClick(0.5D);
						playClickSound();
					}
					return;
				}
				curY += ROW_HEIGHT + 2;
			}
			curY += GROUP_GAP;
		}
	}

	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
		if (this.draggingSlider != null && clickedMouseButton == 0) {
			double norm = (double) (mouseX - this.dragControlLeft) / (double) this.dragControlWidth;
			this.draggingSlider.onDrag(norm);
		}
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int state) {
		super.mouseReleased(mouseX, mouseY, state);
		this.draggingSlider = null;
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) {
		if (keyCode == 1) { // ESC
			if (hasAnyPendingChanges()) {
				applyAllChanges();
			}
			this.mc.displayGuiScreen(this.parentScreen);
		}
	}
}
