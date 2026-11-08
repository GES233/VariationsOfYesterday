package moe.chestnut.awa.voy.inner.mandate

import scala.collection.mutable.ArrayBuffer

import moe.chestnut.awa.voy.inner.event.EventMsg

/** Closed taxonomy of p-channel pulse transmitters.
  *
  * The kernel time constant is a property of the transmitter TYPE, not a
  * number an emitter may choose: the protocol never carries a free `tau`,
  * so nothing outside the engine can reshape the synapse kernel at will.
  * Adding a transmitter is an engine change (and a design decision), never
  * player input.
  */
enum PulseTransmitter(val tau: Double):
  /** Long alpha kernel of a bell strike — the original alpha-shock
    * channel (design draft §1.2). */
  case Bell extends PulseTransmitter(5.0)

  /** Narrow crystal kernel of a pulse link (design note 07). */
  case CrystalLink extends PulseTransmitter(2.0)

/** One crystal link between two lattice cells (design note 07).
  *
  * The link is **phase information only**: when `src` fires, a small
  * `alpha`-kernel pulse of `area` shaped by `transmitter` is delivered to
  * `dst` after `delay` simulation-time units. Deliberately orthogonal to
  * the four equations in [[MandateState]]:
  *
  *   - it travels on the existing `FirePulse` channel (p), never on the
  *     stress channels (u / c / c0), so a link cannot move land stress;
  *   - it adds no term to the RK4 vector, so the Python prototype's numbers
  *     stay reproducible;
  *   - its kernel is a **transmitter constant**, not a player action:
  *     `CrystalLink` is the narrow `tau = 2.0` kernel, `Bell` the original
  *     `tau = 5.0` alpha-shock / bell kernel.
  *
  * The default `area = 0.5` is the measured locking threshold (design note
  * 06, E2/E3) — a link weaker than that cannot pull a neighbour's phase.
  */
final case class PulseLink(
    src: CellCoord,
    dst: CellCoord,
    area: Double = PulseLink.LockThresholdArea,
    transmitter: PulseTransmitter = PulseTransmitter.CrystalLink,
    delay: Double = PulseCoupling.DefaultDelay
)

object PulseLink:
  /** Smallest coupling area that locked a same-period pair in the
    * prototype (E2: `area >= 0.5`, `tau = 2.0`). Crystal quality is graded
    * against this number, not against an arbitrary constant. */
  val LockThresholdArea: Double = 0.5

/** What counts as "the source fired" decides what the link transmits.
  *
  * The prototype counted every Poincaré section upcrossing. That is a
  * *phase sensor*, and it is what produced the reference numbers — but the
  * project already knows a driven crossing is not a spike
  * (`PhaseResponseSpec`: "a driven threshold crossing is not a spike"). A
  * strong pulse can lift `v` over 1.2 without producing an action
  * potential, and a crossing-based link would relay that artefact as a
  * genuine transmission.
  */
enum SpikeCriterion:
  /** Fire on every section upcrossing (`v` passes 1.2 upwards). Exact
    * prototype parity; used by the numeric fingerprint tests. */
  case SectionCrossing

  /** Fire only once a crossing grows into a genuine excursion (peak reaches
    * 1.8). A crossing that stalls below the spike level transmits nothing.
    * The game layer uses this one: the cost is a short confirmation delay
    * (the upstroke from 1.2 to 1.8), not a full cycle, because the spike
    * time reported to the link is still the section crossing. */
  case PeakConfirmed

/** Raw output of an offline coupled run: section crossings and, optionally,
  * the stress trajectory of every cell. */
final case class CoupledTrace(
    crossings: Map[CellCoord, Seq[Double]],
    stress: Map[CellCoord, Seq[(Double, Double)]]
)

/** The transmitted-pulse scheduler and spike detector shared by the actor
  * and the offline harnesses.
  *
  * Determinism: pending pulses are a stable queue ordered by
  * `(fireAt, arrival order)`; cells are observed in coordinate order, so the
  * same lattice and the same event stream produce the same deliveries.
  * Delivery happens before the step (`duePulses`), detection after it
  * (`observe`) — the same ordering the prototype used.
  */
