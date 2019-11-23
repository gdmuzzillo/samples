package controllers

import com.decidir.coretx.domain.TransactionRepository
import javax.inject.Inject

import play.api.mvc._

import scala.util.Success
import play.api.libs.json.Json

import scala.util.Failure
import com.decidir.coretx.api.ErrorFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import com.decidir.coretx.api.OperationJsonFormats._
import com.decidir.coretx.api.OperationResourcePage
import com.decidir.coretx.api.OperationResource
import play.api.libs.json.JsError
import com.decidir.protocol.api.OperationResponse
import com.decidir.coretx.api.ApiException
import com.decidir.coretx.api.NotFoundError
import com.decidir.coretx.domain.ProtocolError
import services.confirmation.PaymentConfirmationsService
import com.decidir.coretx.api.TransactionLockedError

/**
 * TODO 
 */
class TransactionController  @Inject() (implicit ec: ExecutionContext, 
    transactionRepository: TransactionRepository,
    paymentConfirmationsService: PaymentConfirmationsService) extends Controller with MDCHelperTrait {

  def retrieve(siteId:String, chargeId: Long) = Action {request =>
    loadMDC(siteId = Some(siteId),
        paymentId = Some(chargeId.toString()))
    logger.info("TransactionController.retrieve")
    
    transactionRepository.retrieveCharge(siteId, chargeId) match {
      case Success(operation) => {
        logger.info("TransactionController.retrieve success")
        Ok(Json.toJson(operation))
      }
      
      case Failure(exception) => {
        logger.error("TransactionController.retrieve failure", exception)
        NotFound(ErrorFactory.notFoundError("payments", chargeId.toString).toJson)        
      }
    }  
  }
  
  
  def list(siteId: String, offset: Int, oPageSize: Int, siteOperationId: Option[String], merchantId: Option[String], csYellow: Option[Boolean], dateFrom :Option[String], dateTo :Option[String]) = Action { request =>
    try {
      loadMDC(siteId = Some(siteId))
      val pageSize = if(oPageSize >= 500) 500 else oPageSize
      val charges = transactionRepository.listCharges(siteId, offset, pageSize+1, siteOperationId, merchantId, csYellow, dateFrom, dateTo)
      logger.info(s"TransactionController.list success with offset: $offset and pageSize: $pageSize")
      Ok(Json.toJson(OperationResourcePage(offset, pageSize, charges.take(pageSize), charges.size > pageSize)))
    }
    catch {
      case e: Exception => {
        logger.error("TransactionController.list failure", e)
        InternalServerError(ErrorFactory.uncategorizedError(e).toJson)
      }
    }
  } 
  
  /**
  * Verify if the transaction with the chargeId is locked yet.
  * @param chargeId
  * @param toApply
  * @return
  */
  private def isNotLocked(chargeId: Long)(toApply: => Future[Result]) : Future[Result] = {
    paymentConfirmationsService.isLocked(chargeId) match {
      case Success(true) => {
        val message = s"Transaction with chargeId: $chargeId is In Progress."
        logger.error(message)
        Future(InternalServerError(ErrorFactory.transactionLockedError(message).toJson))
      }
      case _ => toApply
    }
  }

  def confirm(siteId:String, chargeId:Long) = Action.async(BodyParsers.parse.json) { implicit request =>
    loadMDC(siteId = Some(siteId),
        paymentId = Some(chargeId.toString()))
    
      isNotLocked(chargeId) {
      
  	    val operationResourceValidation = request.body.validate[OperationResource]
  			operationResourceValidation.fold(
  					
  					errors => {
  						val jsonErrors = JsError.toJson(errors)
  								logger.error("TransactionController.confirm: bad request " + jsonErrors)
  								Future(BadRequest(Json.obj("status" ->"KO", "message" -> jsonErrors)))
  					},
  					
  					operationResource => {
  						logger.info("TransactionController.confirm")
  						paymentConfirmationsService.confirm(siteId, chargeId, operationResource, request.headers.get("user")) map { _ match  {
  						case (Some(oer), Success(OperationResponse(200, _, _, _, _, _, _, _, _, _, _,_))) => {
  							logger.info("TransactionController.confirm success")
  							Ok(Json.toJson(oer))
  						}
  						case (Some(oer), Success(OperationResponse(other, _, _, _, _, _, _, _, _, _, _, _))) => {
  							logger.error("TransactionController.confirm failure")
  							PaymentRequired(Json.toJson(oer))
  						}
  						case (Some(oer), Failure(pe @ ProtocolError(authCode, cardErrorCode))) => {
  							logger.error("TransactionController.confirm Failure: ProtocolError" , pe)
  							BadRequest(Json.toJson(oer))
  						}
  						case (_, Failure(ApiException(error))) => {
  							logger.error("PaymentConfirmationsController.create Failure: ", error)
  							error  match {
  							case NotFoundError(_, _) => NotFound(Json.toJson(error))
  							case _ =>  InternalServerError(Json.toJson(error))
  							}
  						}
  						case (_, Failure(exception)) => {
  							logger.error("TransactionController.confirm Failure: ", exception)
  							InternalServerError(ErrorFactory.uncategorizedError(exception).toJson)
  						}
  						}
  						}
  					})
    }
  }
  
}