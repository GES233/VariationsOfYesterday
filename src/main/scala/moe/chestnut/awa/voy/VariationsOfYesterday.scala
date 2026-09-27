package moe.chestnut.awa.voy

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.slf4j.{Logger, LoggerFactory}

/** *Variations of Yesterday* is a Minecraft mod about a living world whose
  * ecological and social state responds to disturbance, care, and neglect.
  * The player observes the Mandate Engine, learns its delayed feedback, and
  * keeps local communities and landscapes within a recoverable range.
  */
object VariationsOfYesterday extends ModInitializer:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private def registerActorsEvent(): Unit =
    ServerTickEvents.END_SERVER_TICK.register { server =>
      MandateRuntime.tick(server)
    }

  override def onInitialize(): Unit =
    logger.info("Initializing Variations of Yesterday...")

    this.registerActorsEvent()

    logger.info("Variations of Yesterday initialized!")
