package services.validations.operation

import com.decidir.coretx.api.{ErrorFactory, ErrorMessage, OperationResource}
import com.decidir.coretx.domain.Site

object MPOSValidation extends Validation {

  /**
    * Valida si la transaccion es por banda y que el site este habilitado para esa operatoria.
    *
    * @param op
    * @param site
    */
  override def validate(implicit op: OperationResource, site: Site) = {
    site.mensajeriaMPOS.getOrElse("N") match {
      case "S" => {
        op.datos_banda_tarjeta match {
          case None => {
            logger.error("Site allows only MPOS transactions")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SITE_MPOS_ENABLED)
          }
          case _ => {}
        }
      }
      case "N" => {
        op.datos_banda_tarjeta match {
          case Some(datosBandaTarjeta) => {
            logger.error("Site does not allow MPOS transactions")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SITE_MPOS_DISABLED)
          }
          case _ => {}
        }
      }
    }

  }
}
