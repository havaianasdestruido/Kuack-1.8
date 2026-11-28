package net.minecraft.client.renderer.entity.layers;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RendererLivingEntity;

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
public class LayerBipedArmor extends LayerArmorBase<ModelBiped> {
	public LayerBipedArmor(RendererLivingEntity<?> rendererIn) {
		super(rendererIn);
	}

	protected void initArmor() {
		this.modelLeggings = new ModelBiped(0.5F);
		this.modelArmor = new ModelBiped(1.0F);
	}

	protected void setModelPartVisible(ModelBiped modelbiped, int i) {
		this.setModelVisible(modelbiped);
		switch (i) {
		case 1:
			modelbiped.bipedRightLeg.showModel = true;
			modelbiped.bipedLeftLeg.showModel = true;
			break;
		case 2:
			modelbiped.bipedBody.showModel = true;
			modelbiped.bipedRightLeg.showModel = true;
			modelbiped.bipedLeftLeg.showModel = true;
			break;
		case 3:
			modelbiped.bipedBody.showModel = true;
			modelbiped.bipedRightArm.showModel = true;
			modelbiped.bipedLeftArm.showModel = true;
			break;
		case 4:
			modelbiped.bipedHead.showModel = true;
			modelbiped.bipedHeadwear.showModel = true;
		}

	}

	protected void setModelVisible(ModelBiped parModelBiped) {
		parModelBiped.setInvisible(false);
	}
}