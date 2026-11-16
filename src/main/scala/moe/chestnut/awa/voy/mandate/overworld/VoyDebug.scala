package moe.chestnut.awa.voy.mandate.overworld

import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.{CommandManager, ServerCommandSource}
import net.minecraft.text.Text

import moe.chestnut.awa.voy.mandate.MandateRuntime

/** Raw `v` / `s` / `c0` numbers are diagnostics, not player-facing text.
  *
  * Off by default; `/voy debug on` in-game turns them back on. The toggle is
  * a development tool only (design note 06 §1 constraint 5): a release build
  * never registers the command, so the numbers are unreachable there.
  */
object VoyDebug:
  @volatile var numbers: Boolean = false

  private def report(ctx: CommandContext[ServerCommandSource], text: String): Int =
    ctx.getSource.sendFeedback(() => Text.literal(text), false)
    1

  def registerCommand(): Unit =
    CommandRegistrationCallback.EVENT.register { (dispatcher, _, _) =>
      val debug = CommandManager.literal("debug")
        .executes { ctx =>
          numbers = !numbers
          report(ctx, s"§7[旧日变奏] 数值调试：${if numbers then "开" else "关"}")
        }
        .`then`(
          CommandManager.literal("on").executes { ctx =>
            numbers = true
            report(ctx, "§7[旧日变奏] 数值调试：开")
          }
        )
        .`then`(
          CommandManager.literal("off").executes { ctx =>
            numbers = false
            report(ctx, "§7[旧日变奏] 数值调试：关")
          }
        )
      // The chat input live-parses against the SERVER command tree, so the
      // client-only `/voy hud` (MandateHud) shows a spurious red error while
      // typing unless the server tree knows the node too. This placeholder
      // never runs on a modded client (the client dispatcher consumes
      // `/voy hud` first); it only answers on a dedicated server.
      val hudPlaceholder = CommandManager.literal("hud").executes { ctx =>
        report(ctx, "§7[旧日变奏] /voy hud 是客户端命令，直接在本地生效")
      }
      // Verification aid for ruling 16's `NoAnswer` outcome: a dead cell is
      // otherwise ~30 min of play away (design note 11 §2). Sets the player's
      // chunk to M = 0 (absorbing), then `/voy debug on` + ring a bell there.
      // Development-only: a release build never registers this node.
      val decay = CommandManager.literal("decay").executes { ctx =>
        val player = ctx.getSource.getPlayer
        val cx = player.getChunkPos.x
        val cz = player.getChunkPos.z
        MandateRuntime.setSustenanceAt(cx, cz, 0.0)
        report(
          ctx,
          s"§7[旧日变奏] 区块 [$cx,$cz] 菌网置 0（M=0 吸收态）——现在敲钟应得「叩钟无应」"
        )
      }
      dispatcher.register(
        CommandManager.literal("voy").`then`(debug).`then`(hudPlaceholder).`then`(decay)
      )
    }
end VoyDebug
