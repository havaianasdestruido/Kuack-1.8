package net.lax1dude.eaglercraft.v1_8;

import net.lax1dude.eaglercraft.v1_8.internal.PlatformInput;

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
public class Display {

	private static long lastSwap = 0l;

	/**
	 * Slack subtracted from the frame limiter's wait when the loop is paced by
	 * requestAnimationFrame. It makes the loop resume just before the vsync it is aiming at
	 * instead of exactly on it, which is what keeps the limiter from stealing a whole frame.
	 */
	private static final long FRAME_LIMIT_GUARD_MILLIS = 2l;

	public static int getWidth() {
		return PlatformInput.getWindowWidth();
	}
	
	public static int getHeight() {
		return PlatformInput.getWindowHeight();
	}

	public static boolean isActive() {
		return PlatformInput.getWindowFocused();
	}

	public static void create() {
		
	}

	public static void setTitle(String string) {
		EagRuntime.setWindowTitle(string);
	}

	public static boolean isCloseRequested() {
		return PlatformInput.isCloseRequested();
	}

	public static void setVSync(boolean enable) {
		PlatformInput.setVSync(enable);
	}

	public static boolean isVSyncSupported() {
		return PlatformInput.isVSyncSupported();
	}

	public static void update() {
		PlatformInput.update();
	}

	public static void sync(int limitFramerate) {
		boolean limitFPS = limitFramerate > 0 && limitFramerate < 1000;

		if(limitFPS) {
			// On the browser runtime the loop is restarted from requestAnimationFrame, so every
			// frame has already waited for the next display refresh by the time it gets here.
			// Sleeping on top of that wait is what made the camera appear to move in steps: the
			// extra delay pushes the next frame past the vsync it was aiming for, so the loop
			// alternates between presenting on one refresh and on two (a "60 fps" that visibly
			// judders). Only wait when the requested cap is genuinely below the display rate,
			// i.e. when requestAnimationFrame cannot enforce it on its own.
			int refreshRate = PlatformInput.getEstimatedRefreshRate();
			boolean pacedByDisplay = refreshRate > 0
					&& (limitFramerate >= refreshRate - 1 || !PlatformInput.isVSyncEnabled());
			if(!pacedByDisplay) {
				long millis = System.currentTimeMillis();
				long frameMillis = (1000l / limitFramerate) - (millis - lastSwap);
				if(refreshRate > 0) {
					frameMillis -= FRAME_LIMIT_GUARD_MILLIS;
				}
				if(frameMillis > 0l) {
					EagUtils.sleep(frameMillis);
				}
			}
		}

		lastSwap = System.currentTimeMillis();
	}

	/**
	 * The display refresh rate in Hz estimated from requestAnimationFrame callbacks, or 0 when
	 * the platform does not pace frames that way (desktop, or a browser without
	 * requestAnimationFrame support).
	 */
	public static int getEstimatedRefreshRate() {
		return PlatformInput.getEstimatedRefreshRate();
	}

	public static boolean contextLost() {
		return PlatformInput.contextLost();
	}
	
	public static boolean wasResized() {
		return PlatformInput.wasResized();
	}

	public static boolean isFullscreen() {
		return PlatformInput.isFullscreen();
	}

	public static void toggleFullscreen() {
		PlatformInput.toggleFullscreen();
	}

}
