package moe.chestnut.awa.voy.inner.mandate

import moe.chestnut.awa.voy.inner.action.MandateActionProtocol
import org.slf4j.{Logger, LoggerFactory}


object MandateAgent:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

//  def apply(): Behavior[EventDTO] =
//    Behaviors.setup{
//      context =>
//        context.log.info("Actor System activated.")
//    }
    
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
