package moe.chestnut.awa.voy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import moe.chestnut.awa.voy.inner.plant.GastrodiaBedVerdict
import moe.chestnut.awa.voy.mandate.MandatePhenomena
import moe.chestnut.awa.voy.mandate.MandatePhenomena.{ReInoculationOutcome, YellowSeepTier}
import moe.chestnut.awa.voy.inner.veins.*

/** Golden mapping of the sensory phenomenon signatures and the mycelial
  * re-inoculation gate (design note 06 §3, §7.1; work orders WO-1/WO-3/WO-4).
  *
  * The load-bearing invariants:
  *
  *  1. silent blight binds ONLY to deep incapacitation
  *     (a burnout-phase reason, `CellReadout.isBurnoutPhase`, or a dead
  *     network `M <= 0`), never to an oscillating `Regime.Burnout` (§3);
  *  2. the iron-yellow seep escalates None -> Light -> Moderate -> Heavy on
  *     precursor stress, post-spike acid residue, and live spike excursions;
  *  3. mycelial glow requires night + Calm + c0 shelter + healthy M, and is
  *     suppressed by silent blight;
  *  4. re-inoculation seeds `M = 0.05` only into depleted AND sub-toxic soil.
  */
class MandatePhenomenaSpec:

  private def readout(
      v: Double = -1.2,
      s: Double = 0.05,
      q: Double = 0.0,
      regime: Regime = Regime.Calm,
      phase: PhaseReading = PhaseReading.Basin(PhaseLabel.Resting),
      c0: Double = 0.03,
      sustenance: Double = 1.0
  ): CellReadout =
    CellReadout(
      current = MandateCurrent(v, -0.6, s, q),
      regime = regime,
      phase = phase,
      baselineCalming = c0,
      sustenance = sustenance
    )

  @Test def `silent blight binds to deep incapacitation, never to a saturated oscillation`(): Unit =
    // Deep incapacitation via the readout threshold rule (note 06 §3): the
    // fact is built from its own criterion — stacked pulse drive pushes a_eff
    // below the knee — not inferred from the displayed phase.
    val deepDead = MandatePhenomena.classifyPhenomena(
      readout(phase = PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee)).copy(pulseDrive = 2.1),
      isNight = true
    )
    assertTrue(deepDead.silentBlight, "the offset-threshold condition must read as silent blight")
    assertFalse(deepDead.mycelialGlow, "silent blight suppresses the glow")
    assertFalse(deepDead.precursorSpore, "silent blight suppresses precursor spores")

    // Deep incapacitation via a dead network even at calm stress.
    val withdrawn = MandatePhenomena.classifyPhenomena(
      readout(sustenance = 0.0),
      isNight = true
    )
    assertTrue(withdrawn.silentBlight, "M = 0 is absorbing: the patch is dead and silent")

    // Regime.Burnout (s >= 0.95) with a live limit cycle: saturated
    // incapacitation, explicitly NOT silence (note 06 §3).
    val saturated = MandatePhenomena.classifyPhenomena(
      readout(
        v = 0.5,
        s = 0.98,
        regime = Regime.Burnout,
        phase = PhaseReading.Cycle(PhaseLabel.AbsoluteRefractory, 0.25),
        c0 = 0.0
      ),
      isNight = true
    )
    assertFalse(saturated.silentBlight, "an oscillating Regime.Burnout cell is not a dead zone")
    assertEquals(
      YellowSeepTier.Heavy,
      saturated.seepTier,
      "saturated stress still runs the seep heavy"
    )

  @Test def `yellow seep escalates from precursor stress through acid residue to live spike`(): Unit =
    assertEquals(
      YellowSeepTier.None,
      MandatePhenomena.classifySeepTier(readout(s = 0.10)),
      "calm water stays clear"
    )
    assertEquals(
      YellowSeepTier.Light,
      MandatePhenomena.classifySeepTier(readout(s = 0.30)),
      "early precursor stress tints the water lightly"
    )
    assertEquals(
      YellowSeepTier.Moderate,
      MandatePhenomena.classifySeepTier(readout(s = 0.50, regime = Regime.Strained)),
      "Strained stress runs the seep at the middle tier"
    )
    assertEquals(
      YellowSeepTier.Moderate,
      MandatePhenomena.classifySeepTier(readout(s = 0.10, q = 0.15)),
      "post-spike acid residue alone keeps the water yellow"
    )
    assertEquals(
      YellowSeepTier.Heavy,
      MandatePhenomena.classifySeepTier(readout(s = 0.10, q = 0.35)),
      "heavy acid residue runs the seep heavy"
    )
    assertEquals(
      YellowSeepTier.Heavy,
      MandatePhenomena.classifySeepTier(readout(v = 1.9)),
      "a live spike excursion trumps everything (positive excursion)"
    )
    assertEquals(
      YellowSeepTier.Heavy,
      MandatePhenomena.classifySeepTier(readout(v = -1.9)),
      "the spike level is on |v| (negative excursion)"
    )

  @Test def `mycelial glow needs night, calm, shelter and a healthy network`(): Unit =
    assertTrue(
      MandatePhenomena.classifyPhenomena(readout(), isNight = true).mycelialGlow,
      "healthy sheltered calm night glows"
    )
    assertFalse(
      MandatePhenomena.classifyPhenomena(readout(), isNight = false).mycelialGlow,
      "no glow by day"
    )
    assertFalse(
      MandatePhenomena.classifyPhenomena(readout(c0 = 0.0), isNight = true).mycelialGlow,
      "no glow without c0 shelter"
    )
    assertFalse(
      MandatePhenomena.classifyPhenomena(readout(sustenance = 0.3), isNight = true).mycelialGlow,
      "no glow on a damaged network"
    )
    assertFalse(
      MandatePhenomena
        .classifyPhenomena(readout(s = 0.50, regime = Regime.Strained), isNight = true)
        .mycelialGlow,
      "no glow outside Calm"
    )

  @Test def `precursor spores drift while c0 shelters a strained or oscillating cell`(): Unit =
    assertTrue(
      MandatePhenomena
        .classifyPhenomena(readout(s = 0.50, regime = Regime.Strained))
        .precursorSpore
    )
    assertTrue(
      MandatePhenomena
        .classifyPhenomena(readout(s = 0.60, q = 0.5, regime = Regime.Oscillating))
        .precursorSpore
    )
    assertFalse(
      MandatePhenomena.classifyPhenomena(readout()).precursorSpore,
      "a calm cell drifts no precursor spores"
    )

  @Test def `re-inoculation seeds only depleted and sub-toxic soil`(): Unit =
    val depleted = readout(s = 0.20, sustenance = 0.0)
    assertEquals(
      ReInoculationOutcome.Seeded,
      MandatePhenomena.evaluateReInoculation(depleted)
    )
    assertEquals(
      MandatePhenomena.ReInoculationSeed,
      ReInoculationOutcome.Seeded.targetLevel,
      1e-12,
      "the narrative seed must match the logistic regrowth seed of MandateSustenanceSpec"
    )

    val toxic = readout(s = 0.60, sustenance = 0.0)
    assertEquals(
      ReInoculationOutcome.AcidTooToxic,
      MandatePhenomena.evaluateReInoculation(toxic),
      "at the acid burn threshold the inoculum dies"
    )

    val viable = readout(s = 0.20, sustenance = 0.25)
    assertEquals(
      ReInoculationOutcome.AlreadyViable,
      MandatePhenomena.evaluateReInoculation(viable),
      "a network with living threads regrows on its own once stress drops"
    )

  @Test def `economy tiers follow the heartbeat window, with spiking as the acute tier`(): Unit =
    assertEquals(
      MandatePhenomena.EconomyMaintained,
      MandatePhenomena.classifyEconomy(readout().copy(pulseSuppressed = true)),
      "a fresh pulse window leaves the vanilla economy untouched"
    )
    assertEquals(
      MandatePhenomena.EconomyStagnant,
      MandatePhenomena.classifyEconomy(readout()),
      "no fresh pulse -> the economy sags at the baseline tier"
    )
    assertEquals(
      MandatePhenomena.EconomySpiking,
      MandatePhenomena.classifyEconomy(readout(v = 1.9)),
      "a live spike excursion escalates to the acute tier"
    )
    assertEquals(
      MandatePhenomena.EconomySpiking,
      MandatePhenomena.classifyEconomy(readout(s = 0.98, regime = Regime.Burnout)),
      "saturated burnout holds the acute tier"
    )
    assertEquals(
      MandatePhenomena.EconomyStagnant,
      MandatePhenomena.classifyEconomy(CellReadout.zero),
      "chunks outside the live lattice are barren by mechanism (draft-01 §7.8)"
    )
    assertEquals(
      MandatePhenomena.EconomyStagnant,
      MandatePhenomena.classifyEconomy(readout(v = -1.9)),
      "the negative repolarisation overshoot is not a spike for the economy either"
    )
    // Golden values: the whole point is legible severity, so pin the numbers.
    assertEquals(0, MandatePhenomena.EconomyMaintained.tradePriceBonus)
    assertEquals(2, MandatePhenomena.EconomyStagnant.tradePriceBonus)
    assertEquals(5, MandatePhenomena.EconomySpiking.tradePriceBonus)
    assertEquals(0.5, MandatePhenomena.EconomyStagnant.cropKeepChance, 1e-12)
    assertEquals(0.15, MandatePhenomena.EconomySpiking.breedingKeepChance, 1e-12)

  @Test def `sustenance word stays qualitative`(): Unit =
    assertEquals("枯绝", MandatePhenomena.sustenanceWord(0.0))
    assertEquals("受损", MandatePhenomena.sustenanceWord(0.3))
    assertEquals("完好", MandatePhenomena.sustenanceWord(1.0))

  @Test def `gastrodia bed grows only under calm stress and intact mycelial network`(): Unit =
    // Healthy calm cell -> Growing
    val healthy = readout(s = 0.20, regime = Regime.Calm, sustenance = 0.85)
    assertEquals(GastrodiaBedVerdict.Growing, MandatePhenomena.evaluateGastrodiaBed(healthy))
    assertTrue(MandatePhenomena.canInoculateTimberInField(healthy))

    // Strained stress (s >= 0.45) -> Stalled (note 06 §4.3)
    val strained = readout(s = 0.45, regime = Regime.Strained, sustenance = 1.0)
    assertEquals(GastrodiaBedVerdict.Stalled, MandatePhenomena.evaluateGastrodiaBed(strained))
    assertFalse(MandatePhenomena.canInoculateTimberInField(strained))
    assertTrue(
      MandatePhenomena.canRefurbishGastrodiaBed(strained),
      "a sub-toxic strained cell can still accept fresh timber bedding"
    )

    // Damaged mycelial network (floor <= M < 0.60) -> Stalled
    val damaged = readout(s = 0.10, regime = Regime.Calm, sustenance = 0.35)
    assertEquals(GastrodiaBedVerdict.Stalled, MandatePhenomena.evaluateGastrodiaBed(damaged))
    assertFalse(MandatePhenomena.canInoculateTimberInField(damaged))

    // Deep incapacitation via the readout threshold rule -> Withered (note 06
    // §4.3). The fact comes from the cell's own criterion (stacked pulse drive
    // across the knee), not from the displayed phase.
    val trajectoryBurnout = readout(
      s = 0.30,
      phase = PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee),
      sustenance = 0.80
    ).copy(pulseDrive = 2.1)
    assertTrue(trajectoryBurnout.isOffsetBelowKnee, "the readout threshold condition holds")
    assertEquals(GastrodiaBedVerdict.Withered, MandatePhenomena.evaluateGastrodiaBed(trajectoryBurnout))
    assertFalse(MandatePhenomena.canRefurbishGastrodiaBed(trajectoryBurnout))

    // 2026-11-12 gameplay decision: an ACUTE overload lockout now only STALLS
    // the bed — growth pauses (condition 1) and re-evaluates once the lockout
    // expires — instead of latching it withered. The lockout masks the readout
    // threshold reason; a dead network would still wither (death outranks).
    // The silent signature still fires for the same lockout: that is C2, still
    // pending, and the mismatch is deliberate for now.
    val overloadLocked = readout(
      s = 0.30,
      phase = PhaseReading.Undefined(UndefinedReason.OverloadLocked),
      sustenance = 0.80
    ).copy(
      pulseLedger = PulseLedger(overload = OverloadLock(200.0)),
      simTime = 100.0
    )
    assertTrue(overloadLocked.isOverloadLocked, "the ledger holds the lockout")
    assertFalse(overloadLocked.isOffsetBelowKnee, "the readout threshold is not crossed here")
    assertTrue(overloadLocked.isBurnoutPhase, "the coarse predicate still covers both reasons")
    assertEquals(
      GastrodiaBedVerdict.Stalled,
      MandatePhenomena.evaluateGastrodiaBed(overloadLocked),
      "a short lockout only pauses growth; the bed is not latched as withered"
    )
    assertFalse(MandatePhenomena.isGastrodiaBedDeepDead(overloadLocked))
    assertFalse(
      MandatePhenomena.canRefurbishGastrodiaBed(overloadLocked),
      "refurbishing is still blocked during a lockout: the gate keeps the pre-decision signal"
    )
    // [BATCH-2 REGRESSION LOCK] C2 pending: the silent signature still covers
    // the short lockout even though the bed now only stalls under it.
    assertTrue(
      MandatePhenomena.classifyPhenomena(overloadLocked, isNight = true).silentBlight,
      "C2 still pending: the silent signature still covers the lockout"
    )

    // Dead mycelial network (M = 0) -> Withered
    val deadMycelium = readout(s = 0.10, sustenance = 0.0)
    assertEquals(GastrodiaBedVerdict.Withered, MandatePhenomena.evaluateGastrodiaBed(deadMycelium))
    assertFalse(MandatePhenomena.canRefurbishGastrodiaBed(deadMycelium))

    // Rich timber inoculum seed stays strictly between wild seed and viable threshold
    assertTrue(MandatePhenomena.RichReInoculationSeed > MandatePhenomena.ReInoculationSeed)
    assertTrue(MandatePhenomena.RichReInoculationSeed < MandatePhenomena.ReInoculationMaxSustenance)

  @Test def `dead network plus an active lockout: both facts hold, neither cancels the other`(): Unit =
    // Scenario D of docs/mechanism-state-responsibilities.md §6. The phase slot
    // shows the mechanism reason, but the ecological fact (dead network) is
    // still true and every downstream consequence fires.
    val deadAndLocked = readout(
      s = 0.10,
      phase = PhaseReading.Undefined(UndefinedReason.OverloadLocked),
      sustenance = 0.0
    ).copy(
      pulseLedger = PulseLedger(overload = OverloadLock(200.0)),
      simTime = 100.0
    )
    assertTrue(
      deadAndLocked.pulseLedger.overloadedAt(deadAndLocked.simTime),
      "the ledger still holds the lockout"
    )
    assertTrue(
      deadAndLocked.isOffsetBelowKnee,
      "M = 0 also crosses the readout threshold: both facts hold at once"
    )
    assertTrue(deadAndLocked.isOverloadLocked)
    assertTrue(deadAndLocked.isBurnoutPhase)
    assertTrue(
      MandatePhenomena.classifyPhenomena(deadAndLocked, isNight = true).silentBlight,
      "a dead network is silent blight regardless of the lockout"
    )
    assertEquals(
      GastrodiaBedVerdict.Withered,
      MandatePhenomena.evaluateGastrodiaBed(deadAndLocked),
      "Withered does not depend on which no-cycle reason the phase slot shows"
    )
    assertFalse(
      MandatePhenomena.canRefurbishGastrodiaBed(deadAndLocked),
      "a dead network cannot be refurbished just because the lockout expired"
    )

  @Test def `silent blight and bed death use different M thresholds`(): Unit =
    // `0 < M < floor` is REACHABLE transiently: the dynamics snap M to 0 only
    // when it falls below the floor AND the local trend is non-positive, so
    // diffusion from living neighbours (or the narrative `SetSustenance`
    // channel) can leave a sub-floor positive value. The two facts keep
    // different thresholds on purpose: the signature marks the absorbing state
    // (M <= 0), the bed rule marks "below the Allee floor". Do not merge them.
    val vanishing = readout(s = 0.10, sustenance = 0.0)
    assertTrue(vanishing.isMyceliumAbsent)
    assertFalse(vanishing.isMyceliumViable)
    assertTrue(MandatePhenomena.hasSilentBlight(vanishing))
    assertTrue(MandatePhenomena.isGastrodiaBedDeepDead(vanishing))

    val belowFloorButPositive = readout(s = 0.10, sustenance = 0.5 * StressParam().sustenanceFloor)
      // The depth is zeroed only so the readout offset stays above the knee and
      // the two M thresholds can be observed independently; with the live depth
      // an M this low drags a_eff below the knee as well.
      .copy(stressParam = StressParam(sustenanceDepth = 0.0))
    assertFalse(belowFloorButPositive.isMyceliumAbsent, "not the absorbing state")
    assertFalse(belowFloorButPositive.isMyceliumViable, "below the Allee floor")
    assertFalse(
      MandatePhenomena.hasSilentBlight(belowFloorButPositive),
      "silent blight binds to M <= 0, not to the Allee floor"
    )
    assertTrue(
      MandatePhenomena.isGastrodiaBedDeepDead(belowFloorButPositive),
      "the bed rule uses the Allee floor"
    )
    assertEquals(
      GastrodiaBedVerdict.Withered,
      MandatePhenomena.evaluateGastrodiaBed(belowFloorButPositive)
    )

    // 2026-11-12 gameplay decision: an acute lockout still fires the silent
    // signature (C2 pending) but no longer makes the bed deep-dead. The network
    // is alive, so neither verdict fact comes from M here.
    val locked = readout(
      s = 0.30,
      phase = PhaseReading.Undefined(UndefinedReason.OverloadLocked),
      sustenance = 0.80
    ).copy(
      pulseLedger = PulseLedger(overload = OverloadLock(200.0)),
      simTime = 100.0
    )
    assertTrue(locked.isMyceliumViable, "the network is alive during a short lockout")
    assertTrue(MandatePhenomena.hasSilentBlight(locked))
    assertFalse(
      MandatePhenomena.isGastrodiaBedDeepDead(locked),
      "the lockout masks the threshold reason; the bed only stalls"
    )
    assertEquals(GastrodiaBedVerdict.Stalled, MandatePhenomena.evaluateGastrodiaBed(locked))

  @Test def `lockout masks the readout threshold but never masks a dead network`(): Unit =
    // Precedence of the 2026-11-12 bed decision (docs/mechanism-state-
    // responsibilities.md §5 C1/C14): network death > acute lockout > readout
    // threshold reason.
    val thresholdOnly = readout(
      s = 0.30,
      phase = PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee),
      sustenance = 0.80
    ).copy(pulseDrive = 2.1)
    assertTrue(thresholdOnly.isOffsetBelowKnee)
    assertFalse(thresholdOnly.isOverloadLocked)
    assertTrue(MandatePhenomena.isGastrodiaBedDeepDead(thresholdOnly))
    assertEquals(GastrodiaBedVerdict.Withered, MandatePhenomena.evaluateGastrodiaBed(thresholdOnly))

    val thresholdPlusLockout = thresholdOnly.copy(
      pulseLedger = PulseLedger(overload = OverloadLock(200.0)),
      simTime = 100.0
    )
    assertTrue(thresholdPlusLockout.isOffsetBelowKnee, "the readout fact does not vanish")
    assertTrue(thresholdPlusLockout.isOverloadLocked)
    assertFalse(
      MandatePhenomena.isGastrodiaBedDeepDead(thresholdPlusLockout),
      "the active lockout masks the readout-threshold reason"
    )
    assertEquals(
      GastrodiaBedVerdict.Stalled,
      MandatePhenomena.evaluateGastrodiaBed(thresholdPlusLockout),
      "a locked bed cannot be latched withered by the threshold reason"
    )
    assertFalse(
      MandatePhenomena.canRefurbishGastrodiaBed(thresholdPlusLockout),
      "a latched/deep-dead bed must not become refurbishable during the lockout mask"
    )

    val deadPlusLockout = readout(
      s = 0.10,
      phase = PhaseReading.Undefined(UndefinedReason.OverloadLocked),
      sustenance = 0.0
    ).copy(
      pulseLedger = PulseLedger(overload = OverloadLock(200.0)),
      simTime = 100.0
    )
    assertTrue(deadPlusLockout.isOverloadLocked)
    assertTrue(
      MandatePhenomena.isGastrodiaBedDeepDead(deadPlusLockout),
      "a dead network still withers under a lockout: death outranks the mask"
    )
    assertEquals(GastrodiaBedVerdict.Withered, MandatePhenomena.evaluateGastrodiaBed(deadPlusLockout))

  @Test def `the refurbish gate keeps the pre-decision predicate`(): Unit =
    // Before 2026-11-12 the gate was `evaluateGastrodiaBed != Withered && s <
    // toxicity`, i.e. exactly `!(isBurnoutPhase || M < max(floor, 1e-9)) && s <
    // toxicity`. The new verdict masks the threshold reason under a lockout, so
    // the gate must NOT follow it (a latched bed would become refurbishable and
    // waste the material). Enumerate the inputs so a refactor cannot silently
    // reuse the masked verdict.
    def legacy(r: CellReadout): Boolean =
      !(r.isOffsetBelowKnee || r.isOverloadLocked ||
        r.sustenance < Math.max(r.stressParam.sustenanceFloor, 1e-9)) &&
        r.current.stress < r.stressParam.sustenanceBurnThreshold
    var checked = 0
    for
      m <- List(0.0, 0.00005, 0.3, 1.0)
      s <- List(0.2, 0.5, 0.62)
      locked <- List(false, true)
      drive <- List(0.0, 2.1)
    do
      val r = CellReadout(
        MandateCurrent(-1.2, -0.6, s, 0.0),
        Readout.regime(MandateCurrent(-1.2, -0.6, s, 0.0)),
        PhaseReading.Basin(PhaseLabel.Resting),
        sustenance = m,
        pulseDrive = drive,
        pulseLedger =
          if locked then PulseLedger(overload = OverloadLock(100.0)) else PulseLedger.zero,
        simTime = 10.0
      )
      assertEquals(
        legacy(r),
        MandatePhenomena.canRefurbishGastrodiaBed(r),
        s"M=$m s=$s locked=$locked drive=$drive"
      )
      checked += 1
    assertEquals(4 * 3 * 2 * 2, checked)

  @Test def `zh_cn and en_us language files keep identical key sets`(): Unit =
    import com.google.gson.JsonParser
    def keys(name: String): java.util.Set[String] =
      val stream = getClass.getResourceAsStream(s"/assets/variations-of-yesterday/lang/$name")
      assertNotNull(stream, s"missing $name on the test classpath")
      JsonParser
        .parseString(String(stream.readAllBytes(), "UTF-8"))
        .getAsJsonObject
        .keySet()
    assertEquals(keys("zh_cn.json"), keys("en_us.json"), "lang key sets must match")
