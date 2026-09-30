package moe.chestnut.awa.voy.inner.event


/**
 * Event means something accidental that model received.
 * It's caused by the world, some entities, or player.
 * 
 * `EventDTO` is a container that package events from game.
 * 
 * All existed events in the package(file) where this class
 * locate.
 */
sealed trait EventDTO

object EventMsg:
  final case class GenericEventDTO(
      subject: String,
      verb: String,
      target: String,
      context: Any
  ) extends EventDTO
  
  // ==== Timer ==== //

  /** One game tick delivered to the engine. Carries the FIXED simulation
    * step plus the server's current nominal tick rate (design draft §1.4,
    * 2026-09-30: tick-driven, simulation time is identical to game time; no
    * wall-clock measurement). `tickRate` stamps the scheduling context into
    * the event stream so the actor stays a pure function of it — it is the
    * trigger input for step coarsening ("weathering"); the actor ignores it
    * until that mechanism lands. */
  final case class TickArrived(deltaTime: Double, tickRate: Float) extends EventDTO
  
  // ==== Command ==== //

  /** Contain command from user. */
  // final case class UserCommand(subject: String, verb: String, arguments: List[String | Map[String, Any]]) extends EventDTO
