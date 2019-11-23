package services.validations.operation

import java.text.SimpleDateFormat
import java.util.Date

import com.decidir.coretx.api.{ErrorFactory, ErrorMessage, OperationResource}
import com.decidir.coretx.domain.{OfflinePayment, Site}

import scala.util.{Failure, Success, Try}

object DueDateValidation extends Validation {

  override def validate(implicit op: OperationResource, site: Site) = {
    op.datos_offline.foreach {
      case OfflinePayment(_, _, _, _, _, _, Some(fecha), _, _) => {
        op.datos_medio_pago.flatMap(_.medio_de_pago).foreach(
          med_pago => if (med_pago == 41) {
              val toparse = fecha.length match {
                case 6 => fecha + " 235959"
                case 11 => fecha + "59"
                case other => fecha
              }

              val sdf = new SimpleDateFormat("ddMMyy HHmmss")

              val fechavto = try {
                sdf.parse(toparse)
              } catch {
                case e: Throwable => {
                  logger.error(s"Invalid format => must be [ddMMyy hhmmss] instead of $fecha")
                  throw ErrorFactory.validationException("Invalid format", "invoice_expiration")
                }
              }

              if (fechavto.before(new Date())){
                logger.error("invoice_expiration < NOW")
                throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "invoice_expiration")
              }

              if(!isValidDateFormate(toparse, sdf.toPattern)){
                logger.error(s"invalid invoice_expiration >> $fecha")
                throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "invoice_expiration")
              }
          }
        )
      }
      case otherwise =>
        logger.error("Param invoice_expiration required for offline payment")
        ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED + " for offline payment", "invoice_expiration")
    }
  }

  def isValidDateFormate(date: String, format: String = "yyMMdd"): Boolean = {
    val formatter = new java.text.SimpleDateFormat(format)
    Try {
      formatter.format(formatter.parse(date)) equals date
    } match {
      case Success(boolean) => boolean
      case Failure(x) => false
    }

  }
}
