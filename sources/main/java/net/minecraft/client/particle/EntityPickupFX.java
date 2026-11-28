package net.minecraft.client.particle;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.OpenGlHelper;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

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
public class EntityPickupFX extends EntityFX {
	private Entity item;
	private Entity target;
	private int age;
	private int maxAge;
	private float yOffset;
	private RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();

	public EntityPickupFX(World worldIn, Entity parEntity, Entity parEntity2, float parFloat1) {
		super(worldIn, parEntity.posX, parEntity.posY, parEntity.posZ, parEntity.motionX, parEntity.motionY,
				parEntity.motionZ);
		this.item = parEntity;
		this.target = parEntity2;
		this.maxAge = 3;
		this.yOffset = parFloat1;
	}

	/**+
	 * Renders the particle
	 */
	public void renderParticle(WorldRenderer var1, Entity var2, float f, float var4, float var5, float var6, float var7,
			float var8) {
		float f1 = ((float) this.age + f) / (float) this.maxAge;
		f1 = f1 * f1;
		double d0 = this.item.posX;
		double d1 = this.item.posY;
		double d2 = this.item.posZ;
		double d3 = this.target.lastTickPosX
				+ (this.target.posX - this.target.lastTickPosX) * (double) f;
		double d4 = this.target.lastTickPosY
				+ (this.target.posY - this.target.lastTickPosY) * (double) f
				+ (double) this.yOffset;
		double d5 = this.target.lastTickPosZ
				+ (this.target.posZ - this.target.lastTickPosZ) * (double) f;
		double d6 = d0 + (d3 - d0) * (double) f1;
		double d7 = d1 + (d4 - d1) * (double) f1;
		double d8 = d2 + (d5 - d2) * (double) f1;
		int i = this.getBrightnessForRender(f);
		int j = i % 65536;
		int k = i / 65536;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) j / 1.0F, (float) k / 1.0F);
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		d6 = d6 - interpPosX;
		d7 = d7 - interpPosY;
		d8 = d8 - interpPosZ;
		this.renderManager.renderEntityWithPosYaw(this.item, (double) ((float) d6), (double) ((float) d7),
				(double) ((float) d8), this.item.rotationYaw, f);
	}

	/**+
	 * Called to update the entity's position/logic.
	 */
	public void onUpdate() {
		++this.age;
		if (this.age == this.maxAge) {
			this.setDead();
		}

	}

	public int getFXLayer() {
		return 3;
	}
}