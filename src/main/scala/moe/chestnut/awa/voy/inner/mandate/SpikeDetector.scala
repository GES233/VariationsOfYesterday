package moe.chestnut.awa.voy.inner.mandate

/** How a section upcrossing is promoted to a confirmed spike. */
enum SpikeConfirm:
  /** Emit immediately at the section crossing (prototype fingerprint mode). */
  case AtSectionCrossing

  /** Emit once the running peak reaches `spikeLevel` within `window` time units;
    * otherwise the candidate is abandoned (ghost-relay weld). */
  case WithinWindow(window: Double)

  /** Emit when the excursion falls back through zero, if its peak reached
    * `spikeLevel` (the offline PRC/contract measurement mode). */
  case AtZeroCrossing

final case class DetectedSpike(at: Double, peak: Double)

/** One detector for the Poincaré-section / spike rules shared by the crystal
  * links and the offline PRC harness (audit B15).
  *
  * The two consumers disagree only in *when* a candidate is confirmed, not in
  * what a candidate is: `PulseLinkRunner` needs a prompt confirmation (window)
  * and can emit at the bare crossing for the prototype fingerprint;
  * `PhaseResponse.runSpikes` confirms at the return through zero, and measures
  * the crossing time at the step end (no interpolation) because the contract's
  * numbers are defined that way. Those differences are explicit options here,
  * so neither side can silently drift into the other's semantics.
  *
  * The state machine is pure and allocation-light: `observe` is called once per
  * sample and returns at most one confirmed spike.
  */
final class SpikeDetector(
    val section: Double = PhaseDetection.SectionLevel,
    val spikeLevel: Double = PhaseDetection.SpikeLevel,
    val confirm: SpikeConfirm = SpikeConfirm.WithinWindow(PhaseDetection.ConfirmWindow),
    val interpolateCrossing: Boolean = true
):
  private var prevV: Double = Double.NaN
  private var prevT: Double = Double.NaN
  private var candidateAt: Double = Double.NaN
  private var peak: Double = Double.MinValue
  private var fired: Boolean = false

  /** Feed one `(t, v)` sample; returns a confirmed spike, if this sample
    * completes one. */
  def observe(t: Double, v: Double): Option[DetectedSpike] =
    var out: Option[DetectedSpike] = None
    if prevV.isNaN then
      prevV = v
      prevT = t
    else
      if prevV <= section && v > section then
        candidateAt =
          if interpolateCrossing then
            val frac = PhaseDetection.upcrossingFraction(prevV, v, section)
            prevT + frac * (t - prevT)
          else t
        peak = v
        fired = false
        confirm match
          case SpikeConfirm.AtSectionCrossing =>
            fired = true
            out = Some(DetectedSpike(candidateAt, peak))
          case _ => ()
      else if !candidateAt.isNaN then
        peak = Math.max(peak, v)
        confirm match
          case SpikeConfirm.WithinWindow(window) =>
            if !fired && peak >= spikeLevel then
              if t - candidateAt <= window then
                fired = true
                out = Some(DetectedSpike(candidateAt, peak))
              else
                // Ghost-relay weld: a level reached only after the window is a
                // driven creep, not an action potential; the candidate is
                // abandoned for good and must not become fireable later.
                candidateAt = Double.NaN
                fired = false
          case SpikeConfirm.AtZeroCrossing =>
            if v < 0.0 then
              if peak >= spikeLevel then out = Some(DetectedSpike(candidateAt, peak))
              candidateAt = Double.NaN
              fired = false
          case SpikeConfirm.AtSectionCrossing => ()
        if v < 0.0 then
          candidateAt = Double.NaN
          fired = false
      prevV = v
      prevT = t
    out
