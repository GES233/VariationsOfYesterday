package moe.chestnut.awa.voy.core.mandate

class MandateState:
  // present model status & param.
  private var mandate_param: Tuple3[Double, Double, Double] = (0, 0, 0)
  private var mandate_state: Tuple2[Double, Double] = (0, 0)
