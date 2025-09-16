package moe.chestnut.awa.voy.inner.mandate

import moe.chestnut.awa.voy.helpers.NumericTuple

case class MandateParam(alpha: Double, beta: Double, epsilon: Double)
    extends NumericTuple:
  def toArray(): Array[Double] =
    Array(alpha, beta, epsilon)

  def fromArray(nums: Array[Double]): MandateParam =
    MandateParam(nums[0], nums[1], nums[2])

case class MandateCurrent(dissonance: Double, entrench: Double)
    extends NumericTuple:
  def toArray(): Array[Double] =
    Array(dissonance, entrench)

  def fromArray(nums: Array[Double]): MandateCurrent =
    MandateCurrent(nums[0], nums[1])

/** Bla bla.
  */
class MandateState(
    private var param: MandateParam,
    private var state: MandateCurrent
):
  // TODO: find suitable default params and initial state.

  def update(timestep: Double): Unit = {}
  // Calculate ...

  def getState: MandateCurrent = state
  def getParam: MandateParam = param
