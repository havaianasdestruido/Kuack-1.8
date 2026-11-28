package net.minecraft.network.play.server;

import java.io.IOException;
import java.util.Collection;

import com.google.common.collect.Lists;

import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;

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
public class S3EPacketTeams implements Packet<INetHandlerPlayClient> {
	private String name = "";
	private String displayName = "";
	private String prefix = "";
	private String suffix = "";
	private String nameTagVisibility;
	private int color;
	private Collection<String> players;
	private int action;
	private int friendlyFlags;

	public S3EPacketTeams() {
		this.nameTagVisibility = Team.EnumVisible.ALWAYS.internalName;
		this.color = -1;
		this.players = Lists.newArrayList();
	}

	public S3EPacketTeams(ScorePlayerTeam parScorePlayerTeam, int parInt1) {
		this.nameTagVisibility = Team.EnumVisible.ALWAYS.internalName;
		this.color = -1;
		this.players = Lists.newArrayList();
		this.name = parScorePlayerTeam.getRegisteredName();
		this.action = parInt1;
		if (parInt1 == 0 || parInt1 == 2) {
			this.displayName = parScorePlayerTeam.getTeamName();
			this.prefix = parScorePlayerTeam.getColorPrefix();
			this.suffix = parScorePlayerTeam.getColorSuffix();
			this.friendlyFlags = parScorePlayerTeam.func_98299_i();
			this.nameTagVisibility = parScorePlayerTeam.getNameTagVisibility().internalName;
			this.color = parScorePlayerTeam.getChatFormat().getColorIndex();
		}

		if (parInt1 == 0) {
			this.players.addAll(parScorePlayerTeam.getMembershipCollection());
		}

	}

	public S3EPacketTeams(ScorePlayerTeam parScorePlayerTeam, Collection<String> parCollection, int parInt1) {
		this.nameTagVisibility = Team.EnumVisible.ALWAYS.internalName;
		this.color = -1;
		this.players = Lists.newArrayList();
		if (parInt1 != 3 && parInt1 != 4) {
			throw new IllegalArgumentException("Method must be join or leave for player constructor");
		} else if (parCollection != null && !parCollection.isEmpty()) {
			this.action = parInt1;
			this.name = parScorePlayerTeam.getRegisteredName();
			this.players.addAll(parCollection);
		} else {
			throw new IllegalArgumentException("Players cannot be null/empty");
		}
	}

	/**+
	 * Reads the raw packet data from the data stream.
	 */
	public void readPacketData(PacketBuffer parPacketBuffer) throws IOException {
		this.name = parPacketBuffer.readStringFromBuffer(16);
		this.action = parPacketBuffer.readByte();
		if (this.action == 0 || this.action == 2) {
			this.displayName = parPacketBuffer.readStringFromBuffer(32);
			this.prefix = parPacketBuffer.readStringFromBuffer(16);
			this.suffix = parPacketBuffer.readStringFromBuffer(16);
			this.friendlyFlags = parPacketBuffer.readByte();
			this.nameTagVisibility = parPacketBuffer.readStringFromBuffer(32);
			this.color = parPacketBuffer.readByte();
		}

		if (this.action == 0 || this.action == 3 || this.action == 4) {
			int i = parPacketBuffer.readVarIntFromBuffer();

			for (int j = 0; j < i; ++j) {
				this.players.add(parPacketBuffer.readStringFromBuffer(40));
			}
		}

	}

	/**+
	 * Writes the raw packet data to the data stream.
	 */
	public void writePacketData(PacketBuffer parPacketBuffer) throws IOException {
		parPacketBuffer.writeString(this.name);
		parPacketBuffer.writeByte(this.action);
		if (this.action == 0 || this.action == 2) {
			parPacketBuffer.writeString(this.displayName);
			parPacketBuffer.writeString(this.prefix);
			parPacketBuffer.writeString(this.suffix);
			parPacketBuffer.writeByte(this.friendlyFlags);
			parPacketBuffer.writeString(this.nameTagVisibility);
			parPacketBuffer.writeByte(this.color);
		}

		if (this.action == 0 || this.action == 3 || this.action == 4) {
			parPacketBuffer.writeVarIntToBuffer(this.players.size());

			for (String s : this.players) {
				parPacketBuffer.writeString(s);
			}
		}

	}

	/**+
	 * Passes this Packet on to the NetHandler for processing.
	 */
	public void processPacket(INetHandlerPlayClient inethandlerplayclient) {
		inethandlerplayclient.handleTeams(this);
	}

	public String getName() {
		return this.name;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getPrefix() {
		return this.prefix;
	}

	public String getSuffix() {
		return this.suffix;
	}

	public Collection<String> getPlayers() {
		return this.players;
	}

	public int getAction() {
		return this.action;
	}

	public int getFriendlyFlags() {
		return this.friendlyFlags;
	}

	public int getColor() {
		return this.color;
	}

	public String getNameTagVisibility() {
		return this.nameTagVisibility;
	}
}