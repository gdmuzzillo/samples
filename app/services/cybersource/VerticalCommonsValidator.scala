package services.cybersource

import java.text.SimpleDateFormat
import java.util.Optional

import scala.annotation.implicitNotFound
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import org.joda.time.DateTime
import com.decidir.coretx.api.BillingData
import com.decidir.coretx.api.CyberSourceResponse
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.PurchaseTotals
import com.decidir.coretx.api.ValidationError
import com.decidir.coretx.domain.DatosMedioPago
import com.decidir.coretx.domain.OperationData
import javax.inject.Inject

class VerticalCommonsValidator @Inject() (context: ExecutionContext) extends CyberSourceCommonsValidator {
  
  implicit val ec = context
  
  protected def validate(operationData: OperationData, cyberSourceResponses: ArrayBuffer[ValidationError]) {
    operationData.site.cyberSourceConfiguration.getOrElse(throw ErrorFactory.missingDataException(List("cybersource_configuration")))
    val fdd = operationData.resource.fraud_detection.getOrElse(throw ErrorFactory.missingDataException(List("fraud_detection")))
    val billTo: BillingData = fdd.bill_to.getOrElse(throw ErrorFactory.missingDataException(List("bill_to")))
    val purchaseTotals = fdd.purchase_totals.getOrElse(throw ErrorFactory.missingDataException(List("purchase_totals")))
    val datosMedioPago = operationData.datosMedioPago
    val device_unique_id = fdd.device_unique_id.getOrElse(throw ErrorFactory.missingDataException(List("device_unique_id")))
    
    billToValidate(billTo, cyberSourceResponses)
    cardValidate(datosMedioPago, cyberSourceResponses)
    purchaseTotalsValidate(purchaseTotals, cyberSourceResponses)
    deviceFingerprintValidate(device_unique_id, cyberSourceResponses)
    ipValidate(operationData.resource.datos_titular.flatMap(_.ip), cyberSourceResponses)
  }
  
  protected def billToValidate(billTo: BillingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    billToRequiredValidate(billTo, cyberSourceResponses)
    billToFormatValidate(billTo, cyberSourceResponses)
  }

  private def billToRequiredValidate(billTo: BillingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (billTo.first_name.getOrElse("").isEmpty) addErrorCode(10105, cyberSourceResponses)
    if (billTo.last_name.getOrElse("").isEmpty) addErrorCode(10107, cyberSourceResponses)
    if (billTo.street1.getOrElse("").isEmpty) addErrorCode(10111, cyberSourceResponses)
    if (billTo.city.getOrElse("").isEmpty) addErrorCode(10101, cyberSourceResponses)
    if (billTo.state.getOrElse("").isEmpty) addErrorCode(10110, cyberSourceResponses)
    if (billTo.postal_code.getOrElse("").isEmpty) addErrorCode(10109, cyberSourceResponses)
    if (billTo.country.getOrElse("").isEmpty) addErrorCode(10102, cyberSourceResponses)
    if (billTo.phone_number.getOrElse("").isEmpty) addErrorCode(10108, cyberSourceResponses)
    if (billTo.email.getOrElse("").isEmpty) addErrorCode(10104, cyberSourceResponses)
  }
  
  private def billToFormatValidate(billTo: BillingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
	  billTo.first_name.map(firstName => regexValidate(firstName, """^\D+$""", 60, 10305, cyberSourceResponses))
	  billTo.last_name.map(lastName => regexValidate(lastName, """^\D+$""", 60, 10307, cyberSourceResponses))
	  billTo.street1.map(street1 => sizeValidate(street1, 60, "bill_to.street1", cyberSourceResponses))
	  billTo.street2.map(street2 => sizeValidate(street2, 60, "bill_to.street2", cyberSourceResponses))
	  billTo.city.map(city => sizeValidate(city, 50, "bill_to.city", cyberSourceResponses))
	  billTo.state.map(state => regexValidate(state, """^[A-Z0-9]{1,2}$""", 2, 10310, cyberSourceResponses))
    billTo.postal_code.map(postalCode => sizeValidate(postalCode, 10, "bill_to.postal_code", cyberSourceResponses))
	  billTo.country.map(country => regexValidate(country, """^[A-Z]{2}$""", 2, 10302, cyberSourceResponses))
    billTo.phone_number.map(phoneNumber => sizeValidate(phoneNumber, 15, "bill_to.phone_number", cyberSourceResponses))
    billTo.email.map(email => regexValidate(email, """[^@]+@[^\.]+\..+""", 100, 10304, cyberSourceResponses))
  }

