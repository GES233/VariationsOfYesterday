package moe.chestnut.awa.voy.mixin;

import java.util.concurrent.CompletableFuture;
import moe.chestnut.awa.voy.mandate.TerrainCapture;
import net.minecraft.util.collection.BoundedRegionArray;
import net.minecraft.world.chunk.AbstractChunkHolder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkGenerating;
import net.minecraft.world.chunk.ChunkGenerationContext;
import net.minecraft.world.chunk.ChunkGenerationStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional observation only. Custom pipelines may bypass this stage entirely. */
@Mixin(ChunkGenerating.class)
public abstract class TerrainGenerationMixin {
    @Inject(method = "buildSurface", at = @At("RETURN"), cancellable = true, require = 0)
    private static void voy$captureSurface(
            ChunkGenerationContext context, ChunkGenerationStep step,
            BoundedRegionArray<AbstractChunkHolder> region, Chunk chunk,
            CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        // Chain before downstream generation, including asynchronous generators.
        // Sampling catches its own failures; original generation failures propagate.
        cir.setReturnValue(cir.getReturnValue().thenApply(result -> {
            TerrainCapture.capture(context.world(), result, true);
            return result;
        }));
    }
}
