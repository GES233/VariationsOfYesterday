package moe.chestnut.awa.voy

import moe.chestnut.awa.voy.plant.{PlantBlocks, PlantItems}
import net.fabricmc.api.ModInitializer
import org.slf4j.{Logger, LoggerFactory}

/**
 * *Variations of Yesterday: Lie No.13 in Serenity*
 * is a Minecraft mod with no meaning or purpose.
**/
object VariationsOfYesterday extends ModInitializer:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  override def onInitialize(): Unit =
    PlantBlocks.initialize()
    PlantItems.initialize()

    logger.info("Variations of Yesterday with Scala initialized!")
