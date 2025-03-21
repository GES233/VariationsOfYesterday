package moe.chestnut.awa.voy

import moe.chestnut.awa.voy.herbert.HerbertItems
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory import javax.naming.directory.ModificationItem

/**
 * Variations of Yesterday: Lie No.13 in Serenity
 * */
object VariationsOfYesterday : ModInitializer {
	private val logger = LoggerFactory.getLogger( "variations-of-yesterday")

	override fun onInitialize() {
		HerbertItems.initialize()


		logger.info("Variations of Yesterday initialized!")
	}
}