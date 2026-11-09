package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.inner.veins.*

/** Pure sensory phenomenon classification, economic pressure tiers, and
  * mycelial re-inoculation rules (design note 06 §1 constraint 5, §2.1, §3,
  * §7.1, §7.3, §7.4; design draft §3.2).
  *
  * Every function here is a pure projection of [[CellReadout]] (no Minecraft
  * classes, no mutation) so the sensory bindings, economy tiers and narrative
  * re-inoculation gates can be unit-tested deterministically.
  */
object MandatePhenomena:
  /** Economic pressure tier of a chunk (design draft §3.2, 2026-11-09 revision:
    * stagnation's primary target returns to the vanilla economy per the
    * original §7.8 decision — trade, breeding, golem spawn, crop growth —
    * while the player's body only suffers during spikes).
    *
    * `*KeepChance` is the probability that one vanilla attempt proceeds; a
    * stagnated chunk rolls against it per attempt, so the degradation reads
    * as sluggishness rather than a hard stop.
    */
  final case class EconomyProfile(
      tradePriceBonus: Int,
      breedingKeepChance: Double,
      golemKeepChance: Double,
      cropKeepChance: Double
  )

  /** Sheltered chunk: vanilla economy runs untouched. */
  val EconomyMaintained: EconomyProfile = EconomyProfile(0, 1.0, 1.0, 1.0)

  /** Stagnant chunk (no fresh pulse window): the economy sags at half
    * strength. Chunks outside the live lattice land here as well — the
    * "safe but barren" outskirts are barren by mechanism, not by rhetoric
    * (draft-01 §7.8).
    */
  val EconomyStagnant: EconomyProfile = EconomyProfile(2, 0.5, 0.5, 0.5)

  /** Spiking or saturated-burnout chunk: the acute tier (§3.2). Transient for
    * spikes, persistent for `Regime.Burnout`.
    */
  val EconomySpiking: EconomyProfile = EconomyProfile(5, 0.15, 0.25, 0.25)

  /** Project a cell's semantic readout into its economic pressure tier. */
  def classifyEconomy(
      readout: CellReadout,
      p: ReadoutParam = ReadoutParam()
  ): EconomyProfile =
    if readout.isMaintained then EconomyMaintained
    else if readout.regime == Regime.Burnout ||
      readout.current.dissonance >= p.spikeLevel
    then EconomySpiking
    else EconomyStagnant

  /** Narrative seed sustenance level (`M`) restored when re-inoculating a
    * depleted cell (matches `MandateSustenanceSpec` logistic regrowth seed).
    */
  val ReInoculationSeed: Double = 0.05

  /** Above this sustenance level, living mycelial threads remain viable and
    * will self-regrow logistically once stress drops below the burn threshold.
    */
  val ReInoculationMaxSustenance: Double = 0.20

  /** Soil stress threshold (`s_T = 0.6` by default) above which acid burn kills
    * freshly introduced fungal inoculum (design note 06 §2.1 & §7.1). Read from
    * the cell's own parameter set at the call site so a tuned config applies.
    */
  private def toxicityThreshold(readout: CellReadout): Double =
    readout.stressParam.sustenanceBurnThreshold

  /** Minimum `c0` (baseline calming) that still shelters a cell's sensory
    * signatures (design note 08 §5: glow and precursor spores bind to the slow
    * `c0` anchor, not to the pulse window).
    */
  val C0ShelterLevel: Double = 0.01

  /** Minimum sustenance required for healthy night-time mycelial bioluminescence
    * (design note 06 §3 & §7.3).
    */
  val GlowMinSustenance: Double = 0.50

  /** 3-tier iron-hydroxide ("yellow boy") water seep signature (design note 06 §3).
    *
    * Resolutions are strictly qualitative (3 tiers + None) so water colour never
    * leaks a raw continuous float to the player (§1 constraint 5).
    */
  enum YellowSeepTier(val level: Int, val particleCount: Int):
    case None extends YellowSeepTier(0, 0)
    case Light extends YellowSeepTier(1, 2)
    case Moderate extends YellowSeepTier(2, 5)
    case Heavy extends YellowSeepTier(3, 10)

  /** Combined sensory phenomenon signature of a chunk at one instant
    * (design note 06 §3).
    *
    * @param silentBlight
    *   `true` iff the cell is in deep incapacitation / depolarization block
    *   (`PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle)` or
    *   `sustenance <= 0.0`). Explicitly `false` for an oscillating
    *   `Regime.Burnout` (`s >= 0.95`) cell (note 06 §3).
    * @param seepTier
    *   3-tier yellow-ochre water precipitate driven by active spike excursion,
    *   precursor slow stress `s`, or post-spike acid residue `q`.
    * @param mycelialGlow
    *   Sheltered night-time bioluminescence in a healthy `Calm` chunk.
    * @param precursorSpore
    *   Critical slowing-down spore drift while `c0` still shelters a
    *   `Strained` or `Oscillating` cell.
    */
  final case class PhenomenonProfile(
      silentBlight: Boolean,
      seepTier: YellowSeepTier,
      mycelialGlow: Boolean,
      precursorSpore: Boolean
  )

  /** Outcome of a player attempting to re-inoculate the mycelial network (`M`)
    * in a chunk by applying fungal inoculum to soil (design note 06 §2.1 & §7.1).
    */
  enum ReInoculationOutcome(val seedsSustenance: Boolean, val targetLevel: Double):
    /** Mycelium is depleted (`M < 0.20`) and soil stress is below the acid burn
      * threshold (`s < 0.60`): seeds `M = ReInoculationSeed` (`0.05`).
      */
    case Seeded extends ReInoculationOutcome(true, ReInoculationSeed)

    /** Mycelium is depleted (`M < 0.20`), but soil stress exceeds the acid burn
      * threshold (`s >= 0.60`): acid kills the inoculum; player must relieve
      * stress first.
      */
    case AcidTooToxic extends ReInoculationOutcome(false, 0.0)

    /** Mycelium still has viable living threads (`M >= 0.20`) and does not need
      * external re-inoculation.
      */
    case AlreadyViable extends ReInoculationOutcome(false, 0.0)

  /** Classify the 3-tier iron-yellow water seep from `CellReadout`
    * (design note 06 §3):
    *
    *  - Active spike excursion (`|v| >= spikeLevel`): `Heavy`;
    *  - Else driven by precursor stress `s` and post-spike acid tracer `q`:
    *    - `q >= 0.30` or `s >= 0.75` -> `Heavy`
    *    - `q >= 0.10` or `s >= 0.45` (`Strained`) -> `Moderate`
    *    - `q >= 0.02` or `s >= 0.25` -> `Light`
    *    - Otherwise -> `None`.
    */
  def classifySeepTier(
      readout: CellReadout,
      p: ReadoutParam = ReadoutParam()
  ): YellowSeepTier =
    val cur = readout.current
    if Math.abs(cur.dissonance) >= p.spikeLevel then YellowSeepTier.Heavy
    else if cur.activity >= 0.30 || cur.stress >= 0.75 then YellowSeepTier.Heavy
    else if cur.activity >= 0.10 || cur.stress >= p.strained then YellowSeepTier.Moderate
    else if cur.activity >= 0.02 || cur.stress >= 0.25 then YellowSeepTier.Light
    else YellowSeepTier.None

  /** Project a cell's semantic readout and time-of-day into its sensory
    * phenomenon profile (design note 06 §3).
    *
    * Note 06 §3 invariant: `silentBlight` binds ONLY to
    * `PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle)` (or
    * `sustenance <= 0.0`), never to `Regime.Burnout`.
    */
  def classifyPhenomena(
      readout: CellReadout,
      isNight: Boolean = false
  ): PhenomenonProfile =
    val silentBlight =
      readout.phase == PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle) ||
        readout.sustenance <= 0.0
    val seepTier = classifySeepTier(readout)
    // Glow and precursor spores bind to the slow `c0` anchor (a bell's lasting
    // duty), NOT to the pulse suppression window: the heartbeat governs the
    // player's `沉滞`, while these signatures describe the land's own health.
    val c0Sheltered = readout.baselineCalming >= C0ShelterLevel
    val mycelialGlow =
      !silentBlight &&
        isNight &&
        readout.regime == Regime.Calm &&
        c0Sheltered &&
        readout.sustenance >= GlowMinSustenance
    val precursorSpore =
      !silentBlight &&
        c0Sheltered &&
        (readout.regime == Regime.Strained || readout.regime == Regime.Oscillating)
    PhenomenonProfile(
      silentBlight = silentBlight,
      seepTier = seepTier,
      mycelialGlow = mycelialGlow,
      precursorSpore = precursorSpore
    )

  /** Qualitative 3-tier word for mycelial network integrity (`M`), obeying
    * the readout discipline (design note 06 §1 constraint 5: never raw floats).
    */
  def sustenanceWord(
      sustenance: Double,
      floor: Double = StressParam().sustenanceFloor
  ): String =
    if sustenance < floor then "枯绝"
    else if sustenance < 0.60 then "受损"
    else "完好"

  /** Pure decision rule for mycelial re-inoculation (`SetSustenance` wiring,
    * design note 06 §2.1 & §7.1).
    */
  def evaluateReInoculation(readout: CellReadout): ReInoculationOutcome =
    if readout.sustenance >= ReInoculationMaxSustenance then
      ReInoculationOutcome.AlreadyViable
    else if readout.current.stress >= toxicityThreshold(readout) then
      ReInoculationOutcome.AcidTooToxic
    else
      ReInoculationOutcome.Seeded
