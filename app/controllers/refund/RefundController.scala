package controllers.refund

import javax.inject.Inject
import com.decidir.coretx.api.OperationJsonFormats._
import com.decidir.coretx.api._
import com.decidir.coretx.domain.{ProtocolError, TransactionRepository}
import com.decidir.protocol.api.OperationResponse
import controllers.MDCHelperTrait
import play.api.libs.json.{JsError, Json}
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{Action, BodyParsers, Controller, Result}
import services.refunds.RefundService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import TransactionLockedError._

class RefundController  @Inject() (implicit context: ExecutionContext, 
    refundService: RefundService,
    transactionRepository: TransactionRepository) extends Controller with MDCHelperTrait{

  /**
    * Verify if the transaction with the chargeId is locked yet.
    * @param chargeId
    * @param toApply
    * @return
    */
  private def isNotLocked(chargeId: Long)(toApply: => Future[Result]) : Future[Result] = {
    refundService.isLocked(chargeId) match {
      case Success(true) => {
        val message = s"Transaction with chargeId: $chargeId is In Progress."
        logger.error(message)
        Future(InternalServerError(ErrorFactory.transactionLockedError(message).toJson))
      }
      case _ => toApply
    }
  }

  def refund(siteId:String, chargeId:Long) = Action.async(BodyParsers.parse.json) { implicit request =>
    loadMDC(siteId = Some(siteId),
        paymentId = Some(chargeId.toString()))

    isNotLocked(chargeId) {
      val refundValidation = request.body.validate[RefundPaymentRequest] // TODO Agregar errores de formato (nro de tarjeta valido, meses y años)
      refundValidation.fold(

        errors => {
          val jsonErrors = JsError.toJson(errors)
          logger.error("RefundController.refund: bad request " + jsonErrors)
          Future(BadRequest(Json.obj("status" ->"KO", "message" -> jsonErrors))) // TODO
        },

        refundPayment => {
          logger.info("RefundController.refund")
          refundService.process(siteId, chargeId, Some(refundPayment), request.headers.get("user")) map {
            case (rp, Success(or @ OperationResponse(200, _, _, _, _, _, _, _, _, _, _,_)), st) => {
              logger.info("RefundController.refund success")
              Created(Json.toJson(OperationRefundExecutionResponse(refundPaymentResponse = rp.get, operationResponse = or, transactionState = st.get)))
            }
            case (rp, Success(or @ OperationResponse(other, _, _, _, _, _, _, _, _, _, _, _)), st) => {
              logger.info("RefundController.refund success")
              PaymentRequired(Json.toJson(OperationRefundExecutionResponse(refundPaymentResponse = rp.get, operationResponse = or, transactionState = st.get)))
            }
            case (rp, Failure(pe @ ProtocolError(authCode, cardErrorCode)), _) => {
              logger.error("RefundController.refund Failure: ProtocolError" , pe)
              BadRequest(Json.toJson(rp))
            }

            case (rp, Failure(ApiException(error)), _) => {
              logger.error("RefundController.refund Failure: ", error)
              error  match {
                case NotFoundError(_, _) => NotFound(Json.toJson(error))
                case _ =>  InternalServerError(Json.toJson(error))
              }
            }
            case (rp, Failure(exception), _) => {
              logger.error("RefundController.refund Failure: ", exception)
              InternalServerError(ErrorFactory.uncategorizedError(exception).toJson)
            }
          }
        })
    }
  }
  
  
  def list(siteId:String, chargeId:Long) = Action { request =>
    try {
      loadMDC(siteId = Some(siteId), paymentId = Some(chargeId.toString()))
      val history = transactionRepository.listPaymentRefunds(siteId, chargeId)
      val refunds = RefundsPayment(
          parent = OperationExecutionRefundPaymentResponse(history=history),
          sub_payments = transactionRepository.listSubpaymentsRefunds(siteId, chargeId))

      logger.info(s"RefundController.list success")
      Ok(Json.toJson(refunds))
    }
    catch {
      case e: Exception => {
        logger.error("RefundController.list failure", e)
        InternalServerError(ErrorFactory.uncategorizedError(e).toJson)
      }
    }
  }  
  
  def rollback(siteId:String, chargeId: Long, id: Long) = Action.async { implicit request =>
    loadMDC(siteId = Some(siteId),
        paymentId = Some(chargeId.toString()))

    isNotLocked(chargeId) {
      logger.info(s"RefundController.rollback with refundId: $id")
      refundService.rollback(siteId, chargeId, id, request.headers.get("user")) map {
        case (arr, Success(or @ OperationResponse(200, _, _, _, _, _, _, _, _, _, _, _)), txState) => {
          logger.info(s"RefundController.rollback succes with refundId: $id")
          Ok(Json.toJson(OperationAnnulmentExecutionResponse(annulRefundResponse=arr.orNull, operationResponse=or, transactionState = txState.orNull)))
        }

        case (arr, Success(or @ OperationResponse(other, _, _, _, _, _, _, _, _, _, _, _)), txState) => {
          logger.info(s"RefundController.rollback succes with refundId: $id")
          PaymentRequired(Json.toJson(OperationAnnulmentExecutionResponse(annulRefundResponse=arr.orNull, operationResponse=or, transactionState = txState.orNull)))
        }

        case (arp, Failure(pe @ ProtocolError(authCode, cardErrorCode)), txState) => {
          logger.error(s"RefundController.rollback Failure: ProtocolError with refundId: $id" , pe)
          BadRequest(Json.toJson(arp))
        }

        case (arp, Failure(ApiException(error)), txState) => {
          logger.error(s"RefundController.rollback Failure: ProtocolError with refundId: $id " , error)
          error  match {
            case NotFoundError(_, _) => NotFound(Json.toJson(error))
            case _ =>  InternalServerError(Json.toJson(error))
          }
        }

        case (arp, Failure(exception), txState) => {
          logger.error(s"RefundController.rollback Failure with refundId: $id", exception)
          InternalServerError(ErrorFactory.uncategorizedError(exception).toJson)
        }
      }
    }
  } 
  
}