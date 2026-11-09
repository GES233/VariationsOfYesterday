package moe.chestnut.awa.voy.inner.veins

/** Shared constants and interpolation for the phase-sensing layer.
  *
  * The runtime readout (`PhaseTracker`), the crystal-link runner
  * (`PulseLinkRunner`) and the offline PRC harness (`PhaseResponse`) all sense
  * the same Poincaré section: `v` crossing [[SectionLevel]] upwards. Before
  * this object existed the three copies carried their own literals and could
  * drift apart (audit B15). Only the *spike* rule differs by layer:
  *
  *   - readout/link: a candidate is a genuine spike only if the running peak
  *     reaches [[SpikeLevel]] (positive-only, design note 08 §1 Q2);
  *   - offline chain replay: a bidirectional amplitude probe, deliberately
  *     distinct and documented at its use site (`SliceReplay`).
  *
  * The numbers themselves are the frozen contract's (1.2 / 1.8); changing
  * either here is a contract-relevant decision, not a tuning knob.
  */
object PhaseDetection:
  /** Poincaré section: `v` crossing this level upwards defines phase 0. */
  val SectionLevel: Double = 1.2

  /** Positive peak a genuine excursion must reach. */
  val SpikeLevel: Double = 1.8

  /** Max delay between the section upcrossing and the spike level being
    * reached before the candidate is abandoned (ghost-relay weld). */
  val ConfirmWindow: Double = 5.0

  /** Fraction of the step at which `v` crossed `level` while rising.
    * Precondition: `vPrev <= level < vNow`.
    */
  def upcrossingFraction(vPrev: Double, vNow: Double, level: Double): Double =
    if vNow == vPrev then 0.0 else (level - vPrev) / (vNow - vPrev)
