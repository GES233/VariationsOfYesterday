package moe.chestnut.awa.voy.mixin;

import net.minecraft.entity.TntEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import moe.chestnut.awa.voy.TntExplosionQueue;

/**
 * 最小精确注入：只在 TNT 实体爆炸（private explode）时把事件压入
 * TntExplosionQueue，由 Scala 侧轮询后注入一次性扰动。不触碰方块爆炸、
 * 苦力怕等其他爆炸路径，也不改变爆炸本身。
 */
@Mixin(TntEntity.class)
public abstract class TntExplosionMixin {
	@Inject(method = "explode", at = @At("HEAD"))
	private void voy$enqueueTntDisturbance(CallbackInfo ci) {
		TntEntity self = (TntEntity) (Object) this;
		TntExplosionQueue.push(self.getEntityWorld(), self.getX(), self.getY(), self.getZ());
	}
}
