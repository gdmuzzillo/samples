package services.validations.operation

import com.decidir.coretx.api.{ErrorFactory, ErrorMessage, OperationResource}
import com.decidir.coretx.domain.Site

object OperationNumberValidation extends Validation {

  override def validate(implicit op: OperationResource, site: Site) = {
    val nroOperacion = op.nro_operacion.getOrElse{
      logger.error("operation number empty")
      throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "site_transaction_id")
    }
    if(nroOperacion.length() > 40){
      logger.error("wrong operation number")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "site_transaction_id")
    }
  }
}
