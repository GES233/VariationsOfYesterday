package moe.chestnut.awa.voy.inner.action

import moe.chestnut.awa.voy.inner.mandate.{CellCoord, MandateCurrent, PhaseReading, Regime}

sealed trait MandateActionProtocol

object ActionMsgPool:
  final case class MandateCurrentStatus(current: MandateCurrent) extends MandateActionProtocol

  /** Emitted once when a cell's fast variable crosses into a full spike
    * excursion (`|v| >= spikeLevel`).
    */
  final case class CellSpikeTriggered(
      cell: CellCoord,
      current: MandateCurrent,
      regime: Regime
  ) extends MandateActionProtocol

  /** Emitted when a cell's slow-variable regime changes between ticks. */
  final case class RegimeTransitioned(
      cell: CellCoord,
      from: Regime,
      to: Regime,
      phase: PhaseReading
  ) extends MandateActionProtocol

