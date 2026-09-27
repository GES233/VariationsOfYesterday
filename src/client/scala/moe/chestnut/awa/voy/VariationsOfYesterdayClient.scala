package moe.chestnut.awa.voy

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import moe.chestnut.awa.voy.hud.MandateHud

object VariationsOfYesterdayClient extends ClientModInitializer:
  override def onInitializeClient(): Unit =
    if FabricLoader.getInstance.isDevelopmentEnvironment then
      MandateHud.register()
