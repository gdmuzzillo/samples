package services.validations.operation

import com.decidir.coretx.api.{ErrorFactory, ErrorMessage, OperationResource}
import com.decidir.coretx.domain.Site

object SPVValidation extends Validation {

  override def validate(implicit op: OperationResource, site: Site) = {
    op.datos_spv match {
      case Some(spv) => {
        if (spv.client_id.isDefined && spv.client_id.get.length() > 18) {
          logger.error("SPV client_id is invalid")
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SPV_CLIENT_ID)
        }
        if (spv.installment.code.isDefined && spv.installment.code.get.length() > 3) {
          logger.error("SPV installment_code is invalid")
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SPV_INSTALLMENT_CODE)
        }
        if (spv.identificator.isDefined && spv.identificator.get.length() > 1) {
          logger.error("SPV identificator is invalid")
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SPV_IDENTIFICATOR)
        }
        if (spv.installment.quantity.isDefined && spv.installment.quantity.get.length() > 2) {
          logger.error("SPV installment_quantity is invalid")
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SPV_INSTALLMENT_QUANTITY)
        }
      }
      case _ => logger.debug("SPV Validation success.")
    }
  }
}
