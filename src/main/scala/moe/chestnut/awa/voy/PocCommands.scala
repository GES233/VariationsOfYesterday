package moe.chestnut.awa.voy

import com.mojang.brigadier.arguments.{DoubleArgumentType, IntegerArgumentType}
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.command.CommandManager.{argument, literal}
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

import moe.chestnut.awa.voy.inner.mandate.CellCoord

/** `/voy poc ...` 服务端命令族：init / reset / status / timescale /
  * disturb / pulse。全部为 PoC 调试与交互入口，不涉及正式村/寨语义。
  */
object PocCommands:

  def register(): Unit =
    CommandRegistrationCallback.EVENT.register { (dispatcher, _, _) =>
      val poc = literal("poc")
        .`then`(literal("init").executes(runInit))
        .`then`(literal("reset").executes(runReset))
        .`then`(literal("status").executes(runStatus))
        .`then`(
          literal("timescale").`then`(
            argument[java.lang.Double](
              "factor",
              DoubleArgumentType.doubleArg(0.1, 20.0)
            ).executes(runTimescale)
          )
        )
        .`then`(
          literal("disturb").`then`(
            argument[java.lang.Double](
              "amount",
              DoubleArgumentType.doubleArg(0.0)
            ).executes(runDisturb)
          )
        )
        .`then`(
          literal("pulse")
            .`then`(
              argument[java.lang.Double](
                "area",
                DoubleArgumentType.doubleArg(0.0)
              )
                .executes(ctx => runPulse(ctx, None))
                .`then`(
                  argument[java.lang.Integer](
                    "cell",
                    IntegerArgumentType.integer(0)
                  ).executes(ctx =>
                    runPulse(ctx, Some(IntegerArgumentType.getInteger(ctx, "cell")))
                  )
                )
            )
        )
      dispatcher.register(literal("voy").`then`(poc))
    }

  private def feedback(src: ServerCommandSource, message: String): Unit =
    src.sendFeedback(() => Text.literal(message), false)

  private def error(src: ServerCommandSource, message: String): Int =
    src.sendError(Text.literal(message))
    0

  private def runInit(ctx: CommandContext[ServerCommandSource]): Int =
    val src = ctx.getSource
    val player = src.getPlayerOrThrow // 控制台执行时由 brigadier 报错
    val anchor = CellCoord(player.getChunkPos.x, player.getChunkPos.z)
    if MandateRuntime.init(anchor) then
      feedback(
        src,
        s"【VOY·PoC】已初始化：以当前区块 (${anchor.x}, ${anchor.z}) 作为下游村庄/钟锚点，河流向上游（-x）延伸 ${MandateRuntime.chainLength} 个区块。"
      )
      1
    else error(src, "【VOY·PoC】已初始化过；如需重来请先 /voy poc reset。")

  private def runReset(ctx: CommandContext[ServerCommandSource]): Int =
    val src = ctx.getSource
    if MandateRuntime.isInitialized then
      MandateRuntime.reset()
      feedback(src, "【VOY·PoC】已重置，模拟停止。")
      1
    else error(src, "【VOY·PoC】尚未初始化，无需重置。")

  private def runStatus(ctx: CommandContext[ServerCommandSource]): Int =
    val src = ctx.getSource
    MandateRuntime.statusLines.foreach(feedback(src, _))
    if MandateRuntime.isInitialized then 1 else 0

  private def runTimescale(ctx: CommandContext[ServerCommandSource]): Int =
    val src = ctx.getSource
    val factor = DoubleArgumentType.getDouble(ctx, "factor")
    MandateRuntime.setTimescale(factor)
    feedback(
      src,
      f"【VOY·PoC】时间倍率已设为 ×${MandateRuntime.getTimescale}%.2f（高速下相位读数不可信，属预期）。"
    )
    1

  private def runDisturb(ctx: CommandContext[ServerCommandSource]): Int =
    val src = ctx.getSource
    val amount = DoubleArgumentType.getDouble(ctx, "amount")
    if MandateRuntime.setUpstreamDisturbance(amount) then
      feedback(src, f"【VOY·PoC】上游持续扰动已设为 ${amount}%.3f。")
      // 即时阈下反馈：上游区块处冒一缕烟（无害、不破坏方块）。
      MandateRuntime.upstreamChunk.foreach { up =>
        src.getWorld.spawnParticles(
          ParticleTypes.LARGE_SMOKE,
          up.x * 16 + 8.0, 80.0, up.z * 16 + 8.0,
          12, 0.6, 0.6, 0.6, 0.02
        )
      }
      1
    else error(src, "【VOY·PoC】未初始化，无法注入扰动。")

  private def runPulse(
      ctx: CommandContext[ServerCommandSource],
      cell: Option[Int]
  ): Int =
    val src = ctx.getSource
    val area = DoubleArgumentType.getDouble(ctx, "area")
    val index = cell.getOrElse(MandateRuntime.chainLength - 1) // 默认下游（钟）
    if MandateRuntime.pulse(index, area) then
      feedback(src, f"【VOY·PoC】已向 Cell $index 发放相位脉冲（面积 ${area}%.2f）。")
      1
    else
      error(src, s"【VOY·PoC】未初始化或 Cell 序号越界（0 至 ${MandateRuntime.chainLength - 1}）。")
