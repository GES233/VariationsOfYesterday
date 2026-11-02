package moe.chestnut.awa.voy.inner.mandate

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import moe.chestnut.awa.voy.inner.action.{ActionMsgPool, MandateActionProtocol}
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
  * @param snapshotSink called after each integration step with an immutable
  *   state map (grid-local coordinates); production wiring republishes it
  *   as a volatile snapshot, tests capture it directly.
  * @param actionSink receives outbound actions (spike notifications, regime
  *   transitions) produced during integration.
  * @param readoutSink receives the semantic `CellReadout` projection per cell
  *   after each integration step.
  */
object MandateAgent:
  /** Apply one channel event to the lattice.
    *
    * The actor and the offline replay harness share this function so the
    * mailbox semantics (address-by-cell, deterministic drop outside the
    * live lattice, channel level vs. pulse) have exactly one
    * implementation. `TickArrived` is not a channel event: integration is
    * driven by the caller's step, so it is ignored here.
    */
  def applyEvent(grid: MandateGrid, event: EventDTO): Unit =
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
      case EventMsg.FirePulse(cell, area) => onCell(cell)(_.firePulse(area))

  def apply(
      grid: MandateGrid,
      snapshotSink: Map[CellCoord, MandateCurrent] => Unit,
      actionSink: MandateActionProtocol => Unit = (_ => ()),
      readoutSink: Map[CellCoord, CellReadout] => Unit = (_ => ())
  ): Behavior[EventDTO] =
    Behaviors.setup { context =>
      context.log.info("Mandate engine actor started.")
      val readoutParam = ReadoutParam()
      var simTime: Double = 0.0
      val trackers: Map[CellCoord, PhaseTracker] = grid.cells.map { case (coord, cell) =>
        coord -> new PhaseTracker(readoutParam, cell.param, cell.stressParam.coupling)
      }
      var prevRegimes: Map[CellCoord, Regime] = grid.cells.map { case (coord, cell) =>
        coord -> Readout.regime(cell.getState, readoutParam)
      }
      var inSpike: Set[CellCoord] = Set.empty

      Behaviors.receiveMessage {
        // tickRate is reserved for the step-coarsening ("weathering")
        // mechanism (§1.4) and intentionally ignored for now.
        case EventMsg.TickArrived(dt, _) =>
          grid.step(dt)
          simTime += dt

          val currentBuilder = Map.newBuilder[CellCoord, MandateCurrent]
          val readoutBuilder = Map.newBuilder[CellCoord, CellReadout]

          grid.cells.foreach { case (coord, cell) =>
            val cur = cell.getState
            currentBuilder += (coord -> cur)

            val phase = trackers(coord).observe(simTime, cur)
            val regime = Readout.regime(cur, readoutParam)
            readoutBuilder += (coord -> CellReadout(cur, regime, phase, cell.baselineCalming))

            val spikingNow = Math.abs(cur.dissonance) >= readoutParam.spikeLevel
            if spikingNow && !inSpike.contains(coord) then
              inSpike += coord
              actionSink(ActionMsgPool.CellSpikeTriggered(coord, cur, regime))
            else if !spikingNow && inSpike.contains(coord) then
              inSpike -= coord

            prevRegimes.get(coord).foreach { oldRegime =>
              if oldRegime != regime then
                prevRegimes = prevRegimes.updated(coord, regime)
                actionSink(ActionMsgPool.RegimeTransitioned(coord, oldRegime, regime, phase))
            }
          }

          snapshotSink(currentBuilder.result())
          readoutSink(readoutBuilder.result())
          Behaviors.same
        case other =>
          // Channel events apply to a single cell; events addressed outside
          // the active lattice are dropped deterministically (§1.4 loading
          // invariance: presence of a cell must not depend on chunk load
          // order).
          applyEvent(grid, other)
          Behaviors.same
      }
    }


