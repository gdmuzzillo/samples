package com.decidir.coretx.domain

import com.decidir.coretx.api.TransactionState

case class RefundSubPaymentOperation(
    id: Long, 
    amount: Long,
    refundId: Option[Long] = None,
    operation: Operation)

trait Operation {
  def id: Int
}

case class CancelOperation() extends Operation {
  val id = 1
}
case class CancelPreApprovedOperation() extends Operation {
  val id = 2
}
case class RefundOperation() extends Operation {
  val id = 3
}
case class PartialRefundOperation() extends Operation {
  val id = 4
}
case class PartialRefundBeforeClosureOperation() extends Operation {
  val id = 5
}
case class CancelPartialRefundBeforeClosureOperation() extends Operation {
  val id = 6
}
case class CancelRefundAfterClosureOperation() extends Operation {
  val id = 7
}
case class CancelPartialRefundAfterClosureOperation()extends Operation {
  val id = 8
}
case class CancelOperationPostPayment() extends Operation {
  val id = 9
}
case class CancelOperationPostPaymentWithCs() extends Operation {
  val id = 10
}
case class ReverseOperationPostPayment() extends Operation {
  val id = 11
}
case class MeanPaymentErrorOperation(id: Int) extends Operation {
}
case class NonExistanceOperation(id: Int) extends Operation {
}
