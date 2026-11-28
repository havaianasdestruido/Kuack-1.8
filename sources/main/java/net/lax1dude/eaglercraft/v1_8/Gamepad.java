package net.lax1dude.eaglercraft.v1_8;

import net.lax1dude.eaglercraft.v1_8.internal.PlatformInput;

/**
 * Copyright (c) 2022-2024 lax1dude, ayunami2000. All Rights Reserved.
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
 * Gamepad (controller) input for the browser client.
 *
 * <p>
 * This class is intentionally platform agnostic and only deals in <em>logical
 * actions</em>. The platform specific {@link PlatformInput} implementation is
 * responsible for reading the raw controller state (browser Gamepad API on the
 * web runtime), and this class translates that raw state into a small set of
 * logical actions using the selected preset. The game (which owns the
 * {@code KeyBinding} configuration) is then responsible for mapping those logical
 * actions onto the actual key/mouse bindings, so rebinding keys in the controls
 * screen keeps working with a gamepad attached.
 *
 * <p>
 * The right analog stick is turned into virtual mouse delta and injected
 * directly, so the existing camera look code picks it up without any further
 * changes.
 *
 * <p>
 * No threads or JVM-only APIs are used here, so this stays TeaVM compatible.
 */
public class Gamepad {

	/**
	 * Logical action identifiers. These are stable and are what the game maps to
	 * key bindings.
	 */
	public static final int ACT_FORWARD = 0;
	public static final int ACT_BACK = 1;
	public static final int ACT_LEFT = 2;
	public static final int ACT_RIGHT = 3;
	public static final int ACT_JUMP = 4;
	public static final int ACT_SNEAK = 5;
	public static final int ACT_SPRINT = 6;
	public static final int ACT_ATTACK = 7;
	public static final int ACT_USE = 8;
	public static final int ACT_PICK = 9;
	public static final int ACT_INVENTORY = 10;
	public static final int ACT_DROP = 11;
	public static final int ACT_CHAT = 12;
	public static final int ACT_PAUSE = 13;
	public static final int ACT_HOTBAR_NEXT = 14;
	public static final int ACT_HOTBAR_PREV = 15;

	public static final int ACTION_COUNT = 16;

	/**
	 * Preset identifiers, inspired by the control schemes of the legacy Minecraft
	 * console edition and the various console families.
	 */
	public static final int PRESET_LEGACY_CONSOLE = 0;
	public static final int PRESET_XBOX = 1;
	public static final int PRESET_PLAYSTATION = 2;
	public static final int PRESET_NINTENDO_SWITCH = 3;

	public static final int PRESET_COUNT = 4;

	/*
	 * Standard gamepad button indices, as defined by the W3C Gamepad API. The same
	 * physical buttons are exposed by Xbox, PlayStation and Switch controllers, just
	 * with different names, which is why the presets below only differ in which
	 * logical action each button is mapped to.
	 */
	private static final int BTN_A = 0;
	private static final int BTN_B = 1;
	private static final int BTN_X = 2;
	private static final int BTN_Y = 3;
	private static final int BTN_LB = 4;
	private static final int BTN_RB = 5;
	private static final int BTN_LT = 6;
	private static final int BTN_RT = 7;
	private static final int BTN_BACK = 8;
	private static final int BTN_START = 9;
	private static final int BTN_L3 = 10;
	private static final int BTN_R3 = 11;
	private static final int BTN_DPAD_UP = 12;
	private static final int BTN_DPAD_DOWN = 13;
	private static final int BTN_DPAD_LEFT = 14;
	private static final int BTN_DPAD_RIGHT = 15;

	private static final int BUTTON_COUNT = 16;

	/**
	 * Browsers expose at most four gamepads through the standard Gamepad API.
	 */
	private static final int MAX_GAMEPADS = 4;

	/**
	 * Per preset button map: gamepad button index -> logical action, or {@code -1}
	 * when the button is unmapped.
	 */
	private static final int[][] PRESET_BUTTONS = new int[PRESET_COUNT][BUTTON_COUNT];

