package net.lax1dude.eaglercraft.v1_8.internal;

import java.util.LinkedList;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.internal.teavm.TeaVMUtils;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.browser.TimerHandler;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.events.WheelEvent;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.webgl.WebGLFramebuffer;
import org.teavm.jso.webgl.WebGLRenderbuffer;

import net.lax1dude.eaglercraft.v1_8.EagUtils;
import net.lax1dude.eaglercraft.v1_8.internal.teavm.EarlyLoadScreen;
import net.lax1dude.eaglercraft.v1_8.internal.teavm.WebGL2RenderingContext;

import static net.lax1dude.eaglercraft.v1_8.internal.teavm.WebGL2RenderingContext.*;

/**
 * Copyright (c) 2022-2023 lax1dude, ayunami2000. All Rights Reserved.
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
public class PlatformInput {

	private static Window win = null;
	private static HTMLCanvasElement canvas = null;
	static WebGL2RenderingContext context = null;

	static WebGLFramebuffer mainFramebuffer = null;
	static WebGLRenderbuffer mainColorRenderbuffer = null;
	static WebGLRenderbuffer mainDepthRenderbuffer = null;
	private static int framebufferWidth = -1;
	private static int framebufferHeight = -1;
	
	private static EventListener contextmenu = null;
	private static EventListener mousedown = null;
	private static EventListener mouseup = null;
	private static EventListener mousemove = null;
	private static EventListener mouseenter = null;
	private static EventListener mouseleave = null;
	private static EventListener keydown = null;
	private static EventListener keyup = null;
	private static EventListener keypress = null;
	private static EventListener wheel = null;
	private static EventListener pointerlock = null;

	private static final List<int[]> syntheticKeyEvents = new LinkedList();
	private static final List<int[]> syntheticMouseEvents = new LinkedList();

	private static int syntheticKey = 0;
	private static boolean syntheticKeyPressed = false;
	private static boolean syntheticKeyActive = false;

	private static int syntheticMouseButton = 0;
	private static boolean syntheticMousePressed = false;
	private static boolean syntheticMouseActive = false;

	private static boolean gamepadSupported = false;

	private static List<MouseEvent> mouseEvents = new LinkedList();
	private static List<KeyboardEvent> keyEvents = new LinkedList();

	private static int mouseX = 0;
	private static int mouseY = 0;
	private static double mouseDX = 0.0D;
	private static double mouseDY = 0.0D;
	private static double mouseDWheel = 0.0D;
	private static int width = 0;
	private static int height = 0;
	private static boolean enableRepeatEvents = true;
	private static boolean isWindowFocused = true;
	private static boolean isMouseOverWindow = true;
	static boolean unpressCTRL = false;

	private static int windowWidth = -1;
	private static int windowHeight = -1;
	private static int lastWasResizedWindowWidth = -2;
	private static int lastWasResizedWindowHeight = -2;
	
	private static MouseEvent currentEvent = null;
	private static KeyboardEvent currentEventK = null;
	private static boolean[] buttonStates = new boolean[8];
	private static boolean[] keyStates = new boolean[256];

	private static int functionKeyModifier = KeyboardConstants.KEY_F;

	private static long mouseUngrabTimer = 0l;
	private static long mouseGrabTimer = 0l;
	private static int mouseUngrabTimeout = -1;
	private static boolean pointerLockFlag = false;

	private static JSObject fullscreenQuery = null;

	public static boolean keyboardLockSupported = false;
	public static boolean lockKeys = false;

	private static boolean vsync = true;
	private static boolean vsyncSupport = false;

	/**
	 * Timestamp of the last requestAnimationFrame callback and the smoothed interval between
	 * two of them, both in milliseconds. The interval is the display refresh interval, which
	 * is what the frame loop is really paced by in a browser, so Display.sync can tell
	 * whether the configured frame rate limit still has anything to wait for.
	 */
	private static double lastAnimationFrameTimestamp = -1.0D;
	private static double animationFrameInterval = 0.0D;
	
	@JSBody(params = { }, script = "window.onbeforeunload = () => {return false;};")
	private static native void onBeforeCloseRegister();
	
	static void initHooks(Window window, HTMLCanvasElement canvaz) {
		win = window;
		canvas = canvaz;
		canvas.getStyle().setProperty("cursor", "default");
		win.addEventListener("contextmenu", contextmenu = new EventListener<MouseEvent>() {
			@Override
			public void handleEvent(MouseEvent evt) {
				evt.preventDefault();
				evt.stopPropagation();
			}
		});
		canvas.addEventListener("mousedown", mousedown = new EventListener<MouseEvent>() {
			@Override
			public void handleEvent(MouseEvent evt) {
				evt.preventDefault();
				evt.stopPropagation();
				int b = evt.getButton();
				buttonStates[b == 1 ? 2 : (b == 2 ? 1 : b)] = true;
				mouseEvents.add(evt);
			}
		});
		canvas.addEventListener("mouseup", mouseup = new EventListener<MouseEvent>() {
			@Override
			public void handleEvent(MouseEvent evt) {
				evt.preventDefault();
				evt.stopPropagation();
				int b = evt.getButton();
				buttonStates[b == 1 ? 2 : (b == 2 ? 1 : b)] = false;
				mouseEvents.add(evt);
			}
		});
		canvas.addEventListener("mousemove", mousemove = new EventListener<MouseEvent>() {
			@Override
			public void handleEvent(MouseEvent evt) {
				evt.preventDefault();
				evt.stopPropagation();
				mouseX = (int)(getOffsetX(evt) * win.getDevicePixelRatio());
				mouseY = (int)((canvas.getClientHeight() - getOffsetY(evt)) * win.getDevicePixelRatio());
				mouseDX += evt.getMovementX();
				mouseDY += -evt.getMovementY();
				if(hasBeenActive()) {
					mouseEvents.add(evt);
				}
			}
		});
		canvas.addEventListener("mouseenter", mouseenter = new EventListener<MouseEvent>() {
			@Override
			public void handleEvent(MouseEvent evt) {
				isMouseOverWindow = true;
			}
		});
		canvas.addEventListener("mouseleave", mouseleave = new EventListener<MouseEvent>() {
			@Override
			public void handleEvent(MouseEvent evt) {
				isMouseOverWindow = false;
			}
		});
		win.addEventListener("keydown", keydown = new EventListener<KeyboardEvent>() {
			@Override
			public void handleEvent(KeyboardEvent evt) {
				int w = getWhich(evt);
				if (w == 122) { // F11
					toggleFullscreen();
				}
				evt.preventDefault();
				evt.stopPropagation();
				if(!enableRepeatEvents && evt.isRepeat()) return;
				int ww = processFunctionKeys(w);
				keyStates[KeyboardConstants.getEaglerKeyFromBrowser(ww, ww == w ? evt.getLocation() : 0)] = true;
				keyEvents.add(evt);
			}
		});
		win.addEventListener("keyup", keyup = new EventListener<KeyboardEvent>() {
			@Override
			public void handleEvent(KeyboardEvent evt) {
				int w = getWhich(evt);
				evt.preventDefault();
				evt.stopPropagation();
				if(!enableRepeatEvents && evt.isRepeat()) return;
				int ww = processFunctionKeys(w);
				int eagKey = KeyboardConstants.getEaglerKeyFromBrowser(ww, ww == w ? evt.getLocation() : 0);
				keyStates[eagKey] = false;
				if(eagKey == functionKeyModifier) {
					for(int key = KeyboardConstants.KEY_F1; key <= KeyboardConstants.KEY_F10; ++key) {
						keyStates[key] = false;
					}
				}
				keyEvents.add(evt);
			}
		});
		win.addEventListener("keypress", keypress = new EventListener<KeyboardEvent>() {
			@Override
			public void handleEvent(KeyboardEvent evt) {
				evt.preventDefault();
				evt.stopPropagation();
				if(enableRepeatEvents && evt.isRepeat()) keyEvents.add(evt);
			}
		});
		canvas.addEventListener("wheel", wheel = new EventListener<WheelEvent>() {
			@Override
			public void handleEvent(WheelEvent evt) {
				evt.preventDefault();
				evt.stopPropagation();
				mouseEvents.add(evt);
				mouseDWheel += evt.getDeltaY();
			}
		});
		win.addEventListener("blur", new EventListener<WheelEvent>() {
			@Override
			public void handleEvent(WheelEvent evt) {
				isWindowFocused = false;
				for(int i = 0; i < buttonStates.length; ++i) {
					buttonStates[i] = false;
				}
				for(int i = 0; i < keyStates.length; ++i) {
					keyStates[i] = false;
				}
			}
		});
		win.addEventListener("focus", new EventListener<WheelEvent>() {
			@Override
			public void handleEvent(WheelEvent evt) {
				isWindowFocused = true;
			}
		});
		win.getDocument().addEventListener("pointerlockchange", pointerlock = new EventListener<WheelEvent>() {
			@Override
			public void handleEvent(WheelEvent evt) {
				Window.setTimeout(new TimerHandler() {
					@Override
					public void onTimer() {
						boolean grab = isPointerLocked();
						if(!grab) {
							if(pointerLockFlag) {
								mouseUngrabTimer = System.currentTimeMillis();
							}
						}
						pointerLockFlag = grab;
					}
				}, 60);
				mouseDX = 0.0D;
				mouseDY = 0.0D;
			}
		});

		try {
			gamepadSupported = checkGamepadSupported();
		}catch(Throwable t) {
			gamepadSupported = false;
		}

		try {
			onBeforeCloseRegister();
		}catch(Throwable t) {
		}

		try {
			asyncRequestAnimationFrame();
			vsyncSupport = true;
		}catch(Throwable t) {
			PlatformRuntime.logger.error("VSync is not supported on this browser!");
		}

		fullscreenQuery = fullscreenMediaQuery();
		if (keyboardLockSupported = checkKeyboardLockSupported()) {
			TeaVMUtils.addEventListener(fullscreenQuery, "change", new EventListener<Event>() {
				@Override
				public void handleEvent(Event evt) {
					if (!mediaQueryMatches(evt)) {
						unlockKeys();
						lockKeys = false;
					}
				}
			});
		}
	}

	@JSBody(params = { }, script = "if(window.navigator.userActivation){return window.navigator.userActivation.hasBeenActive;}else{return false;}")
	public static native boolean hasBeenActive();
	
	@JSBody(params = { "m" }, script = "return m.offsetX;")
	private static native int getOffsetX(MouseEvent m);
	
	@JSBody(params = { "m" }, script = "return m.offsetY;")
	private static native int getOffsetY(MouseEvent m);
	
	@JSBody(params = { "e" }, script = "return e.which;")
	private static native int getWhich(KeyboardEvent e);

	@JSBody(params = { }, script = "return !!(window.navigator.getGamepads);")
	private static native boolean checkGamepadSupported();

	@JSBody(params = { "i" }, script = "var gps = window.navigator.getGamepads(); return gps ? (gps[i] != null && gps[i].connected) : false;")
	private static native boolean getGamepadConnected(int i);

	@JSBody(params = { "i", "b" }, script = "var gps = window.navigator.getGamepads(); var g = gps ? gps[i] : null; var btn = g ? g.buttons[b] : null; return (btn && btn.pressed) ? 1 : 0;")
	private static native int getGamepadButton(int i, int b);

	@JSBody(params = { "i", "a" }, script = "var gps = window.navigator.getGamepads(); var g = gps ? gps[i] : null; return g ? (g.axes[a] || 0) : 0;")
	private static native float getGamepadAxis(int i, int a);
	
	public static int getWindowWidth() {
		return windowWidth;
	}

	public static int getWindowHeight() {
		return windowHeight;
	}

	public static boolean getWindowFocused() {
		return isWindowFocused || isPointerLocked();
	}

	public static boolean isCloseRequested() {
		return false;
	}

	public static void setVSync(boolean enable) {
		vsync = enable;
	}

	@JSBody(params = { "doc" }, script = "return (doc.visibilityState === \"visible\");")
	private static native boolean getVisibilityState(JSObject doc);

	public static void update() {
		double r = win.getDevicePixelRatio();
		int w = PlatformRuntime.parent.getClientWidth();
		int h = PlatformRuntime.parent.getClientHeight();
		int w2 = windowWidth = (int)(w * r);
		int h2 = windowHeight = (int)(h * r);
		if(canvas.getWidth() != w2) {
			canvas.setWidth(w2);
		}
		if(canvas.getHeight() != h2) {
			canvas.setHeight(h2);
		}
		flipBuffer();
		if (PlatformRuntime.recording) {
			long t = System.currentTimeMillis();
			if(t - PlatformRuntime.lastFrame > (1000 / 30)) {
				PlatformRuntime.recFrame();
				PlatformRuntime.lastFrame = t;
			}
		}
		if(getVisibilityState(win.getDocument())) {
			// Always pace the frame loop with requestAnimationFrame when the browser has it.
			// The old code only did that while VSync was enabled and otherwise resumed the
			// loop from a setTimeout(0) timer, which is not synchronized with the compositor
			// at all: frames were produced faster than the display could show them and the
			// canvas then presented them unevenly (one frame shown for one refresh, the next
			// for two). That uneven presentation is exactly what makes the camera look like it
			// advances in steps while it is being turned. The browser cannot present faster
			// than the display anyway, so the VSync toggle is now only honoured by the frame
			// rate limiter rather than by dropping out of the display-synchronized loop.
			if(vsyncSupport) {
				asyncRequestAnimationFrame();
			}else {
				EagUtils.sleep(0l);
			}
		}else {
			EagUtils.sleep(50l);
		}
	}

	@Async
	private static native void asyncRequestAnimationFrame();

	private static void asyncRequestAnimationFrame(AsyncCallback<Void> cb) {
		final boolean[] hasCompleted = new boolean[1];
		final int[] timeout = new int[] { -1 };
		Window.requestAnimationFrame((d) -> {
			if(!hasCompleted[0]) {
				hasCompleted[0] = true;
				Window.clearTimeout(timeout[0]);
				updateAnimationFrameInterval(d);
				cb.complete(null);
			}
		});
		timeout[0] = Window.setTimeout(() -> {
			if(!hasCompleted[0]) {
				hasCompleted[0] = true;
				cb.complete(null);
			}
		}, 50);
	}

	/**
	 * Feeds one requestAnimationFrame timestamp into the smoothed display interval estimate.
	 * Frames that arrive way too late (a tab that was hidden, or a multi-second hitch) are
	 * dropped instead of being mistaken for the refresh rate.
	 */
	private static void updateAnimationFrameInterval(double timestamp) {
		if(timestamp > 0.0D) {
			if(lastAnimationFrameTimestamp >= 0.0D) {
				double interval = timestamp - lastAnimationFrameTimestamp;
				if(interval >= 3.0D && interval <= 200.0D) {
					if(animationFrameInterval <= 0.0D) {
						animationFrameInterval = interval;
					}else {
						animationFrameInterval += (interval - animationFrameInterval) * 0.1D;
					}
				}
			}
			lastAnimationFrameTimestamp = timestamp;
		}
	}

	/**
	 * The display refresh rate in Hz as measured from requestAnimationFrame, or 0 when the
	 * browser does not pace the frame loop with requestAnimationFrame (in which case the
	 * Java-side frame rate limiter is the only thing pacing it, and it must keep sleeping).
	 */
	public static int getEstimatedRefreshRate() {
		if(!vsyncSupport || animationFrameInterval <= 0.0D) {
			return 0;
		}
		int rate = (int) (1000.0D / animationFrameInterval + 0.5D);
		if(rate < 24) {
			return 24;
		}
		if(rate > 360) {
			return 360;
		}
		return rate;
	}

	public static boolean isVSyncSupported() {
		return vsyncSupport;
	}

	/**
	 * Whether the VSync option is currently enabled.
	 */
	public static boolean isVSyncEnabled() {
		return vsync;
	}

	static void initFramebuffer(WebGL2RenderingContext ctx, WebGLFramebuffer fbo, int sw, int sh) {
		context = ctx;
		mainFramebuffer = fbo;
		
		framebufferWidth = windowWidth = sw;
		framebufferHeight = windowHeight = sh;
		
		ctx.bindFramebuffer(FRAMEBUFFER, fbo);

		mainColorRenderbuffer = ctx.createRenderbuffer();
		mainDepthRenderbuffer = ctx.createRenderbuffer();
		
		ctx.bindRenderbuffer(RENDERBUFFER, mainColorRenderbuffer);
		ctx.renderbufferStorage(RENDERBUFFER, RGBA8, sw, sh);
		ctx.framebufferRenderbuffer(FRAMEBUFFER, COLOR_ATTACHMENT0, RENDERBUFFER, mainColorRenderbuffer);
		
		ctx.bindRenderbuffer(RENDERBUFFER, mainDepthRenderbuffer);
		ctx.renderbufferStorage(RENDERBUFFER, DEPTH_COMPONENT32F, sw, sh);
		ctx.framebufferRenderbuffer(FRAMEBUFFER, DEPTH_ATTACHMENT, RENDERBUFFER, mainDepthRenderbuffer);
		
		ctx.drawBuffers(new int[] { COLOR_ATTACHMENT0 });
	}
	
	private static void flipBuffer() {
		
		context.bindFramebuffer(READ_FRAMEBUFFER, mainFramebuffer);
		context.bindFramebuffer(DRAW_FRAMEBUFFER, null);
		context.blitFramebuffer(0, 0, framebufferWidth, framebufferHeight, 0, 0, windowWidth, windowHeight, COLOR_BUFFER_BIT, NEAREST);
		
		context.bindFramebuffer(FRAMEBUFFER, mainFramebuffer);
		
		if(windowWidth != framebufferWidth || windowHeight != framebufferHeight) {
			framebufferWidth = windowWidth;
			framebufferHeight = windowHeight;
			
			context.bindRenderbuffer(RENDERBUFFER, mainColorRenderbuffer);
			context.renderbufferStorage(RENDERBUFFER, RGBA8, framebufferWidth, framebufferHeight);
			
			context.bindRenderbuffer(RENDERBUFFER, mainDepthRenderbuffer);
			context.renderbufferStorage(RENDERBUFFER, DEPTH_COMPONENT32F, framebufferWidth, framebufferHeight);
		}
		
	}
	
	public static boolean wasResized() {
		if(windowWidth != lastWasResizedWindowWidth || windowHeight != lastWasResizedWindowHeight) {
			lastWasResizedWindowWidth = windowWidth;
			lastWasResizedWindowHeight = windowHeight;
			return true;
		}else {
			return false;
		}
	}
	
	public static boolean keyboardNext() {
		if(unpressCTRL) { //un-press ctrl after copy/paste permission
			keyEvents.clear();
			currentEventK = null;
			keyStates[29] = false;
			keyStates[157] = false;
			keyStates[28] = false;
			keyStates[219] = false;
			keyStates[220] = false;
			unpressCTRL = false;
			return false;
		}
		currentEventK = null;
		syntheticKeyActive = false;
		if(!syntheticKeyEvents.isEmpty()) {
			int[] e = syntheticKeyEvents.remove(0);
			syntheticKey = e[0];
			syntheticKeyPressed = e[1] != 0;
			syntheticKeyActive = true;
			return true;
		}
		return !keyEvents.isEmpty() && (currentEventK = keyEvents.remove(0)) != null;
	}

	public static boolean keyboardGetEventKeyState() {
		if(syntheticKeyActive) return syntheticKeyPressed;
		return currentEventK == null? false : !currentEventK.getType().equals("keyup");
	}

	public static int keyboardGetEventKey() {
		if(syntheticKeyActive) return syntheticKey;
		int w = processFunctionKeys(getWhich(currentEventK));
		return currentEventK == null ? -1 : KeyboardConstants.getEaglerKeyFromBrowser(w, currentEventK.getLocation());
	}

	public static char keyboardGetEventCharacter() {
		if(syntheticKeyActive) return '\0';
		if(currentEventK == null) return '\0';
		String s = currentEventK.getKey();
		return currentEventK == null ? ' ' : (char) (s.length() > 1 ? '\0' : s.charAt(0));
	}

	public static boolean keyboardIsKeyDown(int key) {
		if(unpressCTRL) { //un-press ctrl after copy/paste permission
			keyStates[28] = false;
			keyStates[29] = false;
			keyStates[157] = false;
			keyStates[219] = false;
			keyStates[220] = false;
		}
		return key < 0 || key >= keyStates.length ? false : keyStates[key];
	}

	public static boolean keyboardIsRepeatEvent() {
		if(syntheticKeyActive) return false;
		return currentEventK == null ? false : currentEventK.isRepeat();
	}

	public static void keyboardEnableRepeatEvents(boolean b) {
		enableRepeatEvents = b;
	}

	public static boolean mouseNext() {
		currentEvent = null;
		syntheticMouseActive = false;
		if(!syntheticMouseEvents.isEmpty()) {
			int[] e = syntheticMouseEvents.remove(0);
			syntheticMouseButton = e[0];
			syntheticMousePressed = e[1] != 0;
			syntheticMouseActive = true;
			return true;
		}
		return !mouseEvents.isEmpty() && (currentEvent = mouseEvents.remove(0)) != null;
	}

	public static boolean mouseGetEventButtonState() {
		if(syntheticMouseActive) return syntheticMousePressed;
		return currentEvent == null ? false : currentEvent.getType().equals(MouseEvent.MOUSEDOWN);
	}

	public static int mouseGetEventButton() {
		if(syntheticMouseActive) return syntheticMouseButton;
		if(currentEvent == null || currentEvent.getType().equals(MouseEvent.MOUSEMOVE)) return -1;
		int b = currentEvent.getButton();
		return b == 1 ? 2 : (b == 2 ? 1 : b);
	}

	public static int mouseGetEventX() {
		if(syntheticMouseActive) return -1;
		return currentEvent == null ? -1 : (int)(currentEvent.getClientX() * win.getDevicePixelRatio());
	}

	public static int mouseGetEventY() {
		if(syntheticMouseActive) return -1;
		return currentEvent == null ? -1 : (int)((canvas.getClientHeight() - currentEvent.getClientY()) * win.getDevicePixelRatio());
	}

	public static int mouseGetEventDWheel() {
		if(syntheticMouseActive) return 0;
		return ("wheel".equals(currentEvent.getType())) ? (((WheelEvent)currentEvent).getDeltaY() == 0.0D ? 0 : (((WheelEvent)currentEvent).getDeltaY() > 0.0D ? -1 : 1)) : 0;
	}

	public static int mouseGetX() {
		return mouseX;
	}

	public static int mouseGetY() {
		return mouseY;
	}

	public static boolean mouseIsButtonDown(int i) {
		return buttonStates[i];
	}

	public static void keyboardQueueSyntheticEvent(int key, boolean pressed) {
		syntheticKeyEvents.add(new int[] { key, pressed ? 1 : 0 });
		if(syntheticKeyEvents.size() > 64) {
			syntheticKeyEvents.remove(0);
		}
	}

	public static void mouseQueueSyntheticEvent(int button, boolean pressed) {
		syntheticMouseEvents.add(new int[] { button, pressed ? 1 : 0 });
		if(syntheticMouseEvents.size() > 64) {
			syntheticMouseEvents.remove(0);
		}
	}

	public static void mouseAddVirtualDelta(int dx, int dy) {
		mouseDX += dx;
		mouseDY += dy;
	}

	public static boolean gamepadIsSupported() {
		return gamepadSupported;
	}

	/**
	 * Returns whether the gamepad at the given navigator index is present and connected.
	 */
	public static boolean gamepadIsConnected(int index) {
		if(!gamepadSupported) {
			return false;
		}
		try {
			return getGamepadConnected(index);
		}catch(Throwable t) {
			return false;
		}
	}

	public static boolean gamepadGetButton(int index, int button) {
		if(!gamepadSupported) {
			return false;
		}
		try {
			return getGamepadButton(index, button) != 0;
		}catch(Throwable t) {
			return false;
		}
	}

	public static float gamepadGetAxis(int index, int axis) {
		if(!gamepadSupported) {
			return 0.0F;
		}
		try {
			return getGamepadAxis(index, axis);
		}catch(Throwable t) {
			return 0.0F;
		}
	}

	public static int mouseGetDWheel() {
		int ret = (int)mouseDWheel;
		mouseDWheel = 0.0D;
		return ret;
	}

	public static void mouseSetGrabbed(boolean grab) {
		long t = System.currentTimeMillis();
		pointerLockFlag = grab;
		mouseGrabTimer = t;
		if(grab) {
			canvas.requestPointerLock();
			if(mouseUngrabTimeout != -1) Window.clearTimeout(mouseUngrabTimeout);
			mouseUngrabTimeout = -1;
			if(t - mouseUngrabTimer < 3000l) {
				mouseUngrabTimeout = Window.setTimeout(new TimerHandler() {
					@Override
					public void onTimer() {
						canvas.requestPointerLock();
					}
				}, 3100 - (int)(t - mouseUngrabTimer));
			}
		}else {
			if(mouseUngrabTimeout != -1) Window.clearTimeout(mouseUngrabTimeout);
			mouseUngrabTimeout = -1;
			Window.current().getDocument().exitPointerLock();
		}
		mouseDX = 0.0D;
		mouseDY = 0.0D;
	}

	public static boolean isMouseGrabbed() {
		return pointerLockFlag;
	}

	@JSBody(params = { }, script = "return document.pointerLockElement != null;")
	public static native boolean isPointerLocked();

	public static int mouseGetDX() {
		int ret = (int)mouseDX;
		mouseDX = 0.0D;
		return ret;
	}

	public static int mouseGetDY() {
		int ret = (int)mouseDY;
		mouseDY = 0.0D;
		return ret;
	}

	public static void mouseSetCursorPosition(int x, int y) {
		// obsolete
	}

	public static boolean mouseIsInsideWindow() {
		return isMouseOverWindow;
	}

	public static boolean contextLost() {
		return PlatformRuntime.webgl.isContextLost();
	}
	
	private static int processFunctionKeys(int key) {
		if(keyboardIsKeyDown(functionKeyModifier)) {
			if(key >= 49 && key <= 57) {
				key = key - 49 + 112;
			}
		}
		return key;
	}

	public static void setFunctionKeyModifier(int key) {
		functionKeyModifier = key;
	}

	public static void removeEventHandlers() {
		win.removeEventListener("contextmenu", contextmenu);
		canvas.removeEventListener("mousedown", mousedown);
		canvas.removeEventListener("mouseup", mouseup);
		canvas.removeEventListener("mousemove", mousemove);
		canvas.removeEventListener("mouseenter", mouseenter);
		canvas.removeEventListener("mouseleave", mouseleave);
		win.removeEventListener("keydown", keydown);
		win.removeEventListener("keyup", keyup);
		win.removeEventListener("keypress", keypress);
		canvas.removeEventListener("wheel", wheel);
		win.getDocument().removeEventListener("pointerlockchange", pointerlock);
		if(mouseUngrabTimeout != -1) {
			Window.clearTimeout(mouseUngrabTimeout);
			mouseUngrabTimeout = -1;
		}
	}

	public static void pressAnyKeyScreen() {
		if(mouseEvents.isEmpty() && keyEvents.isEmpty() && !hasBeenActive()) {
			EarlyLoadScreen.paintEnable();
			
			while(mouseEvents.isEmpty() && keyEvents.isEmpty()) {
				EagUtils.sleep(100l);
			}
		}
	}

	public static void clearEvenBuffers() {
		mouseEvents.clear();
		keyEvents.clear();
	}

	@JSBody(params = {}, script = "return window.matchMedia(\"(display-mode: fullscreen)\");")
	private static native JSObject fullscreenMediaQuery();

	@JSBody(params = { "mediaQuery" }, script = "return mediaQuery.matches;")
	private static native boolean mediaQueryMatches(JSObject mediaQuery);

	public static void toggleFullscreen() {
		if (isFullscreen()) {
			if (keyboardLockSupported) {
				unlockKeys();
				lockKeys = false;
			}
			exitFullscreen();
		} else {
			if (keyboardLockSupported) {
				lockKeys();
				lockKeys = true;
			}
			requestFullscreen(canvas);
		}
	}

	public static boolean isFullscreen() {
		return mediaQueryMatches(fullscreenQuery);
	}

	@JSBody(params = { }, script = "window.navigator.keyboard.lock();")
	private static native void lockKeys();

	@JSBody(params = { }, script = "window.navigator.keyboard.unlock();")
	private static native void unlockKeys();

	@JSBody(params = { }, script = "return !!(window.navigator.keyboard && window.navigator.keyboard.lock);")
	private static native boolean checkKeyboardLockSupported();

	@JSBody(params = { }, script = "document.exitFullscreen();")
	private static native void exitFullscreen();

	@JSBody(params = { "element" }, script = "element.requestFullscreen();")
	private	 static native void requestFullscreen(HTMLElement element);

	public static void showCursor(EnumCursorType cursor) {
		switch(cursor) {
		case DEFAULT:
		default:
			canvas.getStyle().setProperty("cursor", "default");
			break;
		case HAND:
			canvas.getStyle().setProperty("cursor", "pointer");
			break;
		case TEXT:
			canvas.getStyle().setProperty("cursor", "text");
			break;
		}
	}
	
}
