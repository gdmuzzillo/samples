package services.protocol

import com.decidir.protocol.api._
import scala.concurrent.Future
import scala.util.Try
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import play.api.libs.ws.WSResponse
import scala.util.Failure
import scala.util.Success
import play.api.libs.json.Json
import controllers.MDCHelperTrait
import play.api.libs.ws.WSClient
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import play.api.Configuration
import scala.concurrent.duration._
import com.decidir.coretx.domain.ProcessingError
import com.decidir.coretx.messaging.KafkaConfigurationProvider
import com.decidir.coretx.messaging.MessagingProducer
import com.decidir.coretx.messaging.RecordWithId
import com.decidir.coretx.messaging.MessagingRecord
import com.decidir.protocol.api.ProtocolResourceJsonFormats._
import collection.JavaConverters._
import com.decidir.coretx.domain.ProtocolError
import com.decidir.coretx.domain.ProtocolError
import com.decidir.coretx.domain.ProtocolError

trait ProtocolHandlerTrait extends MDCHelperTrait {

  def protocolId: List[Int]
  protected def protocolUrl: String
  protected def timeout: Duration
  
  protected def protocolReversalsTopic: String
  protected def messagingProducer: MessagingProducer[String, String]  
  protected def ws: WSClient
  protected def executionContext: ExecutionContext
  implicit val ec = executionContext
  
  
  /**
   * Hook para modificar la llamada
   */
  protected def prepare(call: ProtocolResource): ProtocolResource = call
  /**
   * Hook para modificar la respuesta
   */
  protected def postProcessTx(response: Try[TransactionResponse]): Try[TransactionResponse] = response
  
  protected def postProcessOpx(response: Try[OperationResponse]): Try[OperationResponse] = response
  
  
  def postTx(call: ProtocolResource): Future[Try[TransactionResponse]] = { // TODO
  
    val protocolCall = prepare(call)
    
    val json = Json.toJson(protocolCall)
    logger.debug(s"ProtocolService.post " + json)
    val url = protocolUrl + "/protocol"
    handleResponseTx(ws.url(url).withRequestTimeout(timeout).post(json)).map(postProcessTx)
  }
  
   private def handleResponseTx(futureResponse: Future[WSResponse]): Future[Try[TransactionResponse]] = {
    implicit val ec = executionContext
    futureResponse.map { response: WSResponse => 
      logger.debug("handleResponse " + response)
      
      val protoResponse = response.json.validate[TransactionResponse] match {
        case e: JsError => Failure(ProtocolError(ProtocolResponseCodes.transportFailure, Some(ProcessingError())))
        case JsSuccess(pr, _) => Success(pr)
      }
      
     protoResponse
      
    }.recover{case x =>
      logger.error("Error procesando respuesta de protocolo", x)
      // Lanzar reversa
      Failure(ProtocolError(ProtocolResponseCodes.transportFailure, Some(ProcessingError())))
    }
  }   
   
  private def handleResponse(futureResponse: Future[WSResponse]): Future[Try[OperationResponse]] = {
    implicit val ec = executionContext
//    storeMDC
    futureResponse.map{ response: WSResponse => 
//      restoreMDC
      logger.debug("handleResponse " + response)
      val json = response.json
      val protoResponse = json.validate[OperationResponse] match {
        case e: JsError => {
          logger.error("Error intentando armar OperationResponse a partir de " + json)          
        	Failure(ProtocolError(ProtocolResponseCodes.transportFailure, Some(ProcessingError())))
        }
        case JsSuccess(pr, _) => Success(pr)
      }
      
     protoResponse
      
    }.recover{case x =>
      logger.error("Error en manejo de respuesta de protocolo", x)
      Failure(ProtocolError(ProtocolResponseCodes.transportFailure, Some(ProcessingError())))
    }
  }
  
  def cancel(call: ProtocolResource, withTerminalHeld: Boolean): Future[Try[OperationResponse]] = { // TODO
    val protocolCall = prepare(call)
    val json = Json.toJson(protocolCall)
    logger.debug(s"ProtocolService.cancel " + json)
    var url = protocolUrl + "/cancel"
    if (withTerminalHeld) {
      url += "?" + "withTerminalHeld=true"
    }
    handleResponse(ws.url(url).withRequestTimeout(timeout).post(json)).map(postProcessOpx)
  }
  
