package controllers.reversal

/*
import javax.inject.Inject

import com.decidir.coretx.api.OperationJsonFormats._
import com.decidir.coretx.api._
import com.decidir.coretx.domain.{ProtocolError, TransactionRepository}
import com.decidir.protocol.api.OperationResponse
import controllers.MDCHelperTrait
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsError, Json}
import play.api.mvc.{Action, BodyParsers, Controller}
import services.refunds.{InconsistentTransactionService, RefundService}
import services.reversals.ReversalService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ReversalController @Inject()(implicit context: ExecutionContext,
                                   reversalService: ReversalService)
  extends Controller with MDCHelperTrait {

  def process(siteId:String, chargeId: Long) = Action.async { request =>
    loadMDC(siteId = Some(siteId), paymentId = Some(chargeId.toString()))
    logger.info("ReversalController.process")
    reversalService.reverse(siteId, chargeId) map { reverse =>
      reverse match {
        case Success(reversePaymentResponse) => {
          reversePaymentResponse.status match {
            case Autorizada() => {
              logger.info("ReversalController.process success")
              Ok(Json.toJson(reversePaymentResponse))
            }
            case other => {
              logger.warn(s"ReversalController.process success with status ${other}")
              BadRequest(Json.toJson(reversePaymentResponse))
            }
          }
        }
        case Failure(exception) => {
          logger.error("ReversalController.process Failure: ", exception)
          InternalServerError(ErrorFactory.uncategorizedError(exception).toJson)
        }
      }
    }
  }

}*/
