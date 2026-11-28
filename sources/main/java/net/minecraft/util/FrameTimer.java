package net.minecraft.util;

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
public class FrameTimer {
	private final long[] frames = new long[240];
	private int lastIndex;
	private int counter;
	private int index;

	public void addFrame(long parLong1) {
		this.frames[this.index] = parLong1;
		++this.index;
		if (this.index == 240) {
			this.index = 0;
		}

		if (this.counter < 240) {
			this.lastIndex = 0;
			++this.counter;
		} else {
			this.lastIndex = this.parseIndex(this.index + 1);
		}

	}

	public int getLagometerValue(long parLong1, int parInt1) {
		double d0 = (double) parLong1 / 1.6666666E7D;
		return (int) (d0 * (double) parInt1);
	}

	public int getLastIndex() {
		return this.lastIndex;
	}

	public int getIndex() {
		return this.index;
	}

	public int parseIndex(int parInt1) {
		return parInt1 % 240;
	}

	public long[] getFrames() {
		return this.frames;
	}
}