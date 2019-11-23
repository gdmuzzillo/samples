package services.cybersource

import scala.annotation.implicitNotFound
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import com.decidir.coretx.api.BillingData
import com.decidir.coretx.api.CyberSourceResponse
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.ValidationError
import com.decidir.coretx.domain.OperationData
import javax.inject.Inject
import com.decidir.coretx.api.DigitalGoodsTransactionData

class VerticalDigitalGoodsValidator @Inject() (implicit context: ExecutionContext,
    productsValidator: ProductsValidator) extends CyberSourceValidator {
  
  implicit val ec = context
  
  protected def validate(operationData: OperationData, cyberSourceResponses: ArrayBuffer[ValidationError]) {
    val fdd = operationData.resource.fraud_detection.getOrElse(throw ErrorFactory.missingDataException(List("fraud_detection")))
 		val billTo: BillingData = fdd.bill_to.getOrElse(throw ErrorFactory.missingDataException(List("bill_to")))
    val dgt = fdd.digital_goods_transaction_data.getOrElse(throw ErrorFactory.missingDataException(List("digital_goods_transaction_data")))
    
    billToValidate(billTo, cyberSourceResponses)
    validate(dgt, cyberSourceResponses)
    productsValidator.validate(dgt.items, cyberSourceResponses, false)
    fdd.csmdds.map(csmdds => validate(csmdds, List(32), cyberSourceResponses))
  }
  
  protected def billToValidate(billTo: BillingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    billTo.customer_id.map(customerId => sizeValidate(customerId.toString, 50, "billTo.customer_id", cyberSourceResponses))
    .getOrElse(addErrorCode(10103, cyberSourceResponses))
  }
  
  private def validate(dgt: DigitalGoodsTransactionData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    digitalGoodsTransactionRequiredValidate(dgt, cyberSourceResponses)
    digitalGoodsTransactionRequiredFormatValidate(dgt, cyberSourceResponses)
  }
  
  private def digitalGoodsTransactionRequiredValidate(dgt: DigitalGoodsTransactionData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (dgt.delivery_type.getOrElse("").isEmpty) addErrorCode(10532, cyberSourceResponses)
  }
  
  private def digitalGoodsTransactionRequiredFormatValidate(dgt: DigitalGoodsTransactionData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    dgt.delivery_type.map(deliveryType => sizeValidate(deliveryType, 255, "delivery_type", cyberSourceResponses))
  }
  
}