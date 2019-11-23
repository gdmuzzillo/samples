package services.cybersource

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.collection.mutable.ArrayBuffer
import com.decidir.coretx.api.ValidationError
import com.decidir.coretx.api.CyberSourceResponse
import com.decidir.coretx.api.Item
import com.decidir.coretx.api.BillingData
import com.decidir.coretx.domain.OperationData

class ProductsValidator @Inject() (context: ExecutionContext) extends CyberSourceValidator {
  
  implicit val ec = context
  
  def validate(items: List[Item], cyberSourceResponses: ArrayBuffer[ValidationError], isRetail: Boolean) = {
    if (!items.isEmpty) {
      items.zipWithIndex foreach { case(product, position) => {
        productRequiredValidate(product, position, cyberSourceResponses, isRetail)
        productFormatValidate(product, position, cyberSourceResponses, isRetail)
      }}   
    } else addErrorCode(10139, cyberSourceResponses)
  }
  
  private def productRequiredValidate(product: Item, position: Int, cyberSourceResponses: ArrayBuffer[ValidationError], isRetail: Boolean) = {
    if (product.name.getOrElse("").isEmpty) addErrorCode(10132, position, cyberSourceResponses)
    if (product.sku.getOrElse("").isEmpty) addErrorCode(10133, position, cyberSourceResponses)
    if (product.total_amount.isEmpty) addErrorCode(10135, position, cyberSourceResponses)
    if (product.unit_price.isEmpty) addErrorCode(10136, position, cyberSourceResponses)
    if (product.code.getOrElse("").isEmpty) addErrorCode(10130, position, cyberSourceResponses)
    if (!isRetail && product.description.getOrElse("").isEmpty) addErrorCode(10131, position, cyberSourceResponses)
    if (product.quantity.isEmpty) addErrorCode(10134, position, cyberSourceResponses) 
  }
  
  private def productFormatValidate(product: Item, position: Int, cyberSourceResponses: ArrayBuffer[ValidationError], isRetail: Boolean) = {
    product.name.map(name => sizeValidate(name, 255, 10132, cyberSourceResponses))
    product.sku.map(name => sizeValidate(name, 255, 10133, cyberSourceResponses))
    product.unit_price.map(unitPrice => sizeValidate(unitPrice.toString, 15, 10136, cyberSourceResponses))
    product.code.map(code => sizeValidate(code, 255, 10130, cyberSourceResponses))
    product.quantity.map(quantity => sizeValidate(quantity.toString, 10, 10134, cyberSourceResponses))
  }
  
  protected def addErrorCode(errorCode: Int, item: Int, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    cyberSourceResponses += ValidationError(errorCode.toString(), CyberSourceResponse.fromDecisionAndReason(None, null, errorCode, item).description)
  }
  
  protected def billToValidate(billTo: BillingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = ???
  protected def validate(operationData: OperationData, cyberSourceResponses: ArrayBuffer[ValidationError]) = ???
  
}