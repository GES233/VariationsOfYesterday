package moe.chestnut.awa.voy

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import moe.chestnut.awa.voy.hud.MandateHud
import moe.chestnut.awa.voy.mandate.ClientMandateCache

object VariationsOfYesterdayClient extends ClientModInitializer:
  override def onInitializeClient(): Unit =
    ClientMandateCache.register()
    if FabricLoader.getInstance.isDevelopmentEnvironment then
      MandateHud.register()
