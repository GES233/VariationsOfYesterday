package moe.chestnut.awa.voy.mandate

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer

import moe.chestnut.awa.voy.inner.action.MandateActionProtocol
import moe.chestnut.awa.voy.mandate.overworld.*

/** Entry wiring between the Minecraft Overworld and the Mandate Engine
  * (design draft §1.1, §1.4, §3.2).
  *
  * This object is only the seam: it registers the two world listeners and
  * forwards the server tick / outbound action streams. Every concrete
  * behaviour lives in the [[moe.chestnut.awa.voy.mandate.overworld]] modules,
  * one per channel:
  *
  *  - **World -> Mandate**
  *    - [[BlockBreakListener]]: breaking stone / ore injects land stress
  *      (`StressInject`); breaking a bell removes one calming source;
  *    - [[UseBlockRouter]]: ordered right-click dispatch —
  *      - [[Inspection]]: Clock / Compass readout of the chunk's `Regime`,
  *        `PhaseReading` (20-division dial) and shelter status;
  *      - [[FieldInoculation]]: wild inoculum colonizes stripped timber into
  *        `菌材`, fungal inoculum seeds `M` in depleted sub-toxic soil
  *        (design note 06 §7.1);
  *      - [[BellStrike]]: the bell paces/corrects the heartbeat, anchors
  *        baseline calming and fires an alpha-synapse pulse; the four verdicts
  *        of [[BellRitual]] are rendered as particles + layered sounds
  *        (design note 11 §1.5).
  *  - **Mandate -> Overworld**
  *    - [[StagnationRunner]]: the 20-tick effect pass that projects shelter
  *      onto players and villagers, and the 60 s calming-stock rebalance;
  *    - [[PhenomenaEmitter]]: sensory signature layer, the world as the
  *      instrument panel (design note 06 §3);
  *  - [[ActionFeedback]]: outbound `CellSpikeTriggered` / `RegimeTransitioned`
  *    become tremors and heralds on the server thread.
  *
  * [[VoyDebug]] gates the raw `v` / `s` / `c0` numbers: development builds
  * only, never reachable in a release (design note 06 §1 constraint 5).
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
