package moe.chestnut.awa.voy

import moe.chestnut.awa.voy.herbert.HerbertBlocks
import moe.chestnut.awa.voy.herbert.HerbertItems
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

/**
 * Variations of Yesterday: Lie No.13 in Serenity
 * */
object VariationsOfYesterday : ModInitializer {
    private val logger = LoggerFactory.getLogger("variations-of-yesterday")

	override fun onInitialize() {
		HerbertItems.initialize()
		HerbertBlocks.initialize()

		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		logger.info("Variations of Yesterday initialized!")
	}
}