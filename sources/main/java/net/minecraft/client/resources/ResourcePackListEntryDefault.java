package net.minecraft.client.resources;

import java.io.IOException;

import org.json.JSONException;

import net.lax1dude.eaglercraft.v1_8.log4j.LogManager;
import net.lax1dude.eaglercraft.v1_8.log4j.Logger;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.data.PackMetadataSection;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

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
public class ResourcePackListEntryDefault extends ResourcePackListEntry {
	private static final Logger logger = LogManager.getLogger();
	private final IResourcePack resourcePack;
	private final ResourceLocation resourcePackIcon;

	public ResourcePackListEntryDefault(GuiScreenResourcePacks resourcePacksGUIIn) {
		super(resourcePacksGUIIn);
		this.resourcePack = this.mc.getResourcePackRepository().rprDefaultResourcePack;

		DynamicTexture dynamictexture;
		try {
			dynamictexture = new DynamicTexture(this.resourcePack.getPackImage());
		} catch (IOException var4) {
			dynamictexture = TextureUtil.missingTexture;
		}

		this.resourcePackIcon = this.mc.getTextureManager().getDynamicTextureLocation("texturepackicon",
				dynamictexture);
	}

	protected int getResourcePackFormat() {
		return 1;
	}

	protected String getResourcePackDescription() {
		try {
			PackMetadataSection packmetadatasection = (PackMetadataSection) this.resourcePack
					.getPackMetadata(this.mc.getResourcePackRepository().rprMetadataSerializer, "pack");
			if (packmetadatasection != null) {
				return packmetadatasection.getPackDescription().getFormattedText();
			}
		} catch (JSONException jsonparseexception) {
			logger.error("Couldn\'t load metadata info", jsonparseexception);
		} catch (IOException ioexception) {
			logger.error("Couldn\'t load metadata info", ioexception);
		}

		return EnumChatFormatting.RED + "Missing " + "pack.mcmeta" + " :(";
	}

	protected boolean canMoveRight() {
		return false;
	}

	protected boolean canMoveLeft() {
		return false;
	}

	protected boolean canMoveUp() {
		return false;
	}

	protected boolean canMoveDown() {
		return false;
	}

	protected String getResourcePackName() {
		return "Default";
	}

	protected void bindResourcePackIcon() {
		this.mc.getTextureManager().bindTexture(this.resourcePackIcon);
	}

	protected boolean showHoverOverlay() {
		return false;
	}

	@Override
	protected String getEaglerFolderName() {
		return null;
	}
}