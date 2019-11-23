package services.cybersource

import com.decidir.coretx.api.{BillingData, Csmdd, CyberSourceResponse, ValidationError}
import com.decidir.coretx.domain.OperationData

import scala.collection.mutable.ArrayBuffer

trait CyberSourceCommonsValidator {

  def validate(operationData: OperationData): ArrayBuffer[ValidationError] = {
    val errors = ArrayBuffer[ValidationError]()
    validate(operationData, errors)
    errors
  }

  protected def addErrorCode(errorCode: Int, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    cyberSourceResponses += ValidationError(errorCode.toString(), CyberSourceResponse.fromDecisionAndReason(None, null, errorCode).description)
  }

  private def addErrorSizeInvalid(field: String, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    cyberSourceResponses += ValidationError("00", s"${field}, invalid size")
  }

  protected def regexValidate(valueToValidate: String, regex: String, maxSize: Int, errorCode: Int, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if(!valueToValidate.isEmpty && (invalidSize(valueToValidate, maxSize) || regex.r.unapplySeq(valueToValidate).isEmpty)) addErrorCode(errorCode, cyberSourceResponses)
  }

  protected def sizeValidate(valueToValidate: String, maxSize: Int, field: String, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if(!valueToValidate.isEmpty && invalidSize(valueToValidate, maxSize)) addErrorSizeInvalid(field, cyberSourceResponses)
  }

  protected def sizeValidate(valueToValidate: String, maxSize: Int, errorCode: Int, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if(!valueToValidate.isEmpty && invalidSize(valueToValidate, maxSize)) addErrorCode(errorCode, cyberSourceResponses)
  }

  protected def validate(csmdds: List[Csmdd], invalidCsmdds: List[Int], cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    val codes = csmdds.map(csmdd => csmdd.code)
    val codesRepeated = codes.diff(codes.distinct).distinct
    codesRepeated.map(code => addErrorCsmddRepeted(code, cyberSourceResponses))
    val allInvalidCsmdds = List(1,2,3,4,5,6,7,8,9,10,11,13,35,36,37,38,39,40,41,42) ++(invalidCsmdds)
    csmdds.foreach(csmdd => {if (allInvalidCsmdds.contains(csmdd.code) || (csmdd.code < 1 || csmdd.code > 99)) {
      addErrorCsmddInvalid(csmdd.code, cyberSourceResponses)
    }})

    validateSizeCsmdds(csmdds, (1 to 99 toList) diff allInvalidCsmdds, cyberSourceResponses)
  }

  /**
    * Valida el size de los posibles csmdds no utilizados en la vertical
    */
  private def validateSizeCsmdds(csmdds: List[Csmdd], possibleCsmdds: List[Int], cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    csmdds.foreach(csmdd => {
      if (possibleCsmdds.contains(csmdd.code) && invalidSize(csmdd.description, 255))
        cyberSourceResponses += ValidationError(s"CSMDD${csmdd.code}", "invalid size")
    })
  }

  private def addErrorCsmddRepeted(csmddCode: Int, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    cyberSourceResponses += ValidationError(s"CSMDD${csmddCode}", "repeted CSMDD")
  }

  private def addErrorCsmddInvalid(csmddCode: Int, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    cyberSourceResponses += ValidationError(s"CSMDD${csmddCode}", "invalid CSMDD for vertical")
  }

  protected def billToValidate(billTo: BillingData, cyberSourceResponses: ArrayBuffer[ValidationError])
  protected def validate(operationData: OperationData, cyberSourceResponses: ArrayBuffer[ValidationError])

  private def invalidSize(valueToValidate: String, maxSize: Int) = {
    valueToValidate.size > maxSize
  }

}
