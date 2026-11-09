package moe.chestnut.awa.voy.mixin;

import moe.chestnut.awa.voy.mandate.MandateEconomy;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stagnation economy pressure on crops (design draft §3.2, 2026-11-09
 * revision): in chunks without a fresh pulse window a share of growth
 * random-ticks is swallowed, so farms run at reduced speed instead of
 * stopping outright.
 */
@Mixin(CropBlock.class)
public abstract class CropBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void voy$stagnantCropGrowth(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (MandateEconomy.suppressCropGrowth(pos.getX() >> 4, pos.getZ() >> 4)) {
            ci.cancel();
        }
    }
}
