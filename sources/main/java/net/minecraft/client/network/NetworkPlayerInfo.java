package net.minecraft.client.network;

import net.lax1dude.eaglercraft.v1_8.mojang.authlib.GameProfile;
import net.lax1dude.eaglercraft.v1_8.profile.SkinModel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
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
public class NetworkPlayerInfo {
	private final GameProfile gameProfile;
	private WorldSettings.GameType gameType;
	private int responseTime;
	private String skinType;
	private IChatComponent displayName;
	private int lastHealth = 0;
	private int displayHealth = 0;
	private long lastHealthTime = 0L;
	private long healthBlinkTime = 0L;
	private long renderVisibilityId = 0L;

	public NetworkPlayerInfo(GameProfile parGameProfile) {
		this.gameProfile = parGameProfile;
	}

	public NetworkPlayerInfo(S38PacketPlayerListItem.AddPlayerData parAddPlayerData) {
		this.gameProfile = parAddPlayerData.getProfile();
		this.gameType = parAddPlayerData.getGameMode();
		this.responseTime = parAddPlayerData.getPing();
		this.displayName = parAddPlayerData.getDisplayName();
	}

	/**+
	 * Returns the GameProfile for the player represented by this
	 * NetworkPlayerInfo instance
	 */
	public GameProfile getGameProfile() {
		return this.gameProfile;
	}

	public WorldSettings.GameType getGameType() {
		return this.gameType;
	}

	public int getResponseTime() {
		return this.responseTime;
	}

	protected void setGameType(WorldSettings.GameType parGameType) {
		this.gameType = parGameType;
	}

	protected void setResponseTime(int parInt1) {
		this.responseTime = parInt1;
	}

	public boolean hasLocationSkin() {
		return true;
	}

	public String getSkinType() {
		return Minecraft.getMinecraft().getNetHandler().getSkinCache().getSkin(this.gameProfile)
				.getSkinModel().profileSkinType;
	}

	public SkinModel getEaglerSkinModel() {
		return Minecraft.getMinecraft().getNetHandler().getSkinCache().getSkin(this.gameProfile).getSkinModel();
	}

	public ResourceLocation getLocationSkin() {
		return Minecraft.getMinecraft().getNetHandler().getSkinCache().getSkin(this.gameProfile).getResourceLocation();
	}

	public ResourceLocation getLocationCape() {
		return Minecraft.getMinecraft().getNetHandler().getCapeCache().getCape(this.gameProfile.getId())
				.getResourceLocation();
	}

	public ScorePlayerTeam getPlayerTeam() {
		return Minecraft.getMinecraft().theWorld.getScoreboard().getPlayersTeam(this.getGameProfile().getName());
	}

	public void setDisplayName(IChatComponent displayNameIn) {
		this.displayName = displayNameIn;
	}

	public IChatComponent getDisplayName() {
		return this.displayName;
	}

	public int getLastHealth() {
		return this.lastHealth;
	}

	public void setLastHealth(int parInt1) {
		this.lastHealth = parInt1;
	}

	public int getDisplayHealth() {
		return this.displayHealth;
	}

	public void setDisplayHealth(int parInt1) {
		this.displayHealth = parInt1;
	}

	public long getLastHealthTime() {
		return this.lastHealthTime;
	}

	public void setLastHealthTime(long parLong1) {
		this.lastHealthTime = parLong1;
	}

	public long getHealthBlinkTime() {
		return this.healthBlinkTime;
	}

	public void setHealthBlinkTime(long parLong1) {
		this.healthBlinkTime = parLong1;
	}

	public long getRenderVisibilityId() {
		return this.renderVisibilityId;
	}

	public void setRenderVisibilityId(long parLong1) {
		this.renderVisibilityId = parLong1;
	}
}