package moe.chestnut.awa.voy

import net.fabricmc.api.ModInitializer
import org.slf4j.{Logger, LoggerFactory}

/** *Variations of Yesterday: Lie No.13 in Serenity* is a Minecraft mod that
  * simulates a world born from a lost, incomplete blueprint.
  *
  * This world is caught in a tragic, cyclical loop of collapse and flawed
  * recovery, governed by a core 'Mandate Engine'. And the player enters not as
  * a conqueror, but as a debugger, attempting to understand and mend the
  * deep-seated bugs that plague the world's very existence.
  */
object VariationsOfYesterday extends ModInitializer:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  override def onInitialize(): Unit =
    logger.info("Initializing Variations of Yesterday...")
    // TODO: ...
    logger.info("Variations of Yesterday initialized!")
