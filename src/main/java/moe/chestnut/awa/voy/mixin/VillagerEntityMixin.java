package moe.chestnut.awa.voy.mixin;

import moe.chestnut.awa.voy.MandateEconomy;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stagnation economy pressure on villagers (design draft §3.2, 2026-11-09
 * revision): in chunks without a fresh pulse window, trades cost more,
 * breeding usually fails and golem summons are usually swallowed. The
 * special-price bonus is safe to add on every trade-screen open because
 * vanilla clears special prices when the customer leaves.
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {

    @Inject(method = "prepareOffersFor", at = @At("TAIL"))
    private void voy$stagnantTradePrices(PlayerEntity player, CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity) (Object) this;
        if (self.getWorld().isClient()) return;
        ChunkPos chunk = self.getChunkPos();
        int bonus = MandateEconomy.tradePriceBonus(chunk.x, chunk.z);
        if (bonus <= 0) return;
        for (TradeOffer offer : self.getOffers()) {
            if (offer != null) offer.increaseSpecialPrice(bonus);
        }
    }

    @Inject(method = "canBreed", at = @At("HEAD"), cancellable = true)
    private void voy$suppressBreeding(CallbackInfoReturnable<Boolean> cir) {
        VillagerEntity self = (VillagerEntity) (Object) this;
        if (self.getWorld().isClient()) return;
        ChunkPos chunk = self.getChunkPos();
        if (MandateEconomy.suppressBreeding(chunk.x, chunk.z)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "summonGolem", at = @At("HEAD"), cancellable = true)
    private void voy$suppressGolem(ServerWorld world, long time, int requiredCount, CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity) (Object) this;
        ChunkPos chunk = self.getChunkPos();
        if (MandateEconomy.suppressGolemSpawn(chunk.x, chunk.z)) {
            ci.cancel();
        }
    }
}
