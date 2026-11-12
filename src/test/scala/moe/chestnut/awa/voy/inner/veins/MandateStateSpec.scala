package moe.chestnut.awa.voy.inner.veins

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import scala.collection.mutable.ArrayBuffer

/** Reproduces the numerical-experiment conclusions of design draft
  * section 1.1 against the Scala implementation:
  * threshold ignition, self-sustaining catastrophe, hysteresis extinction,
  * and the forgiving zone.
  */
class MandateStateSpec:
  private val dt = 0.02

  private def freshState(): MandateState =
    MandateState(
      state = MandateCurrent(dissonance = -1.2, entrench = -0.6),
      param = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
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

  /** The indirect physical path a pulse can take to the mycelial network:
    * `p -> v` spike -> `q` (`activity`) -> `s` -> acid burn of `M`. No pulse
    * subtracts `M` directly; this test pins the chain so a future "pulse load
    * -> damage" rule cannot double-count it (memo §3.7).
    */
  @Test def `a physical pulse reaches the acid-burn path indirectly, through activity and stress`(): Unit =
    // The game side enables the mycelial acid burn (design note 06 §7.1);
    // the frozen core default keeps `M` dormant.
    val gameStress = StressParam(sustenanceGrowth = 1e-3, sustenanceBurn = 4e-3)
    def make(): MandateState =
      val st = MandateState(
        state = MandateCurrent(dissonance = -1.2, entrench = -0.6, stress = 0.2),
        param = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
        stressParam = gameStress
      )
      st.disturbance = 0.0
      st

    val pulsed = make()
    val control = make()
    var step = 0
    var maxActivity = 0.0
    var maxStress = 0.0
    val horizon = 3000.0
    while step * dt < horizon do
      // A deliberately strong alpha-kernel pulse every 3 s (a bell is area
      // 12); this is a physics probe, not a gameplay dose.
      if step % 150 == 0 then
        pulsed.synapseTau = 5.0
        pulsed.firePulse(40.0)
      pulsed.step(dt)
      control.step(dt)
      maxActivity = Math.max(maxActivity, pulsed.getState.activity)
      maxStress = Math.max(maxStress, pulsed.getState.stress)
      step += 1

    assertTrue(
      maxActivity > 0.0,
      s"a pulsed cell must spike at least once, driving activity; got $maxActivity"
    )
    assertTrue(
      maxStress > 0.6,
      s"the activity feedback must push stress across the acid-burn threshold; got $maxStress"
    )
    assertTrue(
      pulsed.sustenance < 1.0 - 1e-9,
      s"acid burn must reduce M once stress crosses 0.6; got M=${pulsed.sustenance}"
    )
    assertEquals(
      0.0,
      control.getState.activity,
      1e-12,
      "an unstimulated cell never spikes, so it never drives the tracer"
    )
    assertEquals(
      1.0,
      control.sustenance,
      1e-12,
      "an unstimulated cell stays below the burn threshold and keeps native M"
    )
    assertTrue(
      pulsed.sustenance < control.sustenance,
      "the pulse's indirect path leaves the pulsed network more damaged than the control"
    )
