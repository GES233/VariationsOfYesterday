package moe.chestnut.awa.voy.hud

import net.fabricmc.fabric.api.client.command.v2.{ClientCommandManager, ClientCommandRegistrationCallback}
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import moe.chestnut.awa.voy.{MandateOverworld, MandateRuntime}
import moe.chestnut.awa.voy.inner.mandate.{CellReadout, PhaseLabel, PhaseReading, ReadoutParam, Regime}

/** Development HUD overlay that visualises the mandate readout
  * (`Regime`, `PhaseReading`, `c0` maintenance status, and continuous
  * state) of the chunks around the player using uniform-size color tiles.
  *
  * Toggled via the client-side `/voy hud [on|off]` command, registered only
  * in development environments by [[moe.chestnut.awa.voy.VariationsOfYesterdayClient]].
  */
object MandateHud:
  private val radius = 3 // 7x7 chunk matrix
  private val tileSize = 9
  private val tileGap = 2
  private val tileStep = tileSize + tileGap
  private val readoutParam = ReadoutParam()
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
      dispatcher.register(ClientCommandManager.literal("voy").`then`(hud))
    }

  private def render(context: DrawContext): Unit =
    val client = MinecraftClient.getInstance
    val player = client.player
    if player == null || client.options.hudHidden then return

    val centerX = player.getChunkPos.x
    val centerZ = player.getChunkPos.z
    val readout = MandateRuntime.readoutAt(centerX, centerZ)
    val center = readout.current
    val textRenderer = client.textRenderer

    val x0 = 8
    val y0 = 8
    val lineHeight = 10

    val gridCount = radius * 2 + 1
    val gridPixelSize = gridCount * tileStep - tileGap
    val panelWidth = 236
    val panelHeight = 36 + gridPixelSize + 22

    // Backdrop panel & border
    context.fill(x0 - 4, y0 - 4, x0 + panelWidth, y0 + panelHeight, ColPanelBg)
    drawBoxBorder(context, x0 - 4, y0 - 4, panelWidth + 4, panelHeight + 4, ColPanelBorder)

    // Header & telemetry lines
    val shelterTag = if readout.isMaintained then "脉冲庇护" else "沉滞(STAGNANT)"
    context.drawTextWithShadow(
      textRenderer,
      f"VOY Mandate [$centerX%d, $centerZ%d]  [$shelterTag]",
      x0,
      y0,
      tileColor(centerX, centerZ, readout)
    )
    context.drawTextWithShadow(
      textRenderer,
      f"v=${center.dissonance}%+.2f  s=${center.stress}%.3f  q=${center.activity}%.3f  M=${readout.sustenance}%.2f  c0=${readout.baselineCalming}%.2f  p=${readout.pulseDrive}%.2f  tol=${readout.pulseTolerance}%.2f  win=${readout.pulseState.remainingSuppressionAt(readout.simTime)}%.1f",
      x0,
      y0 + lineHeight,
      0xFFE2E6EC
    )
    context.drawTextWithShadow(
      textRenderer,
      s"regime=${readout.regime}  phase=${MandateOverworld.formatPhaseShort(readout.phase)}",
      x0,
      y0 + 2 * lineHeight,
      tileColor(centerX, centerZ, readout)
    )

    // 7x7 uniform square color-block grid
    val gridX = x0
    val gridY = y0 + 3 * lineHeight + 3

    for
      dz <- -radius to radius
      dx <- -radius to radius
    do
      val cx = centerX + dx
      val cz = centerZ + dz
      val cellReadout = MandateRuntime.readoutAt(cx, cz)
      val tx = gridX + (dx + radius) * tileStep
      val ty = gridY + (dz + radius) * tileStep
      val color = tileColor(cx, cz, cellReadout)

      context.fill(tx, ty, tx + tileSize, ty + tileSize, color)

      // Small inner dot if the chunk carries an active c0 baseline calming source
      if cellReadout.baselineCalming >= 0.01 then
        val dotX = tx + tileSize / 2 - 1
        val dotY = ty + tileSize / 2 - 1
        context.fill(dotX, dotY, dotX + 2, dotY + 2, ColC0Dot)

      // Crisp 1px white frame around the player's current chunk
      if dx == 0 && dz == 0 then
        drawBoxBorder(context, tx - 1, ty - 1, tileSize + 2, tileSize + 2, ColPlayerBorder)

    // Color swatch legend beside the 7x7 grid
    val legendX = gridX + gridPixelSize + 10
    val legendItems = Seq(
      (ColCalmMaintained, "Calm + 脉冲 (庇护)"),
      (ColCalmStagnant, "Calm (沉滞无脉冲)"),
      (ColStrained, "Strained (承压)"),
      (ColOscillating, "Oscillating (振荡)"),
      (ColSpike, "Spike (尖峰爆发)"),
      (ColBurnout, "Burnout (饱和失能)")
    )
    legendItems.zipWithIndex.foreach { case ((col, label), idx) =>
      val ly = gridY + idx * 12
      context.fill(legendX, ly + 1, legendX + 7, ly + 8, col)
      context.drawTextWithShadow(textRenderer, label, legendX + 11, ly, 0xFFD0D5DD)
    }

    // 20-division phase dial bar below the grid
    val barY = gridY + gridPixelSize + 6
    val activeTick = readout.dialTick
    val segW = 6
    val segH = 6
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

  private def tileColor(chunkX: Int, chunkZ: Int, r: CellReadout): Int =
    if !MandateRuntime.isLiveChunk(chunkX, chunkZ) then ColOutside
    else if r.current.dissonance >= readoutParam.spikeLevel then ColSpike
    else
      r.regime match
        case Regime.Burnout => ColBurnout
        case Regime.Oscillating => ColOscillating
        case Regime.Strained => ColStrained
        case Regime.Calm =>
          r.phase match
            case PhaseReading.Cycle(PhaseLabel.Excitation, _) => ColSpike
            case _ => if r.isMaintained then ColCalmMaintained else ColCalmStagnant

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
