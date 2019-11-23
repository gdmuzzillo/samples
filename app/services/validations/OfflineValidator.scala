package services.validations

import java.text.SimpleDateFormat
import java.util.Date

import scala.concurrent.ExecutionContext
import com.decidir.coretx.api.{ErrorFactory, ErrorMessage, OperationResource}
import com.decidir.coretx.domain.MedioDePagoRepository
import com.decidir.coretx.domain.OfflinePayment
import javax.inject.Inject
import javax.inject.Singleton

import legacy.decidir.sps.offline.{ProtocoloCajaDePagos, ProtocoloCobroExpress, ProtocoloPagoFacil, ProtocoloRapiPago}
import services.validations.operation.DueDateValidation

import scala.util.Try
import scala.util.Failure
import scala.util.Success

@Singleton
class OfflineValidator @Inject()(context: ExecutionContext,
                                 medioDePagoRepository: MedioDePagoRepository){

  implicit val ec = context   

  def validate(operation: OperationResource): OperationResource = {

    val isNumeric = "^[0-9]*$"
    //Validacion de cod_p*

    if(operation.datos_titular.flatMap(_.email_cliente).isDefined &&
      operation.datos_titular.flatMap(_.email_cliente).get.length > 80)
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "email")

    operation.datos_medio_pago.flatMap(_.medio_de_pago) match {
        //CAJA DE PAGOS y COBRO EXPRESS
      case Some(ProtocoloCajaDePagos.idmediopago) |
           Some(ProtocoloCobroExpress.idmediopago) => {

        if(operation.datos_medio_pago.get.medio_de_pago.get != ProtocoloCobroExpress.idmediopago) {

          //TODO Ver como armar la fecha de 2do vencimiento
          operation.datos_offline match {
            case Some(OfflinePayment(Some(cod_p1), _, _, _, _, _, _, _, _)) => {
              if(!cod_p1.matches(isNumeric) || cod_p1.length != 4)
                throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "cod_p1")
            }
            case other => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "cod_p1")
          }

  //        operation.datos_offline match {
  //          case Some(OfflinePayment(_, Some(cod_p2), _, _, _, _, _, _, _)) => {
  //            if(!cod_p2.matches(isNumeric) || cod_p2.length != 1)
  //              throw ErrorFactory.validationException("cod_p2", "Tipo de generacion inválido - Longitud 1 caracter numerico")
  //          }
  //          case other => throw ErrorFactory.validationException("cod_p2", "Missing tipo de generacion")
  //        }

          operation.datos_offline match {
            case Some(OfflinePayment(_, _, Some(cod_p3), _, _, _, _, _, _)) => {
              if(!cod_p3.matches(isNumeric) || cod_p3.length != 1)
                throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "cod_p3")
            }
            case other => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "cod_p3")
          }
        }

        operation.datos_offline match {
          case Some(OfflinePayment(_, _, _, _, _, _, None, _, _)) => {
            throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "invoice_expiration")
          }

          case Some(OfflinePayment(_, _, _, _, _, _, Some(fechavto), _, _)) => {
            if (!DueDateValidation.isValidDateFormate(fechavto, "yyMMdd") )
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "invoice_expiration")

            val formatter = new java.text.SimpleDateFormat("yyMMdd")
            if(formatter.parse(fechavto).before(new Date))
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "invoice_expiration")
          }

          case Some(OfflinePayment(_, _, _, _, _, _, Some(fechavto), _, Some(fechavto2))) => {

            if ( !DueDateValidation.isValidDateFormate(fechavto, "yyMMdd") || fechavto.isEmpty)
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "invoice_expiration")

            if ( !DueDateValidation.isValidDateFormate(fechavto2, "yyMMdd") || fechavto2.isEmpty)
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "second_invoice_expiration")

            val sdf = new SimpleDateFormat("yyMMdd")
            if(sdf.parse(fechavto2).before(sdf.parse(fechavto)))
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "second_invoice_expiration")

          }
        }

        operation.nro_operacion match {
          case None => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "site_transaction_id")
          case Some(opn: String) => if(!opn.matches("^[0-9]{8}$")) throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "site_transaction_id")
        }

        //TODO esta validacion es correcta?
        if(operation.idTransaccion.isDefined && !operation.idTransaccion.get.matches(isNumeric))
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "site_transaction_id")
      }
        //PAGO FACIL Y RAPIPAGO
      case Some(ProtocoloPagoFacil.idmediopago) | Some(ProtocoloRapiPago.idmediopago) => {
        operation.datos_offline match {
          case Some(OfflinePayment(Some(cod_p1), _, _, _, _, _, _, _, _)) => {
            if(!cod_p1.matches(isNumeric) || cod_p1.length != 3)
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "cod_p1")
          }
          case other => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "cod_p1")
        }

        operation.datos_offline match {
          case Some(OfflinePayment(_, Some(cod_p2), _, _, _, _, _, _, _)) => {
            if(!cod_p2.matches(isNumeric) || cod_p2.length != 4)
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "cod_p2")
          }
          case other => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "cod_p2")
        }

        operation.datos_offline match {
          case Some(OfflinePayment(_, _, Some(cod_p3), _, _, _, _, _, _)) => {
            if(!cod_p3.matches(isNumeric) || cod_p3.length != 2)
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "cod_p3")
          }
          case other => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "cod_p3")
        }

        operation.datos_offline match {
          case Some(OfflinePayment(_, _, _, Some(cod_p4), _, _, _, _, _)) => {
            if(!cod_p4.matches(isNumeric) || cod_p4.length != 3)
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "cod_p4")
          }
          case other => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "cod_p4")
        }
      }
        //OTRO
      case other => throw ErrorFactory.validationException("Payment method id is not configured", "payment_method_id")
    }

    operation.datos_offline match {
      case Some(OfflinePayment(_, _, _, _, Some(recargo), _, _, _, _)) => {
        if(!recargo.matches(isNumeric))
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "surcharge")
        if(recargo.length > 7)
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "surcharge")
      }
      case other => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "surcharge")
    }

    operation.datos_offline match {
      case Some(OfflinePayment(_, _, _, _, _, Some(cliente), _, _, _)) => {
        if(!cliente.matches(isNumeric))
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "client")
        if(cliente.length != 8)
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "client")
      }
      case other => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "client")
    }

    operation.monto match {
      case Some(monto) => {
        if(!monto.toString.matches(isNumeric))
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "amount")
        if(monto.toString.length > 8)
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "amount")
      }
      case other => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "amount")
    }

    operation
  }
  
}