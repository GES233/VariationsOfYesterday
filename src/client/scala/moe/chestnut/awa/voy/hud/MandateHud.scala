package moe.chestnut.awa.voy.hud

import net.fabricmc.fabric.api.client.command.v2.{ClientCommandManager, ClientCommandRegistrationCallback}
import net.fabricmc.fabric.api.client.networking.v1.{ClientPlayConnectionEvents, ClientPlayNetworking}
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import moe.chestnut.awa.voy.MandateRuntime
import moe.chestnut.awa.voy.inner.mandate.{MandateCurrent, MandateReadout}
import moe.chestnut.awa.voy.network.MandateHudPackets

/** Development HUD overlay that visualises the mandate stress/activity
  * snapshot of the chunks around the player.
  *
  * Toggled via the client-side `/voy hud [on|off]` command, registered only
  * in development environments by [[moe.chestnut.awa.voy.VariationsOfYesterdayClient]].
  * Synchronised from the server via [[MandateHudPackets.HudSnapshotPayload]]
  * so integrated and dedicated servers behave identically.
  */
object MandateHud:
  private val radius = MandateHudPackets.DefaultRadius
  private var enabled = false

  // -- client-synced snapshot from server packet (falls back to MandateRuntime in SP)
  @volatile private var syncedCells: Map[(Int, Int), MandateCurrent] = Map.empty
  @volatile private var syncedSimTime: Double = 0.0
  @volatile private var hasSyncedPacket: Boolean = false

  // -- fast-variable trace (scrolling dissonance-t line of the current chunk)
  /** Sampling interval (simulation time units): 0.05 = one tick at the
    * nominal rate. The horizontal axis aligns to simulation time, so tick
    * rate changes do not warp the trace.
    */
  private val SimSampleInterval = 0.05

  /** Window length: 900 x 0.05 = 45 time units, roughly one self-sustained
    * oscillation period (T ~ 39.5).
    */
  private val TraceCapacity = 900

  /** FHN spike amplitude is about +/-2.2; the vertical axis is pinned to
    * +/-2.5 (no auto-scaling) so phase stays readable.
    */
  private val TraceAbsMax = 2.5
  private val TraceWidth = 140
  private val TraceHeight = 40

  private val trace = scala.collection.mutable.ArrayDeque.empty[Double]
  private var traceChunkX = Int.MinValue
  private var traceChunkZ = Int.MinValue
  private var lastSampleSimTime = 0.0

  def register(): Unit =
    MandateHudPackets.registerCommon()
    registerNetworking()
    registerToggleCommand()
    HudRenderCallback.EVENT.register { (context: DrawContext, _: RenderTickCounter) =>
      if enabled then render(context)
    }

  private def registerNetworking(): Unit =
    ClientPlayNetworking.registerGlobalReceiver(
      MandateHudPackets.HudSnapshotPayload.Id,
      (payload, context) =>
        context.client().execute { () =>
          val r = payload.radius
          val side = 2 * r + 1
          val builder = Map.newBuilder[(Int, Int), MandateCurrent]
          var idx = 0
          for dz <- -r to r do
            for dx <- -r to r do
              if idx < payload.cells.size then
                builder += ((payload.centerChunkX + dx, payload.centerChunkZ + dz) -> payload.cells(idx))
              idx += 1
          syncedCells = builder.result()
          syncedSimTime = payload.simTime
          hasSyncedPacket = true
        }
    )
    ClientPlayConnectionEvents.JOIN.register { (_, _, _) =>
      resetSyncedState()
      if enabled then sendSubscription(true)
    }
    ClientPlayConnectionEvents.DISCONNECT.register { (_, _) =>
      resetSyncedState()
    }

  private def resetSyncedState(): Unit =
    syncedCells = Map.empty
    syncedSimTime = 0.0
    hasSyncedPacket = false
    trace.clear()
    lastSampleSimTime = 0.0

  private def setEnabled(value: Boolean): Unit =
    enabled = value
    sendSubscription(value)

  private def sendSubscription(value: Boolean): Unit =
    if ClientPlayNetworking.canSend(MandateHudPackets.HudSubscribePayload.Id) then
      ClientPlayNetworking.send(MandateHudPackets.HudSubscribePayload(value))

  private def registerToggleCommand(): Unit =
    ClientCommandRegistrationCallback.EVENT.register { (dispatcher, _) =>
      val hud = ClientCommandManager.literal("hud")
        .executes { _ =>
          setEnabled(!enabled)
          1
        }
        .`then`(ClientCommandManager.literal("on").executes { _ =>
          setEnabled(true)
          1
        })
        .`then`(ClientCommandManager.literal("off").executes { _ =>
          setEnabled(false)
          1
        })
      dispatcher.register(ClientCommandManager.literal("voy").`then`(hud))
    }

  private def readStateAt(chunkX: Int, chunkZ: Int): MandateCurrent =
    if hasSyncedPacket then
      syncedCells.getOrElse((chunkX, chunkZ), MandateCurrent(0.0, 0.0, 0.0, 0.0))
    else
      MandateRuntime.stateAt(chunkX, chunkZ)

  private def currentSimTime: Double =
    if hasSyncedPacket then syncedSimTime else MandateRuntime.getSimTime

  private def render(context: DrawContext): Unit =
    val client = MinecraftClient.getInstance
    val player = client.player
    if player == null || client.options.hudHidden then return

    val centerX = player.getChunkPos.x
    val centerZ = player.getChunkPos.z
    val center = readStateAt(centerX, centerZ)
    val readout = MandateReadout.classify(center)
    val textRenderer = client.textRenderer
    val x = 8
    val y = 8
    val lineHeight = 10

    context.drawTextWithShadow(textRenderer, "VOY mandate / nearby chunks", x, y, 0xFFE8D6)
    context.drawTextWithShadow(
      textRenderer,
      f"center [$centerX%d, $centerZ%d] stress=${center.stress}%.3f activity=${center.activity}%.3f",
      x,
      y + lineHeight,
      statusColor(center)
    )
    context.drawTextWithShadow(
      textRenderer,
      s"readout: ${readout.regime} / ${readout.phase}",
      x,
      y + 2 * lineHeight,
      0xFFD0E8FF
    )

    for dz <- -radius to radius do
      val row = (-radius to radius).map { dx =>
        val state = readStateAt(centerX + dx, centerZ + dz)
        val marker = statusMarker(state)
        s"$marker "
      }.mkString
      context.drawTextWithShadow(textRenderer, row, x, y + (dz + radius + 3) * lineHeight, 0xFFFFFFFF)

    context.drawTextWithShadow(
      textRenderer,
      "legend: . calm  o watch  ! unstable  X critical",
      x,
      y + (radius * 2 + 5) * lineHeight,
      0xFFBDBDBD
    )

    drawTrace(context, centerX, centerZ, center.dissonance, x, y + (radius * 2 + 7) * lineHeight)

  /** Sample the fast variable of the chunk under the player into a ring
    * buffer and render it as a scrolling v-t trace. A min/max envelope per
    * pixel column keeps spikes visible when the window down-samples.
    */
  private def drawTrace(
      context: DrawContext,
      cx: Int,
      cz: Int,
      v: Double,
      x: Int,
      y: Int
  ): Unit =
    sampleTrace(cx, cz, v)
    val textRenderer = MinecraftClient.getInstance.textRenderer
    context.drawTextWithShadow(
      textRenderer,
      f"dissonance(t) @ [$cx%d, $cz%d] v=$v%+.3f (45tu window)",
      x,
      y,
      0xFFB0C4DE
    )
    val top = y + 10

    def vy(value: Double): Int =
      top + ((TraceAbsMax - value) / (2 * TraceAbsMax) * (TraceHeight - 1)).toInt

    // Backdrop, spike-threshold lines (+/-1.8) and the zero line.
    context.fill(x - 1, top - 1, x + TraceWidth + 1, top + TraceHeight + 1, 0x60000000)
    context.fill(x, vy(1.8), x + TraceWidth, vy(1.8) + 1, 0x40FF5555)
    context.fill(x, vy(-1.8), x + TraceWidth, vy(-1.8) + 1, 0x40FF5555)
    context.fill(x, vy(0.0), x + TraceWidth, vy(0.0) + 1, 0x60FFFFFF)

    val samples = trace.toVector
    val n = samples.size
    if n < 2 then return
    for px <- 0 until TraceWidth do
      val from = px * n / TraceWidth
      val to = Math.min(Math.max((px + 1) * n / TraceWidth, from + 1), n)
      var lo = Double.MaxValue
      var hi = Double.MinValue
      var i = from
      while i < to do
        val s = samples(i)
        if s < lo then lo = s
        if s > hi then hi = s
        i += 1
      val yTop = vy(hi)
      val yBot = vy(lo)
      context.fill(x + px, yTop, x + px + 1, Math.max(yBot, yTop) + 1, 0xFF66CCFF)

  private def sampleTrace(cx: Int, cz: Int, v: Double): Unit =
    // Chunk changed: drop the old chunk's history instead of mixing traces.
    if cx != traceChunkX || cz != traceChunkZ then
      trace.clear()
      traceChunkX = cx
      traceChunkZ = cz
      lastSampleSimTime = 0.0
    val simNow = currentSimTime
    // Engine restart re-zeroes the sim clock: drop stale samples.
    if simNow < lastSampleSimTime then trace.clear()
    if simNow - lastSampleSimTime >= SimSampleInterval || trace.isEmpty then
      trace.append(v)
      while trace.size > TraceCapacity do trace.removeHead()
      lastSampleSimTime = simNow

  private def statusMarker(state: MandateCurrent): String =
    if state.stress >= 0.8 then "X"
    else if state.stress >= 0.5 || state.activity >= 0.4 then "!"
    else if state.stress >= 0.2 || state.activity >= 0.1 then "o"
    else "."

  private def statusColor(state: MandateCurrent): Int =
    if state.stress >= 0.8 then 0xFFFF5555
    else if state.stress >= 0.5 || state.activity >= 0.4 then 0xFFFFAA00
    else 0xFF80D080

