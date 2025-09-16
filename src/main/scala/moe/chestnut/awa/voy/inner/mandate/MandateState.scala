package moe.chestnut.awa.voy.inner.mandate

import moe.chestnut.awa.voy.inner.event.EventMsg
import moe.chestnut.awa.voy.helpers.{NumericTuple, ODESolver}
import org.slf4j.LoggerFactory

case class MandateParam(alpha: Double, beta: Double, epsilon: Double)
    extends NumericTuple[MandateParam]:
  def toArray: Array[Double] =
    Array(alpha, beta, epsilon)

  def fromArray(nums: Array[Double]): MandateParam =
    MandateParam(nums(0), nums(1), nums(2))

case class MandateCurrent(dissonance: Double, entrench: Double)
    extends NumericTuple[MandateCurrent]:
  def toArray: Array[Double] =
    Array(dissonance, entrench)

  def fromArray(nums: Array[Double]): MandateCurrent =
    MandateCurrent(nums(0), nums(1))

/** Bla bla.
  */
class MandateState(
    var state: MandateCurrent,
    var param: MandateParam,
    var timestep: Double = 0.01,
    var timeDeltaAccumulator: Double = 0.0,
):
  // TODO: add some context for user input.
  // It's can be used when model update.

  private def equ(dis_and_en: Array[Double], _player_input: Option[Map[String, Any]]): Array[Double] =
    val dis = dis_and_en(0)
    val en = dis_and_en(1)

    val delta_dis = dis - Math.pow(dis, 3) / 3 - en
    val delta_en = param.epsilon * (dis + param.alpha - param.beta * en)

    Array(delta_dis, delta_en)

  val solver = ODESolver(equation = this.equ)

  def getState: MandateCurrent = state
  def getParam: MandateParam = param

