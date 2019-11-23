package services.protocol

import javax.inject.Inject
import com.decidir.coretx.domain.{Green, IdComercioLocationRepository, InProcess, OperationResourceRepository}
import com.decidir.protocol.api._
import controllers.MDCHelperTrait
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}
import scala.util.Failure
import com.decidir.coretx.domain.Yellow
import scala.util.Success
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.ErrorMessage
import com.decidir.coretx.messaging.KafkaConfigurationProvider



/**
 * @author martinpaoletta
 */
class ProtocolService  @Inject() (implicit context: ExecutionContext, 
                                  configuration: Configuration, 
                                  ws: WSClient, 
//                                  nroIdRepository: IdComercioLocationRepository, 
                                  operationRepository: OperationResourceRepository,
                                  kafkaConfigurationProvider: KafkaConfigurationProvider) extends MDCHelperTrait {
  
  val protocolHandlers = {
   
    val oProtocolsConf = configuration.getConfig("sps.protocols")
    
    val oVisaProtocol = oProtocolsConf.flatMap(_.getConfig("visa")).map{ new DefaultHandler(_, context, ws, "http://localhost:9003", kafkaConfigurationProvider) }

    val oMastercardProtocol = oProtocolsConf.flatMap(_.getConfig("mastercard")).map{ new DefaultHandler(_, context, ws, "http://localhost:9003", kafkaConfigurationProvider) }

    val oPMCProtocol = oProtocolsConf.flatMap(_.getConfig("pmc")).map{ new DefaultHandler(_, context, ws, "http://localhost:9003", kafkaConfigurationProvider) }

    var protoHandlers = Map[List[Int], ProtocolHandlerTrait]()
    protoHandlers = oVisaProtocol map {ph => protoHandlers + (ph.protocolId -> ph)} getOrElse (protoHandlers)
    protoHandlers = oMastercardProtocol map {ph => protoHandlers + (ph.protocolId -> ph)} getOrElse (protoHandlers)
    protoHandlers = oPMCProtocol map {ph => protoHandlers + (ph.protocolId -> ph)} getOrElse (protoHandlers)

    protoHandlers.keys.foreach(name => logger.info("Configurada integracion con protocolo " + name))
    
    protoHandlers
    
  }

  private def resolveAndCallOperation(protocolCall: ProtocolResource, call: (ProtocolHandlerTrait, ProtocolResource) => Future[Try[OperationResponse]]): Future[Try[OperationResponse]] = {
    val idProtocolo = protocolCall.ids.id_protocolo
    val handler = protocolHandlers.find(_._1.contains(idProtocolo)).getOrElse(throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.PROTOCOL_ID))
    
    transitionToSent2Protocol(protocolCall)
    val futureResponse = call(handler._2, protocolCall)
    futureResponse.foreach(transitionToReceivedFromOperation(protocolCall, _))
    futureResponse
  }
  
  private def resolverAndCallReverse(protocolCall: ProtocolResource): Future[Try[OperationResponse]] = {
    val idProtocolo = protocolCall.ids.id_protocolo
    val handler = protocolHandlers.find(_._1.contains(idProtocolo)).getOrElse(throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.PROTOCOL_ID))
    handler._2.reverse(protocolCall)
  }
  
  private def resolveAndCallTransaction(protocolCall: ProtocolResource, call: (ProtocolHandlerTrait, ProtocolResource) => Future[Try[TransactionResponse]]): Future[Try[TransactionResponse]] = {
    val idProtocolo = protocolCall.ids.id_protocolo
    val handler = protocolHandlers.find(_._1.contains(idProtocolo)).getOrElse(throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.PROTOCOL_ID))
    transitionToSent2Protocol(protocolCall)
    val futureResponse = call(handler._2, protocolCall)
    futureResponse.foreach(transitionToReceivedFromTransaction(protocolCall, _))
    futureResponse
  }
	
  def postTx(protocolCall: ProtocolResource): Future[Try[TransactionResponse]] = {
    val action = "postTx"
    val ini = System.currentTimeMillis
    resolveAndCallTransaction(protocolCall, (handler, protoCall) => handler.postTx(protoCall))
  }

  private def transitionToSent2Protocol(protocolCall: ProtocolResource) = {
    operationRepository.transitionOperation(protocolCall.id_operacion, InProcess(Green()))
  }
  
  private def transitionToReceivedFromOperation(protocolCall: ProtocolResource, response: Try[OperationResponse]) = {
    response match {
      case Success(OperationResponse(200,_ ,_ ,_ ,_ ,_ ,_ ,_ , _, _, _, _)) => {
        operationRepository.transitionOperation(protocolCall.id_operacion, Green())
      }
      case Success(OperationResponse(other, _, _ ,_ ,_ ,_ ,_ ,_ , _, _, _, _)) => {
        operationRepository.transitionOperation(protocolCall.id_operacion, Yellow())
      }
      case Failure(exception) => {
        logger.error("Error tratando respuesta de invocacion a protocolo", exception) 
        // TODO manejar bien el error en la transicion
        ErrorFactory.uncategorizedException(exception)
      }
    }
  }
  
    private def transitionToReceivedFromTransaction(protocolCall: ProtocolResource, response: Try[TransactionResponse]) = {
    response match {
      case Success(TransactionResponse(200, _, _, _, _, _, _, _, _,_, _, _)) => {
        operationRepository.transitionOperation(protocolCall.id_operacion, Green())
      }
      case Success(TransactionResponse(other, _, _, _, _, _, _, _, _,_, _, _)) => {
        operationRepository.transitionOperation(protocolCall.id_operacion, Yellow()) // TODO Revisar
      }
      case Failure(exception) => {
        logger.error("Error tratando respuesta de invocacion a protocolo", exception) 
        // TODO manejar bien el error en la transicion
        ErrorFactory.uncategorizedException(exception)
      }
    }
  }

  def cancel(protocolCall: ProtocolResource, withTerminalHeld: Option[Boolean] = None): Future[Try[OperationResponse]] = {
    val action = "cancel"
    val ini = System.currentTimeMillis
    resolveAndCallOperation(protocolCall, (handler, protoCall) => handler.cancel(protoCall, withTerminalHeld.getOrElse(false)))
  }

  def cancelPreApproved(protocolCall: ProtocolResource): Future[Try[OperationResponse]] = {
    val action = "cancelPreApproved"
    val ini = System.currentTimeMillis
    resolveAndCallOperation(protocolCall, (handler, protoCall) => handler.cancelPreApproved(protoCall))
  }

  def refund(protocolCall: ProtocolResource): Future[Try[OperationResponse]] = {
    val action = "refund"
    val ini = System.currentTimeMillis
    resolveAndCallOperation(protocolCall, (handler, protoCall) => handler.refund(protoCall))
  }
  
  def refundAfter(protocolCall: ProtocolResource): Future[Try[OperationResponse]] = {
    val action = "refund"
    val ini = System.currentTimeMillis
    resolveAndCallOperation(protocolCall, (handler, protoCall) => handler.refundAfter(protoCall))
  }
  
  def reverse(protocolCall: ProtocolResource): Future[Try[OperationResponse]] = {
    val action = "reverse"
    val ini = System.currentTimeMillis
    resolverAndCallReverse(protocolCall)
  }
  
  def cancelRefundBeforeClosure(protocolCall: ProtocolResource): Future[Try[OperationResponse]] = {
    val action = "cancelrefundbeforeclosure"
    val ini = System.currentTimeMillis
    resolveAndCallOperation(protocolCall, (handler, protoCall) => handler.cancelRefundBeforeClosure(protoCall))
  }
  
  def cancelRefundAfterClosure(protocolCall: ProtocolResource): Future[Try[OperationResponse]] = {
    val action = "cancelrefundafterclosure"
    val ini = System.currentTimeMillis
    resolveAndCallOperation(protocolCall, (handler, protoCall) => handler.cancelRefundAfterClosure(protoCall))
  }
  
  def paymentConfirm(protocolCall: ProtocolResource): Future[Try[OperationResponse]] = {
    val action = "confirm"
    val ini = System.currentTimeMillis
    resolveAndCallOperation(protocolCall, (handler, protoCall) => handler.paymentConfirm(protoCall))
  }
  
}


