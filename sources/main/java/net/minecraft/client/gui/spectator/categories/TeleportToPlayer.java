package net.minecraft.client.gui.spectator.categories;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiSpectator;
import net.minecraft.client.gui.spectator.ISpectatorMenuObject;
import net.minecraft.client.gui.spectator.ISpectatorMenuView;
import net.minecraft.client.gui.spectator.PlayerMenuObject;
import net.minecraft.client.gui.spectator.SpectatorMenu;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldSettings;

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
public class TeleportToPlayer implements ISpectatorMenuView, ISpectatorMenuObject {
	private static final Ordering<NetworkPlayerInfo> PROFILE_ORDER = Ordering
			.from(new Comparator<NetworkPlayerInfo>() {
				public int compare(NetworkPlayerInfo networkplayerinfo, NetworkPlayerInfo networkplayerinfo1) {
					return ComparisonChain.start().compare(networkplayerinfo.getGameProfile().getId(),
							networkplayerinfo1.getGameProfile().getId()).result();
				}
			});
	private final List<ISpectatorMenuObject> items;

	public TeleportToPlayer() {
		this(PROFILE_ORDER.sortedCopy(Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap()));
	}

	public TeleportToPlayer(Collection<NetworkPlayerInfo> parCollection) {
		this.items = Lists.newArrayList();

		List<NetworkPlayerInfo> lst = PROFILE_ORDER.sortedCopy(parCollection);
		for (int i = 0, l = lst.size(); i < l; ++i) {
			NetworkPlayerInfo networkplayerinfo = lst.get(i);
			if (networkplayerinfo.getGameType() != WorldSettings.GameType.SPECTATOR) {
				this.items.add(new PlayerMenuObject(networkplayerinfo.getGameProfile()));
			}
		}

	}

	public List<ISpectatorMenuObject> getItems() {
		return this.items;
	}

	public IChatComponent getPrompt() {
		return new ChatComponentText("Select a player to teleport to");
	}

	public void selectItem(SpectatorMenu spectatormenu) {
		spectatormenu.selectCategory(this);
	}

	public IChatComponent getSpectatorName() {
		return new ChatComponentText("Teleport to player");
	}

	public void renderIcon(float var1, int var2) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(GuiSpectator.SPECTATOR_WIDGETS);
		Gui.drawModalRectWithCustomSizedTexture(0, 0, 0.0F, 0.0F, 16, 16, 256.0F, 256.0F);
	}

	public boolean isEnabled() {
		return !this.items.isEmpty();
	}
}