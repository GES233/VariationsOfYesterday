package moe.chestnut.awa.voy.inner.mandate

import moe.chestnut.awa.voy.inner.action.{ActionMsgPool, MandateActionProtocol}
import moe.chestnut.awa.voy.inner.actor.{Actor, ActorRef}
import moe.chestnut.awa.voy.inner.event.{EventDTO, EventMsg}

/** The mandate engine actor: owns the lattice and integrates it on tick
  * events.
  *
  * Scheduling semantics (design draft §1.4, 2026-09-30): every game tick is
  * delivered as one `TickArrived` carrying a fixed simulation dt; world
  * events share the same mailbox, so processing order is total and the
  * event sequence is deterministic and replayable. The actor thread is the
  * single writer of the grid; readers consume the published immutable
  * snapshot instead of asking the actor.
  *
  * The mailbox is the in-repo kernel in `inner/actor`, not a third-party
  * actor framework: the engine only needs one worker thread and a total
  * order, and the kernel keeps the mod jar free of an extra runtime.
  *
  * @param snapshotSink called after each integration step with an immutable
  *   state map (grid-local coordinates); production wiring republishes it
  *   as a volatile snapshot, tests capture it directly.
  * @param actionSink receives outbound actions (spike notifications, regime
  *   transitions) produced during integration.
  * @param readoutSink receives the semantic `CellReadout` projection per cell
  *   after each integration step.
  * @param initialSimTime restored integration clock on world load (0 for a
  *   fresh lattice); pulse delays and phase readouts continue from it.
  * @param defaultFastParam fast set for cells registered without an explicit
  *   one; the wiring layer injects the config here.
  * @param defaultStressParam slow set for the same cells (dormant core
  *   default unless the wiring layer injects the config).
  * @param persistSink receives the full `MandateSnapshot` on a slow cadence
  *   for world persistence; the saved copy may lag the live lattice by at
  *   most `persistPeriod` without losing anything a player can feel.
  * @param persistPeriod snapshot cadence in simulation-time units.
  */
