package net.minecraft.client.util;

import org.json.JSONObject;

import net.lax1dude.eaglercraft.v1_8.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;

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
public class JsonBlendingMode {
	private static JsonBlendingMode lastApplied = null;
	private final int srcColorFactor;
	private final int srcAlphaFactor;
	private final int destColorFactor;
	private final int destAlphaFactor;
	private final int blendFunction;
	private final boolean separateBlend;
	private final boolean opaque;

	private JsonBlendingMode(boolean parFlag, boolean parFlag2, int parInt1, int parInt2, int parInt3, int parInt4,
			int parInt5) {
		this.separateBlend = parFlag;
		this.srcColorFactor = parInt1;
		this.destColorFactor = parInt2;
		this.srcAlphaFactor = parInt3;
		this.destAlphaFactor = parInt4;
		this.opaque = parFlag2;
		this.blendFunction = parInt5;
	}

	public JsonBlendingMode() {
		this(false, true, 1, 0, 1, 0, '\u8006');
	}

	public JsonBlendingMode(int parInt1, int parInt2, int parInt3) {
		this(false, false, parInt1, parInt2, parInt1, parInt2, parInt3);
	}

	public JsonBlendingMode(int parInt1, int parInt2, int parInt3, int parInt4, int parInt5) {
		this(true, false, parInt1, parInt2, parInt3, parInt4, parInt5);
	}

	public void apply() {
		if (!this.equals(lastApplied)) {
			if (lastApplied == null || this.opaque != lastApplied.isOpaque()) {
				lastApplied = this;
				if (this.opaque) {
					GlStateManager.disableBlend();
					return;
				}

				GlStateManager.enableBlend();
			}

			EaglercraftGPU.glBlendEquation(this.blendFunction);
			if (this.separateBlend) {
				GlStateManager.tryBlendFuncSeparate(this.srcColorFactor, this.destColorFactor, this.srcAlphaFactor,
						this.destAlphaFactor);
			} else {
				GlStateManager.blendFunc(this.srcColorFactor, this.destColorFactor);
			}

		}
	}

	public boolean equals(Object object) {
		if (this == object) {
			return true;
		} else if (!(object instanceof JsonBlendingMode)) {
			return false;
		} else {
			JsonBlendingMode jsonblendingmode = (JsonBlendingMode) object;
			return this.blendFunction != jsonblendingmode.blendFunction ? false
					: (this.destAlphaFactor != jsonblendingmode.destAlphaFactor ? false
							: (this.destColorFactor != jsonblendingmode.destColorFactor ? false
									: (this.opaque != jsonblendingmode.opaque ? false
											: (this.separateBlend != jsonblendingmode.separateBlend ? false
													: (this.srcAlphaFactor != jsonblendingmode.srcAlphaFactor ? false
															: this.srcColorFactor == jsonblendingmode.srcColorFactor)))));
		}
	}

	public int hashCode() {
		int i = this.srcColorFactor;
		i = 31 * i + this.srcAlphaFactor;
		i = 31 * i + this.destColorFactor;
		i = 31 * i + this.destAlphaFactor;
		i = 31 * i + this.blendFunction;
		i = 31 * i + (this.separateBlend ? 1 : 0);
		i = 31 * i + (this.opaque ? 1 : 0);
		return i;
	}

	public boolean isOpaque() {
		return this.opaque;
	}

	public static JsonBlendingMode parseBlendNode(JSONObject parJsonObject) {
		if (parJsonObject == null) {
			return new JsonBlendingMode();
		} else {
			int i = '\u8006';
			int j = 1;
			int k = 0;
			int l = 1;
			int i1 = 0;
			boolean flag = true;
			boolean flag1 = false;
			if (parJsonObject.get("func") instanceof String) {
				i = stringToBlendFunction(parJsonObject.getString("func"));
				if (i != '\u8006') {
					flag = false;
				}
			}

			if (parJsonObject.get("srcrgb") instanceof String) {
				j = stringToBlendFactor(parJsonObject.getString("srcrgb"));
				if (j != 1) {
					flag = false;
				}
			}

			if (parJsonObject.get("dstrgb") instanceof String) {
				k = stringToBlendFactor(parJsonObject.getString("dstrgb"));
				if (k != 0) {
					flag = false;
				}
			}

			if (parJsonObject.get("srcalpha") instanceof String) {
				l = stringToBlendFactor(parJsonObject.getString("srcalpha"));
				if (l != 1) {
					flag = false;
				}

				flag1 = true;
			}

			if (parJsonObject.get("dstalpha") instanceof String) {
				i1 = stringToBlendFactor(parJsonObject.getString("dstalpha"));
				if (i1 != 0) {
					flag = false;
				}

				flag1 = true;
			}

			return flag ? new JsonBlendingMode()
					: (flag1 ? new JsonBlendingMode(j, k, l, i1, i) : new JsonBlendingMode(j, k, i));
		}
	}

	private static int stringToBlendFunction(String parString1) {
		String s = parString1.trim().toLowerCase();
		return s.equals("add") ? '\u8006'
				: (s.equals("subtract") ? '\u800a'
						: (s.equals("reversesubtract") ? '\u800b'
								: (s.equals("reverse_subtract") ? '\u800b'
										: (s.equals("min") ? '\u8007' : (s.equals("max") ? '\u8008' : '\u8006')))));
	}

	private static int stringToBlendFactor(String parString1) {
		String s = parString1.trim().toLowerCase();
		s = s.replaceAll("_", "");
		s = s.replaceAll("one", "1");
		s = s.replaceAll("zero", "0");
		s = s.replaceAll("minus", "-");
		return s.equals("0") ? 0
				: (s.equals("1") ? 1
						: (s.equals("srccolor") ? 768
								: (s.equals("1-srccolor") ? 769
										: (s.equals("dstcolor") ? 774
												: (s.equals("1-dstcolor") ? 775
														: (s.equals("srcalpha") ? 770
																: (s.equals("1-srcalpha") ? 771
																		: (s.equals("dstalpha") ? 772
																				: (s.equals("1-dstalpha") ? 773
																						: -1)))))))));
	}
}