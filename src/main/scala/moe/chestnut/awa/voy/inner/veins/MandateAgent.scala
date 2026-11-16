package moe.chestnut.awa.voy.inner.veins

import moe.chestnut.awa.voy.inner.action.{ActionMsgPool, MandateActionProtocol}
import moe.chestnut.awa.voy.inner.actor.{Actor, ActorRef}
import moe.chestnut.awa.voy.inner.event.{EventDTO, EventMsg}

/** Bootstrapping and tuning configuration for [[MandateAgent]]. */
final case class AgentConfig(
    links: Seq[PulseLink] = Nil,
    spikeCriterion: SpikeCriterion = SpikeCriterion.PeakConfirmed,
    initialSimTime: Double = 0.0,
    defaultFastParam: FastParam = MandateAgent.DefaultFastParam,
    defaultStressParam: StressParam = MandateAgent.DefaultStressParam,
    persistPeriod: Double = 1.0,
    initialRhythmEvidence: Map[CellCoord, RhythmEvidence] = Map.empty,
    regimeDwell: Double = 5.0,
    rhythmParam: RhythmParam = RhythmParam(),
    calmingParam: CalmingParam = CalmingParam()
)

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
  val DefaultFastParam: FastParam =
    FastParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)

  /** Dormant sustenance by default: the pure FHN core, as the contract
    * slice and isolated core specs instantiate it. Overworld wiring injects
    * [[StressParam.OverworldDefault]].
    */
  val DefaultStressParam: StressParam = StressParam.BareCore

  /** Per-cell runtime bookkeeping consolidated into one slot, eliminating
    * parallel maps that had to be manually synchronized on cell add/remove.
    */
  private final class CellSlot(
      val tracker: PhaseTracker,
      var publishedRegime: Regime,
      var regimeCandidate: Option[(Regime, Double)] = None,
      var inSpike: Boolean = false,
      var rhythm: RhythmVerdict = RhythmVerdict.Warming,
      var heartbeatBadSince: Double = Double.NaN,
      /** Last bell strike time for the overdrive guard (design note 11 §1.5).
        * Deliberately NOT persisted: an 8 s guard is meaningless across a
        * save/load boundary, so a reload simply reads as guard-expired.
        */
      var lastBellStrikeAt: Double = Double.NaN
  )

  /** Apply one channel or topology event to the lattice. */
  def applyEvent(
      grid: MandateGrid,
      event: EventDTO,
      defaultFastParam: FastParam = DefaultFastParam,
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
      case EventMsg.StrikeBell(_, _, _, _, _, _) => ()
      case EventMsg.FlushNow => ()

  def apply(
      grid: MandateGrid,
      readoutSink: Map[CellCoord, CellReadout] => Unit = _ => (),
      actionSink: MandateActionProtocol => Unit = _ => (),
      persistSink: MandateSnapshot => Unit = _ => (),
      config: AgentConfig = AgentConfig()
  ): ActorRef[EventDTO] =
    val readoutParam = ReadoutParam()
    var simTime: Double = config.initialSimTime
    var nextPersistAt: Double = config.initialSimTime
    val linkRunner = new PulseLinkRunner(config.links, criterion = config.spikeCriterion)

    def mkSlot(cell: MandateState, stats: RhythmEvidence = RhythmEvidence.zero): CellSlot =
      val tracker = new PhaseTracker(
        readoutParam,
        cell.param,
        cell.stressParam.coupling,
        cell.stressParam.sustenanceDepth
      )
      // Restoring rhythm evidence keeps a reloaded cell's rhythm verdict
      // continuous instead of restarting the Warming grace (note 11 risk 2).
      if stats.periods.nonEmpty || !stats.lastCross.isNaN || !stats.bornAt.isNaN then
        tracker.restoreRhythmEvidence(stats)
      new CellSlot(
        tracker = tracker,
        publishedRegime = Readout.regime(cell.getState, readoutParam)
      )

    val slots = scala.collection.mutable.HashMap.empty[CellCoord, CellSlot]
    grid.cells.foreach { case (coord, cell) =>
      slots(coord) = mkSlot(cell, config.initialRhythmEvidence.getOrElse(coord, RhythmEvidence.zero))
    }

    def currentRhythmEvidence: Map[CellCoord, RhythmEvidence] =
      slots.iterator.map { case (c, slot) => c -> slot.tracker.rhythmEvidence }.toMap

    def integrate(dt: Double): Unit =
      linkRunner
        .duePulses(simTime)
        .foreach { event =>
          applyEvent(grid, event, config.defaultFastParam, config.defaultStressParam)
        }
      grid.step(dt)
      simTime += dt
      linkRunner.observe(simTime, grid.cells)

      val readoutBuilder = Map.newBuilder[CellCoord, CellReadout]

      grid.cells.foreach { case (coord, cell) =>
        val cur = cell.getState

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
          if simTime - since >= config.regimeDwell then
            val oldRegime = slot.publishedRegime
            slot.publishedRegime = rawRegime
            slot.regimeCandidate = None
            actionSink(ActionMsgPool.RegimeTransitioned(coord, oldRegime, rawRegime))
          else
            slot.regimeCandidate = Some((rawRegime, since))

        val regime = slot.publishedRegime
        val pDrive = cell.impulse + cell.synapseOutput

        // RhythmVerdict axis (design note 11): classify the tracker's rhythm
        // statistics, then publish through the exit rule: arrhythmia is held
        // for two of its own periods (single-cycle flicker guard), while
        // over-pacing and silence publish immediately — their evidence is
        // already averaged / waited. `selfSustained` only records
        // `RhythmSource`, never gates.
        val aSlow = Readout.slowA(
          cell.param,
          cur.stress,
          cell.sustenance,
          cell.stressParam.coupling,
          cell.stressParam.sustenanceDepth
        )
        val rawRhythm = RhythmVerdict.classify(
          slot.tracker.rhythmEvidence,
          simTime,
          selfSustained = Readout.oscillating(aSlow, cell.param),
          config.rhythmParam
        )
        val (rhythm, badSince) = RhythmVerdict.smooth(
          rawRhythm,
          slot.rhythm,
          slot.heartbeatBadSince,
          simTime,
          config.rhythmParam
        )
        slot.rhythm = rhythm
        slot.heartbeatBadSince = badSince

        readoutBuilder += (coord -> CellReadout(
          current = cur,
          regime = regime,
          phase = phase,
          sustenance = cell.sustenance,
          baselineCalming = cell.baselineCalming,
          pulseDrive = pDrive,
          rhythm = rhythm,
          sustenanceFloor = cell.stressParam.sustenanceFloor,
          toxicityThreshold = cell.stressParam.sustenanceBurnThreshold
        ))

        val spikingNow = cur.excitation >= readoutParam.spikeLevel
        if spikingNow && !slot.inSpike then
          slot.inSpike = true
          actionSink(ActionMsgPool.CellSpikeTriggered(coord, cur, regime))
        else if !spikingNow && slot.inSpike then
          slot.inSpike = false
      }

      readoutSink(readoutBuilder.result())

      if simTime >= nextPersistAt then
        persistSink(MandateSnapshot.capture(grid, linkRunner.links, simTime, currentRhythmEvidence))
        nextPersistAt = simTime + config.persistPeriod

    def handle(event: EventDTO): Unit =
      event match
        case EventMsg.TickArrived(dt, _) => integrate(dt)
        case EventMsg.AddLink(link) => linkRunner.addLink(link)
        case EventMsg.RemoveLink(src, dst) => linkRunner.removeLink(src, dst)
        case EventMsg.FlushNow =>
          persistSink(MandateSnapshot.capture(grid, linkRunner.links, simTime, currentRhythmEvidence))
          nextPersistAt = simTime + config.persistPeriod
        case other @ EventMsg.RegisterCell(cell, _, _) =>
          applyEvent(grid, other, config.defaultFastParam, config.defaultStressParam)
          grid.cells.get(cell).foreach { c =>
            slots(cell) = mkSlot(c)
          }
        case other @ EventMsg.RemoveCell(cell) =>
          applyEvent(grid, other, config.defaultFastParam, config.defaultStressParam)
          slots.remove(cell)
          linkRunner.forgetCell(cell)
        case EventMsg.StrikeBell(cell, bx, by, bz, strikerId, area) =>
          grid.cells.get(cell).foreach { state =>
            val cur = state.getState
            val ecoDead = Readout.isEcologicallyDead(
              state.sustenance,
              cur.stress,
              state.getParam,
              state.stressParam.coupling,
              state.stressParam.sustenanceDepth,
              state.stressParam.sustenanceFloor
            )
            val slot = slots.getOrElseUpdate(cell, mkSlot(state))
            val outcome =
              BellRitual.evaluate(ecoDead, slot.lastBellStrikeAt, simTime, config.rhythmParam)
            state.synapseTau = PulseTransmitter.Bell.tau
            state.firePulse(area)
            if !ecoDead && area > 0.0 then slot.lastBellStrikeAt = simTime
            if outcome.anchorsC0 then
              state.baselineCalming = CalmingStock.dose(state.baselineCalming, config.calmingParam)
            actionSink(ActionMsgPool.BellStruck(cell, outcome, bx, by, bz, strikerId))
          }
        case other => applyEvent(grid, other, config.defaultFastParam, config.defaultStressParam)

    Actor.spawn("mandate")(handle)