  private def ipValidate(ipAddress: Option[String], cyberSourceResponses: ArrayBuffer[ValidationError]) {
     if(ipAddress.getOrElse("").isEmpty) addErrorCode(10106, cyberSourceResponses)
     ipAddress.map(ip => {
       if("""^(?:(?:2[0-4]\d|25[0-5]|1\d{2}|[1-9]?\d)\.){3}(?:2[0-4]\d|25[0-5]|1\d{2}|[1-9]?\d)$""".r.unapplySeq(ip).isEmpty 
          && """^[a-fA-F0-9:.]+$""".r.unapplySeq(ip).isEmpty) //IPVx format
        addErrorCode(10306, cyberSourceResponses)
     })
  }
  
  private def cardValidate(datosMedioPago: DatosMedioPago, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    cardExpirationDateValidate(datosMedioPago, cyberSourceResponses)
    cardFormatValidate(datosMedioPago, cyberSourceResponses)
  }
  
  private def cardExpirationDateValidate(datosMedioPago: DatosMedioPago, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (!datosMedioPago.expiration_year.isEmpty && !datosMedioPago.expiration_month.isEmpty){
  	  val dateFormat = new SimpleDateFormat("MM/yyyy")
      val currentDate = dateFormat.parse(DateTime.now.getMonthOfYear + "/" + DateTime.now.year().get)
      val expirationDate = dateFormat.parse(datosMedioPago.expiration_month + "/" + "20" + datosMedioPago.expiration_year)
      if (expirationDate.before(currentDate)) addErrorCode(10324, cyberSourceResponses)
    }
  }

  private def cardFormatValidate(datosMedioPago: DatosMedioPago, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    sizeValidate(datosMedioPago.nro_tarjeta, 20, "nro_tarjeta", cyberSourceResponses)
    sizeValidate(datosMedioPago.expiration_month, 2, "expiration_month", cyberSourceResponses)
    sizeValidate(datosMedioPago.expiration_year, 4, "expiration_year", cyberSourceResponses)
  }
    
  private def purchaseTotalsValidate(purchaseTotals: PurchaseTotals, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    purchaseTotalsRequiredValidate(purchaseTotals, cyberSourceResponses)
    purchaseTotalsFormatValidate(purchaseTotals, cyberSourceResponses)
  }
  
  private def purchaseTotalsRequiredValidate(purchaseTotals: PurchaseTotals, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (purchaseTotals.currency.getOrElse("").isEmpty) addErrorCode(10140, cyberSourceResponses)
    if (purchaseTotals.amount.isEmpty) addErrorCode(10141, cyberSourceResponses)
  }
  
  private def purchaseTotalsFormatValidate(purchaseTotals: PurchaseTotals, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    purchaseTotals.currency.map(currency => regexValidate(currency, """^[A-Z]{3}$""", 5, 10340, cyberSourceResponses))
    purchaseTotals.amount.map(amount => sizeValidate(amount.toString, 15, "purchase_totals.amount", cyberSourceResponses))
  }  
  
  private def deviceFingerprintValidate(deviceUniqueId: String, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (deviceUniqueId.isEmpty) {
      addErrorCode(10150, cyberSourceResponses)
    } else sizeValidate(deviceUniqueId, 255, "device_unique_id", cyberSourceResponses)
  }
  
}