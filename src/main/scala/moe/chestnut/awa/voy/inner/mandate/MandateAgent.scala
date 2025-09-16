package moe.chestnut.awa.voy.inner.mandate

import moe.chestnut.awa.voy.inner.action.MandateActionProtocol
import moe.chestnut.awa.voy.inner.event.EventDTO
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.{Logger, LoggerFactory}


object MandateAgent:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

//  def apply(): Behavior[EventDTO] =
//    Behaviors.setup{
//      context =>
//        context.log.info("Actor System activated.")
//    }
    
  // simulation loop not handle extra context or events.
  private def simulationLoop(oldState: MandateState, deltaTime: Double, dt : Double): (MandateState, Option[MandateActionProtocol]) =
    var newAccumulator = oldState.timeDeltaAccumulator + dt
    var currentMandateState = oldState.state
    
    while (newAccumulator >= dt) {
      val nextMandateState: MandateCurrent = oldState.solver.update(oldState.state, dt)
      
      newAccumulator -= dt
      currentMandateState = nextMandateState
    }
    
    oldState.state = currentMandateState
    oldState.timeDeltaAccumulator = newAccumulator
    
    Tuple2(oldState, None)
