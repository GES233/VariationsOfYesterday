package moe.chestnut.awa.voy.mandate

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer

import moe.chestnut.awa.voy.inner.action.MandateActionProtocol
import moe.chestnut.awa.voy.mandate.overworld.*

/** The seam between the Minecraft Overworld and the Mandate engine: registers
  * the two world listeners and forwards the server tick / outbound action
  * streams. Every concrete behaviour lives in a `overworld/` module, one per
  * channel. See `AGENTS.md` §「一页图」for the data flow and the "change X, open
  * these files" table.
  *
  * Channels:
  *  - **world -> engine**: [[BlockBreakListener]] (breaking stone injects
  *    stress; breaking a bell removes a calming source), [[UseBlockRouter]]
  *    (ordered right-click dispatch: clock/compass readout, field inoculation,
  *    bell strike);
  *  - **engine -> world**: [[StagnationRunner]] (the 20-tick effect pass, the
  *    sensory phenomenon layer, and the 60 s calming-stock rebalance),
  *    [[ActionFeedback]] (spikes and regime transitions).
  *
  * `VoyDebug` gates the raw `v` / `s` / `c0` numbers and lives in `mandate/`,
  * not here: it is a development tool, not a channel (design note 06 §1
  * constraint 5).
  */
object MandateOverworld:
  def register(): Unit =
    BlockBreakListener.register()
    UseBlockRouter.register()
    // Raw-number diagnostics are a development tool only: they must never be
    // reachable in a release build (design note 06 §1 constraint 5).
    if FabricLoader.getInstance.isDevelopmentEnvironment then VoyDebug.registerCommand()

  def onServerTick(server: MinecraftServer): Unit =
    StagnationRunner.tick(server)

  /** Handle outbound actions drained from the Mandate actor on the server thread. */
  def onAction(server: MinecraftServer, action: MandateActionProtocol): Unit =
    ActionFeedback.handle(server, action)
end MandateOverworld
