package moe.chestnut.awa.voy.inner.veins

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Provenance relations of [[RhythmParam]] (2026-11-16 discipline: no
  * unsourced literals in the rhythm module). These asserts keep the
  * thresholds tracking `nominalPeriod` and the shared readout sources, so a
  * future retune cannot silently desync them.
  */
class RhythmParamSpec:
  private val p = RhythmParam()

  @Test def `thresholds are expressions of the nominal period`(): Unit =
    assertEquals(0.75 * p.nominalPeriod, p.periodMin, 1e-12)
    assertEquals(2.0 * p.nominalPeriod, p.periodMax, 1e-12)

  @Test def `the duty bound is half the measured saturated plateau fraction`(): Unit =
    assertEquals(0.5 * RhythmParam.SaturationPlateauFraction, p.dutyMax, 1e-12)
    assertEquals(0.606, RhythmParam.SaturationPlateauFraction, 1e-12, "measured, draft 01 §1.1")

  @Test def `tolerances reuse the readout-layer sources`(): Unit =
    assertEquals(ReadoutParam().regularityTol, p.regularityTol, 0.0)
    assertEquals(ReadoutParam().staleCycles, p.staleCycles, 0.0)

  @Test def `the overdrive guard is 1 point 6 bell time constants`(): Unit =
    assertEquals(1.6 * PulseTransmitter.Bell.tau, p.overdriveGuard, 1e-12)

  @Test def `the exit hysteresis is the ruled two bad cycles`(): Unit =
    assertEquals(
      2,
      p.exitBadCycles,
      "design note 11 §1.3 rev.: two of the cell's OWN arrhythmic periods (acute and silence publish immediately)"
    )

  @Test def `the nominal period stays anchored to the contract measurement`(): Unit =
    assertEquals(39.47, p.nominalPeriod, 1e-12, "measured: mandate-slice-v1 settled_period")

  @Test def `relations track a retuned nominal period`(): Unit =
    val retuned = RhythmParam(nominalPeriod = 50.0)
    assertEquals(37.5, retuned.periodMin, 1e-12)
    assertEquals(100.0, retuned.periodMax, 1e-12)
    assertEquals(p.dutyMax, retuned.dutyMax, 0.0, "dutyMax does not follow nominalPeriod")

  @Test def `derived lattice defaults give an 8x8 remnant rhythm zone`(): Unit =
    // 2026-11-16 ruling 7: peak = s_onset + 0.25 × (s_acid − s_onset),
    // falloff = (peak − s_onset)/4 so the in-band radius is 4 cells.
    import moe.chestnut.awa.voy.LatticeConfig
    val onset = LatticeConfig.OscillationOnsetStress
    val acid = StressParam().sustenanceBurnThreshold
    val peak = LatticeConfig.DefaultInitialStressPeak
    val falloff = LatticeConfig.DefaultInitialStressFalloff
    assertEquals(0.265, onset, 1e-3, "s_onset = α − a_c with the frozen fast params")
    assertEquals(onset + 0.25 * (acid - onset), peak, 1e-12)
    assertTrue(peak > onset && peak < acid, "the remnant peak must sit inside the natural band")
    assertEquals((peak - onset) / 4.0, falloff, 1e-12)
    assertEquals(4.0, (peak - onset) / falloff, 1e-9, "in-band radius: 4 cells = 8x8 chunks")
