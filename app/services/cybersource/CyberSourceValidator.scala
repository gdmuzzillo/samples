package services.cybersource

import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.api.ValidationError
import scala.collection.mutable.ArrayBuffer
import com.decidir.coretx.api.BillingData
import com.decidir.coretx.api.CyberSourceResponse
import com.decidir.coretx.api.Csmdd
import com.decidir.coretx.api.ErrorFactory

trait CyberSourceValidator extends CyberSourceCommonsValidator {

  override def validate(operationData: OperationData): ArrayBuffer[ValidationError] = {
    val errors = ArrayBuffer[ValidationError]()
    customerInSiteValidate(operationData, errors)
    validate(operationData, errors)
    errors
  }

  def customerInSiteValidate(operationData: OperationData, cyberSourceResponses: ArrayBuffer[ValidationError])= {
    val customerInSite = operationData.resource.fraud_detection.flatMap(_.customer_in_site) //.getOrElse(throw ErrorFactory.missingDataException(List("customer_in_site")))
    val dispatchMethod = operationData.resource.fraud_detection.flatMap(_.dispatch_method)
//    customerInSite.days_in_site.map(daysInSite => sizeValidate(daysInSite.toString, 255, 0, cyberSourceResponses))
//    customerInSite.is_guest.map(isGuest => sizeValidate(isGuest.toString, 255, 0, cyberSourceResponses))
    customerInSite.map(customer => customer.password.map(password => sizeValidate(password, 255, "customer_in_site.password", cyberSourceResponses)))
//    customerInSite.num_of_transactions.map(numOfTransactions => sizeValidate(numOfTransactions.toString, 255, 0, cyberSourceResponses))
    customerInSite.map(customer => customer.cellphone_number.map(cellphoneNumber => sizeValidate(cellphoneNumber, 255, "customer_in_site.cellphone_number", cyberSourceResponses)))
    dispatchMethod.map(dispatchMethod => sizeValidate(dispatchMethod, 255, "dispatch_method", cyberSourceResponses))
    customerInSite.map(customer => customer.street.map(street => sizeValidate(street, 255, "customer_in_site.street", cyberSourceResponses)))
    customerInSite.map(customer => customer.date_of_birth.map(dateOfBirth => sizeValidate(dateOfBirth, 255, "customer_in_site.date_of_birth", cyberSourceResponses)))
  }
}