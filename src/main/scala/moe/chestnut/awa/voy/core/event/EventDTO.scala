package moe.chestnut.awa.voy.core.event

/**
 * Receive event from Minecraft.
**/
case class EventDTO(subject: String, verb: String, target: String, context: Any);
