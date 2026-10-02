package moe.chestnut.awa.voy.inner.mandate

import java.nio.file.{Files, Paths, StandardOpenOption}

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

import scala.collection.mutable.ArrayBuffer

/** Opt-in performance probe for the lattice at scale — NOT part of the
  * normal test run. Run with:
  * {{{
  * VOY_BENCH=1 ./gradlew test --tests "*GridBenchmarkSpec*"
  * }}}
  *
  * Scenario under discussion: 20 nodes x 5-7 settlements per node, each
  * settlement up to a 100x100 cell grid (~1.2M cells total — 120x the
  * 10k-cell worldwide target of design draft section 1.2). Measures
  * per-tick integration cost plus the actor's per-tick immutable snapshot
  * publication against the 50 ms tick budget. Results are printed and
  * appended to build/voy-benchmark.txt.
  */
class GridBenchmarkSpec:
  private val Dt = 0.05

  // Shared immutable parameter instance keeps per-cell memory closer to
  // what a production wiring would use.
  private val sharedStressParam = StressParam()

  private def mkSettlement(side: Int): MandateGrid =
    MandateGrid.rectangular(side, side, diffusion = 0.002, coord =>
      val cell = MandateState(
        MandateCurrent(0.0, 0.0, 0.0, 0.0),
        MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
        sharedStressParam
      )
      cell.disturbance = if coord.x == side / 2 && coord.z == side / 2 then 0.08 else 0.002
      cell
    )

  private def mkWorld(nodes: Int, settlementsPerNode: Int, side: Int): Seq[MandateGrid] =
    Seq.fill(nodes * settlementsPerNode)(mkSettlement(side))

  private def stats(xs: ArrayBuffer[Double]): String =
    val s = xs.sorted
    f"avg ${xs.sum / xs.size}%.1f / p50 ${s(s.size / 2)}%.1f / max ${s.last}%.1f ms"

  private def measure(label: String, grids: Seq[MandateGrid], ticks: Int): String =
    val cells = grids.map(_.cells.size).sum
    val t0 = System.nanoTime()
    for _ <- 1 to 5 do grids.foreach(_.step(Dt)) // JIT warmup
    val stepMs = ArrayBuffer.empty[Double]
    val snapshotMs = ArrayBuffer.empty[Double]
    var sink = 0.0
    for _ <- 1 to ticks do
      val a = System.nanoTime()
      grids.foreach(_.step(Dt))
      val b = System.nanoTime()
      // Mimic MandateAgent's per-tick immutable snapshot publication.
      val snapshot = grids.map(g => g.cells.iterator.map((c, cell) => c -> cell.getState).toMap)
      sink += snapshot.iterator.map(_.values.map(_.stress).sum).sum
      val c = System.nanoTime()
      stepMs += (b - a) / 1e6
      snapshotMs += (c - b) / 1e6
    f"$label: $cells%,d cells, $ticks ticks | step: ${stats(stepMs)} | snapshot: ${stats(snapshotMs)} | 50ms budget; sink=$sink%.0f"

  @Test def latticeScalesAgainstTickBudget(): Unit =
    assumeTrue(sys.env.contains("VOY_BENCH"), "opt-in benchmark: set VOY_BENCH=1 to run")

    val scenarios = Seq(
      // ~12k cells: around the worldwide 10k-cell design target (§1.2)
      ("design-target 20x6x(10x10)", mkWorld(20, 6, 10), 200),
      // ~100k cells: ten times the design target
      ("10x-over       20x6x(29x29)", mkWorld(20, 6, 29), 50),
      // ~1.2M cells: full scenario, every settlement at its 100x100 max
      ("full-spec      20x6x(100x100)", mkWorld(20, 6, 100), 20)
    )

    val lines = scenarios.map { case (label, grids, ticks) =>
      println(s"running $label ...")
      measure(label, grids, ticks)
    }
    lines.foreach(println)
    val out = Paths.get("build/voy-benchmark.txt")
    Files.writeString(
      out,
      lines.mkString("\n") + "\n",
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
