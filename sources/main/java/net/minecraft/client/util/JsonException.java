package net.minecraft.client.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Lists;

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
public class JsonException extends IOException {
	private final List<JsonException.Entry> entries = Lists.newArrayList();
	private final String field_151382_b;

	public JsonException(String parString1) {
		this.entries.add(new JsonException.Entry());
		this.field_151382_b = parString1;
	}

	public JsonException(String parString1, Throwable parThrowable) {
		super(parThrowable);
		this.entries.add(new JsonException.Entry());
		this.field_151382_b = parString1;
	}

	public void prependJsonKey(String parString1) {
		((JsonException.Entry) this.entries.get(0)).addJsonKey(parString1);
	}

	public void setFilenameAndFlush(String parString1) {
		((JsonException.Entry) this.entries.get(0)).filename = parString1;
		this.entries.add(0, new JsonException.Entry());
	}

	public String getMessage() {
		return "Invalid " + ((JsonException.Entry) this.entries.get(this.entries.size() - 1)).toString()
				+ ": " + this.field_151382_b;
	}

	public static JsonException forException(Exception parException) {
		if (parException instanceof JsonException) {
			return (JsonException) parException;
		} else {
			String s = parException.getMessage();
			if (parException instanceof FileNotFoundException) {
				s = "File not found";
			}

			return new JsonException(s, parException);
		}
	}

	public static class Entry {
		private String filename;
		private final List<String> jsonKeys;

		private Entry() {
			this.filename = null;
			this.jsonKeys = Lists.newArrayList();
		}

		private void addJsonKey(String parString1) {
			this.jsonKeys.add(0, parString1);
		}

		public String getJsonKeys() {
			return StringUtils.join(this.jsonKeys, "->");
		}

		public String toString() {
			return this.filename != null
					? (!this.jsonKeys.isEmpty() ? this.filename + " " + this.getJsonKeys()
							: this.filename)
					: (!this.jsonKeys.isEmpty() ? "(Unknown file) " + this.getJsonKeys() : "(Unknown file)");
		}
	}
}