object MandateAgent:
  /** Core parameter sets used for a cell registered without explicit
    * parameters when the wiring layer injects nothing. The game side always
    * injects its configuration instead (`VoyConfig`), so "save > config >
    * default" has a well-defined last resort: these.
    */
  val DefaultFastParam: MandateParam =
    MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)

  /** Dormant sustenance by default: the pure FHN core, as the contract
    * slice and the fingerprint rigs instantiate it. */
  val DefaultStressParam: StressParam = StressParam()

  /** Apply one channel or topology event to the lattice.
    *
    * The actor and the offline replay harness share this function so the
    * mailbox semantics (address-by-cell, deterministic drop outside the
    * live lattice, channel level vs. pulse, topology upserts) have exactly
    * one implementation. `TickArrived` is not a channel event: integration
    * is driven by the caller's step, so it is ignored here. Link events
    * (`AddLink` / `RemoveLink`) are wiring, not lattice: they are handled by
    * the caller's [[PulseLinkRunner]], and ignored here.
    */
  def applyEvent(
      grid: MandateGrid,
      event: EventDTO,
      defaultFastParam: MandateParam = DefaultFastParam,
      defaultStressParam: StressParam = DefaultStressParam
  ): Unit =
    def onCell(cell: CellCoord)(op: MandateState => Unit): Unit =
      grid.cells.get(cell).foreach(op)

    event match
      case EventMsg.TickArrived(_, _) => ()
      case EventMsg.StressInject(cell, amount) => onCell(cell)(_.injectStress(amount))
      case EventMsg.SetDisturbance(cell, rate) =>
        onCell(cell)(_.disturbance = Math.max(0.0, rate))
      case EventMsg.SetCalming(cell, level) => onCell(cell)(_.calming = level)
      case EventMsg.SetBaselineCalming(cell, level) => onCell(cell)(_.baselineCalming = level)
      case EventMsg.HoldImpulse(cell, strength) => onCell(cell)(_.impulse = strength)
      case EventMsg.SetSustenance(cell, level) =>
        onCell(cell)(c => c.sustenance = Math.min(Math.max(level, 0.0), 1.0))
      case EventMsg.FirePulse(cell, area, transmitter) =>
        onCell(cell) { state =>
          state.synapseTau = transmitter.tau
          state.firePulse(area)
        }
      // Topology events rebuild the lattice itself (design draft §1.4):
      // they share the mailbox's total order, so a replayed event stream
      // reproduces the same topology history.
      case EventMsg.RegisterCell(cell, initial, fastParam) =>
        grid.registerCell(
          cell,
          MandateState(
            initial.getOrElse(MandateCurrent(0.0, 0.0, 0.0, 0.0)),
            fastParam.getOrElse(defaultFastParam),
            defaultStressParam
          )
        )
      case EventMsg.RemoveCell(cell) => grid.removeCell(cell)
      case EventMsg.UpsertEdge(edge) =>
        // An edge whose endpoints are not both live is dropped
        // deterministically, same as a channel event outside the lattice.
        if grid.cells.contains(edge.from) && grid.cells.contains(edge.to) then
          grid.upsertEdge(edge)
      case EventMsg.RemoveEdge(from, to) => grid.removeEdge(from, to)
      case EventMsg.AddLink(_) | EventMsg.RemoveLink(_, _) => ()
      // Persistence control: no lattice effect (see `EventMsg.FlushNow`).
      case EventMsg.FlushNow => ()

  def apply(
      grid: MandateGrid,
      snapshotSink: Map[CellCoord, MandateCurrent] => Unit,
      actionSink: MandateActionProtocol => Unit = (_ => ()),
      readoutSink: Map[CellCoord, CellReadout] => Unit = (_ => ()),
      links: Seq[PulseLink] = Nil,
      spikeCriterion: SpikeCriterion = SpikeCriterion.PeakConfirmed,
      initialSimTime: Double = 0.0,
      defaultFastParam: MandateParam = DefaultFastParam,
      defaultStressParam: StressParam = DefaultStressParam,
      persistSink: MandateSnapshot => Unit = (_ => ()),
      persistPeriod: Double = 1.0
  ): ActorRef[EventDTO] =
    val readoutParam = ReadoutParam()
    var simTime: Double = initialSimTime
    // The full lattice snapshot for world persistence is published on a
    // slow cadence (the dynamics' fastest timescale is seconds; the saved
    // copy may lag the live one by at most persistPeriod without losing
    // anything a player can feel).
    var nextPersistAt: Double = initialSimTime
    // Always constructed: links are runtime wiring (AddLink/RemoveLink), so
    // "no links yet" must not mean "no way to add one". The constructor's
    // `links` are merely the initial wiring.
    val linkRunner = new PulseLinkRunner(links, criterion = spikeCriterion)
    /** A phase tracker reading the cell's LIVE sustenance: the burnout knee
      * is crossed when the recovery offset collapses, which the sustenance
      * collapse drives (design note 06 §7.1). */
    def trackerFor(cell: MandateState): PhaseTracker =
      new PhaseTracker(
        readoutParam,
        cell.param,
        cell.stressParam.coupling,
        () => cell.sustenance,
        cell.stressParam.sustenanceDepth
      )

    val trackers = scala.collection.mutable.HashMap.empty[CellCoord, PhaseTracker]
    grid.cells.foreach { case (coord, cell) =>
      trackers(coord) = trackerFor(cell)
    }
    var prevRegimes: Map[CellCoord, Regime] = grid.cells.map { case (coord, cell) =>
      coord -> Readout.regime(cell.getState, readoutParam)
    }
    var inSpike: Set[CellCoord] = Set.empty

    def integrate(dt: Double): Unit =
      // Links deliver before the step and observe after it, the same order
      // the offline harness uses (design note 07).
      linkRunner
        .duePulses(simTime)
        .foreach(event => applyEvent(grid, event, defaultFastParam, defaultStressParam))
      grid.step(dt)
      simTime += dt
      linkRunner.observe(simTime, grid.cells)

      val currentBuilder = Map.newBuilder[CellCoord, MandateCurrent]
      val readoutBuilder = Map.newBuilder[CellCoord, CellReadout]

      grid.cells.foreach { case (coord, cell) =>
        val cur = cell.getState
        currentBuilder += (coord -> cur)

        // Cells registered after startup get their tracker on first sight.
        val tracker = trackers.getOrElseUpdate(coord, trackerFor(cell))
        val phase = tracker.observe(simTime, cur)
        val regime = Readout.regime(cur, readoutParam)
        readoutBuilder += (coord -> CellReadout(
          cur,
          regime,
          phase,
          cell.baselineCalming,
          cell.sustenance
        ))

        val spikingNow = Math.abs(cur.dissonance) >= readoutParam.spikeLevel
        if spikingNow && !inSpike.contains(coord) then
          inSpike += coord
          actionSink(ActionMsgPool.CellSpikeTriggered(coord, cur, regime))
        else if !spikingNow && inSpike.contains(coord) then
          inSpike -= coord

        prevRegimes.get(coord) match
          case Some(oldRegime) if oldRegime != regime =>
            prevRegimes = prevRegimes.updated(coord, regime)
            actionSink(ActionMsgPool.RegimeTransitioned(coord, oldRegime, regime, phase))
          case None =>
            // A cell registered after startup: adopt its current regime as
            // the baseline instead of fabricating a transition.
            prevRegimes = prevRegimes.updated(coord, regime)
          case _ => ()
      }

      snapshotSink(currentBuilder.result())
      readoutSink(readoutBuilder.result())

      if simTime >= nextPersistAt then
        persistSink(MandateSnapshot.capture(grid, linkRunner.links, simTime))
        nextPersistAt = simTime + persistPeriod

    def handle(event: EventDTO): Unit =
      // tickRate is reserved for the step-coarsening ("weathering")
      // mechanism (§1.4) and intentionally ignored for now.
      event match
        case EventMsg.TickArrived(dt, _) => integrate(dt)
        // Link wiring lives in the runner, not the grid.
        case EventMsg.AddLink(link) => linkRunner.addLink(link)
        case EventMsg.RemoveLink(src, dst) => linkRunner.removeLink(src, dst)
        // Save-time flush (persistence only): publish the current lattice
        // without advancing the clock, then resume the normal cadence.
        case EventMsg.FlushNow =>
          persistSink(MandateSnapshot.capture(grid, linkRunner.links, simTime))
          nextPersistAt = simTime + persistPeriod
        case other @ EventMsg.RegisterCell(cell, _, _) =>
          applyEvent(grid, other, defaultFastParam, defaultStressParam)
          // A (re-)registered cell restarts phase tracking — the dynamics
          // were just reset.
          grid.cells.get(cell).foreach { c =>
            trackers(cell) = trackerFor(c)
          }
        case other @ EventMsg.RemoveCell(cell) =>
          applyEvent(grid, other, defaultFastParam, defaultStressParam)
          // Drop the per-cell bookkeeping so a stale detector cannot leak
          // into a cell later re-registered at the same coordinate.
          trackers.remove(cell)
          prevRegimes = prevRegimes - cell
          inSpike = inSpike - cell
          linkRunner.forgetCell(cell)
        // Channel events apply to a single cell; events addressed outside
        // the active lattice are dropped deterministically (§1.4 loading
        // invariance: presence of a cell must not depend on chunk load
        // order).
        case other => applyEvent(grid, other, defaultFastParam, defaultStressParam)

    Actor.spawn("mandate")(handle)
