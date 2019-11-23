package services.payments

import javax.inject.{Inject, Singleton}
import akka.actor.Actor
import com.decidir.coretx.api._
import com.decidir.coretx.domain.{Cuenta, NrosTraceSite, OperationData, Site, TransactionRepository}
import com.decidir.protocol.api.{OperationResponse, TransactionResponse}
import play.api.Logger
import services.metrics.MetricsClient
import com.decidir.coretx.domain.OperationRepository
import scala.util.{Failure, Success, Try}
import com.decidir.util.RefundsLockRepository
import play.api.Configuration
import controllers.MDCHelperTrait

@Singleton
class LegacyTransactionService @Inject() (operationRepository: OperationRepository,
                                          transactionRepository: TransactionRepository,
                                          metrics: MetricsClient,
                                          paymentNotification: NotifyPayment,
                                           refundsLockRepository: RefundsLockRepository,
                                          configuration: Configuration) extends MDCHelperTrait {
  
  
  val refundLockAllowed = configuration.getBoolean("lock.refunds.allowed").getOrElse(false)
  
  //Desbloquea las operacion para este chargeId hasta que sea persisitida en la base (tiene TTL)
  def releaseRefundsLock(chargeId: Long) = {
    if (refundLockAllowed) {
      refundsLockRepository.releaseLock(chargeId.toString) match {
        case Failure(error) => logger.error(s"Cannot release refundsLock for chargeId: $chargeId", error)
        case Success(released) => logger.debug(s"Success refundsLockRepository.releaseLock($chargeId) = $released")
      }
    }
  }
  
  def handle(msg: LegacyDBCommandTrait) = msg match {
    case InsertTx(
        chargeId, 
        site, 
        distribuida, 
        estadoFinal, 
        oer @ OperationExecutionResponse(_, _, _, _, _, Some(op), _, _)
        ) => insertTx(chargeId, op, site, distribuida, estadoFinal, oer)
    case UpdateTx(chargeId, 
        site,
        distribuida, 
        estadoFinal, 
        oer @ OperationExecutionResponse(_, _, _, _, _, Some(op), _, _),
        respuesta) => updateTx(chargeId, op, site, distribuida, estadoFinal, oer, respuesta)
    case InsertDistributedTx(chargeId, parent, subtxs, estadoFinal) => insertDistributedTx(chargeId, parent, subtxs, estadoFinal)
    case UpdateTxOnOperation(opData, estado, distribuida, result, idTransaccion, user) => updateTxAfterOperation(opData, estado, distribuida, result, idTransaccion, user)
    case InsertCS(operationResource,csResponse) => insertCS(operationResource,csResponse)
    case UpdateCS(operationExecutionResponse,csResponse, annulmentPending) => updateCS(operationExecutionResponse,csResponse, annulmentPending)
    case InsertTxHistorico(oidTransaccion, oChargeId, protocolId, estado, distribuida, changeStateDate, reasonCode, mTransactionId) => insertTxHistorico(oidTransaccion, oChargeId, protocolId, estado, distribuida, changeStateDate, reasonCode)
    case UpdateXref(chargeId,oer) => updateXref(chargeId,oer)
    case UpdateDistributedTx(chargeId, parent, subtxs, estadoFinal) => updateDistributedTx(chargeId, parent, subtxs, estadoFinal)
    case InsertOpx(op, respuesta, oidTransaccion, refundId, cancelId, user) => insertOpx(op, respuesta, oidTransaccion, refundId, cancelId, user)
    case InsertDOPx(opData, respuesta, subpaymentId, refundId, chargeId, cancelId, user) => insertDOPx(opData, respuesta, subpaymentId, refundId, chargeId, cancelId, user)
    case InsertConfirmationOpx(opData, respuesta, confirmationPaymentResponse, user) => insertConfirmationOpx(opData, respuesta, confirmationPaymentResponse, user)
    case TransactionStateMessage(chargeId, transactionState, notify, merchantTransactionId) => updateTxStateAndNotify(chargeId, transactionState, notify)
//    case UpdateReverse(opData, distribuida, oidTransaccion, state) => updateReverse(opData, distribuida, oidTransaccion, state)
  }
  
  def insertOpx(op: OperationData,  respuesta: OperationResponse, oidTransaccion:Option[String], refundId:Option[Long], cancelId:Option[Long], user: Option[String] = None) = {
    operationRepository.insertNewOperation(op, respuesta, oidTransaccion, refundId, cancelId, user)
  }
   
  def insertDOPx(opData:OperationData, respuesta: OperationResponse, subpaymentId:Long, refundId:Option[Long], chargeId:Long, cancelId:Option[Long], user: Option[String] = None) = {
    operationRepository.insertNewDistributedOperation(opData,respuesta,subpaymentId,refundId,chargeId,cancelId, user)
  }
  
  def updateXref(chargeId: Long, oer: OperationExecutionResponse) = {
    transactionRepository.updateCSResponseInXref(chargeId, oer)  
  }
  
  def updateTxAfterOperation(opData:OperationData, estado:TransactionState, distribuida: Option[String], result:OperationResponse,idTransaccion:Option[String], user: Option[String] = None) = {
    transactionRepository.updateTransaccionOperacion(opData, estado, distribuida, result,  idTransaccion, user)
    releaseRefundsLock(opData.chargeId)
  }
  
  def insertCS(resource: OperationResource,csResponse:CyberSourceResponse) = {
    transactionRepository.insertTransCybersource(resource, csResponse)
  }
  
  def updateCS(oer: OperationExecutionResponse, csResponse:CyberSourceResponse, annulmentPending: Boolean) = {
    transactionRepository.updateTransCybersource(oer, csResponse, annulmentPending)
  }
  
  def insertTx(chargeId: Long, op: OperationResource, site: Site, distribuida: Option[String], estadoFinal: TransactionState, oer: OperationExecutionResponse) = {
        val ini = System.currentTimeMillis    
    val num = transactionRepository.insertTransaccionBasico(chargeId, op, site, distribuida, TransactionFSM.estadosPara(estadoFinal).map(_.id), oer)
    metrics.recordInMillis(op.id, "coretx", "LegacyTransactionServiceActor", "insertTx", System.currentTimeMillis()-ini)
    num
  }
  
  def updateTx(chargeId: Long, op: OperationResource, site: Site, distribuida: Option[String], estadoFinal: TransactionState, oer: OperationExecutionResponse, respuesta: Option[TransactionResponse]) = {
    val ini = System.currentTimeMillis    
    transactionRepository.updateMainTransaccion(chargeId: Long, op, site, distribuida, TransactionFSM.estadosPara(estadoFinal).map(_.id), oer, respuesta)
    metrics.recordInMillis(op.id, "coretx", "LegacyTransactionServiceActor", "updateTx", System.currentTimeMillis()-ini)
  }
  
  private def updateSubTx(chargeId: Long, op: OperationResource, site: Site, distribuida: Option[String], estadoFinal: TransactionState, oer: OperationExecutionResponse, respuesta: Option[TransactionResponse]) = {
    val ini = System.currentTimeMillis    
    transactionRepository.updateSubTransaccion(chargeId: Long, op, site, distribuida, TransactionFSM.estadosPara(estadoFinal).map(_.id), oer, respuesta)
    metrics.recordInMillis(op.id, "coretx", "LegacyTransactionServiceActor", "updateSubTx", System.currentTimeMillis()-ini)
  }
  
  def updateDistributedTx(chargeId: Long, parent: UpdateTx, subtxs: List[DistributedTxElement], estadoFinal: TransactionState) = {
      val ini = System.currentTimeMillis   
      val oerInstallmentsFixed = fixInstallmentDistributed(parent.oer, subtxs)
      val parentId = updateTx(chargeId, oerInstallmentsFixed.operationResource.get, parent.site,
          parent.distribuida, parent.estadoFinal, oerInstallmentsFixed, parent.transactionResponse)
      subtxs.foreach { child => {
    	  var subTxChargeId = child.operation.charge_id.getOrElse(throw new Exception("No se definio charge_id en SubTransacciones"))
    	  val subTransaction = parent.oer.operationResource.flatMap {or => or.sub_transactions.find { st => st.site_id.equals(child.site.id) }}
    	  val state = subTransaction.flatMap(st => st.status.map(s => TransactionState.apply(s))).getOrElse(estadoFinal)
    	  val subTxId = updateSubTx(subTxChargeId, child.operation, child.site, Some("C"), state, parent.oer, child.transactionResponse) 
      }}
  }
  
  def insertTxHistorico(oidTransaccion:Option[Long], oChargeId:Option[Long], protocolId: Int, estado:Int, distribuida:Option[String], changeStateDate: Option[Long], reasonCode: Option[Int]) = {
    transactionRepository.doInsertEstadoHistorico(oidTransaccion, oChargeId, protocolId, estado, distribuida, changeStateDate, reasonCode)
  }

  def insertDistributedTx(chargeId: Long, parent: InsertTx, subtxs: List[DistributedTxElement], estadoFinal: TransactionState) = {
      val ini = System.currentTimeMillis    
      val oerInstallmentsFixed = fixInstallmentDistributed(parent.oer, subtxs)
      
      transactionRepository.insertDistributedTx(chargeId, oerInstallmentsFixed, parent, subtxs, estadoFinal)      
  }
  
  private def fixInstallmentDistributed(oer: OperationExecutionResponse, subtxs: List[DistributedTxElement]): OperationExecutionResponse = {
      //Distribuidas por monto termina guardando en el campo cuotas, una de las cuotas de los subpayments, asi se visualiza en el sac, pedido por cliente.
      val installments = oer.operationResource.map(_.cuotas.getOrElse(subtxs.lift(0).map(_.cuotas).get))
      oer.copy(operationResource = oer.operationResource.map(_.copy(cuotas = installments)))
  }
  
  def updateNroTrace(nrosTraceSite:NrosTraceSite) = {
    Logger.debug(s"nroTicket = ${nrosTraceSite.nroTicket}, nrTrace = ${nrosTraceSite.nroTrace}, nroBatch = ${nrosTraceSite.nroBatch}" )
    
    (nrosTraceSite.nroTicket, nrosTraceSite.nroTrace, nrosTraceSite.nroBatch) match {
      case (_, -1, -1) => transactionRepository.updateNroTicket(nrosTraceSite)
      case (-1, _, -1) => transactionRepository.updateNroTrace(nrosTraceSite)
      case (-1, -1, _)  => transactionRepository.updateNroBatch(nrosTraceSite)
    }
  }
  
  def insertConfirmationOpx(opData:OperationData,  respuesta: OperationResponse, confirmationPaymentResponse: ConfirmPaymentResponse, user: Option[String] = None) = {
    operationRepository.insertNewPaymentConfirmation(opData,respuesta,confirmationPaymentResponse, user)
  }

  def updateTxStateAndNotify(chargeId: Long, transactionState: TransactionState, notify: Boolean = false) = {
    transactionRepository.updateSimpleState(chargeId, transactionState)
    if(notify){
      val siteId = transactionRepository.retrieveSiteIdFromChargeId(chargeId)
      val toer = transactionRepository.retrieveCharge(siteId, chargeId)
      toer match {
        case Success(oer) => paymentNotification.sendUpdate(oer)
        case Failure(e) => throw e
      }
    }
  }

 /* def updateReverse(opData:OperationData, distribuida: Option[String], oidTransaccion: Option[String], state:TransactionState) = {
    transactionRepository.updateReverse(opData, distribuida, oidTransaccion, state)
  }*/
}

