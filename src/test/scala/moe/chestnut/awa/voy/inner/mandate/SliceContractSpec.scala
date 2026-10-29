package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.{Test, TestInstance}

/** The cross-stack contract: what it claims, and what it must refuse to
  * claim (§1.1, §1.1.4, §2.1).
  *
  * Three things are checked here and nothing else:
  *
  *  1. every analytic number is what the closed form says;
  *  2. every measured number is step-converged and carries its own
  *     disagreement with the design draft as data;
  *  3. the checked-in JSON is exactly the rendering of what this run
  *     measured — so the artifact Elixir reads cannot drift away from the
  *     numbers Scala actually verified.
  *
  * Per-class lifecycle so the (expensive) measurement runs once for the
  * whole spec instead of once per test.
  */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SliceContractSpec:
  private lazy val data = SliceContract.measure()
  private lazy val rendered = SliceContract.render(data)

  @Test def `the checked-in contract is the rendering of this run`(): Unit =
    if SliceContract.writeRequested then
      SliceContract.write(rendered)
      println(s"[contract] rewrote ${SliceContract.contractPath}")
    val checked = SliceContract.checkedIn()
    assertEquals(
      checked,
      rendered,
      s"contracts/mandate-slice-v1.json does not match the measurement; " +
        "regenerate deliberately with gradlew test -Dvoy.contract.write=true"
    )

  @Test def `the period is step-converged and the control spike is stable`(): Unit =
    assertTrue(data.period > 20.0 && data.period < 80.0, s"period ${data.period}")
    val rows = data.periods.map(_.measured)
    val spread = (rows.max - rows.min) / rows.head
    assertTrue(spread < 0.001, s"period must be step-converged across ${data.periods.map(_.dt)}: $rows")
    assertTrue(data.controlSpike.isDefined, "the unpulsed reference spike must exist")
    assertTrue(data.controlSpike.get.peak > PhaseResponse.spikeLevel)

  @Test def `the analytic Hopf and burnout boundaries are exact`(): Unit =
    assertEquals(-Math.sqrt(1.0 - SliceContract.fast.epsilon * SliceContract.fast.beta), data.hopfV, 1e-9)
    assertEquals(SliceContract.fast.alpha - data.hopfA, data.hopfS, 1e-12)
    assertEquals(
      SliceContract.fast.beta * (2.0 / 3.0) - 1.0,
      Readout.burnoutA(SliceContract.fast),
      1e-12
    )

  @Test def `no declared pulse reaches the fire threshold at the slice operating point`(): Unit =
    // The point of the contract: the design draft's vulnerable window is not
    // reachable with the doses the content declares.
    val threshold = data.fireThreshold
    assertTrue(threshold.isDefined, "a threshold must be measurable, or the channel is dead")
    assertTrue(
      threshold.get > PulseKind.AlphaShock.shape.area,
      s"fire threshold ${threshold.get} must exceed the declared alpha_shock dose"
    )
    for k <- data.kinds do
      if k.role == "world" && k.kind != PulseKind.MetalResonance && k.kind != PulseKind.Overwhelming then
        assertTrue(k.fire.isEmpty, s"${k.kind.wire} must not fire anywhere: ${k.fire}")

  @Test def `the declared window midpoints do not reproduce the draft window semantics`(): Unit =
    val alpha = data.kind(PulseKind.AlphaShock.wire).get
    def outcomeOf(w: PhaseWindow) = alpha.table.find(_.window == w).get.trial.outcome
    assertEquals(PulseOutcome.Delayed, outcomeOf(PhaseWindow.Relative), "draft says fire, measurement says delay")
    assertEquals(PulseOutcome.Delayed, outcomeOf(PhaseWindow.Absolute), "draft says absorb, measurement says delay")
    assertEquals(PulseOutcome.Absorbed, outcomeOf(PhaseWindow.Late))

  @Test def `every claim in the contract is generated from a measurement`(): Unit =
    val claims = data.claims
    assertEquals(5, claims.size)
    // The two draft numbers and the fire-window layout are refuted; the
    // refractory-delay claim and the bare existence of window structure
    // survive. Both verdicts are computed from measurements, above.
    assertEquals("refuted", claims.find(_.id == "hopf.a_c").get.verdict)
    assertEquals("refuted", claims.find(_.id == "prc.vulnerable_window_fires").get.verdict)
    assertEquals("supported", claims.find(_.id == "prc.absolute_window_delays").get.verdict)
    assertEquals("supported", claims.find(_.id == "prc.window_structure_exists").get.verdict)
    assertEquals("refuted-at-eps=0.08", claims.find(_.id == "period.singular_limit").get.verdict)

  @Test def `the c0 footprints are the discriminator the slice needs`(): Unit =
    assertTrue(
      data.collapsed.impactTimes.size >= 20,
      s"collapsed must keep spiking, saw ${data.collapsed.impactTimes.size}"
    )
    assertEquals(1, data.intact.impactTimes.size, "intact must show exactly one transient")
    assertTrue(data.collapsed.meanStress > 0.5 && data.intact.meanStress < 0.2)
    assertTrue(
      data.collapsed.settledPeriod.exists(sp => Math.abs(sp - data.period) < 0.15),
      s"settled period ${data.collapsed.settledPeriod} must agree with oscillator period ${data.period}"
    )
    assertTrue(
      data.duty.plateauFraction > 0.50 && data.onset.duty.plateauFraction < 0.35,
      s"duty-cycle inversion must be recorded in the contract: sat=${data.duty.plateauFraction}, onset=${data.onset.duty.plateauFraction}"
    )

  @Test def `the chain observation refuses phase superposition`(): Unit =
    assertTrue(
      !data.chain.composable || data.chain.relativePeriodGap.exists(_ <= 0.10),
      s"composability must be justified: ${data.chain}"
    )

  @Test def `the regime samples are the stock partition, not the phase domain`(): Unit =
    assertEquals(10, data.samples.size)
    val byPoint = data.samples.map(s => (s.stress, s.activity) -> s.regime).toMap
    assertEquals(Regime.Calm, byPoint((0.0, 0.0)))
    // `calm` is a tolerance threshold, not a partition edge: the partition
    // only knows strained / oscillating / burnout plus the default.
    assertEquals(Regime.Calm, byPoint((0.18, 0.0)))
    assertEquals(Regime.Strained, byPoint((0.45, 0.0)))
    assertEquals(Regime.Oscillating, byPoint((0.50, 0.40)))
    assertEquals(Regime.Burnout, byPoint((0.99, 0.90)))
