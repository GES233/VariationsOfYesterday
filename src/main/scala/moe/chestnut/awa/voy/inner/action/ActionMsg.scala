package moe.chestnut.awa.voy.inner.action

import moe.chestnut.awa.voy.inner.mandate.MandateCurrent

sealed trait MandateMsg

object ActionMsg {
  final case class MandateCurrentMsg(current: MandateCurrent) extends MandateMsg
}
