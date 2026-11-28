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
public class Vec4b {
	private byte type;
	private byte x;
	private byte y;
	private byte rotation;

	public Vec4b(byte parByte1, byte parByte2, byte parByte3, byte parByte4) {
		this.type = parByte1;
		this.x = parByte2;
		this.y = parByte3;
		this.rotation = parByte4;
	}

	public Vec4b(Vec4b parVec4b) {
		this.type = parVec4b.type;
		this.x = parVec4b.x;
		this.y = parVec4b.y;
		this.rotation = parVec4b.rotation;
	}

	public byte getType() {
		return this.type;
	}

	public byte getX() {
		return this.x;
	}

	public byte getY() {
		return this.y;
	}

	public byte getRotation() {
		return this.rotation;
	}

	public boolean equals(Object object) {
		if (this == object) {
			return true;
		} else if (!(object instanceof Vec4b)) {
			return false;
		} else {
			Vec4b vec4b = (Vec4b) object;
			return this.type != vec4b.type ? false
					: (this.rotation != vec4b.rotation ? false
							: (this.x != vec4b.x ? false
									: this.y == vec4b.y));
		}
	}

	public int hashCode() {
		int i = this.type;
		i = 31 * i + this.x;
		i = 31 * i + this.y;
		i = 31 * i + this.rotation;
		return i;
	}
}