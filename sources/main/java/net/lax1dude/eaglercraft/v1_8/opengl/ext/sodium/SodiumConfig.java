package net.lax1dude.eaglercraft.v1_8.opengl.ext.sodium;

/**
 * Copyright (c) 2024 lax1dude, ayunami2000. All Rights Reserved.
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

/**
 * Presets and tunables for the built-in SodiumMC style chunk renderer.
 * <p>
 * The renderer is "baked in" like the deferred (PBR) pipeline and the dynamic
 * lights extension which already ship with the client: there is nothing to
 * download or install, the quality level is a single cyclable option exposed on
 * the "Video Settings" screen and it is persisted with the rest of the game
 * settings.
 * <p>
 * The levels intentionally mirror what Sodium itself calls its "quality"
 * settings, they only trade off how aggressively chunk updates are throttled
 * and how often the visible chunk list is re-sorted, never the correctness of
 * what ends up on screen.
 */
public final class SodiumConfig {

	/**
	 * Renderer disabled, the vanilla 1.8 chunk renderer is used instead.
	 */
	public static final int LEVEL_OFF = 0;

	/**
	 * Best frame times, fewest chunk uploads per frame.
	 */
	public static final int LEVEL_FAST = 1;

	/**
	 * The default, balanced chunk build budget with per frame re-sorting.
	 */
	public static final int LEVEL_BALANCED = 2;

	/**
	 * Highest chunk build throughput, closest to the vanilla "build
	 * everything as fast as possible" behavior.
	 */
	public static final int LEVEL_FANCY = 3;

	public static final int LEVEL_COUNT = 4;

	/**
	 * Chunk uploads that are allowed to run in a single client tick.
	 */
	private static final int[] MAX_UPLOADS = new int[] { 0, 2, 4, 8 };

	/**
	 * Minimum amount of time, in milliseconds, that must pass between two
	 * incremental re-sorts of the visible chunk list. Zero means "sort on
	 * every frame".
	 */
	private static final float[] RESORT_INTERVAL = new float[] { 0.0f, 100.0f, 0.0f, 0.0f };

	/**
	 * Whether chunks which are currently outside of the visible set are
	 * allowed to sit in the update queue until they become visible, instead
	 * of being rebuilt immediately. This is what keeps long flights from
	 * stalling on thousands of pointless chunk rebuilds.
	 */
	private static final boolean[] CULL_PENDING = new boolean[] { false, true, true, false };

	private SodiumConfig() {
	}

	public static int clampLevel(int level) {
		if(level < 0) {
			return 0;
		}
		if(level >= LEVEL_COUNT) {
			return LEVEL_COUNT - 1;
		}
		return level;
	}

	/**
	 * Cycles the quality level by the given amount, wrapping around.
	 */
	public static int cycleLevel(int level, int direction) {
		int ret = (clampLevel(level) + direction) % LEVEL_COUNT;
		if(ret < 0) {
			ret += LEVEL_COUNT;
		}
		return ret;
	}

	public static boolean isEnabled(int level) {
		return clampLevel(level) != LEVEL_OFF;
	}

	public static int getMaxChunkUploadsPerFrame(int level) {
		int i = MAX_UPLOADS[clampLevel(level)];
		return i == 0 ? MAX_UPLOADS[LEVEL_BALANCED] : i;
	}

	public static float getResortIntervalMillis(int level) {
		return RESORT_INTERVAL[clampLevel(level)];
	}

	public static boolean shouldCullPendingUpdates(int level) {
		return CULL_PENDING[clampLevel(level)];
	}

	/**
	 * Translation key of the name of the given quality level.
	 */
	public static String getLevelTranslationKey(int level) {
		return "options.sodiumRenderer." + clampLevel(level);
	}

}
