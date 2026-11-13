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
      pulseStamp: PulseStamp = PulseStamp.zero,
      simTime: Double = 0.0
  ): CellReadout =
    CellReadout(
      current = MandateCurrent(v, -0.6, s, q),
      regime = regime,
      phase = phase,
      baselineCalming = c0,
      sustenance = sustenance,
      pulseStamp = pulseStamp,
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

    val inPulseCooldown = MandatePhenomena.classifyPhenomena(
      readout(pulseStamp = PulseStamp(cooldownUntil = 20.0), simTime = 5.0),
      isNight = true
    )
    assertFalse(
      inPulseCooldown.silentBlight,
      "a short pulse cooldown is NOT ecological death and must not trigger silent blight"
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

  @Test def `economy tiers follow the shelter window, with spiking as the acute tier`(): Unit =
    val sheltered = readout(pulseStamp = PulseStamp(shelterUntil = 40.0, cooldownUntil = 8.0), simTime = 2.0)
    assertEquals(MandatePhenomena.EconomyMaintained, MandatePhenomena.classifyEconomy(sheltered))
    assertEquals(MandatePhenomena.EconomyStagnant, MandatePhenomena.classifyEconomy(readout()))
    assertEquals(MandatePhenomena.EconomySpiking, MandatePhenomena.classifyEconomy(readout(v = 1.9)))
    assertEquals(
      MandatePhenomena.EconomySpiking,
      MandatePhenomena.classifyEconomy(readout(s = 0.98, regime = Regime.Burnout))
    )
    assertEquals(MandatePhenomena.EconomyStagnant, MandatePhenomena.classifyEconomy(CellReadout.zero))
    assertEquals(MandatePhenomena.EconomyStagnant, MandatePhenomena.classifyEconomy(readout(v = -1.9)))

  @Test def `sustenance word stays qualitative`(): Unit =
    assertEquals("枯绝", MandatePhenomena.sustenanceWord(0.0))
    assertEquals("受损", MandatePhenomena.sustenanceWord(0.3))
    assertEquals("完好", MandatePhenomena.sustenanceWord(1.0))

  @Test def `gastrodia bed grows under calm stress and intact mycelial network, independent of pulse cooldown`(): Unit =
    val healthy = readout(s = 0.20, regime = Regime.Calm, sustenance = 0.85)
    assertEquals(GastrodiaBedVerdict.Growing, MandatePhenomena.evaluateGastrodiaBed(healthy))
    assertTrue(MandatePhenomena.canInoculateTimberInField(healthy))

    val inCooldown = healthy.copy(
      pulseStamp = PulseStamp(shelterUntil = Double.NegativeInfinity, cooldownUntil = 20.0),
      simTime = 5.0
    )
    assertEquals(
      GastrodiaBedVerdict.Growing,
      MandatePhenomena.evaluateGastrodiaBed(inCooldown),
      "pulse cooldown is a bell/shelter rule and does not stall healthy mycelial beds"
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
