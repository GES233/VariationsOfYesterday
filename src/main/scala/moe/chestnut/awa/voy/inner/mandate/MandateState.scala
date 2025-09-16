package moe.chestnut.awa.voy.inner.mandate

import moe.chestnut.awa.voy.helpers.{NumericTuple, ODESolver}

case class MandateParam(alpha: Double, beta: Double, epsilon: Double)
    extends NumericTuple:
  def toArray: Array[Double] =
    Array(alpha, beta, epsilon)

  def fromArray(nums: Array[Double]): MandateParam =
    MandateParam(nums(0), nums(1), nums(2))

case class MandateCurrent(dissonance: Double, entrench: Double)
    extends NumericTuple:
  def toArray: Array[Double] =
    Array(dissonance, entrench)

  def fromArray(nums: Array[Double]): MandateCurrent =
    MandateCurrent(nums(0), nums(1))

/** Bla bla.
  */
class MandateState(
    private var param: MandateParam,
    private var state: MandateCurrent
):
  // TODO: find suitable default params and initial state.

  private def do_update(current: Array[Double]): Array[Double] =
    val delta_dis = 0.0
    val delta_en = 0.0
    Array(delta_dis, delta_en)

  private def equ(dis_and_en: Array[Double], _player_input: Option[Map[String, Any]]): Array[Double] =
    val dis = dis_and_en(0)
    val en = dis_and_en(1)

    val delta_dis = dis - Math.pow(dis, 3) / 3 - en
    val delta_en = param.epsilon * (dis + param.alpha - param.beta * en)

    Array(delta_dis, delta_en)
  
  private val solver = ODESolver(equation = this.equ)

  def update(timestep: Double): Unit = {
    // Calculate...
  }

  def getState: MandateCurrent = state
  def getParam: MandateParam = param
