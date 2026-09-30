package moe.chestnut.awa.voy

import net.fabricmc.api.ModInitializer
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
    ServerLifecycleEvents.SERVER_STARTING.register(_ => MandateRuntime.start())
    ServerLifecycleEvents.SERVER_STOPPED.register(_ => MandateRuntime.stop())
    ServerTickEvents.END_SERVER_TICK.register(MandateRuntime.onTick(_))

  override def onInitialize(): Unit =
    logger.info("Initializing Variations of Yesterday...")

    this.registerMandateEngine()

    logger.info("Variations of Yesterday initialized!")
