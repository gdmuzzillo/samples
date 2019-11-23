package services.cybersource

import scala.annotation.implicitNotFound
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import com.decidir.coretx.api.BillingData
import com.decidir.coretx.api.CyberSourceResponse
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.RetailTransactionData
import com.decidir.coretx.api.ValidationError
import com.decidir.coretx.domain.OperationData
import javax.inject.Inject
import com.decidir.coretx.api.ShipingData

class VerticalRetailValidator @Inject() (implicit context: ExecutionContext,
    productsValidator: ProductsValidator) extends CyberSourceValidator {
  
  implicit val ec = context
  
  protected def validate(operationData: OperationData, cyberSourceResponses: ArrayBuffer[ValidationError]) {
    val fdd = operationData.resource.fraud_detection.getOrElse(throw ErrorFactory.missingDataException(List("fraud_detection")))
 		val billTo: BillingData = fdd.bill_to.getOrElse(throw ErrorFactory.missingDataException(List("bill_to")))
    val rt = fdd.retail_transaction_data.getOrElse(throw ErrorFactory.missingDataException(List("retail_transaction_data")))
    val shipTo = rt.ship_to.getOrElse(throw ErrorFactory.missingDataException(List("ship_to")))
    
    billToValidate(billTo, cyberSourceResponses)
    validate(shipTo, cyberSourceResponses)
    validate(rt, cyberSourceResponses)
    productsValidator.validate(rt.items, cyberSourceResponses, true)
    fdd.csmdds.map(csmdds => validate(csmdds, List(12,14,15,16), cyberSourceResponses))
  }
  
  protected def billToValidate(billTo: BillingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (billTo.customer_id.getOrElse("").isEmpty) addErrorCode(10103, cyberSourceResponses)
    billTo.customer_id.map(customerId => sizeValidate(customerId.toString, 50, "billTo.customer_id", cyberSourceResponses))
  }
  
  private def validate(shipTo: ShipingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    shipingRequiredValidate(shipTo, cyberSourceResponses)
    shipingRequiredFormatValidate(shipTo, cyberSourceResponses)
  }

  private def shipingRequiredValidate(shipTo: ShipingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (shipTo.first_name.getOrElse("").isEmpty) addErrorCode(10163, cyberSourceResponses)
    if (shipTo.last_name.getOrElse("").isEmpty) addErrorCode(10164, cyberSourceResponses)
    if (shipTo.street1.getOrElse("").isEmpty) addErrorCode(10168, cyberSourceResponses)
    if (shipTo.city.getOrElse("").isEmpty) addErrorCode(10160, cyberSourceResponses)
    if (shipTo.state.getOrElse("").isEmpty) addErrorCode(10167, cyberSourceResponses)
    if (shipTo.postal_code.getOrElse("").isEmpty) addErrorCode(10166, cyberSourceResponses)
    if (shipTo.country.getOrElse("").isEmpty) addErrorCode(10161, cyberSourceResponses)
    if (shipTo.phone_number.getOrElse("").isEmpty) addErrorCode(10165, cyberSourceResponses)
    if (shipTo.email.getOrElse("").isEmpty) addErrorCode(10162, cyberSourceResponses)
  }
  
  private def shipingRequiredFormatValidate(shipTo: ShipingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
	  shipTo.first_name.map(firstName => regexValidate(firstName, """^\D+$""", 60, 10363, cyberSourceResponses))
	  shipTo.last_name.map(lastName => regexValidate(lastName, """^\D+$""", 60, 10364, cyberSourceResponses))
	  shipTo.city.map(city => sizeValidate(city, 50, 10360, cyberSourceResponses))
	  shipTo.street1.map(street1 => sizeValidate(street1, 60, 10368, cyberSourceResponses))
	  shipTo.street2.map(street2 => sizeValidate(street2, 60, "ship_to.street2", cyberSourceResponses))
	  shipTo.state.map(state => sizeValidate(state, 2, 10367, cyberSourceResponses))
    shipTo.postal_code.map(postalCode => sizeValidate(postalCode, 10, 10366, cyberSourceResponses))
    shipTo.country.map(country => regexValidate(country, """^[A-Z]{2}$""", 2, 10361, cyberSourceResponses))
    shipTo.phone_number.map(phoneNumber => sizeValidate(phoneNumber, 15, 10365, cyberSourceResponses))
    shipTo.email.map(email => regexValidate(email, """[^@]+@[^\.]+\..+""", 100, 10362, cyberSourceResponses))
  }
    
  private def validate(rt: RetailTransactionData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
	  rt.days_to_delivery.map(daysToDelivery => sizeValidate(daysToDelivery, 255, "days_to_delivery", cyberSourceResponses))
//	  rt.tax_voucher_required.map(taxVoucherRequired => sizeValidate(taxVoucherRequired, 255, 0, cyberSourceResponses))
	  rt.customer_loyality_number.map(customerLoyalityNumber => sizeValidate(customerLoyalityNumber, 255, "customer_loyality_number", cyberSourceResponses))
    rt.coupon_code.map(couponCode => sizeValidate(couponCode, 255, "coupon_code", cyberSourceResponses))
  }
}