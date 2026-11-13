package moe.chestnut.awa.voy.inner.veins

import moe.chestnut.awa.voy.inner.action.{ActionMsgPool, MandateActionProtocol}
import moe.chestnut.awa.voy.inner.actor.{Actor, ActorRef}
import moe.chestnut.awa.voy.inner.event.{EventDTO, EventMsg}

/** The mandate engine actor: owns the lattice and integrates it on tick
  * events.
  *
  * Scheduling semantics (design draft §1.4): every game tick is delivered as
  * one `TickArrived` carrying a fixed simulation dt; world events share the
  * same mailbox, so processing order is total and the event sequence is
  * deterministic and replayable. The actor thread is the single writer of the
  * grid; readers consume the published immutable snapshot.
  */
object MandateAgent:
  val DefaultFastParam: MandateParam =
    MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)

  /** Dormant sustenance by default: the pure FHN core, as the contract
    * slice and the fingerprint rigs instantiate it.
    */
  val DefaultStressParam: StressParam = StressParam()

  /** Per-cell runtime bookkeeping consolidated into one slot, eliminating
    * parallel maps that had to be manually synchronized on cell add/remove.
    */
  private final class CellSlot(
      val tracker: PhaseTracker,
      var pulseStamp: PulseStamp,
      var publishedRegime: Regime,
      var regimeCandidate: Option[(Regime, Double)] = None,
      var inSpike: Boolean = false
  )

  /** Apply one channel or topology event to the lattice. */
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
      case EventMsg.FirePulse(cell, area, transmitter, _) =>
        onCell(cell) { state =>
          state.synapseTau = transmitter.tau
          state.firePulse(area)
        }
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
        if grid.cells.contains(edge.from) && grid.cells.contains(edge.to) then
          grid.upsertEdge(edge)
      case EventMsg.RemoveEdge(from, to) => grid.removeEdge(from, to)
      case EventMsg.AddLink(_) | EventMsg.RemoveLink(_, _) => ()
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
      persistPeriod: Double = 1.0,
      pulseParam: PulseParam = PulseParam(),
      initialPulseStamps: Map[CellCoord, PulseStamp] = Map.empty,
      regimeDwell: Double = 5.0
  ): ActorRef[EventDTO] =
    val readoutParam = ReadoutParam()
    var simTime: Double = initialSimTime
    var nextPersistAt: Double = initialSimTime
    val linkRunner = new PulseLinkRunner(links, criterion = spikeCriterion)

    def mkSlot(cell: MandateState, stamp: PulseStamp = PulseStamp.zero): CellSlot =
      new CellSlot(
        tracker = new PhaseTracker(
          readoutParam,
          cell.param,
          cell.stressParam.coupling,
          cell.stressParam.sustenanceDepth
        ),
        pulseStamp = stamp,
        publishedRegime = Readout.regime(cell.getState, readoutParam)
      )

    val slots = scala.collection.mutable.HashMap.empty[CellCoord, CellSlot]
    grid.cells.foreach { case (coord, cell) =>
      slots(coord) = mkSlot(cell, initialPulseStamps.getOrElse(coord, PulseStamp.zero))
    }

    def currentStamps: Map[CellCoord, PulseStamp] =
      slots.iterator.map { case (c, slot) => c -> slot.pulseStamp }.toMap

    def recordPulse(coord: CellCoord, area: Double): Unit =
      if area > 0.0 then
        grid.cells.get(coord).foreach { state =>
          val cur = state.getState
          val ecoDead = Readout.isEcologicalDeath(
            state.sustenance,
            cur.stress,
            state.getParam,
            state.stressParam.coupling,
            state.stressParam.sustenanceDepth,
            state.stressParam.sustenanceFloor
          )
          if !ecoDead then
            val slot = slots.getOrElseUpdate(coord, mkSlot(state))
            val (next, _) = slot.pulseStamp.onCountedPulse(simTime, pulseParam)
            slot.pulseStamp = next
        }

    def integrate(dt: Double): Unit =
      linkRunner
        .duePulses(simTime)
        .foreach { event =>
          applyEvent(grid, event, defaultFastParam, defaultStressParam)
          if event.bookkeeping.counted then recordPulse(event.cell, event.area)
        }
      grid.step(dt)
      simTime += dt
      linkRunner.observe(simTime, grid.cells)

      val currentBuilder = Map.newBuilder[CellCoord, MandateCurrent]
      val readoutBuilder = Map.newBuilder[CellCoord, CellReadout]

      grid.cells.foreach { case (coord, cell) =>
        val cur = cell.getState
        currentBuilder += (coord -> cur)

        val slot = slots.getOrElseUpdate(coord, mkSlot(cell))
        val phase = slot.tracker.observe(simTime, cur, cell.sustenance)

        // Debounce raw regime classification by `regimeDwell`
        val rawRegime = Readout.regime(cur, readoutParam)
        if slot.publishedRegime == rawRegime then
          slot.regimeCandidate = None
        else
          val since = slot.regimeCandidate match
            case Some((cand, t)) if cand == rawRegime => t
            case _                                    => simTime
          if simTime - since >= regimeDwell then
            val oldRegime = slot.publishedRegime
            slot.publishedRegime = rawRegime
            slot.regimeCandidate = None
            actionSink(ActionMsgPool.RegimeTransitioned(coord, oldRegime, rawRegime, phase))
          else
            slot.regimeCandidate = Some((rawRegime, since))

        val regime = slot.publishedRegime
        val pDrive = cell.impulse + cell.synapseOutput

        readoutBuilder += (coord -> CellReadout(
          current = cur,
          regime = regime,
          phase = phase,
          sustenance = cell.sustenance,
          baselineCalming = cell.baselineCalming,
          pulseDrive = pDrive,
          pulseStamp = slot.pulseStamp,
          simTime = simTime,
          sustenanceFloor = cell.stressParam.sustenanceFloor,
          toxicityThreshold = cell.stressParam.sustenanceBurnThreshold
        ))

        val spikingNow = cur.dissonance >= readoutParam.spikeLevel
        if spikingNow && !slot.inSpike then
          slot.inSpike = true
          actionSink(ActionMsgPool.CellSpikeTriggered(coord, cur, regime))
        else if !spikingNow && slot.inSpike then
          slot.inSpike = false
      }

      snapshotSink(currentBuilder.result())
      readoutSink(readoutBuilder.result())

      if simTime >= nextPersistAt then
        persistSink(MandateSnapshot.capture(grid, linkRunner.links, simTime, currentStamps))
        nextPersistAt = simTime + persistPeriod

    def handle(event: EventDTO): Unit =
      event match
        case EventMsg.TickArrived(dt, _) => integrate(dt)
        case EventMsg.AddLink(link) => linkRunner.addLink(link)
        case EventMsg.RemoveLink(src, dst) => linkRunner.removeLink(src, dst)
        case EventMsg.FlushNow =>
          persistSink(MandateSnapshot.capture(grid, linkRunner.links, simTime, currentStamps))
          nextPersistAt = simTime + persistPeriod
        case other @ EventMsg.RegisterCell(cell, _, _) =>
          applyEvent(grid, other, defaultFastParam, defaultStressParam)
          grid.cells.get(cell).foreach { c =>
            slots(cell) = mkSlot(c)
          }
        case other @ EventMsg.RemoveCell(cell) =>
          applyEvent(grid, other, defaultFastParam, defaultStressParam)
          slots.remove(cell)
          linkRunner.forgetCell(cell)
        case other @ EventMsg.FirePulse(cell, area, _, bookkeeping) =>
          applyEvent(grid, other, defaultFastParam, defaultStressParam)
          if bookkeeping.counted then recordPulse(cell, area)
        case other => applyEvent(grid, other, defaultFastParam, defaultStressParam)

    Actor.spawn("mandate")(handle)
