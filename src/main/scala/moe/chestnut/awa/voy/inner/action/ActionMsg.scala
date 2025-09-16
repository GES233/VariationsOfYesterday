package moe.chestnut.awa.voy.inner.action

import moe.chestnut.awa.voy.inner.mandate.MandateCurrent

sealed trait MandateActionProtocol

object ActionMsgPool:
  final case class MandateCurrentStatus(current: MandateCurrent) extends MandateActionProtocol
  
//  final case class 

