package services.cybersource

import scala.annotation.implicitNotFound
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext

import com.decidir.coretx.api.BillingData
import com.decidir.coretx.api.CyberSourceResponse
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.TicketingTransactionData
import com.decidir.coretx.api.ValidationError
import com.decidir.coretx.domain.OperationData

import javax.inject.Inject

class VerticalTicketingValidator @Inject() (implicit context: ExecutionContext,
    productsValidator: ProductsValidator) extends CyberSourceValidator {
  
  implicit val ec = context
  
  protected def validate(operationData: OperationData, cyberSourceResponses: ArrayBuffer[ValidationError]) {
    val fdd = operationData.resource.fraud_detection.getOrElse(throw ErrorFactory.missingDataException(List("fraud_detection")))
 		val billTo: BillingData = fdd.bill_to.getOrElse(throw ErrorFactory.missingDataException(List("bill_to")))
    val tt = fdd.ticketing_transaction_data.getOrElse(throw ErrorFactory.missingDataException(List("ticketing_transaction_data")))
    
    billToValidate(billTo, cyberSourceResponses)
    validate(tt, cyberSourceResponses)
    productsValidator.validate(tt.items, cyberSourceResponses, false)
    fdd.csmdds.map(csmdds => validate(csmdds, List(33,34), cyberSourceResponses))
  }
  
  protected def billToValidate(billTo: BillingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (billTo.customer_id.getOrElse("").isEmpty) addErrorCode(10103, cyberSourceResponses)
    billTo.customer_id.map(customerId => sizeValidate(customerId.toString, 50, "bill_to.customer_id", cyberSourceResponses))
  }
  
  private def validate(tt: TicketingTransactionData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    ticketingTransactionRequiredValidate(tt, cyberSourceResponses)
    ticketingTransactionRequiredFormatValidate(tt, cyberSourceResponses)
  }
  
  private def ticketingTransactionRequiredValidate(tt: TicketingTransactionData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (tt.days_to_event.isEmpty) addErrorCode(10533, cyberSourceResponses)
    if (tt.delivery_type.getOrElse("").isEmpty) addErrorCode(10534, cyberSourceResponses)
  }
  
  private def ticketingTransactionRequiredFormatValidate(tt: TicketingTransactionData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    tt.days_to_event.map(daysToEvent => if (daysToEvent <= 0) addErrorCode(10733, cyberSourceResponses))
    tt.delivery_type.map(deliveryType => sizeValidate(deliveryType, 255, "delivery_type", cyberSourceResponses))
  }
  
}