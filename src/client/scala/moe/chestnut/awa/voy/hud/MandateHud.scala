package moe.chestnut.awa.voy.hud

import net.fabricmc.fabric.api.client.command.v2.{ClientCommandManager, ClientCommandRegistrationCallback}
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import moe.chestnut.awa.voy.MandateRuntime
import moe.chestnut.awa.voy.inner.mandate.MandateCurrent

/** Development HUD overlay that visualises the mandate stress/activity
  * snapshot of the chunks around the player.
  *
  * Toggled via the client-side `/voy hud [on|off]` command, registered only
  * in development environments by [[moe.chestnut.awa.voy.VariationsOfYesterdayClient]].
  */
object MandateHud:
  private val radius = 2
  private var enabled = false

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
    val center = MandateRuntime.stateAt(centerX, centerZ)
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

    for dz <- -radius to radius do
      val row = (-radius to radius).map { dx =>
        val state = MandateRuntime.stateAt(centerX + dx, centerZ + dz)
        val marker = statusMarker(state)
        s"$marker "
      }.mkString
      context.drawTextWithShadow(textRenderer, row, x, y + (dz + radius + 2) * lineHeight, 0xFFFFFFFF)

    context.drawTextWithShadow(
      textRenderer,
      "legend: . calm  o watch  ! unstable  X critical",
      x,
      y + (radius * 2 + 4) * lineHeight,
      0xFFBDBDBD
    )

  private def statusMarker(state: MandateCurrent): String =
    if state.stress >= 0.8 then "X"
    else if state.stress >= 0.5 || state.activity >= 0.4 then "!"
    else if state.stress >= 0.2 || state.activity >= 0.1 then "o"
    else "."

  private def statusColor(state: MandateCurrent): Int =
    if state.stress >= 0.8 then 0xFFFF5555
    else if state.stress >= 0.5 || state.activity >= 0.4 then 0xFFFFAA00
    else 0xFF80D080
