package com.decidir.coretx.domain

/**
 * @author martinpaoletta
 */

sealed trait OperationFSM

case class Ready() extends OperationFSM {
  override def toString = "ready"
}

case class InProcess(next: OperationFSM) extends OperationFSM {
  override def toString = "in_process=>" + next
}

case class Green() extends OperationFSM {
  override def toString = "green"
}

case class Yellow() extends OperationFSM {
  override def toString = "yellow"
}

case class Red() extends OperationFSM {
  override def toString = "red"
}

case class Cancelled() extends OperationFSM {
  override def toString = "deleted"
}

case class CancelFailed() extends OperationFSM {
  override def toString = "delete_failed"
}

case class Refund() extends OperationFSM {
  override def toString = "refunded"
}

case class RefundFailed() extends OperationFSM {
  override def toString = "refund_failed"
}

case class PaymentConfirm() extends OperationFSM {
  override def toString = "payment_confirm"
}

case class PaymentConfirmFailed() extends OperationFSM {
  override def toString = "payment_confirm_failed"
}