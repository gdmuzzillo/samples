package services.cybersource

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import com.decidir.coretx.api._
import com.decidir.coretx.domain.OperationData
import javax.inject.Inject

class VerticalServicesValidator @Inject() (implicit context: ExecutionContext,
                                         productsValidator: ProductsValidator) extends CyberSourceValidator {

  implicit val ec = context

  protected def validate(operationData: OperationData, cyberSourceResponses: ArrayBuffer[ValidationError]) {
    val fdd = operationData.resource.fraud_detection.getOrElse(throw ErrorFactory.missingDataException(List("fraud_detection")))
    val billTo: BillingData = fdd.bill_to.getOrElse(throw ErrorFactory.missingDataException(List("bill_to")))
    val std = fdd.services_transaction_data.getOrElse(throw ErrorFactory.missingDataException(List("services_transaction_data")))

    billToValidate(billTo, cyberSourceResponses)
    validate(std, cyberSourceResponses)
    productsValidator.validate(std.items, cyberSourceResponses, true)
    fdd.csmdds.map(csmdds => validate(csmdds, List(12,14,15,16), cyberSourceResponses))
  }

  protected def billToValidate(billTo: BillingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (billTo.customer_id.getOrElse("").isEmpty) addErrorCode(10103, cyberSourceResponses)
    billTo.customer_id.map(customerId => sizeValidate(customerId.toString, 50, "customer_id", cyberSourceResponses))
  }

  private def validate(std: ServicesTransactionData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    servicesRequiredValidate(std, cyberSourceResponses)
    servicesFormatValidate(std, cyberSourceResponses)
  }

  private def servicesRequiredValidate(std: ServicesTransactionData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (std.service_type.getOrElse("").isEmpty) addErrorCode(10528, cyberSourceResponses)
  }
  
  private def servicesFormatValidate(std: ServicesTransactionData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    std.service_type.map(serviceType => sizeValidate(serviceType, 255, "service_type", cyberSourceResponses))
    std.reference_payment_service1.map(rps1 => sizeValidate(rps1, 255, "reference_payment_service1", cyberSourceResponses))
    std.reference_payment_service2.map(rps2 => sizeValidate(rps2, 255, "reference_payment_service2", cyberSourceResponses))
    std.reference_payment_service3.map(rps3 => sizeValidate(rps3, 255, "reference_payment_service3", cyberSourceResponses))
  }

}