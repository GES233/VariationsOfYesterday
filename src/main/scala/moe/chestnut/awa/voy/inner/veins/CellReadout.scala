package moe.chestnut.awa.voy.inner.veins

/** Slim, self-contained semantic readout of one cell published to the game side.
  *
  * Exposes three orthogonal dimensions without leaking internal parameter bags:
  *   1. **Dynamics**: `regime`, `phase`, `isSpiking`, `isSlowOffsetBelowKnee`
  *   2. **Mycelium**: `mycelium` (`Intact` / `Damaged` / `Dead`), `isToxicSoil`
  *   3. **Shelter**: `isSheltered` — the rhythm model (design note 11 §1.3):
  *      a beating or warming rhythm on viable, non-collapsed ground
  */
final case class CellReadout(
    current: MandateCurrent,
    regime: Regime,
    phase: PhaseReading,
    sustenance: Double = 1.0,
    baselineCalming: Double = 0.0,
    pulseDrive: Double = 0.0,
    rhythm: RhythmVerdict = RhythmVerdict.Warming,
    sustenanceFloor: Double = StressParam.BareCore.sustenanceFloor,
    toxicityThreshold: Double = StressParam.BareCore.sustenanceBurnThreshold
):
  /** Canonical 3-tier mycelial health of this cell. */
  def mycelium: SustenanceTier = Readout.sustenanceTier(sustenance, sustenanceFloor)

  /** Whether the cell is currently in a positive spike excursion (`v >= spikeLevel`). */
  def isSpiking: Boolean = current.excitation >= PhaseDetection.SpikeLevel

  /** Whether the cell's slow recovery offset sits at or below the burnout knee. */
  def isSlowOffsetBelowKnee: Boolean =
    phase == PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee) ||
      Readout.isSlowOffsetBelowKnee(current.stress, sustenance)

  /** Whether soil stress is at or above the acid burn threshold that kills inoculum. */
  def isToxicSoil: Boolean = current.stress >= toxicityThreshold

  /** Dimension 3: Whether this chunk is sheltered from `沉滞` (Stagnation).
    * The beat itself shelters (design note 11 §1.3). `Warming` grace shelters
    * (it replaces the old "first strike opens a window" immediacy); a spike
    * phase deliberately does NOT suspend shelter — the beat's high phase is
    * part of it.
    */
  def isSheltered: Boolean =
    (rhythm.isBeating || rhythm.isWarming) &&
      mycelium.isViable &&
      !isSlowOffsetBelowKnee

  /** 20-division mountain dial tick (`0..19`) when in a regular cycle. */
  def dialTick: Option[Int] = Readout.dialTick(phase)

object CellReadout:
  val zero: CellReadout = CellReadout(
    current = MandateCurrent(0.0, 0.0, 0.0, 0.0),
    regime = Regime.Calm,
    phase = PhaseReading.Basin(PhaseLabel.Resting),
    baselineCalming = 0.0
  )
