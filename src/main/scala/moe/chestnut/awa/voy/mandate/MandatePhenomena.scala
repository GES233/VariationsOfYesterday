package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.inner.plant.GastrodiaBedVerdict
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

  /** Project a cell's semantic readout into its economic pressure tier.
    *
    * Batch 2 names the two clauses: `isMaintained` (the composed shelter fact)
    * and [[CellReadout.hasActivePositiveSpike]]. The tier VALUES are unchanged;
    * whether the economy should stop hanging on the shelter window is coupling
    * C9 (docs/mechanism-state-responsibilities.md §8.5, 待拍板).
    */
  def classifyEconomy(
      readout: CellReadout,
      p: ReadoutParam = ReadoutParam()
  ): EconomyProfile =
    if readout.isMaintained then EconomyMaintained
    else if readout.regime == Regime.Burnout ||
      readout.hasActivePositiveSpike(p)
    then EconomySpiking
    else EconomyStagnant

  /** Narrative seed sustenance level (`M`) restored when re-inoculating a
    * depleted cell (matches `MandateSustenanceSpec` logistic regrowth seed).
    */
  val ReInoculationSeed: Double = 0.05

  /** Richer seed sustenance level (`M`) restored when re-inoculating soil with
    * prepared `菌材` (Inoculated Timber): skips the slowest initial lag of
    * logistic regrowth while staying strictly below [[ReInoculationMaxSustenance]].
    */
  val RichReInoculationSeed: Double = 0.12

  /** Minimum sustenance (`M`) for an intact mycelial network ("完好") capable
    * of feeding symbiotic Gastrodia tubers at full vigour.
    */
  val GastrodiaHealthySustenance: Double = 0.60

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
    *   (`CellReadout.isBurnoutPhase` or
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

  /** Deep-incapacitation fact behind the silent signature: either no-cycle
    * reason (`CellReadout.isBurnoutPhase`, the union of the readout rule and
    * the ledger lockout) or a network that is truly gone (`M <= 0`, the
    * absorbing state).
    *
    * Boundary: this `M` guard is `<= 0`, deliberately STRICTER than the Allee
    * floor used by maintenance (`M >= floor`) and by the bed rule (`M < floor`).
    * The signature marks the absorbing state, not merely a depleted patch.
    * Still pending (C2): a short acute lockout also reads as silent blight,
    * even though the bed rule no longer withers under the same lockout. The
    * mismatch is deliberate for now — the bed decision is 2026-11-12, the
    * signature decision is not.
    */
  def hasSilentBlight(readout: CellReadout): Boolean =
    readout.isBurnoutPhase || readout.isMyceliumAbsent

  /** Deep-dead fact behind the bed verdict: the mycelial network is below the
    * Allee floor (with the `1e-9` guard the bed rule has always used), or the
    * readout rule puts the cell below the knee while NO acute overload lockout
    * is active.
    *
    * Gameplay decision (2026-11-12, user-approved): a short acute lockout must
    * NOT be able to latch a bed as dead. While `isOverloadLocked` holds, the
    * mechanism rule masks the readout-threshold reason, so the bed stalls and
    * re-evaluates once the lockout expires (the block only writes
    * `condition = 2` from a `Withered` verdict). A dead network (`M` below the
    * floor) still withers regardless of the lockout: death outranks both rules.
    *
    * Still pending and deliberately NOT changed here: the readout-threshold
    * reason on its own (including transient stacked-pulse drive) still latches
    * a bed when no lockout is active — §8.4 in
    * docs/mechanism-state-responsibilities.md — and the silent blight signature
    * still covers the lockout reason (C2).
    */
  def isGastrodiaBedDeepDead(readout: CellReadout): Boolean =
    readout.sustenance < Math.max(readout.stressParam.sustenanceFloor, 1e-9) ||
      (!readout.isOverloadLocked && readout.isOffsetBelowKnee)

  /** Project a cell's semantic readout and time-of-day into its sensory
    * phenomenon profile (design note 06 §3).
    *
    * Batch-1 rule (unchanged outcome): `silentBlight` fires for BOTH
    * no-cycle reasons (`CellReadout.isBurnoutPhase`) or a dead network, never
    * for `Regime.Burnout`. In particular a *short* acute overload lockout
    * currently reads as silent blight as well; whether it should is a pending
    * gameplay decision (docs/mechanism-state-responsibilities.md 待拍板), and
    * the split reasons make that decision a one-line change instead of a
    * label archaeology.
    */
  def classifyPhenomena(
      readout: CellReadout,
      isNight: Boolean = false
  ): PhenomenonProfile =
    val silentBlight = hasSilentBlight(readout)
    val seepTier = classifySeepTier(readout)
    // Glow and precursor spores bind to the slow `c0` anchor (a bell's lasting
    // duty), NOT to the pulse shelter window: the shelter window governs the
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

  /** Pure symbiotic growth rule for a `菌塘` (mycelial bed, design note 06
    * §4.3 & WO-3):
    *
    *  - Deep-dead ([[isGastrodiaBedDeepDead]]: `M` below the Allee floor, or
    *    the readout rule below the knee while no lockout masks it) ->
    *    [[GastrodiaBedVerdict.Withered]]; the block latches `condition = 2`
    *    until refurbished;
    *  - Else an active acute overload lockout (`isOverloadLocked`) ->
    *    [[GastrodiaBedVerdict.Stalled]]: the bed PAUSES growth and re-evaluates
    *    when the lockout expires (gameplay decision 2026-11-12, see
    *    [[isGastrodiaBedDeepDead]]);
    *  - Else `s >= 0.45` (`Strained`+) or damaged mycelial network
    *    (`M < 0.60`) -> [[GastrodiaBedVerdict.Stalled]];
    *  - Else (`s < 0.45` and `M >= 0.60`) -> [[GastrodiaBedVerdict.Growing]].
    *
    * Precedence: network death outranks the lockout rule, which outranks the
    * readout-threshold reason (the lockout masks it). The silent signature does
    * NOT follow this precedence yet (C2, still pending).
    */
  def evaluateGastrodiaBed(
      readout: CellReadout,
      p: ReadoutParam = ReadoutParam()
  ): GastrodiaBedVerdict =
    if isGastrodiaBedDeepDead(readout) then GastrodiaBedVerdict.Withered
    else if readout.isOverloadLocked then GastrodiaBedVerdict.Stalled
    else if readout.current.stress >= p.strained ||
      readout.sustenance < GastrodiaHealthySustenance
    then GastrodiaBedVerdict.Stalled
    else GastrodiaBedVerdict.Growing

  /** Whether a withered `菌塘` can be refurbished with fresh `荧丝菌材`
    * (Inoculated Timber): requires the cell to be out of deep incapacitation
    * and soil stress below the acid toxicity threshold.
    *
    * The gate deliberately does NOT use the lockout-masked bed verdict: a bed
    * that is latched (`condition = 2`) must not become refurbishable just
    * because an acute lockout currently masks the threshold reason — that would
    * waste the material and contradict the inspection text, which still shows
    * the withered state. So the gate keeps the pre-2026-11-12 signal:
    * `!isBurnoutPhase && M >= max(floor, 1e-9) && s < toxicity`. This is the
    * exact HEAD predicate that the verdict used to provide (finite inputs;
    * under NaN, which is outside the domain, the `>=` form is the conservative
    * one: it refuses refurbishing).
    */
  def canRefurbishGastrodiaBed(
      readout: CellReadout,
      p: ReadoutParam = ReadoutParam()
  ): Boolean =
    !readout.isBurnoutPhase &&
      readout.sustenance >= Math.max(readout.stressParam.sustenanceFloor, 1e-9) &&
      readout.current.stress < toxicityThreshold(readout)

  /** Whether a stripped log in the world can be colonized in-situ into `菌材`
    * (Inoculated Timber) by applying fungal inoculum: requires a healthy,
    * calm mycelial network ([[GastrodiaBedVerdict.Growing]]).
    */
  def canInoculateTimberInField(
      readout: CellReadout,
      p: ReadoutParam = ReadoutParam()
  ): Boolean =
    evaluateGastrodiaBed(readout, p) == GastrodiaBedVerdict.Growing

