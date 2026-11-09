package moe.chestnut.awa.voy

import net.fabricmc.api.ModInitializer

import moe.chestnut.awa.voy.mandate.{MandateEffects, MandateOverworld, MandateRuntime}
import net.fabricmc.fabric.api.event.lifecycle.v1.{ServerLifecycleEvents, ServerTickEvents}
import org.slf4j.{Logger, LoggerFactory}

/** *Variations of Yesterday* is a Minecraft mod about a living world whose
  * ecological and social state responds to disturbance, care, and neglect.
  * The player observes the Mandate Engine, learns its delayed feedback, and
  * keeps local communities and landscapes within a recoverable range.
  */
object VariationsOfYesterday extends ModInitializer:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private def registerMandateEngine(): Unit =
    // Load the external config first: the engine's geometry, tick step and
    // parameters are all read from it when the engine starts.
    VoyConfig.reload()
    // Touch the effect holder first: registering status effects must happen
    // during mod init, before any server-driven `StatusEffectInstance` exists.
    MandateEffects.Stagnation
    MandateEffects.StagnationSpike
    MandateEffects.PulseSaturation
    // SERVER_STARTED, not STARTING: the engine restores its lattice from
    // the overworld's PersistentStateManager, which only exists after the
    // dimensions are loaded.
    ServerLifecycleEvents.SERVER_STARTED.register(server => MandateRuntime.start(server))
    // BEFORE_SAVE covers autosave, /save-all and the shutdown save (shutdown
    // also goes through MinecraftServer.save): flush the actor so the carrier
    // holds the latest tick, not a snapshot up to one persist period old.
    ServerLifecycleEvents.BEFORE_SAVE.register((_, _, _) => MandateRuntime.flush())
    ServerLifecycleEvents.SERVER_STOPPED.register(_ => MandateRuntime.stop())
    ServerTickEvents.END_SERVER_TICK.register(MandateRuntime.onTick(_))
    MandateOverworld.register()


  override def onInitialize(): Unit =
    logger.info("Initializing Variations of Yesterday...")

    this.registerMandateEngine()

    logger.info("Variations of Yesterday initialized!")