final class PulseLinkRunner(
    initialLinks: Seq[PulseLink],
    val section: Double = PhaseResponse.section,
    val spikeLevel: Double = PhaseResponse.spikeLevel,
    val criterion: SpikeCriterion = SpikeCriterion.PeakConfirmed,
    val confirmWindow: Double = PulseCoupling.SpikeConfirmWindow
):
  // Links are runtime wiring (AddLink/RemoveLink topology events), keyed by
  // (src, dst): re-adding a pair replaces its parameters. Mutation only
  // happens on the actor thread, between steps.
  private var linksV: Vector[PulseLink] = initialLinks.toVector

  /** The currently registered links. */
  def links: Seq[PulseLink] = linksV

  /** Register a link; an existing `(src, dst)` pair is replaced. */
  def addLink(link: PulseLink): Unit =
    linksV = linksV.filterNot(l => l.src == link.src && l.dst == link.dst) :+ link

  /** Remove the link `src -> dst` if present. Pulses it already scheduled
    * are in-flight events and are not retracted. */
  def removeLink(src: CellCoord, dst: CellCoord): Unit =
    linksV = linksV.filterNot(l => l.src == src && l.dst == dst)

  /** Drop the spike detector of a removed cell, so a cell later
    * re-registered at the same coordinate does not inherit a stale `v`. */
  def forgetCell(coord: CellCoord): Unit =
    trends.remove(coord)
  private final case class Scheduled(
      fireAt: Double,
      dst: CellCoord,
      area: Double,
      transmitter: PulseTransmitter,
      order: Long
  )

  /** Running detector for one cell, delegating the spike state machine to
    * [[SpikeDetector]]. Links confirm within a window (ghost-relay weld) and
    * interpolate the crossing time, so coarse steps do not quantize the phase a
    * link reports; the prototype fingerprint mode still emits at the crossing.
    */
  private final class CellTrend:
    private val detector = new SpikeDetector(
      section = section,
      spikeLevel = spikeLevel,
      confirm = criterion match
        case SpikeCriterion.SectionCrossing => SpikeConfirm.AtSectionCrossing
        case SpikeCriterion.PeakConfirmed => SpikeConfirm.WithinWindow(confirmWindow),
      interpolateCrossing = true
    )

    def observe(t: Double, v: Double): Option[Double] =
      detector.observe(t, v).map(_.at)

  private var pending: Vector[Scheduled] = Vector.empty
  private var nextOrder: Long = 0L
  private val trends = scala.collection.mutable.HashMap.empty[CellCoord, CellTrend]

  /** Pulses whose delivery time has arrived, in deterministic order. */
  def duePulses(now: Double): Seq[EventMsg.FirePulse] =
    val (due, rest) = pending.partition(_.fireAt <= now)
    pending = rest
    due
      .sortBy(s => (s.fireAt, s.order))
      .map(s => EventMsg.FirePulse(s.dst, s.area, s.transmitter))

  /** Advance every cell's detector by one step and enqueue the links of the
    * cells that fired, stamped with the spike time. Returns those spikes so
    * callers (replay, the game side) can log them. */
  def observe(now: Double, cells: Map[CellCoord, MandateState]): Seq[(CellCoord, Double)] =
    val fired = ArrayBuffer.empty[(CellCoord, Double)]
    cells.keys.toSeq.sortBy(c => (c.x, c.z)).foreach { coord =>
      val trend = trends.getOrElseUpdate(coord, new CellTrend)
      trend.observe(now, cells(coord).getState.dissonance).foreach(at => fired += (coord -> at))
    }
    fired.foreach { case (src, at) =>
      linksV.foreach { link =>
        if link.src == src then
          pending = pending :+ Scheduled(at + link.delay, link.dst, link.area, link.transmitter, nextOrder)
          nextOrder += 1
      }
    }
    fired.toSeq

/** Prototype migration of the cloud agent's `mandate/coupling.py`
  * (design note 07). Everything here is pure: no Minecraft types, no clock.
  */
object PulseCoupling:
  /** Nominal link latency: one simulation-time unit. A fixed delay is the
    * point of E4/E4b — it is what breaks under stress drift. */
  val DefaultDelay: Double = 1.0

  /** Confirmation window of the `PeakConfirmed` criterion: the section
    * upcrossing and the spike level must be reached within this many
    * simulation-time units of each other. Genuine FHN upstrokes take
    * ~0.6 (saturated) to ~1.2 (near-critical) units; the shared window keeps a
    * wide margin while rejecting driven creeps (ghost relays). */
  val SpikeConfirmWindow: Double = PhaseDetection.ConfirmWindow

  /** Fold each `other` spike into the window `(-period/2, period/2]` of the
    * nearest `ref` spike: the prototype's `lag_series`, unchanged.
    *
    * `j` is a shared cursor over `other` (never reset), exactly like the
    * original: lag series are consumed in time order.
    */
  def lagSeries(
      ref: Seq[Double],
      other: Seq[Double],
      period: Double
  ): Seq[(Double, Double)] =
    val out = ArrayBuffer.empty[(Double, Double)]
    var j = 0
    ref.foreach { ta =>
      while j < other.size && other(j) < ta - period / 2.0 do j += 1
      var best: Option[Double] = None
      for k <- Seq(j - 1, j, j + 1) do
        if k >= 0 && k < other.size then
          var lag = other(k) - ta
          if lag > period / 2.0 then lag -= period
          else if lag <= -period / 2.0 then lag += period
          if best.forall(b => Math.abs(lag) < Math.abs(b)) then best = Some(lag)
      best.foreach(b => out += (ta -> b))
    }
    out.toSeq

  /** Run a set of cells coupled only by [[PulseLink]]s for `tEnd`
    * simulation-time units at step `dt`.
    *
    * The lattice is intentionally edge-free: links are event-level, and the
    * prototype's point was to show what phase coupling alone does. Use a
    * real [[MandateGrid]] with edges when studying the interaction with
    * diffusion/advection.
    */
  def runCoupled(
      cells: Map[CellCoord, MandateState],
      links: Seq[PulseLink],
      dt: Double,
      tEnd: Double,
      criterion: SpikeCriterion = SpikeCriterion.SectionCrossing,
      section: Double = PhaseResponse.section,
      spikeLevel: Double = PhaseResponse.spikeLevel,
      confirmWindow: Double = PulseCoupling.SpikeConfirmWindow,
      recordStress: Boolean = false
  ): CoupledTrace =
    val grid = MandateGrid(cells, Nil)
    val runner = new PulseLinkRunner(links, section, spikeLevel, criterion, confirmWindow)
    val crossings = cells.keys.map(_ -> ArrayBuffer.empty[Double]).toMap
    val stress = cells.keys.map(_ -> ArrayBuffer.empty[(Double, Double)]).toMap
    var t = 0.0
    while t < tEnd do
      runner.duePulses(t).foreach(pulse => MandateAgent.applyEvent(grid, pulse))
      grid.step(dt)
      t += dt
      runner.observe(t, grid.cells).foreach { case (coord, at) => crossings(coord) += at }
      if recordStress then
        cells.foreach { case (coord, cell) => stress(coord) += (t -> cell.getState.stress) }
    CoupledTrace(
      crossings.view.mapValues(_.toSeq).toMap,
      stress.view.mapValues(_.toSeq).toMap
    )
