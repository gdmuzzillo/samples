package services.validations.operation

import com.decidir.coretx.api.{ErrorFactory, ErrorMessage, OperationResource}
import com.decidir.coretx.domain.Site
import decidir.sps.core.Protocolos

object AmountValidation extends Validation {

  override def validate(implicit op: OperationResource, site: Site): Unit = {
    op.monto.flatMap{ amount =>
      if(amount <= 0){
        logger.error("Amount Validation - invalid amount")
        throw ErrorFactory.validationException(ErrorMessage.INVALID_AMOUNT, ErrorMessage.AMOUNT)
      }

      op.datos_medio_pago.flatMap(_.medio_de_pago) map { medioPago =>
        if(medioPago == 41 && amount.toString.length > 11 ){
          logger.error("Amount Validation - invalid amount")
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.AMOUNT)
        }
      }

      Some(amount)
    }
  }
}
