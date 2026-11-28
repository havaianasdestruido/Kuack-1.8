package net.minecraft.entity.ai;

import com.google.common.base.Predicate;
import java.util.List;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.lax1dude.eaglercraft.v1_8.log4j.LogManager;
import net.lax1dude.eaglercraft.v1_8.log4j.Logger;

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
public class EntityAIFindEntityNearest extends EntityAIBase {
	private static final Logger LOGGER = LogManager.getLogger();
	private EntityLiving mob;
	private final Predicate<EntityLivingBase> predicate;
	private final EntityAINearestAttackableTarget.Sorter sorter;
	private EntityLivingBase target;
	private Class<? extends EntityLivingBase> field_179439_f;

	public EntityAIFindEntityNearest(EntityLiving parEntityLiving, Class<? extends EntityLivingBase> parClass1) {
		this.mob = parEntityLiving;
		this.field_179439_f = parClass1;
		if (parEntityLiving instanceof EntityCreature) {
			LOGGER.warn("Use NearestAttackableTargetGoal.class for PathfinerMob mobs!");
		}

		this.predicate = new Predicate<EntityLivingBase>() {
			public boolean apply(EntityLivingBase entitylivingbase) {
				double d0 = EntityAIFindEntityNearest.this.getFollowRange();
				if (entitylivingbase.isSneaking()) {
					d0 *= 0.800000011920929D;
				}

				return entitylivingbase.isInvisible() ? false
						: ((double) entitylivingbase
								.getDistanceToEntity(EntityAIFindEntityNearest.this.mob) > d0 ? false
										: EntityAITarget.isSuitableTarget(EntityAIFindEntityNearest.this.mob,
												entitylivingbase, false, true));
			}
		};
		this.sorter = new EntityAINearestAttackableTarget.Sorter(parEntityLiving);
	}

	/**+
	 * Returns whether the EntityAIBase should begin execution.
	 */
	public boolean shouldExecute() {
		double d0 = this.getFollowRange();
		List list = this.mob.worldObj.getEntitiesWithinAABB(this.field_179439_f,
				this.mob.getEntityBoundingBox().expand(d0, 4.0D, d0), this.predicate);
		if (list.isEmpty()) {
			return false;
		} else {
			this.target = (EntityLivingBase) EntityAINearestAttackableTarget.findNearest(list,
					this.mob);
			return true;
		}
	}

	/**+
	 * Returns whether an in-progress EntityAIBase should continue
	 * executing
	 */
	public boolean continueExecuting() {
		EntityLivingBase entitylivingbase = this.mob.getAttackTarget();
		if (entitylivingbase == null) {
			return false;
		} else if (!entitylivingbase.isEntityAlive()) {
			return false;
		} else {
			double d0 = this.getFollowRange();
			return this.mob.getDistanceSqToEntity(entitylivingbase) > d0 * d0 ? false
					: !(entitylivingbase instanceof EntityPlayerMP)
							|| !((EntityPlayerMP) entitylivingbase).theItemInWorldManager.isCreative();
		}
	}

	/**+
	 * Execute a one shot task or start executing a continuous task
	 */
	public void startExecuting() {
		this.mob.setAttackTarget(this.target);
		super.startExecuting();
	}

	/**+
	 * Resets the task
	 */
	public void resetTask() {
		this.mob.setAttackTarget((EntityLivingBase) null);
		super.startExecuting();
	}

	protected double getFollowRange() {
		IAttributeInstance iattributeinstance = this.mob
				.getEntityAttribute(SharedMonsterAttributes.followRange);
		return iattributeinstance == null ? 16.0D : iattributeinstance.getAttributeValue();
	}
}