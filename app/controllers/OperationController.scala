package controllers

import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success
import com.decidir.coretx.api._
import com.decidir.coretx.api.OperationJsonFormats._
import javax.inject.Inject
import javax.inject.Singleton

import play.api.libs.json.JsError
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.Action
import play.api.mvc.BodyParsers
import play.api.mvc.Controller
import services.OperacionService
import services.metrics.MetricsClient
import com.decidir.coretx.domain.ProcessingError
import com.decidir.coretx.domain.ProtocolError
import services.payments.PaymentsService


/**
 * TODO Ver porque no funciona el modulo
 */
//class OperationController @Inject()(val jedisPool: JedisPool) extends Controller with JedisUtils {
class OperationController @Inject() (implicit context: ExecutionContext, 
    operacionService: OperacionService, 
    metrics: MetricsClient,
    paymentsService: PaymentsService) extends Controller with MDCHelperTrait {

  val appId = "coretx"
  
  def maskedObject(or: OperationResource): OperationResource = or.copy(datos_medio_pago = or.datos_medio_pago.map(maskedObject(_)),
    datos_banda_tarjeta = or.datos_banda_tarjeta.map(maskedObject(_)))

  def maskedObject(dmpr: DatosMedioPagoResource): DatosMedioPagoResource = dmpr.copy(nro_tarjeta= dmpr.nro_tarjeta.map(nro => maskedObject(nro.dropRight(4)) concat nro.takeRight(4)),
                   security_code = dmpr.security_code.map(maskedObject(_)),
                   expiration_month = dmpr.expiration_month.map(maskedObject(_)),
                   expiration_year = dmpr.expiration_year.map(maskedObject(_)))
  def maskedObject(str: String): String = str.map(x => 'X')

  def maskedObject(dbt: DatosBandaTarjeta): DatosBandaTarjeta = {
    val card_track_1 = dbt.card_track_1.getOrElse("") match { case cn: String => maskedObject(cn) }
    val card_track_2 = dbt.card_track_2.getOrElse("") match { case cn: String => maskedObject(cn) }
    val input_mode = dbt.input_mode match { case cn: String => maskedObject(cn) }
    DatosBandaTarjeta(Some(card_track_1), Some(card_track_2), input_mode)
  }

  /**
   * Creacion de la Operation
   */
  def post() = Action(BodyParsers.parse.json) { implicit request =>
    loadMDC()
    
    var ini = System.currentTimeMillis()
    val operationValidation = request.body.validate[OperationResource]
    metrics.recordInMillis("", appId, "post", "validate-json", System.currentTimeMillis-ini)
    
    operationValidation.fold(
      errors => {
        val jsonErrors = JsError.toJson(errors)
        logger.error("bad request " + jsonErrors)
        BadRequest(Json.obj("status" ->"KO", "message" -> jsonErrors))
      },
      operation => {
          val referer = operation.datos_site.flatMap {_.referer}.getOrElse("NO_REFERER")
          loadMDC(siteId = Some(operation.siteId),
              referer = Some(referer), 
              merchantTransactionId = operation.nro_operacion)   
           
          logger.info("createToken for body " + Json.toJson(maskedObject(operation)))
          
          ini = System.currentTimeMillis()
          val res = operacionService.createOperation(operation)
          res match {
            
            case Success(response) => {
              metrics.recordInMillis(res.get.id, appId, "post", "createOperation", System.currentTimeMillis()-ini)
              logger.info("Token created Success")
              val json = Json.toJson(response)
            	Ok(json)
            }
            case Failure(e: ApiException) => {
              logger.warn(e.error.toString(), e)
              BadRequest(e.error.toJson)
            }
            case Failure(e: Throwable) => {
              logger.error("Throwable en post", e)  
            	InternalServerError(Json.obj("error" -> e.getMessage))
            }
          } 
      })     
  }

  def put(txId: String) = Action(BodyParsers.parse.json) { implicit request =>
    loadMDC(transactionId = Some(txId))
    
    var ini = System.currentTimeMillis()
    val operationValidation = request.body.validate[OperationResource]
    metrics.recordInMillis("", appId, "put", "validate-json", System.currentTimeMillis-ini)
    ini = System.currentTimeMillis()
    
    operationValidation.fold(
      errors => {
        val jsonErrors = JsError.toJson(errors)
        logger.error("Put data for Payment: Bad request " + jsonErrors)
        BadRequest(Json.obj("status" ->"KO", "message" -> jsonErrors))
      },
      operation => {
        updateMDCFromOperation(operation)
        
        ini = System.nanoTime()
        logger.info(s"Put data for Payment with body:" + Json.toJson(maskedObject(operation)))
        operacionService.updateOperation(operation.copy(id = txId)) match {
          case Success(operation) => {
            logger.debug("Put data for Payment Success")
            metrics.recordInMillis(operation.id, appId, "put", "updateOperation", System.currentTimeMillis-ini)
        	  Ok(Json.toJson(operation))
          }
          case Failure(ae: ApiException) =>{
            val json = Json.toJson(ae.error)
            logger.warn("ApiException: " + json)
            BadRequest(json)
          }
          case Failure(e: Throwable) => {
            logger.error("Put data for Payment failure: ", e)  
        	  InternalServerError(ErrorFactory.wrap(e).error.toJson)
          }
        }})
  }

  // TODO Borrar el token
  def procesar(txId: String) = Action.async {
    
    loadMDC(transactionId = Some(txId))
    logger.debug("Process data for Payment")
    val ini = System.currentTimeMillis()
    
    paymentsService.process(txId) map {pr =>
      
      metrics.recordInMillis(txId, appId, "put", "procesar", System.currentTimeMillis-ini)

      // TODO todo esto tiene que cambiar
      pr match {
        case Success(oer: OperationExecutionResponse) => {
          oer.operationResource.foreach(or => updateMDCFromOperation(or))
          oer.authorized match {
            case true => {
              logger.info("Process authorized Success")
              Ok(Json.toJson(oer))
            }
            case _ => {
              logger.error("Process not authorized Success")
              PaymentRequired(Json.toJson(oer))
            }
          }
        }
        
        case Failure(ProtocolError(authCode, cardErrorCode)) => {
          val error = OperationExecutionResponse(400, authCode, cardErrorCode, false, None, None)
          val json = Json.toJson(error)         
          logger.error("ProtocolError : " , json)
          Ok(json)
        }
        
        case Failure(ApiException(error)) => {
          val json = Json.toJson(error)         
          logger.error("ProtocolError : " , json)
          PaymentRequired(json)
        }
        
        case Failure(exception) => {
          logger.error("Excepcion al procesar pago", exception)
          val error = OperationExecutionResponse(500, "exception", Some(ProcessingError()), false, None, None)
          val json = Json.toJson(error) 
          InternalServerError(json)
        }
        
      }
    }
  }

  def offline(txId: String) = Action {

    loadMDC(transactionId = Some(txId))
    logger.info("Process date for offline Payment")
    val ini = System.currentTimeMillis()

    operacionService.processOffline(txId) match {
        case Success(oer: OperationExecutionResponse) => {
          oer.operationResource.foreach(or => updateMDCFromOperation(or))
          oer.authorized match {
            case true => {
              logger.info("Successful offline process")
              Ok(Json.toJson(oer))
            }
            case _ => {
              logger.error("Failed offline process")
              PaymentRequired(Json.toJson(oer))
            }
          }
        }

        case Failure(ProtocolError(authCode, cardErrorCode)) => {
          val error = OperationExecutionResponse(400, authCode, cardErrorCode, false, None, None)
          val json = Json.toJson(error)
          logger.error("ProtocolError : " , json)
          Ok(json)
        }

        case Failure(ApiException(error)) => {
          val json = Json.toJson(error)
          logger.error("ProtocolError : " , json)
          PaymentRequired(json)
        }

        case Failure(exception) => {
          logger.error("Excepcion al procesar pago offline", exception)
          val error = OperationExecutionResponse(500, "exception", Some(ProcessingError()), false, None, None)
          val json = Json.toJson(error)
          InternalServerError(json)
        }
      }
  }
}

