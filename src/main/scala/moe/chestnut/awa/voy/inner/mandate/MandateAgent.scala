package moe.chestnut.awa.voy.inner.mandate

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import moe.chestnut.awa.voy.inner.action.MandateActionProtocol
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
  * @param actionSink receives outbound actions (presentation commands,
  *   notifications); no action producers exist yet, the channel is part of
  *   the interface contract.
  */
object MandateAgent:
  def apply(
      grid: MandateGrid,
      snapshotSink: Map[CellCoord, MandateCurrent] => Unit,
      actionSink: MandateActionProtocol => Unit = (_ => ())
  ): Behavior[EventDTO] =
    // Channel events apply to a single cell; events addressed outside the
    // active lattice are dropped deterministically (§1.4 loading
    // invariance: presence of a cell must not depend on chunk load order).
    def onCell(cell: CellCoord)(op: MandateState => Unit): Unit =
      grid.cells.get(cell).foreach(op)

    Behaviors.setup { context =>
      context.log.info("Mandate engine actor started.")
      Behaviors.receiveMessage {
        // tickRate is reserved for the step-coarsening ("weathering")
        // mechanism (§1.4) and intentionally ignored for now.
        case EventMsg.TickArrived(dt, _) =>
          grid.step(dt)
          snapshotSink(
            grid.cells.iterator.map { case (coord, cell) =>
              coord -> cell.getState
            }.toMap
          )
          Behaviors.same
        case EventMsg.StressInject(cell, amount) =>
          onCell(cell)(_.injectStress(amount))
          Behaviors.same
        case EventMsg.SetDisturbance(cell, rate) =>
          onCell(cell)(_.disturbance = Math.max(0.0, rate))
          Behaviors.same
        case EventMsg.SetCalming(cell, level) =>
          onCell(cell)(_.calming = level)
          Behaviors.same
        case EventMsg.SetBaselineCalming(cell, level) =>
          onCell(cell)(_.baselineCalming = level)
          Behaviors.same
        case EventMsg.HoldImpulse(cell, strength) =>
          onCell(cell)(_.impulse = strength)
          Behaviors.same
        case EventMsg.FirePulse(cell, area) =>
          onCell(cell)(_.firePulse(area))
          Behaviors.same
      }
    }

  // simulation loop not handle extra context or events.
  private[mandate] def simulationLoop(oldState: MandateState, deltaTime: Double, dt: Double): (MandateState, Option[MandateActionProtocol]) =
    var newAccumulator = oldState.timeDeltaAccumulator + deltaTime

    // Fixed-step integration: each substep must advance from the result of
    // the previous one. `step` also clamps stress to stay non-negative.
    while (newAccumulator >= dt) {
      oldState.step(dt)
      newAccumulator -= dt
    }

    oldState.timeDeltaAccumulator = newAccumulator

    Tuple2(oldState, None)
