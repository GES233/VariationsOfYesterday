package moe.chestnut.awa.voy.inner.event

import moe.chestnut.awa.voy.inner.mandate.CellCoord

/** Event means something the model receives from outside: the world, some
  * entities, or the player.
  *
  * `EventDTO` is the inbound protocol of the mandate engine. Design rules
  * (2026-09-30):
  *
  *  - DTOs speak CHANNEL language (stress injection, channel levels,
  *    pulses — the input channels of `MandateState`), not world semantics:
  *    there is no "explosion" or "bell" here. One game occurrence maps to
  *    one or MORE channel events; that 1:N mapping lives on the Minecraft
  *    side, so the simulation core stays free of game vocabulary.
  *  - DTOs are pure data (no Minecraft types) so the event stream can be
  *    logged and replayed for determinism checks (§1.4 loading invariance).
  *  - Events carry no timestamp: causality is their position relative to
  *    `TickArrived` events in the mailbox's total order.
  *  - Events carry no dimension id: engines are per-dimension and routing
  *    happens on the game side.
  *
  * All events live in this file.
  */
sealed trait EventDTO

object EventMsg:
  // ==== Timer ==== //

  /** One game tick delivered to the engine. Carries the FIXED simulation
    * step plus the server's current nominal tick rate (design draft §1.4,
    * 2026-09-30: tick-driven, simulation time is identical to game time; no
    * wall-clock measurement). `tickRate` stamps the scheduling context into
    * the event stream so the actor stays a pure function of it — it is the
    * trigger input for step coarsening ("weathering"); the actor ignores it
    * until that mechanism lands. */
  final case class TickArrived(deltaTime: Double, tickRate: Float) extends EventDTO

  // ==== Input channels (design draft §1.1) ==== //
  // Game occurrences are translated into these on the Minecraft side, 1:N.

  /** One-shot stress injection: raise the cell's stress immediately,
    * clamped to [0, stressMax]. */
  final case class StressInject(cell: CellCoord, amount: Double) extends EventDTO

  /** Set the sustained disturbance level (u channel). */
  final case class SetDisturbance(cell: CellCoord, rate: Double) extends EventDTO

  /** Set the active calming level (c channel). */
  final case class SetCalming(cell: CellCoord, level: Double) extends EventDTO

  /** Set the baseline calming level (c0 channel). */
  final case class SetBaselineCalming(cell: CellCoord, level: Double) extends EventDTO

  /** Hold the fast-variable impulse at a constant level (p channel,
    * sustained form). */
  final case class HoldImpulse(cell: CellCoord, strength: Double) extends EventDTO

  /** Fire one alpha-synapse pulse on the fast variable (p channel, discrete
    * form). Carries the kernel AREA; tau stays a per-cell parameter for
    * now. */
  final case class FirePulse(cell: CellCoord, area: Double) extends EventDTO
