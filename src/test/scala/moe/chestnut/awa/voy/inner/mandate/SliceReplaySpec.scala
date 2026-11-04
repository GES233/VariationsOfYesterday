package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import moe.chestnut.awa.voy.inner.event.{EventDTO, EventMsg}

/** The raw event stream and the two facts the slice needs from it (§1.2,
  * §1.3, §1.4): replay determinism, and the *condition* under which a phase
  * composition along the advection chain is legitimate.
  */
class SliceReplaySpec:
  private val dt = SliceReplay.tickDt

  private def chain(cells: Int = 3): MandateGrid =
    val coords = (0 until cells).map(i => CellCoord(i, 0)).toSeq
    MandateGrid(
      coords.map(c => c -> SliceReplay.nodeState(0.0)).toMap,
      coords.sliding(2).filter(_.size == 2).map { case Seq(a, b) =>
        LatticeEdge(a, b, advection = 0.001)
      }.toSeq
    )

  @Test def `the same event stream replays to the same trajectory`(): Unit =
    val script = SliceReplay.script(c0 = 0.0, pulseTick = 60, PulseKind.AlphaShock)
    val a = SliceReplay.replay(chain(), script, ticks = 400, dt = dt)
    val b = SliceReplay.replay(chain(), script, ticks = 400, dt = dt)
    assertEquals(a.size, 400)
    for i <- a.indices do
      assertEquals(a(i)(CellCoord(0, 0)).dissonance, b(i)(CellCoord(0, 0)).dissonance, 0.0)
      assertEquals(a(i)(CellCoord(0, 0)).stress, b(i)(CellCoord(0, 0)).stress, 0.0)

  @Test def `the replay applies events before the tick that carries them`(): Unit =
    // A single cell: an injection stamped at tick 10 must be visible in the
    // snapshot of tick 10 and not before.
    val grid = MandateGrid(Map(CellCoord(0, 0) -> SliceReplay.nodeState(0.0)), Seq.empty)
    val script = Seq(SliceReplay.Scripted(10, EventMsg.StressInject(CellCoord(0, 0), 0.5)))
    val frames = SliceReplay.replay(grid, script, ticks = 12, dt = dt)
    assertEquals(0.0, frames(9)(CellCoord(0, 0)).stress, 1e-12)
    assertTrue(frames(10)(CellCoord(0, 0)).stress > 0.4, "the injection must land on its own tick")

  @Test def `events outside the lattice are dropped and do not disturb the stream`(): Unit =
    val grid = MandateGrid(Map(CellCoord(0, 0) -> SliceReplay.nodeState(0.0)), Seq.empty)
    val inside = Seq(SliceReplay.Scripted(3, EventMsg.StressInject(CellCoord(0, 0), 0.5)))
    val withNoise =
      inside :+ SliceReplay.Scripted(4, EventMsg.StressInject(CellCoord(7, 7), 5.0)) :+
        SliceReplay.Scripted(5, EventMsg.FirePulse(CellCoord(7, 7), 100.0))
    val clean = SliceReplay.replay(grid, inside, ticks = 6, dt = dt)
    val noisy =
      SliceReplay.replay(MandateGrid(Map(CellCoord(0, 0) -> SliceReplay.nodeState(0.0)), Seq.empty), withNoise, ticks = 6, dt = dt)
    for i <- clean.indices do
      assertEquals(clean(i)(CellCoord(0, 0)).stress, noisy(i)(CellCoord(0, 0)).stress, 1e-12)

  @Test def `an ignited cell keeps the chain downstream and stays periodic`(): Unit =
    val truth = SliceReplay.truth("branch_01_baishui/up", c0 = 0.0, tEnd = 4000.0, measureFrom = 2500.0)
    assertTrue(truth.spikes >= 5, s"the collapsed c0 case must self-oscillate, saw ${truth.spikes}")
    assertTrue(truth.period.exists(p => p > 20.0 && p < 80.0), s"unexpected period ${truth.period}")
    assertTrue(truth.settledPeriod.exists(p => Math.abs(p - 39.47) < 0.25), s"settled period must match frozen s=1.0 oscillator (~39.47), got ${truth.settledPeriod}")
    assertTrue(truth.meanStress > 0.5, s"stress must climb, got ${truth.meanStress}")

  @Test def `an intact c0 absorbs the same load and the same pulse`(): Unit =
    val collapsed = SliceReplay.truth("branch_01_baishui/up", c0 = 0.0, tEnd = 4000.0,
      pulse = Some((300.0, PulseKind.AlphaShock)))
    val intact = SliceReplay.truth("branch_01_baishui/up", c0 = SliceContract.c0Intact, tEnd = 4000.0,
      pulse = Some((300.0, PulseKind.AlphaShock)))
    assertTrue(collapsed.spikes >= 20, s"collapsed must keep spiking, saw ${collapsed.spikes}")
    assertEquals(1, intact.spikes, "intact must show exactly one transient and then hold")
    assertTrue(intact.finalStress < 0.2, s"intact stress must stay low, got ${intact.finalStress}")

  @Test def `phase composition along the chain is only declared when the lag holds`(): Unit =
    val obs = SliceReplay.chainObservation(advection = 0.001, tEnd = 9000.0)
    assertTrue(obs.upstreamPeriod.isDefined && obs.downstreamPeriod.isDefined)
    // The two cells ignite from different stress histories, so the interval
    // between the paired surges does not stay put. The observation must
    // report that instead of a constant "transport delay".
    assertTrue(obs.lagDriftCycles.isDefined, s"the lag drift must be measured: $obs")

    if obs.composable then
      assertTrue(obs.lagDriftCycles.exists(_ <= 0.10), s"composable implies a constant lag: $obs")
    else
      assertTrue(
        obs.lagDriftCycles.exists(_ > 0.10),
        s"non-composable must be justified by a measured lag drift: $obs"
      )
      // And the drift should be gross, not a marginal call: a rule that
      // composed phases here would be wrong by many whole cycles.
      assertTrue(obs.lagDriftCycles.exists(_ > 1.0), s"the drift should be >1 cycle: $obs")
