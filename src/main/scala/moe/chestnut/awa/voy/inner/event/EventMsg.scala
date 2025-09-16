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

  /** Arrive when tick update(`deltaTime` means time elapse in real world). */
  final case class TickArrived(deltaTime: Double) extends EventDTO

  /** When game's tick speed updated by player or other mod. */
  // final case class TickSpeedUpdate(newTickSpeed: Double) extends EventDTO
  
  // Record game's time are updated.
  // Is it essential?
  // final case class TimeUpdate() extends EventDTO
  
  // ==== Command ==== //

  /** Contain command from user. */
  // final case class UserCommand(subject: String, verb: String, arguments: List[String | Map[String, Any]]) extends EventDTO
