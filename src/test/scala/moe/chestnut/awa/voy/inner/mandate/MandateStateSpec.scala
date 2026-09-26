package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import scala.collection.mutable.ArrayBuffer

/** Reproduces the numerical-experiment conclusions of design draft
  * section 1.1 against the Scala implementation:
  * threshold ignition, self-sustaining catastrophe, hysteresis extinction,
  * forgiving zone, and the fixed-substep regression.
  */
class MandateStateSpec:
  private val dt = 0.02

  private def freshState(): MandateState =
    MandateState(
      state = MandateCurrent(dissonance = -1.2, entrench = -0.6),
      param = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
      time_step = dt
    )

  private class Recorder:
    val spikeTimes: ArrayBuffer[Double] = ArrayBuffer.empty
    var maxStress: Double = 0.0
    private var wasSpiking = false

    def observe(t: Double, s: MandateState): Unit =
      maxStress = Math.max(maxStress, s.getState.stress)
      val spiking = Math.abs(s.getState.dissonance) > 1.9
      if spiking && !wasSpiking then spikeTimes += t
      wasSpiking = spiking

    def spikesBetween(lo: Double, hi: Double): Int =
      spikeTimes.count(t => t >= lo && t < hi)

  /** Run the system until `tEnd`; `forcing(t)` returns (disturbance u, calming c). */
  private def run(
      state: MandateState,
      tEnd: Double,
      forcing: Double => (Double, Double)
  ): Recorder =
    val rec = Recorder()
    var t = 0.0
    while t < tEnd do
      val (u, c) = forcing(t)
      state.disturbance = u
      state.calming = c
      state.step(dt)
      t += dt
      rec.observe(t, state)
    rec

  @Test def `simulation loop advances substeps sequentially`(): Unit =
    // Use an exactly representable dt so accumulator arithmetic is exact.
    val dtExact = 0.0625
    val viaLoop = freshState()
    val viaSteps = freshState()
    viaLoop.disturbance = 0.02
    viaSteps.disturbance = 0.02

    MandateAgent.simulationLoop(viaLoop, deltaTime = 5 * dtExact, dtExact)
    for _ <- 1 to 5 do viaSteps.step(dtExact)

    assertEquals(viaSteps.getState.dissonance, viaLoop.getState.dissonance, 1e-12)
    assertEquals(viaSteps.getState.entrench, viaLoop.getState.entrench, 1e-12)
    assertEquals(viaSteps.getState.stress, viaLoop.getState.stress, 1e-12)

    // The accumulator keeps the unconsumed remainder.
    MandateAgent.simulationLoop(viaLoop, deltaTime = 0.5 * dtExact, dtExact)
    assertEquals(0.5 * dtExact, viaLoop.timeDeltaAccumulator, 1e-12)

  @Test def `sustained overload ignites catastrophe beyond stress threshold`(): Unit =
    val state = freshState()
    val rec = run(state, tEnd = 6000.0, _ => (0.05, 0.0))

    // Early calm: nothing happens while stress is still low.
    assertEquals(0, rec.spikesBetween(0.0, 500.0), "no excursion expected early on")
    // Once stress crosses the threshold the fast system flips into
    // relaxation oscillation and keeps spiking.
    assertTrue(rec.spikeTimes.size >= 5, s"expected repeated spikes, got ${rec.spikeTimes.size}")
    assertTrue(rec.maxStress > 0.4, s"stress should pass the ignition region, got ${rec.maxStress}")

  @Test def `forgiving zone absorbs gentle disturbance indefinitely`(): Unit =
    val state = freshState()
    // u = 0.01 is below the forgiving capacity u* ~= 0.0225.
    val rec = run(state, tEnd = 8000.0, _ => (0.01, 0.0))

    assertEquals(0, rec.spikeTimes.size, "no catastrophe expected inside the forgiving zone")
    assertTrue(
      state.getState.stress < 0.1,
      s"stress should settle at a low equilibrium, got ${state.getState.stress}"
    )

  @Test def `catastrophe self-sustains then yields only to active calming`(): Unit =
    val state = freshState()
    val forcing = (t: Double) =>
      val u = if t > 500.0 && t < 2500.0 then 0.08 else 0.0
      val c = if t > 6000.0 && t < 7500.0 then 0.10 else 0.0
      (u, c)
    val rec = run(state, tEnd = 9000.0, forcing)

    // Self-sustaining: long after the pulse ended, still spiking.
    assertTrue(
      rec.spikesBetween(2600.0, 5900.0) >= 5,
      s"expected self-sustained oscillation, got ${rec.spikesBetween(2600.0, 5900.0)} spikes"
    )
    // Hysteresis: without calming it never stopped (spikes right up to t=6000),
    assertTrue(
      rec.spikesBetween(5500.0, 6000.0) >= 1,
      "oscillation should persist until calming starts"
    )
    // and after the calming window it stays extinguished for good.
    assertEquals(
      0,
      rec.spikesBetween(7800.0, 9000.0),
      "oscillation should not reignite after calming ends"
    )
    assertTrue(
      state.getState.stress < 0.05,
      s"calming should push stress far below the ignition threshold, got ${state.getState.stress}"
    )

  @Test def `sub-threshold kick rings down without spiking`(): Unit =
    val state = freshState()
    state.state = state.state.copy(dissonance = state.getState.dissonance + 0.3)
    val rec = run(state, tEnd = 200.0, _ => (0.0, 0.0))

    assertEquals(0, rec.spikeTimes.size, "a gentle kick must not trigger an excursion")
    assertTrue(
      Math.abs(state.getState.dissonance - -1.185) < 0.2,
      s"fast variable should settle back near rest, got ${state.getState.dissonance}"
    )
