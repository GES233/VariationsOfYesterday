package moe.chestnut.awa.voy.core.event

sealed trait EventDTO

object EventMsg:
  final case class GenericEventDTO(
      subject: String,
      verb: String,
      target: String,
      context: Any
  ) extends EventDTO

  final case class TickArrived(deltaTime: Double) extends EventDTO
