package net.minecraft.client.gui.spectator.categories;

import java.util.List;
import com.google.common.collect.Lists;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiSpectator;
import net.minecraft.client.gui.spectator.ISpectatorMenuObject;
import net.minecraft.client.gui.spectator.ISpectatorMenuView;
import net.minecraft.client.gui.spectator.SpectatorMenu;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
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
public class TeleportToTeam implements ISpectatorMenuView, ISpectatorMenuObject {
	private final List<ISpectatorMenuObject> items = Lists.newArrayList();

	public TeleportToTeam() {
		Minecraft minecraft = Minecraft.getMinecraft();

		for (ScorePlayerTeam scoreplayerteam : minecraft.theWorld.getScoreboard().getTeams()) {
			this.items.add(new TeleportToTeam.TeamSelectionObject(scoreplayerteam));
		}

	}

	public List<ISpectatorMenuObject> getItems() {
		return this.items;
	}

	public IChatComponent getPrompt() {
		return new ChatComponentText("Select a team to teleport to");
	}

	public void selectItem(SpectatorMenu spectatormenu) {
		spectatormenu.selectCategory(this);
	}

	public IChatComponent getSpectatorName() {
		return new ChatComponentText("Teleport to team member");
	}

	public void renderIcon(float var1, int var2) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(GuiSpectator.SPECTATOR_WIDGETS);
		Gui.drawModalRectWithCustomSizedTexture(0, 0, 16.0F, 0.0F, 16, 16, 256.0F, 256.0F);
	}

	public boolean isEnabled() {
		for (int i = 0, l = this.items.size(); i < l; ++i) {
			if (this.items.get(i).isEnabled()) {
				return true;
			}
		}

		return false;
	}

	class TeamSelectionObject implements ISpectatorMenuObject {
		private final ScorePlayerTeam team;
		private final ResourceLocation location;
		private final List<NetworkPlayerInfo> players;

		public TeamSelectionObject(ScorePlayerTeam parScorePlayerTeam) {
			this.team = parScorePlayerTeam;
			this.players = Lists.newArrayList();

			for (String s : parScorePlayerTeam.getMembershipCollection()) {
				NetworkPlayerInfo networkplayerinfo = Minecraft.getMinecraft().getNetHandler().getPlayerInfo(s);
				if (networkplayerinfo != null) {
					this.players.add(networkplayerinfo);
				}
			}

			this.location = DefaultPlayerSkin.getDefaultSkinLegacy();

			// TODO: program team skins

			if (!this.players.isEmpty()) {
				// TODO: pick a random member of this.players and use its team skin instead of the
				// legacy default skin that is assigned above
			}

		}

		public void selectItem(SpectatorMenu spectatormenu) {
			spectatormenu.selectCategory(new TeleportToPlayer(this.players));
		}

		public IChatComponent getSpectatorName() {
			return new ChatComponentText(this.team.getTeamName());
		}

		public void renderIcon(float f, int i) {
			int j = -1;
			String s = FontRenderer.getFormatFromString(this.team.getColorPrefix());
			if (s.length() >= 2) {
				j = Minecraft.getMinecraft().fontRendererObj.getColorCode(s.charAt(1));
			}

			if (j >= 0) {
				float f1 = (float) (j >> 16 & 255) / 255.0F;
				float f2 = (float) (j >> 8 & 255) / 255.0F;
				float f3 = (float) (j & 255) / 255.0F;
				Gui.drawRect(1, 1, 15, 15, MathHelper.rgb(f1 * f, f2 * f, f3 * f) | i << 24);
			}

			Minecraft.getMinecraft().getTextureManager().bindTexture(this.location);
			GlStateManager.color(f, f, f, (float) i / 255.0F);
			Gui.drawScaledCustomSizeModalRect(2, 2, 8.0F, 8.0F, 8, 8, 12, 12, 64.0F, 64.0F);
			Gui.drawScaledCustomSizeModalRect(2, 2, 40.0F, 8.0F, 8, 8, 12, 12, 64.0F, 64.0F);
		}

		public boolean isEnabled() {
			return !this.players.isEmpty();
		}
	}
}