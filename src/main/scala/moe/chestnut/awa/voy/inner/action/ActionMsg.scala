package moe.chestnut.awa.voy.inner.action

import moe.chestnut.awa.voy.inner.mandate.{CellCoord, MandateCurrent, PhaseReading, Regime}

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

  /** Emitted when a cell's slow-variable regime changes between ticks. */
  final case class RegimeTransitioned(
      cell: CellCoord,
      from: Regime,
      to: Regime,
      phase: PhaseReading
  ) extends MandateActionProtocol

