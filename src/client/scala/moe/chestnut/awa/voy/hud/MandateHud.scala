package moe.chestnut.awa.voy.hud

import net.fabricmc.fabric.api.client.command.v2.{ClientCommandManager, ClientCommandRegistrationCallback}
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import moe.chestnut.awa.voy.mandate.{
  ClientCellView,
  ClientMandateCache,
  MandateWording,
  SyncedCellEntry
}
import moe.chestnut.awa.voy.inner.veins.{Heartbeat, PhaseLabel, PhaseReading, Regime}

/** Development HUD overlay that visualises the mandate readout
  * (`Regime`, `PhaseReading`, `MycelialHealth`, `c0` status, and continuous
  * state) of the chunks around the player using uniform-size color tiles.
  */
object MandateHud:
  private val radius = 3 // 7x7 chunk matrix
  private val tileSize = 9
  private val tileGap = 2
  private val tileStep = tileSize + tileGap
  private var enabled = false

  // Palette (ARGB)
  private val ColPanelBg = 0xAA101318
  private val ColPanelBorder = 0xFF343B46
  private val ColOutside = 0xFF252930
  private val ColCalmMaintained = 0xFF38B86D
  private val ColCalmStagnant = 0xFF526275
  private val ColStrained = 0xFFE6B432
  private val ColOscillating = 0xFFEB6828
  private val ColSpike = 0xFFFF2E2E
  private val ColBurnout = 0xFF7D2948
  private val ColC0Dot = 0xFFD6FFE4
  private val ColPlayerBorder = 0xFFFFFFFF

  def register(): Unit =
    registerToggleCommand()
    HudRenderCallback.EVENT.register { (context: DrawContext, _: RenderTickCounter) =>
      if enabled then render(context)
    }

  private def registerToggleCommand(): Unit =
    ClientCommandRegistrationCallback.EVENT.register { (dispatcher, _) =>
      val hud = ClientCommandManager.literal("hud")
        .executes { _ =>
          enabled = !enabled
          1
        }
        .`then`(ClientCommandManager.literal("on").executes { _ =>
          enabled = true
          1
        })
        .`then`(ClientCommandManager.literal("off").executes { _ =>
          enabled = false
          1
        })
      def forward(command: String): Int =
        val handler = MinecraftClient.getInstance.getNetworkHandler
        if handler != null then
          handler.sendPacket(net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket(command))
        1
      val debug = ClientCommandManager.literal("debug")
        .executes(_ => forward("voy debug"))
        .`then`(ClientCommandManager.literal("on").executes(_ => forward("voy debug on")))
        .`then`(ClientCommandManager.literal("off").executes(_ => forward("voy debug off")))
      dispatcher.register(ClientCommandManager.literal("voy").`then`(hud).`then`(debug))
    }

  private def render(context: DrawContext): Unit =
    val client = MinecraftClient.getInstance
    val player = client.player
    if player == null || client.options.hudHidden then return

    val centerX = player.getChunkPos.x
    val centerZ = player.getChunkPos.z
    val centerEntry = ClientMandateCache.get(centerX, centerZ)
    val textRenderer = client.textRenderer

    val x0 = 8
    val y0 = 8
    val lineHeight = 10

    val gridCount = radius * 2 + 1
    val gridPixelSize = gridCount * tileStep - tileGap

    // Record the player-centred cell into the change tracker (the 1 Hz sync
    // stream -> human-readable transitions), then measure the content so the
    // panel fits the longest line instead of clipping it.
    CellChangeTracker.record(centerX, centerZ, centerEntry)
    val changes = CellChangeTracker.history

    val centerColor = tileColor(centerEntry.map(_.view))
    val (headerText, tel1, tel2, phaseLine) = centerEntry match
      case Some(SyncedCellEntry(view, debugOpt)) =>
        val shelterTag = if view.isSheltered then "庇护" else "沉滞(STAGNANT)"
        val (t1, t2) = debugOpt match
          case Some(d) =>
            val per = d.beatPeriod.map(x => f"$x%.1f").getOrElse("--")
            val duty = d.beatDuty.map(x => f"$x%.3f").getOrElse("--")
            (
              f"v=${d.dissonance}%+.2f  s=${d.stress}%.3f  q=${d.activity}%.3f  M=${d.sustenance}%.2f  c0=${d.baselineCalming}%.2f",
              f"p=${d.pulseDrive}%.2f  win=${d.remainingShelter}%.1f  cd=${d.remainingCooldown}%.1f  hb=${Heartbeat.wireName(d.heartbeatOrdinal)}  per=$per  duty=$duty"
            )
          case None =>
            ("debug telemetry withheld (requires dev or OP)", "")
        val phaseStr = debugOpt
          .map(d => MandateWording.formatPhaseShort(d.phaseReading(view.phaseLabel)))
          .getOrElse(view.dialTick.map(t => s"刻度 $t/20").getOrElse("无周期"))
        (
          f"VOY Mandate [$centerX%d, $centerZ%d]  [$shelterTag]  菌:${view.mycelium.word}",
          t1,
          t2,
          s"regime=${view.regime}  phase=$phaseStr"
        )
      case None =>
        (
          f"VOY Mandate [$centerX%d, $centerZ%d]  [域外/未同步]",
          "outside active mandate lattice",
          "",
          "regime=-  phase=-"
        )

    val legendLabels = Seq(
      "Calm + 脉冲 (庇护)",
      "Calm (沉滞无脉冲)",
      "Strained (承压)",
      "Oscillating (振荡)",
      "Spike (尖峰爆发)",
      "Burnout (饱和失能)"
    )
    val legendW = gridPixelSize + 21 + legendLabels.map(textRenderer.getWidth(_)).max
    val textW = Seq(headerText, tel1, tel2, phaseLine).map(textRenderer.getWidth(_)).max
    val changesW = if changes.isEmpty then 0 else changes.map(textRenderer.getWidth(_)).max
    val panelWidth = math.max(236, math.max(math.max(textW, legendW), changesW) + 8)

    // 7x7 uniform square color-block grid
    val gridX = x0
    val gridY = y0 + 4 * lineHeight + 3
    val barY = gridY + gridPixelSize + 6
    val segH = 6
    val changesY = barY + segH + 8
    val contentBottom =
      if changes.isEmpty then barY + segH
      else changesY + (changes.size + 1) * lineHeight
    val panelHeight = contentBottom - y0 + 2

    // Backdrop panel & border
    context.fill(x0 - 4, y0 - 4, x0 + panelWidth, y0 + panelHeight, ColPanelBg)
    drawBoxBorder(context, x0 - 4, y0 - 4, panelWidth + 4, panelHeight + 4, ColPanelBorder)

    // Header & telemetry lines
    context.drawTextWithShadow(textRenderer, headerText, x0, y0, centerColor)
    context.drawTextWithShadow(textRenderer, tel1, x0, y0 + lineHeight, 0xFFE2E6EC)
    if tel2.nonEmpty then
      context.drawTextWithShadow(textRenderer, tel2, x0, y0 + 2 * lineHeight, 0xFFE2E6EC)
    context.drawTextWithShadow(textRenderer, phaseLine, x0, y0 + 3 * lineHeight, centerColor)

    for
      dz <- -radius to radius
      dx <- -radius to radius
    do
      val cx = centerX + dx
      val cz = centerZ + dz
      val cellView = ClientMandateCache.getView(cx, cz)
      val tx = gridX + (dx + radius) * tileStep
      val ty = gridY + (dz + radius) * tileStep
      val color = tileColor(cellView)

      context.fill(tx, ty, tx + tileSize, ty + tileSize, color)

      if cellView.exists(_.c0Sheltered) then
        val dotX = tx + tileSize / 2 - 1
        val dotY = ty + tileSize / 2 - 1
        context.fill(dotX, dotY, dotX + 2, dotY + 2, ColC0Dot)

      if dx == 0 && dz == 0 then
        drawBoxBorder(context, tx - 1, ty - 1, tileSize + 2, tileSize + 2, ColPlayerBorder)

    // Color swatch legend beside the 7x7 grid
    val legendX = gridX + gridPixelSize + 10
    val legendColors = Seq(ColCalmMaintained, ColCalmStagnant, ColStrained, ColOscillating, ColSpike, ColBurnout)
    legendColors.lazyZip(legendLabels).zipWithIndex.foreach { case ((col, label), idx) =>
      val ly = gridY + idx * 12
      context.fill(legendX, ly + 1, legendX + 7, ly + 8, col)
      context.drawTextWithShadow(textRenderer, label, legendX + 11, ly, 0xFFD0D5DD)
    }

    // 20-division phase dial bar below the grid
    val activeTick = centerEntry.flatMap(_.view.dialTick)
    val segW = 6
    val segGap = 2
    for tick <- 0 until 20 do
      val sx = gridX + tick * (segW + segGap)
      val isCurrent = activeTick.contains(tick)
      val baseCol =
        if tick >= 4 && tick <= 5 then 0xFF2B6E58 // absolute window [0.20, 0.30]
        else if tick >= 8 && tick <= 11 then 0xFF355E88 // relative window [0.40, 0.60]
        else 0xFF2E343E
      val fillCol = if isCurrent then 0xFFFFF066 else baseCol
      context.fill(sx, barY, sx + segW, barY + segH, fillCol)
      if isCurrent then
        drawBoxBorder(context, sx - 1, barY - 1, segW + 2, segH + 2, ColPlayerBorder)

    val dialLabel = activeTick.map(t => f"刻度 $t%02d/20").getOrElse("无周期")
    context.drawTextWithShadow(
      textRenderer,
      dialLabel,
      gridX + 20 * (segW + segGap) + 4,
      barY - 1,
      0xFFB8C0CC
    )

    // Recent change history of the player-centred cell, below the dial
    if changes.nonEmpty then
      context.drawTextWithShadow(textRenderer, "近期变化", gridX, changesY, 0xFF8A93A3)
      changes.zipWithIndex.foreach { case (line, idx) =>
        context.drawTextWithShadow(
          textRenderer,
          line,
          gridX,
          changesY + (idx + 1) * lineHeight,
          0xFFC8CFDA
        )
      }

  private def tileColor(viewOpt: Option[ClientCellView]): Int =
    viewOpt match
      case None => ColOutside
      case Some(v) =>
        if v.isSpiking then ColSpike
        else
          v.regime match
            case Regime.Burnout => ColBurnout
            case Regime.Oscillating => ColOscillating
            case Regime.Strained => ColStrained
            case Regime.Calm =>
              v.phaseLabel match
                case Some(PhaseLabel.Excitation) => ColSpike
                case _ => if v.isSheltered then ColCalmMaintained else ColCalmStagnant

  private def drawBoxBorder(
      context: DrawContext,
      x: Int,
      y: Int,
      w: Int,
      h: Int,
      color: Int
  ): Unit =
    context.fill(x, y, x + w, y + 1, color)
    context.fill(x, y + h - 1, x + w, y + h, color)
    context.fill(x, y, x + 1, y + h, color)
    context.fill(x + w - 1, y, x + w, y + h, color)
