package net.caffeinemc.mods.sodium.client;

import net.caffeinemc.mods.sodium.client.gui.SodiumGameOptions;
import net.lax1dude.eaglercraft.v1_8.log4j.LogManager;
import net.lax1dude.eaglercraft.v1_8.log4j.Logger;

public final class SodiumClientMod {

	public static final String MOD_ID = "sodium";
	public static final String MOD_NAME = "Sodium";
	public static final String MOD_VERSION = "0.6.0+kuack1.8";

	private static final Logger LOGGER = LogManager.getLogger("Sodium");
	private static SodiumGameOptions CONFIG;
	private static boolean initialized = false;

	private SodiumClientMod() {
	}

	public static void init() {
		if (!initialized) {
			initialized = true;
			CONFIG = SodiumGameOptions.load();
			LOGGER.info("Initialized {} {} (baked-in rendering engine)", MOD_NAME, MOD_VERSION);
		}
	}

	public static SodiumGameOptions options() {
		SodiumGameOptions cfg = CONFIG;
		if (cfg == null) {
			cfg = SodiumGameOptions.load();
			CONFIG = cfg;
			initialized = true;
		}
		return cfg;
	}

	public static void saveOptions() {
		SodiumGameOptions cfg = options();
		cfg.save();
	}

	public static void resetToDefaults() {
		SodiumGameOptions fresh = SodiumGameOptions.defaults();
		CONFIG = fresh;
		fresh.save();
	}

	public static String getVersion() {
		return MOD_VERSION;
	}

	public static Logger logger() {
		return LOGGER;
	}
}
