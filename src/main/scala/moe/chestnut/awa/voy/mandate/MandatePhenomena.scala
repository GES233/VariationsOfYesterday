package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.inner.plant.GastrodiaBedVerdict
import moe.chestnut.awa.voy.inner.veins.*

/** Orthogonal consumer projections over [[CellReadout]] (no Minecraft classes,
  * no mutation, no cross-consumer coupling):
  *
  *   1. **Settlement economy** ([[classifyEconomy]])
  *   2. **Player bodily exposure** ([[shouldApplyAcuteBodyStrain]])
  *   3. **Sensory phenomena** ([[classifyPhenomena]], [[classifySeepTier]])
  *   4. **Mycelial symbiosis & re-inoculation** ([[evaluateGastrodiaBed]],
  *      [[canRefurbishGastrodiaBed]], [[evaluateReInoculation]])
  */
object MandatePhenomena:
  final case class EconomyProfile(
      tradePriceBonus: Int,
      breedingKeepChance: Double,
      golemKeepChance: Double,
      cropKeepChance: Double
  )

  val EconomyMaintained: EconomyProfile = EconomyProfile(0, 1.0, 1.0, 1.0)
  val EconomyStagnant: EconomyProfile = EconomyProfile(2, 0.5, 0.5, 0.5)
  val EconomySpiking: EconomyProfile = EconomyProfile(5, 0.15, 0.25, 0.25)

  /** 1. Settlement economy tier (design note 11 §1.4): `Beating`/`Warming` →
    * Maintained, `Fast`/`Arrhythmic` → Spiking, `Slow` → Stagnant.
    * Ecological death stays Stagnant regardless of the rhythm verdict.
    */
  def classifyEconomy(readout: CellReadout): EconomyProfile =
    if !readout.mycelium.isViable || readout.isSlowOffsetBelowKnee then EconomyStagnant
    else
      readout.rhythm match
        case RhythmVerdict.Beating(_, _, _) | RhythmVerdict.Warming => EconomyMaintained
        case RhythmVerdict.Fast(_) | RhythmVerdict.Arrhythmic(_, _) => EconomySpiking
        case RhythmVerdict.Slow(_)                              => EconomyStagnant

  /** 2. Player bodily exposure: only acute rhythm (`Fast`/`Arrhythmic`) or
    * saturated burnout on unsheltered ground affects the player's body.
    * (The bell-strike `pulse_saturation` backlash stays event-triggered;
    * this is the ambient strain axis only.)
    */
  def shouldApplyAcuteBodyStrain(readout: CellReadout): Boolean =
    !readout.isSheltered &&
      (readout.regime == Regime.Burnout || readout.rhythm.isAcute)

  val ReInoculationSeed: Double = 0.05
  val RichReInoculationSeed: Double = 0.12
  val ReInoculationMaxSustenance: Double = 0.20
  val C0ShelterLevel: Double = 0.01

  enum YellowSeepTier(val level: Int, val particleCount: Int):
    case None extends YellowSeepTier(0, 0)
    case Light extends YellowSeepTier(1, 2)
    case Moderate extends YellowSeepTier(2, 5)
    case Heavy extends YellowSeepTier(3, 10)

  final case class PhenomenonProfile(
      silentBlight: Boolean,
      seepTier: YellowSeepTier,
      mycelialGlow: Boolean,
      precursorSpore: Boolean
  )

  enum ReInoculationOutcome(val seedsSustenance: Boolean, val targetLevel: Double):
    case Seeded extends ReInoculationOutcome(true, ReInoculationSeed)
    case AcidTooToxic extends ReInoculationOutcome(false, 0.0)
    case AlreadyViable extends ReInoculationOutcome(false, 0.0)

  /** 3a. 3-tier iron-yellow water seep signature. */
  def classifySeepTier(
      readout: CellReadout,
      p: ReadoutParam = ReadoutParam()
  ): YellowSeepTier =
    val cur = readout.current
    if Math.abs(cur.excitation) >= p.spikeLevel then YellowSeepTier.Heavy
    else if cur.activity >= 0.30 || cur.stress >= 0.75 then YellowSeepTier.Heavy
    else if cur.activity >= 0.10 || cur.stress >= p.strained then YellowSeepTier.Moderate
    else if cur.activity >= 0.02 || cur.stress >= 0.25 then YellowSeepTier.Light
    else YellowSeepTier.None

  /** 3b. Silent blight binds strictly to genuine ecological incapacitation
    * (`mycelium.isDead` or `isSlowOffsetBelowKnee`), never to short pulse
    * cooldowns or oscillating `Regime.Burnout`.
    */
  def hasSilentBlight(readout: CellReadout): Boolean =
    readout.mycelium.isDead || readout.isSlowOffsetBelowKnee

  /** 3c. Combined sensory phenomenon profile for a chunk. */
  def classifyPhenomena(
      readout: CellReadout,
      isNight: Boolean = false
  ): PhenomenonProfile =
    val silentBlight = hasSilentBlight(readout)
    val seepTier = classifySeepTier(readout)
    val hasCalmingStock = readout.baselineCalming >= C0ShelterLevel
    val mycelialGlow =
      !silentBlight &&
        isNight &&
        readout.regime == Regime.Calm &&
        hasCalmingStock &&
        readout.mycelium.isIntact
    val precursorSpore =
      !silentBlight &&
        hasCalmingStock &&
        (readout.regime == Regime.Strained || readout.regime == Regime.Oscillating)
    PhenomenonProfile(
      silentBlight = silentBlight,
      seepTier = seepTier,
      mycelialGlow = mycelialGlow,
      precursorSpore = precursorSpore
    )

  /** 4a. Pure decision rule for mycelial re-inoculation into soil. */
  def evaluateReInoculation(readout: CellReadout): ReInoculationOutcome =
    if readout.sustenance >= ReInoculationMaxSustenance then
      ReInoculationOutcome.AlreadyViable
    else if readout.isToxicSoil then
      ReInoculationOutcome.AcidTooToxic
    else
      ReInoculationOutcome.Seeded

  /** 4b. Symbiotic growth verdict for a `菌塘` (mycelial bed):
    *   - `Withered`: dead mycelial network (`mycelium.isDead`);
    *   - `Stalled`: `isSlowOffsetBelowKnee`, non-`Calm` stress (`s >= 0.45`),
    *     or damaged mycelial network (`!mycelium.isIntact`);
    *   - `Growing`: `Calm` stress (`s < 0.45`) and `Intact` mycelial network (`M >= 0.60`).
    */
  def evaluateGastrodiaBed(readout: CellReadout): GastrodiaBedVerdict =
    if readout.mycelium.isDead then GastrodiaBedVerdict.Withered
    else if readout.isSlowOffsetBelowKnee ||
      readout.current.stress >= ReadoutParam().strained ||
      !readout.mycelium.isIntact
    then GastrodiaBedVerdict.Stalled
    else GastrodiaBedVerdict.Growing

  /** 4c. Whether a withered `菌塘` can be refurbished with fresh inoculum:
    * requires a viable mycelial network (`!mycelium.isDead`) and sub-toxic soil.
    */
  def canRefurbishGastrodiaBed(readout: CellReadout): Boolean =
    readout.mycelium.isViable && !readout.isToxicSoil

  /** 4d. Whether a stripped log in the world can be colonized in-situ into `菌材`. */
  def canInoculateTimberInField(readout: CellReadout): Boolean =
    evaluateGastrodiaBed(readout) == GastrodiaBedVerdict.Growing
