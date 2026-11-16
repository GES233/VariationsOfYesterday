package moe.chestnut.awa.voy.inner.veins

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import moe.chestnut.awa.voy.LatticeConfig

/** The calibrated opening field (design note 11 ruling 7 + appendix C).
  *
  * **Replaces the former spec, which asserted the opposite of the design.** It
  * built a hand-written field with the pre-calibration numbers (drive 0.08,
  * seed 0.18) and required the hotspot to stay subcritical forever. After the
  * 2026-11-16 calibration the opening core is deliberately supra-onset
  * (`peak ≈ 0.349 > s_onset ≈ 0.265`): it is *meant* to beat, drift to the acid
  * line in ~33 min and be rescuable, while the outer land stays silent. A test
  * that pins "never spikes" is therefore a guard for a world that no longer
  * exists, and it silently told every reader the wrong model of the opening.
  *
  * The two ends of the three-act arc are guarded here cheaply, one cell each.
  * The full arc over a lattice belongs in a future `RemnantArcSpec` once grid
  * construction leaves the wiring layer (note 12 §9.2 / P2): today the opening
  * field is built inside `MandateRuntime.buildGrid`, which pulls in Minecraft.
  */
class PrototypeHotspotSpec:
  private val dt = 0.05
  private val fast = MandateAgent.DefaultFastParam

  /** Count positive section upcrossings: a beat, not merely a spike. */
  private def beats(cell: MandateState, seconds: Double): Int =
    var crossings = 0
    var previous = cell.getState.dissonance
    var t = 0.0
    while t < seconds do
      cell.step(dt)
      t += dt
      val v = cell.getState.dissonance
      if previous < PhaseDetection.SectionLevel && v >= PhaseDetection.SectionLevel then
        crossings += 1
      previous = v
    crossings

  @Test def `the calibrated opening core is supra-onset and beats`(): Unit =
    val onset = LatticeConfig.OscillationOnsetStress
    val peak = LatticeConfig.DefaultInitialStressPeak

    assertTrue(peak > onset, s"the opening core must start inside the band: peak $peak vs onset $onset")

    val core = MandateState(MandateCurrent(0.0, 0.0, peak, 0.0), fast)
    core.disturbance = LatticeConfig().hotspotDisturbance
    assertTrue(
      beats(core, 240.0) >= 3,
      "the opening core must beat (design note 11 appendix C: it is the first act of the arc)"
    )

  @Test def `the far field stays silent`(): Unit =
    // Below the oscillation onset: the outer land never fires on its own
    // (appendix C: the annulus goes quiet; the arc is a story-region event).
    val outer = MandateState(
      MandateCurrent(0.0, 0.0, LatticeConfig.OscillationOnsetStress - 0.06, 0.0),
      fast
    )
    outer.disturbance = LatticeConfig().ambientDisturbance
    assertEquals(0, beats(outer, 240.0), "outside the band the land must stay silent")
