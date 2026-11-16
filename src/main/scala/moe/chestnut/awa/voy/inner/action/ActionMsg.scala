package moe.chestnut.awa.voy.inner.action

import java.util.UUID

import moe.chestnut.awa.voy.inner.veins.{BellOutcome, CellCoord, MandateCurrent, Regime}

sealed trait MandateActionProtocol

object ActionMsgPool:
  /** Emitted once when a cell's fast variable crosses into a positive spike
    * excursion (`v >= spikeLevel`). The negative repolarisation overshoot is
    * not a spike (design note 08).
    */
  final case class CellSpikeTriggered(
      cell: CellCoord,
      current: MandateCurrent,
      regime: Regime
  ) extends MandateActionProtocol

  /** Emitted when a cell's slow-variable regime changes between ticks.
    *
    * `phase` used to ride along and was never read (the consumer discarded it
    * with `_`), so the payload now carries only what a consumer can act on.
    * Dropping `from` is a note-12 §5.2 item for the atomisation step.
    */
  final case class RegimeTransitioned(
      cell: CellCoord,
      from: Regime,
      to: Regime
  ) extends MandateActionProtocol

  /** Receipt emitted by the engine when processing `EventMsg.StrikeBell`. */
  final case class BellStruck(
      cell: CellCoord,
      outcome: BellOutcome,
      blockX: Int,
      blockY: Int,
      blockZ: Int,
      strikerId: Option[UUID]
  ) extends MandateActionProtocol

