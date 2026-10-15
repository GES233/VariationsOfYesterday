package moe.chestnut.awa.voy

import com.mojang.serialization.MapCodec
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.{ServerLifecycleEvents, ServerTickEvents}
import net.fabricmc.fabric.api.networking.v1.{ServerPlayConnectionEvents, ServerPlayNetworking}
import net.minecraft.registry.Registry
import net.minecraft.world.gen.chunk.ChunkGenerator
import moe.chestnut.awa.voy.network.MandateHudPackets
import moe.chestnut.awa.voy.world.DemoValleyChunkGenerator
import org.slf4j.{Logger, LoggerFactory}

/** *Variations of Yesterday* is a Minecraft mod about a living world whose
  * ecological and social state responds to disturbance, care, and neglect.
  * The player observes the Mandate Engine, learns its delayed feedback, and
  * keeps local communities and landscapes within a recoverable range.
  */
object VariationsOfYesterday extends ModInitializer:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private def registerMandateEngine(): Unit =
    MandateHudPackets.registerCommon()
    ServerPlayNetworking.registerGlobalReceiver(
      MandateHudPackets.HudSubscribePayload.Id,
      (payload, context) =>
        MandateRuntime.setHudSubscribed(context.player().getUuid, payload.enabled)
    )
    ServerPlayConnectionEvents.DISCONNECT.register((handler, _) =>
      MandateRuntime.setHudSubscribed(handler.getPlayer.getUuid, false)
    )
    ServerLifecycleEvents.SERVER_STARTING.register(_ => MandateRuntime.start())
    ServerLifecycleEvents.SERVER_STOPPED.register(_ => MandateRuntime.stop())
    ServerTickEvents.END_SERVER_TICK.register(MandateRuntime.onTick(_))

  override def onInitialize(): Unit =
    logger.info("Initializing Variations of Yesterday...")

    Registry.register[MapCodec[? <: ChunkGenerator], MapCodec[DemoValleyChunkGenerator]](
      net.minecraft.registry.Registries.CHUNK_GENERATOR,
      DemoValleyChunkGenerator.GeneratorId,
      DemoValleyChunkGenerator.CODEC
    )

    this.registerMandateEngine()

    logger.info("Variations of Yesterday initialized!")

