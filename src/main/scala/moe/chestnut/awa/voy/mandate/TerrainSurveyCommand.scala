package moe.chestnut.awa.voy.mandate

import com.mojang.brigadier.arguments.IntegerArgumentType
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.{argument, literal}
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.world.Heightmap
import net.minecraft.world.chunk.ChunkStatus
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import scala.util.control.NonFatal
import moe.chestnut.awa.voy.inner.veins.{CellCoord, TerrainDrainage}

/** Read-only terrain probe: never loads or generates chunks. */
object TerrainSurveyCommand:
  def register(): Unit =
    CommandRegistrationCallback.EVENT.register((dispatcher, _, _) =>
      dispatcher.register(literal("voy").`then`(
        literal("terrain").requires(_.hasPermissionLevel(2))
          .executes(ctx => run(ctx.getSource, 4))
          .`then`(literal("export")
            .executes(ctx => exportSamples(ctx.getSource, 4))
            .`then`(argument("radius", IntegerArgumentType.integer(1, 8))
              .executes(ctx => exportSamples(ctx.getSource, IntegerArgumentType.getInteger(ctx, "radius")))))
          .`then`(argument("radius", IntegerArgumentType.integer(1, 8))
            .executes(ctx => run(ctx.getSource, IntegerArgumentType.getInteger(ctx, "radius"))))
      ))
      ()
    )

  private def run(source: ServerCommandSource, radius: Int): Int =
    val world = source.getWorld
    val center = CellCoord(
      Math.floor(source.getPosition.x).toInt >> 4,
      Math.floor(source.getPosition.z).toInt >> 4
    )
    val heights = (for
      x <- center.x - radius to center.x + radius
      z <- center.z - radius to center.z + radius
      chunk = world.getChunkManager.getChunk(x, z, ChunkStatus.FULL, false)
      if chunk != null
    yield
      // Median limits the influence of isolated pillars. Water surfaces and
      // buildings still participate; this is a surface survey, not geology.
      val samples = (for
        dx <- 0 until 16
        dz <- 0 until 16
      yield chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, dx, dz)).sorted
      CellCoord(x, z) -> ((samples(127).toDouble + samples(128)) / 2.0)
    ).toMap
    val survey = TerrainDrainage.survey(heights)
    def say(message: String): Unit =
      source.sendFeedback(() => Text.literal(message), false)
    def label(outlet: TerrainDrainage.Outlet): String =
      val kind = outlet.kind match
        case TerrainDrainage.EndKind.OpenBoundary => "扫描边界（归属待定）"
        case TerrainDrainage.EndKind.Flat => "平地（流向待解）"
        case TerrainDrainage.EndKind.Depression => "局部洼地"
      s"(${outlet.cell.x}, ${outlet.cell.z}) $kind"
    say(s"[地形勘测] 半径 $radius 区块；已采样 ${heights.size}/${(2 * radius + 1) * (2 * radius + 1)}，D8 下坡边 ${survey.downstream.size}。")
    val captures = heights.keys.toVector.map { c =>
      val chunk = world.getChunkManager.getChunk(c.x, c.z, ChunkStatus.FULL, false)
      (TerrainCapture.get(chunk, true), TerrainCapture.get(chunk, false))
    }
    say(s"生成采集：地表阶段 ${captures.count(_._1.nonEmpty)}；首次完整观测 ${captures.count(_._2.nonEmpty)}；仅回退 ${captures.count((early, full) => early.isEmpty && full.nonEmpty)}。")
    if !TerrainCapture.enabled(world) then say("此维度的自动采集已禁用；当前 D8 仍是现场勘测。")
    if heights.contains(center) then
      val chunk = world.getChunkManager.getChunk(center.x, center.z, ChunkStatus.FULL, false)
      val early = TerrainCapture.get(chunk, true)
      val full = TerrainCapture.get(chunk, false)
      full.foreach { sample =>
        val wet = sample.fluidDepth.count(_ > 0)
        say(s"脚下历史样本：地面中位高程 ${sample.medianGround.fold("无地面")(_.toString)}；液体覆盖 $wet/256 柱；最大液深 ${sample.fluidDepth.max}。")
      }
      (for a <- early; b <- full; changed <- a.changedColumns(b) yield changed).foreach { changed =>
        say(s"地表阶段→首次完整观测：$changed/256 柱高度改变；可能包含雕刻、结构或装饰，不能直接归因于玩家。")
      }
    survey.outlets.get(center) match
      case Some(outlet) =>
        val next = survey.downstream.get(center).map(c => s"(${c.x}, ${c.z})").getOrElse("无已确认下坡边")
        say(s"脚下区块 (${center.x}, ${center.z})：下游 $next；汇流量 ${survey.accumulation(center)}；归属 ${label(outlet)}。")
      case None => say("脚下区块尚未加载，无法认领。")
    survey.outlets.values.toVector.groupBy(identity).toVector
      .sortBy { case (outlet, cells) => (-cells.size, outlet.cell.x, outlet.cell.z) }
      .take(8).foreach { case (outlet, cells) =>
        say(s"局部汇水单元：${label(outlet)}，${cells.size} 区块。")
      }
    say("结果仅描述本次已加载地表；平地未排水、洼地未填充，边界汇流量为下界。")
    heights.size

  /** Explicit diagnostic export; immutable stored samples only, no terrain loads. */
  private def exportSamples(source: ServerCommandSource, radius: Int): Int =
    val world = source.getWorld
    val cx = Math.floor(source.getPosition.x).toInt >> 4
    val cz = Math.floor(source.getPosition.z).toInt >> 4
    val rows = new StringBuilder("chunk_x,chunk_z,block_x,block_z,stage,generator,bottom_y,top_y,surface_y,ground_y,fluid_depth\n")
    var count = 0
    for x <- cx - radius to cx + radius; z <- cz - radius to cz + radius do
      val chunk = world.getChunkManager.getChunk(x, z, ChunkStatus.FULL, false)
      if chunk != null then
        for early <- Seq(true, false); sample <- TerrainCapture.get(chunk, early) do
          count += 1
          val generator = "\"" + sample.generator.replace("\"", "\"\"") + "\""
          for i <- 0 until 256 do
            rows.append(s"$x,$z,${x * 16 + i % 16},${z * 16 + i / 16},${sample.stage},$generator,${sample.bottomY},${sample.topY},${sample.surface(i)},${sample.ground(i)},${sample.fluidDepth(i)}\n")
    if count == 0 then
      source.sendError(Text.literal("此范围没有已保存的地形样本；请检查采集配置，或前往新生成区域。"))
      return 0
    try
      val directory = source.getServer.getSavePath(WorldSavePath.ROOT).resolve("voy-terrain")
      Files.createDirectories(directory)
      val dimension = world.getRegistryKey.getValue.toString.replaceAll("[^a-zA-Z0-9_-]", "_")
      val path = Files.createTempFile(directory, s"$dimension-$cx-$cz-", ".csv")
      Files.writeString(path, rows.toString)
      val total = count
      source.sendFeedback(() => Text.literal(s"已导出 $total 份阶段样本：${path.toAbsolutePath}"), false)
      count
    catch
      case NonFatal(e) =>
        source.sendError(Text.literal(s"地形样本导出失败：${e.getMessage}"))
        0
