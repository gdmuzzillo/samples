package services.validations.operation

import com.decidir.coretx.api.{ErrorFactory, ErrorMessage, OperationResource}
import com.decidir.coretx.domain.Site

object HashValidation extends Validation {

  override def validate(implicit op: OperationResource, site: Site): Unit = {
    if(op.origin.flatMap(_.app).getOrElse("") == "WEBTX"){
      if((site.hashConfiguration.useHash && !op.origin.flatMap(_.useHash).getOrElse(false)) ||
        (site.hashConfiguration.firstHashDate.isDefined && !op.origin.flatMap(_.useHash).getOrElse(false))){
        logger.error("validate hash - Missing Hash")
        throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "HASH")
      }
    }
  }
}
