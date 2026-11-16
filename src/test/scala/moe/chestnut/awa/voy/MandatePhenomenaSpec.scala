package moe.chestnut.awa.voy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import moe.chestnut.awa.voy.inner.plant.GastrodiaBedVerdict
import moe.chestnut.awa.voy.mandate.MandatePhenomena
import moe.chestnut.awa.voy.mandate.MandatePhenomena.{ReInoculationOutcome, YellowSeepTier}
import moe.chestnut.awa.voy.inner.veins.*

/** Golden mapping of the 4 orthogonal consumer projections in [[MandatePhenomena]]:
  * economy tiers, player bodily exposure, sensory signatures, and mycelial
  * symbiosis (`GastrodiaBed` & re-inoculation).
  */
class MandatePhenomenaSpec:

  private def readout(
      v: Double = -1.2,
      s: Double = 0.05,
      q: Double = 0.0,
      regime: Regime = Regime.Calm,
      phase: PhaseReading = PhaseReading.Basin(PhaseLabel.Resting),
      c0: Double = 0.03,
      sustenance: Double = 1.0,
      heartbeat: Heartbeat = Heartbeat.Warming,
      lastBellStrikeAt: Double = Double.NaN,
      simTime: Double = 0.0
  ): CellReadout =
    CellReadout(
      current = MandateCurrent(v, -0.6, s, q),
      regime = regime,
      phase = phase,
      baselineCalming = c0,
      sustenance = sustenance,
      heartbeat = heartbeat,
      lastBellStrikeAt = lastBellStrikeAt,
      simTime = simTime
    )

  @Test def `silent blight binds to ecological incapacitation, never to pulse cooldown or saturated oscillation`(): Unit =
    val collapsedOffset = MandatePhenomena.classifyPhenomena(
      readout(phase = PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee)),
      isNight = true
    )
    assertTrue(collapsedOffset.silentBlight, "slow offset below knee must read as silent blight")
    assertFalse(collapsedOffset.mycelialGlow, "silent blight suppresses the glow")
    assertFalse(collapsedOffset.precursorSpore, "silent blight suppresses precursor spores")

    val deadNetwork = MandatePhenomena.classifyPhenomena(
      readout(sustenance = 0.0),
      isNight = true
    )
    assertTrue(deadNetwork.silentBlight, "dead mycelium is silent blight")

    val inOverdriveGuard = MandatePhenomena.classifyPhenomena(
      readout(lastBellStrikeAt = 2.0, simTime = 5.0),
      isNight = true
    )
    assertFalse(
      inOverdriveGuard.silentBlight,
      "a recent bell strike is NOT ecological death and must not trigger silent blight"
    )

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
    assertEquals(YellowSeepTier.Heavy, saturated.seepTier)

  @Test def `yellow seep escalates from precursor stress through acid residue to live spike`(): Unit =
    assertEquals(YellowSeepTier.None, MandatePhenomena.classifySeepTier(readout(s = 0.10)))
    assertEquals(YellowSeepTier.Light, MandatePhenomena.classifySeepTier(readout(s = 0.30)))
    assertEquals(
      YellowSeepTier.Moderate,
      MandatePhenomena.classifySeepTier(readout(s = 0.50, regime = Regime.Strained))
    )
    assertEquals(YellowSeepTier.Moderate, MandatePhenomena.classifySeepTier(readout(s = 0.10, q = 0.15)))
    assertEquals(YellowSeepTier.Heavy, MandatePhenomena.classifySeepTier(readout(s = 0.10, q = 0.35)))
    assertEquals(YellowSeepTier.Heavy, MandatePhenomena.classifySeepTier(readout(v = 1.9)))
    assertEquals(YellowSeepTier.Heavy, MandatePhenomena.classifySeepTier(readout(v = -1.9)))

  @Test def `mycelial glow needs night, calm, shelter and an intact network`(): Unit =
    assertTrue(MandatePhenomena.classifyPhenomena(readout(), isNight = true).mycelialGlow)
    assertFalse(MandatePhenomena.classifyPhenomena(readout(), isNight = false).mycelialGlow)
    assertFalse(MandatePhenomena.classifyPhenomena(readout(c0 = 0.0), isNight = true).mycelialGlow)
    assertFalse(MandatePhenomena.classifyPhenomena(readout(sustenance = 0.3), isNight = true).mycelialGlow)
    assertFalse(
      MandatePhenomena
        .classifyPhenomena(readout(s = 0.50, regime = Regime.Strained), isNight = true)
        .mycelialGlow
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
    assertFalse(MandatePhenomena.classifyPhenomena(readout()).precursorSpore)

  @Test def `re-inoculation seeds only depleted and sub-toxic soil`(): Unit =
    val depleted = readout(s = 0.20, sustenance = 0.0)
    assertEquals(ReInoculationOutcome.Seeded, MandatePhenomena.evaluateReInoculation(depleted))
    assertEquals(MandatePhenomena.ReInoculationSeed, ReInoculationOutcome.Seeded.targetLevel, 1e-12)

    val toxic = readout(s = 0.60, sustenance = 0.0)
    assertEquals(ReInoculationOutcome.AcidTooToxic, MandatePhenomena.evaluateReInoculation(toxic))

    val viable = readout(s = 0.20, sustenance = 0.25)
    assertEquals(ReInoculationOutcome.AlreadyViable, MandatePhenomena.evaluateReInoculation(viable))

  @Test def `economy tiers follow the heartbeat rhythm (design note 11 section 1 point 4)`(): Unit =
    val beating = readout(heartbeat = Heartbeat.Beating(40.0, 0.10, BeatSource.Intrinsic))
    assertEquals(MandatePhenomena.EconomyMaintained, MandatePhenomena.classifyEconomy(beating))
    assertEquals(
      MandatePhenomena.EconomyMaintained,
      MandatePhenomena.classifyEconomy(readout()),
      "Warming counts as maintained"
    )
    assertEquals(
      MandatePhenomena.EconomyMaintained,
      MandatePhenomena.classifyEconomy(CellReadout.zero),
      // Ruling 2026-11-16 (note 12 §9.3-1): chunks outside the simulated lattice
      // are the ORDINARY region and are safe by design; stagnation belongs to the
      // story region (the active valley). This is a design ruling, not an
      // artefact of `Warming` being the default.
      "the ordinary region outside the lattice is not pressured"
    )
    assertEquals(
      MandatePhenomena.EconomySpiking,
      MandatePhenomena.classifyEconomy(readout(heartbeat = Heartbeat.Fast(20.0)))
    )
    assertEquals(
      MandatePhenomena.EconomySpiking,
      MandatePhenomena.classifyEconomy(readout(heartbeat = Heartbeat.Arrhythmic(44.0, 0.45)))
    )
    assertEquals(
      MandatePhenomena.EconomyStagnant,
      MandatePhenomena.classifyEconomy(readout(heartbeat = Heartbeat.Slow(Double.NaN)))
    )
    assertEquals(
      MandatePhenomena.EconomyStagnant,
      MandatePhenomena.classifyEconomy(beating.copy(sustenance = 0.0)),
      "ecological death stays stagnant regardless of the rhythm verdict"
    )
    assertEquals(
      MandatePhenomena.EconomyStagnant,
      MandatePhenomena.classifyEconomy(
        beating.copy(phase = PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee))
      ),
      "a below-knee cell stays stagnant regardless of the rhythm verdict"
    )

  @Test def `heartbeat bodily strain: acute means Fast or Arrhythmic, never Slow`(): Unit =
    val fast = readout(heartbeat = Heartbeat.Fast(20.0))
    assertTrue(MandatePhenomena.shouldApplyAcuteBodyStrain(fast))
    assertTrue(
      MandatePhenomena.shouldApplyAcuteBodyStrain(readout(heartbeat = Heartbeat.Arrhythmic(44.0, 0.45)))
    )
    assertFalse(
      MandatePhenomena.shouldApplyAcuteBodyStrain(readout(heartbeat = Heartbeat.Slow(Double.NaN))),
      "stagnation only presses the economy, never the body"
    )
    assertFalse(
      MandatePhenomena.shouldApplyAcuteBodyStrain(
        readout(heartbeat = Heartbeat.Beating(40.0, 0.10, BeatSource.Paced))
      )
    )

  @Test def `gastrodia bed grows under calm stress and intact mycelial network, independent of pulse cooldown`(): Unit =
    val healthy = readout(s = 0.20, regime = Regime.Calm, sustenance = 0.85)
    assertEquals(GastrodiaBedVerdict.Growing, MandatePhenomena.evaluateGastrodiaBed(healthy))
    assertTrue(MandatePhenomena.canInoculateTimberInField(healthy))

    val inGuard = healthy.copy(lastBellStrikeAt = 2.0, simTime = 5.0)
    assertEquals(
      GastrodiaBedVerdict.Growing,
      MandatePhenomena.evaluateGastrodiaBed(inGuard),
      "the bell overdrive guard is a strike rule and does not stall healthy mycelial beds"
    )

    val strained = readout(s = 0.45, regime = Regime.Strained, sustenance = 1.0)
    assertEquals(GastrodiaBedVerdict.Stalled, MandatePhenomena.evaluateGastrodiaBed(strained))
    assertFalse(MandatePhenomena.canInoculateTimberInField(strained))
    assertTrue(MandatePhenomena.canRefurbishGastrodiaBed(strained))

    val damaged = readout(s = 0.10, regime = Regime.Calm, sustenance = 0.35)
    assertEquals(GastrodiaBedVerdict.Stalled, MandatePhenomena.evaluateGastrodiaBed(damaged))
    assertFalse(MandatePhenomena.canInoculateTimberInField(damaged))

    val slowBelowKnee = readout(
      s = 0.70,
      regime = Regime.Strained,
      phase = PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee),
      sustenance = 0.50
    )
    assertEquals(GastrodiaBedVerdict.Stalled, MandatePhenomena.evaluateGastrodiaBed(slowBelowKnee))

    val deadMycelium = readout(s = 0.10, sustenance = 0.0)
    assertEquals(GastrodiaBedVerdict.Withered, MandatePhenomena.evaluateGastrodiaBed(deadMycelium))
    assertFalse(MandatePhenomena.canRefurbishGastrodiaBed(deadMycelium))

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
