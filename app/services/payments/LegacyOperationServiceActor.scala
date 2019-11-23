package services.payments

import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.domain.OperationRepository
import com.decidir.protocol.api.OperationResponse
import akka.actor.Actor
import javax.inject.Inject
import javax.inject.Singleton
import services.metrics.MetricsClient
import com.decidir.coretx.api.{LegacyDBCommandTrait, ConfirmPaymentResponse}


@Singleton
class LegacyOperationService @Inject() (operationRepository: OperationRepository, metrics: MetricsClient) {
  def handle(msg: LegacyDBCommandTrait) = msg match {
    case InsertOpx(op, respuesta, oidTransaccion, refundId, cancelId, user) => insertOpx(op, respuesta, oidTransaccion, refundId, cancelId, user)
    case InsertDOPx(opData, respuesta, subpaymentId, refundId, chargeId, cancelId, user) => insertDOPx(opData, respuesta, subpaymentId, refundId, chargeId, cancelId, user)
    case InsertConfirmationOpx(opData, respuesta, confirmationPaymentResponse, user) => insertConfirmationOpx(opData, respuesta, confirmationPaymentResponse, user)
  }
  
  def insertOpx(op: OperationData,  respuesta: OperationResponse, oidTransaccion:Option[String], refundId:Option[Long], cancelId:Option[Long], user: Option[String] = None) = {
    operationRepository.insertNewOperation(op, respuesta, oidTransaccion, refundId, cancelId, user)
  }
   
  def insertDOPx(opData:OperationData, respuesta: OperationResponse, subpaymentId:Long, refundId:Option[Long], chargeId:Long, cancelId:Option[Long], user: Option[String] = None) = {
    operationRepository.insertNewDistributedOperation(opData,respuesta,subpaymentId,refundId,chargeId,cancelId, user)
  }
  
  def insertConfirmationOpx(opData:OperationData,  respuesta: OperationResponse, confirmationPaymentResponse: ConfirmPaymentResponse, user: Option[String] = None) = {
    operationRepository.insertNewPaymentConfirmation(opData,respuesta,confirmationPaymentResponse, user)
  }
    
}

class LegacyOperationServiceActor(operationRepository: OperationRepository) extends Actor {
  
  def receive = {
    case InsertOpx( op, respuesta, oidTransaccion, refundId, cancelId, user) => operationRepository.insertNewOperation(op, respuesta, oidTransaccion, refundId, cancelId, user)
    case InsertDOPx(opData, respuesta, subpaymentId, refundId, chargeId, cancelId, user) => operationRepository.insertNewDistributedOperation(opData, respuesta, subpaymentId, refundId, chargeId, cancelId, user)
    case InsertConfirmationOpx(opData, respuesta, confirmationPaymentResponse, user) => operationRepository.insertNewPaymentConfirmation(opData, respuesta, confirmationPaymentResponse, user)
  }
}