  def cancelPreApproved(call: ProtocolResource): Future[Try[OperationResponse]] = { // TODO
    val protocolCall = prepare(call)
    val json = Json.toJson(protocolCall)
    logger.debug(s"ProtocolService.cancelpreapproved " + json)
    val url = protocolUrl + "/cancelpreapproved"
    handleResponse(ws.url(url).withRequestTimeout(timeout).post(json)).map(postProcessOpx)
  }

  def refund(call: ProtocolResource): Future[Try[OperationResponse]] = { // TODO
    val protocolCall = prepare(call)
    val json = Json.toJson(protocolCall)
    logger.debug(s"ProtocolService.post " + json)
    val url = protocolUrl + "/refund"
    handleResponse(ws.url(url).withRequestTimeout(timeout).post(json)).map(postProcessOpx)
  }
  
  def refundAfter(call: ProtocolResource): Future[Try[OperationResponse]] = { // TODO
    val protocolCall = prepare(call)
    val json = Json.toJson(protocolCall)
    logger.debug(s"ProtocolService.refundAfter " + json)
    val url = protocolUrl + "/refundAfter"
    handleResponse(ws.url(url).withRequestTimeout(timeout).post(json)).map(postProcessOpx)
  }
  
  def reverse(call: ProtocolResource): Future[Try[OperationResponse]] = { // TODO
    val protocolCall = prepare(call)
    val json = Json.toJson(protocolCall)
//    messagingProducer.sendMessageToTopic(MessagingRecord(None, json.toString), protocolReversalsTopic)
    logger.debug(s"ProtocolService.reverse " + json)
    val url = protocolUrl + "/reverse"
    handleResponse(ws.url(url).withRequestTimeout(timeout).post(json)).map(postProcessOpx)
  }
  
  def cancelRefundBeforeClosure(call: ProtocolResource): Future[Try[OperationResponse]] = {
    val protocolCall = prepare(call)
    val json = Json.toJson(protocolCall)
    logger.debug(s"ProtocolService.cancelrefundbeforeclosure " + json)
    val url = protocolUrl + "/cancelrefundbeforeclosure"
    handleResponse(ws.url(url).withRequestTimeout(timeout).post(json)).map(postProcessOpx)
  }
    
  def cancelRefundAfterClosure(call: ProtocolResource): Future[Try[OperationResponse]] = { // TODO
    val protocolCall = prepare(call)
    val json = Json.toJson(protocolCall)
    logger.debug(s"ProtocolService.cancelrefundafterclosure " + json)
    val url = protocolUrl + "/cancelrefundafterclosure"
    handleResponse(ws.url(url).withRequestTimeout(timeout).post(json)).map(postProcessOpx)
  }
  
  def paymentConfirm(call: ProtocolResource): Future[Try[OperationResponse]] = {
    val protocolCall = prepare(call)
    val json = Json.toJson(protocolCall)
    logger.debug(s"ProtocolService.paymentconfirm " + json)
    val url = protocolUrl + "/paymentconfirm"
    handleResponse(ws.url(url).withRequestTimeout(timeout).post(json)).map(postProcessOpx)
  }
}


case class DefaultHandler(configuration: Configuration, executionContext: ExecutionContext, ws: WSClient, defaultUrl: String, kafkaConfigurationProvider: KafkaConfigurationProvider) extends ProtocolHandlerTrait {
  val protocolId = configuration.getIntList("id").map(_.asScala.map(_.toInt).toList).getOrElse(throw new Exception("No se configuro id de protocolo"))
  val protocolUrl =  configuration.getString("url").getOrElse(defaultUrl)  
  val timeout = configuration.getInt("timeoutMillis").getOrElse(300000) millis
  val messagingProducer = kafkaConfigurationProvider.defaultProducer
  val protocolReversalsTopic = configuration.getString("reversalsTopic").getOrElse(throw new Exception("No se definio sps.reversals.topic"))
}