	static {
		// Legacy Minecraft console edition style: A jumps, B attacks, X picks the
		// block and Y uses the item.
		PRESET_BUTTONS[PRESET_LEGACY_CONSOLE] = new int[] {
				ACT_JUMP, ACT_ATTACK, ACT_PICK, ACT_USE, ACT_SNEAK, ACT_SPRINT, -1, -1,
				ACT_INVENTORY, ACT_PAUSE, ACT_DROP, ACT_CHAT, ACT_HOTBAR_NEXT, ACT_HOTBAR_PREV, -1, -1 };

		// Xbox style: A jumps, X attacks, B uses, Y picks the block.
		PRESET_BUTTONS[PRESET_XBOX] = new int[] {
				ACT_JUMP, ACT_USE, ACT_ATTACK, ACT_PICK, ACT_SNEAK, ACT_SPRINT, -1, -1,
				ACT_INVENTORY, ACT_PAUSE, ACT_DROP, ACT_CHAT, ACT_HOTBAR_NEXT, ACT_HOTBAR_PREV, -1, -1 };

		// PlayStation style: Cross jumps, Square attacks, Circle uses, Triangle picks
		// the block. Drop and chat live on the L2/R2 triggers instead of the stick
		// clicks to match the PlayStation layout.
		PRESET_BUTTONS[PRESET_PLAYSTATION] = new int[] {
				ACT_JUMP, ACT_USE, ACT_ATTACK, ACT_PICK, ACT_SNEAK, ACT_SPRINT, ACT_DROP, ACT_CHAT,
				ACT_INVENTORY, ACT_PAUSE, -1, -1, ACT_HOTBAR_NEXT, ACT_HOTBAR_PREV, -1, -1 };

		// Nintendo Switch style: A jumps, B attacks, X uses, Y picks the block.
		PRESET_BUTTONS[PRESET_NINTENDO_SWITCH] = new int[] {
				ACT_JUMP, ACT_ATTACK, ACT_USE, ACT_PICK, ACT_SNEAK, ACT_SPRINT, -1, -1,
				ACT_INVENTORY, ACT_PAUSE, ACT_DROP, ACT_CHAT, ACT_HOTBAR_NEXT, ACT_HOTBAR_PREV, -1, -1 };
	}

	/**
	 * How strongly the right stick rotates the camera. The value is in the same
	 * "mouse delta" units the existing look code consumes, so the configured mouse
	 * sensitivity still applies on top of it.
	 */
	private static final float LOOK_SCALE = 20.0F;

	private static final float DEFAULT_DEADZONE = 0.15F;

	private static final boolean[] actionDown = new boolean[ACTION_COUNT];
	private static final boolean[] actionPressed = new boolean[ACTION_COUNT];
	private static final boolean[] actionReleased = new boolean[ACTION_COUNT];
	private static final boolean[] prevActionDown = new boolean[ACTION_COUNT];
	private static final boolean[] buttonRaw = new boolean[BUTTON_COUNT];

	private static boolean enabled = false;
	private static int preset = PRESET_LEGACY_CONSOLE;
	private static boolean lookEnabled = true;
	private static boolean invertLookY = false;
	private static float deadzone = DEFAULT_DEADZONE;

