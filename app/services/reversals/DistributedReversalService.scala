package services.reversals
/*
import scala.concurrent.ExecutionContext
import controllers.MDCHelperTrait
import scala.concurrent.duration.DurationInt
import javax.inject.Inject
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import com.decidir.coretx.api._
import com.decidir.coretx.domain._
import scala.concurrent.Future
import services.converters.OperationResourceConverter
import com.decidir.protocol.api.{OperationResponse, TransactionResponse}
import services.protocol.{Operation2ProtocolConverter, ProtocolService}
import services.payments.{LegacyTransactionServiceClient, LegacyOperationServiceClient, UpdateTx}
import services.DOPXResponse
import services.DOPX
import akka.util.Timeout
import services.DOPXHandlerActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import services.DOPXs
import com.decidir.coretx.domain.SiteRepository
import com.decidir.coretx.api._
import decidir.sps.core.Motivo
import decidir.sps.core.Protocolos
import services.payments.{UpdateReverse, InsertDOPx}
import com.decidir.protocol.api.HistoricalStatus
import com.decidir.core.EstadoOperacionSPS
import java.util.Date

class DistributedReversalService @Inject() (
    context: ExecutionContext,
    operationResourceConverter: OperationResourceConverter,
    protocolService: ProtocolService,
    actorSystem: ActorSystem,
    transactionRepository: TransactionRepository,
    legacyTxService: LegacyTransactionServiceClient,
    legacyOpService: LegacyOperationServiceClient,
    siteRepository: SiteRepository) extends MDCHelperTrait {
  
  implicit val ec = context
  
  def reverseTx(oer: OperationExecutionResponse, chargeId: Long): Future[ReversePaymentResponse] = {
    logger.info(s"Reverse transaction with chargeId: ${chargeId}")
    val operation = oer.operationResource.getOrElse(throw new Exception("operationResource not available in operationExecuteResource"))
    val opData = operationResourceConverter.operationResource2OperationData(operation)
    val amount = opData.resource.monto.getOrElse(throw new Exception("amount not available in operationExecuteResource"))
    prepareDistributedOperations(opData) match {
      case dOPXs: List[DOPX] => {
        reversTx(dOPXs) map { responses =>
          val (subTransactions, reverseSubPaymentResponses) = handleResponse(opData, responses, chargeId).unzip
          val opDataFixed = opData.copy(resource = opData.resource.copy(sub_transactions = 
            opData.resource.sub_transactions.map(st => getSubTransactionFixed(st, subTransactions))
          ))
          val allReversed = (subTransactions.size == reverseSubPaymentResponses.filter { reversed => reversed.status == Autorizada() }.size)
          val status = allReversed match {
            case true => Rechazada()
            case false => TransactionState.apply(oer.status)
          }
          handleResponse(opDataFixed, amount, reverseSubPaymentResponses, status, allReversed)
        }
      }
      case Nil => {
        logger.warn("has not subpayments to revers")
        Future(handleResponse(opData, amount, Nil, TransactionState.apply(oer.status), false))
      }
    }
  }
  
  private def handleResponse(opData: OperationData, amount: Long, subTransReversed: List[ReverseSubPaymentResponse], status: TransactionState, allReversed: Boolean): ReversePaymentResponse = {
    subTransReversed.find {reversed => reversed.status == Autorizada() }.map { _ => 
      legacyTxService.update(UpdateReverse(opData, Some("F"), opData.resource.idTransaccion, status)) 
    }
    val reverseStatus = allReversed match {
      case true => Autorizada()
      case false => Rechazada()
    }
    ReversePaymentResponse(amount = amount, status = reverseStatus, sub_payments = Some(subTransReversed))
  }
  
  private def getSubTransactionFixed(subTransaction: SubTransaction, subTransactionsFixed: List[SubTransaction]): SubTransaction = {
    subTransactionsFixed.find { sTFixed => sTFixed.site_id == subTransaction.site_id}.getOrElse(subTransaction)
  }
  
  private def handleResponse(operationData: OperationData, responses: List[DOPXResponse], chargeId: Long): List[(SubTransaction, ReverseSubPaymentResponse)] = {
		loadMDCFromOperation(operationData.resource)
    val isMascercad = operationData.cuenta.idProtocolo.equals(Protocolos.codigoProtocoloMastercard)
    responses.map { result =>
      result match {
        case DOPXResponse(request,operation,Success(response)) => {
          handleSuccess(isMascercad, request, response, chargeId)
        }
        case DOPXResponse(request,operation,Failure(ProtocolError(authCode, reason))) => {
	        logger.error(s"reverse failure: ProtocolError(${authCode}, ${reason})")
          handleFailure(request)
        }
        case DOPXResponse(request,operation,Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param)))))) => {
          logger.error(s"reverse failure: InvalidRequestError")
          handleFailure(request)
        }
      }
    }
  }
  
  private def handleSuccess(isMascercad: Boolean, request: OperationData, response: OperationResponse, chargeId: Long):(SubTransaction, ReverseSubPaymentResponse) = {
	  val subTx = request.resource.sub_transactions.find { sub => sub.site_id == request.site.id }.getOrElse{
      logger.error(s"error al obtener subtransacciones ${request.resource.sub_transactions}")
      throw new Exception("error al obtener la subtransaccion, esto no deberia pasar.")
    }
	  val subpaymentId = subTx.subpayment_id.getOrElse(throw new Exception("error al obtener la subpaymentId"))
	  val masterNotExisted = mastercardOriginalNotExisted(isMascercad, response)
    if (response.authorized || masterNotExisted){
      logger.info("reverse Success")
      //TODO: revisar donde obtiene el estado historico, y mejorarlo
      legacyOpService.insert(InsertDOPx(request, response.copy(historicalStatusList = List(HistoricalStatus(EstadoOperacionSPS.getEstadoReversada.getIdEstado,-1,new Date()))), subTx.subpayment_id.get, None, chargeId, None, None))
      legacyTxService.update(UpdateReverse(request, Some("C"), None, Rechazada()))
      (subTx.copy(status = Some(Rechazada().id)), ReverseSubPaymentResponse(amount = subTx.amount, site_id = subTx.site_id, id = subpaymentId, card_error_code = None, status = Autorizada(), reversed_in_brand = Some(!masterNotExisted)))
    } else {
      logger.info(s"reverse Failure, state: ${response}")
      (subTx, ReverseSubPaymentResponse(amount = subTx.amount, site_id = subTx.site_id, id = subpaymentId, card_error_code = response.cardErrorCode, status = Rechazada(), reversed_in_brand = Some(false)))
    }
  }
  
  private def mastercardOriginalNotExisted(isMascercad:Boolean, or: OperationResponse):Boolean = {
    (!or.authorized && isMascercad && or.idMotivo == Motivo.TRX_ORIGINAL_INEXISTENTE)
  }
  
  private def handleFailure(request: OperationData):(SubTransaction, ReverseSubPaymentResponse) = {
	  val subTx = request.resource.sub_transactions.find { sub => sub.site_id == request.site.id }.getOrElse{
      logger.error(s"error al obtener subtransacciones ${request.resource.sub_transactions}")
      throw new Exception("error al obtener la subtransaccion, esto no deberia pasar.")
    }
	  val subpaymentId = subTx.subpayment_id.getOrElse(throw new Exception("error al obtener la subpaymentId"))
    (subTx, ReverseSubPaymentResponse(amount = subTx.amount, site_id = subTx.site_id, id = subpaymentId, card_error_code = Some(ProcessingError()), status = Rechazada()))
  }
  
  private def reversTx(requests: List[DOPX]): Future[List[DOPXResponse]] = {
    implicit val timeout = Timeout(60 seconds)
    (actorSystem.actorOf(Props(new DOPXHandlerActor(protocolService))) ? DOPXs(requests)).mapTo[List[DOPXResponse]]
  }

  private def prepareDistributedOperations(operationData: OperationData): List[DOPX] = {
    operationData.resource.sub_transactions.map{ subPayment => {
      val subPaymentId = subPayment.subpayment_id.getOrElse(throw new Exception("subpayment_id not available in subPayment"))
      TransactionState.apply(transactionRepository.retrieveSubPaymentState(subPaymentId).toInt) match {
        case AReversar() => {
          logger.debug(s"subpayment to revers ${subPaymentId}")
        	Some(getDistributedOperation(subPaymentId, operationData))          
        }
        case other => None
      }
    }}.flatten
  }
  
  private def getDistributedOperation(subPaymentId: Long, operationData:OperationData):DOPX = {
    val subTx = getSubTransaction(operationData.resource, subPaymentId)
    val subSite = siteRepository.retrieve(subTx.site_id).getOrElse(throw new Exception("site not available"))
    val resource = operationData.resource.copy(datos_medio_pago = operationData.resource.datos_medio_pago.map( dmp => dmp.copy(nro_trace = subTx.nro_trace, nro_ticket = subTx.nro_ticket, nro_terminal = subTx.terminal, id_operacion_medio_pago = subTx.id_operacion_medio_pago)))
    val fixedOpData = operationData.copy(resource = resource,site = subSite).replaceMonto(subTx.amount).replaceChargeId(Some(subPaymentId)).replaceCuotas(subTx.installments)
    DOPX(fixedOpData, ReverseOperationPostPayment(), subPaymentId)
  }
  
  private def getSubTransaction(op:OperationResource, id:Long) = {
    val chargeId = op.charge_id.getOrElse(throw new Exception("chargeId not available from OperationResource"))
	  op.sub_transactions.find { osubTx => osubTx.subpayment_id.getOrElse(throw new Exception("subpayment_id not available")) == id } match {
  	  case Some(subTx) => subTx
  	  case None => {
  		  logger.error(s"Subpayment: ${id} is not part of Payment: ${chargeId}")
  		  throw ErrorFactory.validationException("subpayment_id",s"Subpayment: ${id} is not part of Payment: ${chargeId}") 
  	  }
	  }
  }
}*/