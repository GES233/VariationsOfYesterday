package moe.chestnut.awa.voy.core.mandate

case class MandateParam(alpha: Double, beta: Double, epsilon: Double)

case class MandateCurrent(dissonance: Double, entrench: Double)

/** Bla bla.
  */
class MandateState(
    private var param: MandateParam,
    private var state: MandateCurrent
):
  // TODO: find default params and state.

  def update(timestep: Double): Unit = {}
  // Calculate ...

  def getState: MandateCurrent = state
  def getParam: MandateParam = param