	/**
	 * Called once per tick by the game. Polls the raw controller state from the
	 * platform layer and (when enabled and a controller is connected) refreshes the
	 * logical action state and injects any right-stick camera delta.
	 *
	 * @param preset       the selected preset id
	 * @param enabled      whether gamepad input is enabled in the settings
	 * @param lookEnabled  whether the right stick drives the camera
	 * @param invertLookY  whether to invert the vertical look axis
	 * @param deadzone     the analog stick deadzone, clamped to [0, 0.5]
	 * @param inGame       whether the player is currently in-game (no GUI open). Look
	 *                     delta is only injected while in-game.
	 */
	public static void update(int presetIn, boolean enabledIn, boolean lookEnabledIn, boolean invertLookYIn,
			float deadzoneIn, boolean inGame) {
		preset = presetIn < 0 || presetIn >= PRESET_COUNT ? PRESET_LEGACY_CONSOLE : presetIn;
		enabled = enabledIn;
		lookEnabled = lookEnabledIn;
		invertLookY = invertLookYIn;
		deadzone = deadzoneIn < 0.0F ? 0.0F : (deadzoneIn > 0.5F ? 0.5F : deadzoneIn);

		// Poll the first connected gamepad exposed by the browser.
		int gamepad = -1;
		if (enabled) {
			for (int i = 0; i < MAX_GAMEPADS; i++) {
				if (PlatformInput.gamepadIsConnected(i)) {
					gamepad = i;
					break;
				}
			}
		}

		// Action state is recomputed from scratch every tick so released buttons
		// and centered sticks do not leave actions stuck "down".
		for (int i = 0; i < ACTION_COUNT; i++) {
			actionDown[i] = false;
			actionPressed[i] = false;
			actionReleased[i] = false;
		}

		if (gamepad < 0) {
			// The controller went away (or was disabled); report a release for
			// everything that was held on the previous tick so the game sees the
			// matching key-up events and does not stay stuck.
			for (int i = 0; i < ACTION_COUNT; i++) {
				if (prevActionDown[i]) {
					actionReleased[i] = true;
				}
				prevActionDown[i] = false;
			}
			return;
		}

		for (int b = 0; b < BUTTON_COUNT; b++) {
			buttonRaw[b] = PlatformInput.gamepadGetButton(gamepad, b);
		}

		int[] map = PRESET_BUTTONS[preset];
		for (int b = 0; b < BUTTON_COUNT; b++) {
			int action = map[b];
			if (action >= 0 && buttonRaw[b]) {
				actionDown[action] = true;
			}
		}

		// Left stick drives movement. The Gamepad API reports the left stick's
		// vertical axis as positive when pushed down, so forward is the negative
		// direction.
		float lx = PlatformInput.gamepadGetAxis(gamepad, 0);
		float ly = PlatformInput.gamepadGetAxis(gamepad, 1);
		if (ly < -deadzone) {
			actionDown[ACT_FORWARD] = true;
		}
		if (ly > deadzone) {
			actionDown[ACT_BACK] = true;
		}
		if (lx < -deadzone) {
			actionDown[ACT_LEFT] = true;
		}
		if (lx > deadzone) {
			actionDown[ACT_RIGHT] = true;
		}

		// Derive one-shot press/release transitions from the held state. The game
		// only updates its key binding state from input events, so the caller uses
		// these to queue synthetic key/mouse press and release events.
		for (int i = 0; i < ACTION_COUNT; i++) {
			if (actionDown[i] && !prevActionDown[i]) {
				actionPressed[i] = true;
			} else if (!actionDown[i] && prevActionDown[i]) {
				actionReleased[i] = true;
			}
			prevActionDown[i] = actionDown[i];
		}

		if (lookEnabled && inGame) {
			float rx = applyDeadzone(PlatformInput.gamepadGetAxis(gamepad, 2), deadzone);
			float ry = applyDeadzone(PlatformInput.gamepadGetAxis(gamepad, 3), deadzone);
			float dx = rx * LOOK_SCALE;
			float dy = ry * LOOK_SCALE;
			if (invertLookY) {
				dy = -dy;
			}
			PlatformInput.mouseAddVirtualDelta((int) dx, (int) dy);
		}
	}

	private static float applyDeadzone(float value, float dz) {
		if (value < 0.0F) {
			return -applyDeadzoneMagnitude(-value, dz);
		}
		return applyDeadzoneMagnitude(value, dz);
	}

	private static float applyDeadzoneMagnitude(float magnitude, float dz) {
		if (magnitude <= dz) {
			return 0.0F;
		}
		return (magnitude - dz) / (1.0F - dz);
	}

	/**
	 * Whether the current platform can read gamepads at all.
	 */
	public static boolean isSupported() {
		return PlatformInput.gamepadIsSupported();
	}

	/**
	 * Whether a gamepad is currently connected.
	 */
	public static boolean isConnected() {
		if (!isSupported()) {
			return false;
		}
		for (int i = 0; i < MAX_GAMEPADS; i++) {
			if (PlatformInput.gamepadIsConnected(i)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the logical action is currently held down.
	 */
	public static boolean isActionDown(int action) {
		if (action < 0 || action >= ACTION_COUNT) {
			return false;
		}
		return actionDown[action];
	}

	/**
	 * Consumes a logical action press. Returns {@code true} exactly once per
	 * press, on the tick the button transitioned from up to down.
	 */
	public static boolean isActionPressed(int action) {
		if (action < 0 || action >= ACTION_COUNT) {
			return false;
		}
		boolean pressed = actionPressed[action];
		actionPressed[action] = false;
		return pressed;
	}

	/**
	 * Consumes a logical action release. Returns {@code true} exactly once per
	 * release, on the tick the button transitioned from down to up (or the tick
	 * the controller is disconnected).
	 */
	public static boolean isActionReleased(int action) {
		if (action < 0 || action >= ACTION_COUNT) {
			return false;
		}
		boolean released = actionReleased[action];
		actionReleased[action] = false;
		return released;
	}
}
