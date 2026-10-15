package moe.chestnut.awa.voy

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.render.RenderLayer
import moe.chestnut.awa.voy.block.VoyBlocks
import moe.chestnut.awa.voy.hud.MandateHud

object VariationsOfYesterdayClient extends ClientModInitializer:
  override def onInitializeClient(): Unit =
    BlockRenderLayerMap.INSTANCE.putBlock(VoyBlocks.TelluricPlumb, RenderLayer.getCutout())
    if FabricLoader.getInstance.isDevelopmentEnvironment then
      MandateHud.register()

