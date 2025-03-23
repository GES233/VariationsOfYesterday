package moe.chestnut.awa.voy

import moe.chestnut.awa.voy.herbert.HerbertItems
import moe.chestnut.awa.voy.plant.PlantBlocks
import net.fabricmc.api.ModInitializer
import org.slf4j.{Logger, LoggerFactory}

object VariationsOfYesterday extends ModInitializer:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  override def onInitialize(): Unit =
    PlantBlocks.initialize()
    HerbertItems.initialize()

    logger.info("Variations of Yesterday with Scala initialized!")
