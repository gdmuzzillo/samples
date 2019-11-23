package controllers

import javax.inject.Inject

import com.decidir.coretx.api.{Acreditada, FacturaGenerada, OperationExecutionResponse, TransactionState}
import com.decidir.coretx.domain._
import play.api.libs.json.Json
import play.api.mvc.{Action, BodyParsers, Controller}
import services.payments.{NotifyPayment, OfflinePaymentsNotificationService}

import scala.util.{Failure, Success, Try}

class OfflineNotificationController @Inject()(notifyPayment: NotifyPayment,
                                              transactionRepository: TransactionRepository,
                                              offlinePaymentsNotificationService: OfflinePaymentsNotificationService)
  extends Controller with MDCHelperTrait {

  def paymentNotification(nroOperacion: String, codigoBarra: String) = {
    rapiPagoNotification(nroOperacion: String, codigoBarra: String, Acreditada(): TransactionState)
  }

  def reverseNotification(nroOperacion: String, codigoBarra: String) = {
    rapiPagoNotification(nroOperacion: String, codigoBarra: String, FacturaGenerada(): TransactionState)
  }

  def rapiPagoNotification(nroOperacion: String, codigoBarra: String, transactionState: TransactionState) = Action(BodyParsers.parse.json) { implicit request =>
    Ok(Json.toJson(validatePayment(nroOperacion: String, codigoBarra: String, transactionState)))
  }

  def validatePayment(nroOperacion: String, codigoBarra: String, transactionState: TransactionState): PaymentNotification = {
    val operationExecutionResponse: Try[Try[OperationExecutionResponse]] = offlinePaymentsNotificationService.notifyOfflinePayment(nroOperacion: String, codigoBarra: String, transactionState)

    operationExecutionResponse.flatMap(identity) match {

      case Success(oer) => {
        notifyPayment.sendConfirmationPaymentOffline(oer.copy(status = 6, authorized = true))
        PaymentNotification("0", "Transacción aceptada")
      }
      case Failure(e) => {
        logger.error(e.toString)
        PaymentNotification("10", "Error interno de la entidad")
      }

    }

  }

  implicit val paymentNotificationWrites = Json.writes[PaymentNotification]
  implicit val paymentNotificationReads = Json.reads[PaymentNotification]
}

case class PaymentNotification(
                                codigo_respuesta: String,
                                msg: String
                              )