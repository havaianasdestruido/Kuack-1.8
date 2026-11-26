package net.minecraft.client.renderer.block.model;

import net.lax1dude.eaglercraft.v1_8.minecraft.EaglerTextureAtlasSprite;
import net.minecraft.util.EnumFacing;

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
public class BakedQuad {
	protected final int[] vertexData;
	protected final int[] vertexDataWithNormals;
	protected final int tintIndex;
	protected final EnumFacing face;
	protected final EaglerTextureAtlasSprite sprite;

	public BakedQuad(int[] vertexDataIn, int tintIndexIn, EnumFacing faceIn) {
		this(vertexDataIn, null, tintIndexIn, faceIn, null);
	}

	public BakedQuad(int[] vertexDataIn, int[] vertexDataWithNormalsIn, int tintIndexIn, EnumFacing faceIn) {
		this(vertexDataIn, vertexDataWithNormalsIn, tintIndexIn, faceIn, null);
	}

	public BakedQuad(int[] vertexDataIn, int[] vertexDataWithNormalsIn, int tintIndexIn, EnumFacing faceIn,
			EaglerTextureAtlasSprite spriteIn) {
		this.vertexData = vertexDataIn;
		this.vertexDataWithNormals = vertexDataWithNormalsIn;
		this.tintIndex = tintIndexIn;
		this.face = faceIn;
		this.sprite = spriteIn;
	}

	public int[] getVertexData() {
		return this.vertexData;
	}

	public int[] getVertexDataWithNormals() {
		return this.vertexDataWithNormals;
	}

	public boolean hasTintIndex() {
		return this.tintIndex != -1;
	}

	public int getTintIndex() {
		return this.tintIndex;
	}

	public EnumFacing getFace() {
		return this.face;
	}

	public EaglerTextureAtlasSprite getSprite() {
		return this.sprite;
	}
}