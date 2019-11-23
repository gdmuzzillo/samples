package services.refunds

import java.util.Date

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import com.decidir.core.EstadoOperacionSPS
import com.decidir.coretx.api._
import com.decidir.coretx.domain.CancelOperation
import com.decidir.coretx.domain.Motivo
import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.domain.OperationResourceRepository
import com.decidir.coretx.domain.PartialRefundBeforeClosureOperation
import com.decidir.coretx.domain.PartialRefundOperation
import com.decidir.coretx.domain.RefundOperation
import com.decidir.coretx.domain.RefundSubPaymentOperation
import com.decidir.coretx.domain.SiteRepository
import com.decidir.protocol.api.HistoricalStatus
import com.decidir.protocol.api.OperationResponse
import controllers.MDCHelperTrait
import javax.inject.Inject

import services.payments._
import services.protocol.Operation2ProtocolConverter
import services.protocol.ProtocolService
import services.DOPXResponse
import services.DOPX
import com.decidir.coretx.domain.RefundOperation
import com.decidir.coretx.domain.PartialRefundOperation

import scala.collection.mutable.ArrayBuffer
import com.decidir.coretx.domain.PartialRefundBeforeClosureOperation
import com.decidir.coretx.domain.NonExistanceOperation
import com.decidir.coretx.domain.MeanPaymentErrorOperation
import com.decidir.coretx.domain.TransactionRepository
import services.payments.LegacyOperationServiceClient
import services.DOPXHandlerActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import com.decidir.coretx.domain.SubpaymentState
import com.decidir.coretx.domain.Operation
import com.decidir.coretx.domain.MeanPaymentErrorOperation
import com.decidir.coretx.domain.CancelOperationPostPayment
import com.decidir.coretx.domain.ReverseOperationPostPayment
import com.decidir.coretx.api.Rechazada

import scala.util.Try
import com.decidir.coretx.domain.ProtocolError
import com.decidir.coretx.domain.CancelOperationPostPaymentWithCs
import com.decidir.coretx.domain.ProcessingError
import services.DOPXs

import scala.concurrent.Future
import com.decidir.util.RefundsLockRepository
import play.api.Configuration

class DistributedOperationProcessor @Inject() (context: ExecutionContext, 
                                      actorSystem: ActorSystem, 
                                      protocolService: ProtocolService, 
                                      siteRepository: SiteRepository,
                                      legacyOpxService: LegacyOperationServiceClient,
                                      legacyTxService:LegacyTransactionServiceClient,
                                      operationRepository: OperationResourceRepository,
                                      transactionRepository: TransactionRepository,
                                      refundStateService: RefundStateService,
                                      refundsLockRepository: RefundsLockRepository,
                                      configuration: Configuration) extends MDCHelperTrait {
  implicit val ec = context

  val refundLockAllowed = configuration.getBoolean("lock.refunds.allowed").getOrElse(false)
  
//Bloquea las operacion para este chargeId hasta que sea persisitida en la base (tiene TTL)
  private def tryRefundsLock(chargeId: Long) = {
    if (refundLockAllowed) {
      refundsLockRepository.getLock(chargeId.toString) match {
        case Failure(error) => logger.error(s"Cannot get refundsLock for chargeId: $chargeId", error)
        case Success(adquired) => logger.debug(s"Success refundsLockRepository.getLock($chargeId) = $adquired")
      }
    }
  }
   def processDistributedOPx(chargeId: Long, fatherState: TransactionState, operationData: OperationData, refundPayment: Option[RefundPaymentRequest], 
       meanPayment: Int, oRefundSubPaymentOperation: Option[List[RefundSubPaymentOperation]] = None,  user: Option[String] = None) :  Future[(RefundPaymentResponse, Try[OperationResponse], TransactionState)] = {
	  
    loadMDCFromOperation(operationData.resource)
    val subPay = oRefundSubPaymentOperation //worksAsReverse
        .getOrElse(buildStateOfSubpayments(operationData, refundPayment, meanPayment, operationData.resource.datos_medio_pago.get.bin.get))
    
    val requests = prepareDistributedOperations(operationData, subPay, fatherState)
    
    refundDistributed(requests) map { responses =>
          
    val updatedSubPaymentsAndTransactions = handleResponse(operationData, responses, user)
      
    val cOperationPostPaymentWithCs = requests.find(dopx => dopx.operationType match {
       case CancelOperationPostPaymentWithCs() => true
       case _ => false
    }).map(dopx => true).getOrElse(false)
    
  	val (subPaymentsList, subTransactions, subTransactionState) = updatedSubPaymentsAndTransactions.unzip3
  	val updatedSubTransactions = operationData.resource.sub_transactions.map { subTx => 
  	   updatedSubPaymentsAndTransactions.find{ rSubPaymentResponseAndTransaction => subTx.subpayment_id == rSubPaymentResponseAndTransaction._2.subpayment_id && rSubPaymentResponseAndTransaction._1.card_error_code.isEmpty} match {
  	     case Some(subPaymentResponseTransaction) =>  subPaymentResponseTransaction._2
  	     case None => {
  	       if (cOperationPostPaymentWithCs) {
  	         //Falla la anulacion post cs, la deja como autorizada
  	         subTx.copy(status = Some(Autorizada().id)) 
  	       } else{
  	         subTx
  	       }
  	     }
  	   }
  	}
  	var parentAmount:Long = 0
  	updatedSubTransactions.foreach { sub => {parentAmount += sub.amount} }
  	 val operationSucces = responses.collect{ case DOPXResponse(request,operation,Success(response)) => {
  	   if (response.statusCode == 200) {
  	     Some(operation, response)
  	   } else  {
  	     None
  	   }}}.flatten
  	   
     val (newFatherState, sacDatherState) = oRefundSubPaymentOperation.map(rspo => 
    	  //En este caso no importa el estado de los subpayments. Se da cuando se reversa (Asi no se consulta lo ya almacenado)
    	  if (rspo.size == operationSucces.size) {
    	    (fatherState, fatherState)
    	  } else{
    	    (ARevisar(), Autorizada()) //Alguna de las subtransacciones no se reversaron
    	  }) 
  	  .getOrElse{
  	     val fStatus = getFatherStatus(subTransactions, chargeId, operationSucces)
  	     (fStatus,fStatus)
  	 }
		 
  	 operationSucces match {
   	   case Nil => logger.info("No se realizo cambios sobre transacciones")
  	   case sucessResponses => {
           legacyTxService.update(UpdateTxOnOperation(operationData.copy(resource = operationData.resource.copy(monto = Some(parentAmount), sub_transactions = updatedSubTransactions)),
						 sacDatherState, Some("F"), sucessResponses.head._2, None))
  				
					 tryRefundsLock(operationData.chargeId)
						 
  				 val operationSuccesInsertFH = sucessResponses.find(os => os._1 match {
             case PartialRefundOperation() | RefundOperation() | CancelOperation() | CancelOperationPostPaymentWithCs() => true
             case _ => false
           })		 
  				
           operationSuccesInsertFH.map(os => 
             legacyTxService.insert(InsertTxHistorico(None, Some(operationData.chargeId), operationData.cuenta.idProtocolo, newFatherState.id, Some("F"), Some(System.currentTimeMillis()), None, operationData.resource.nro_operacion)))
             .getOrElse(logger.info(s"Not save Historical state in father"))
  	   }
  	 }

  	 logger.info(s"Main Transaction is now on Status: ${newFatherState.toString()}")
     (RefundPaymentResponse(refund_id = None, amount = parentAmount, sub_payments = Some(subPaymentsList.map(subPayment => 
       RefundSubPaymentResponse(subPayment.id, subPayment.amount, subPayment.refund_id, subPayment.card_error_code, getState(subPayment.status, operationSucces, responses)))),
       status = getState(newFatherState, operationSucces, responses)), getResponseState(responses), subTransactionState.head)
    }
  }
  
  private def refundDistributed(requests: List[DOPX]): Future[List[DOPXResponse]] = {
    implicit val timeout = Timeout(60 seconds)
    (actorSystem.actorOf(Props(new DOPXHandlerActor(protocolService))) ? DOPXs(requests)).mapTo[List[DOPXResponse]]
  }
  
  private def handleResponse(operationData: OperationData, responses: List[DOPXResponse],  user: Option[String] = None): List[(RefundSubPaymentResponse, SubTransaction, TransactionState)] = {
    responses.map { result =>
        result match {
          case  DOPXResponse(request,operation,Success(response)) => {
            loadMDCFromOperation(operationData.resource)
            val subTx = operationData.resource.sub_transactions.find { sub => sub.site_id == request.site.id }.getOrElse{
              logger.error(s"error al obtener monto de las subtransacciones ${operationData.resource.sub_transactions}")
              throw new Exception("error al obtener la subtransaccion, esto no deberia pasar.")
            }
            val newamount = subTx.amount - request.resource.monto.getOrElse{
              logger.error(s"error al obtener monto de ${request.resource}")
              throw new Exception("error al obtener monto para la subtransaccion, esto no deberia pasar.")
            }
            val chargeId = operationData.chargeId
            
            operation match {
              case RefundOperation() => {
                logger.info("Success: Total refund operation")
                handleRefundResponse(subTx, chargeId, request,TxDevuelta(),response, operation, user)
              }
              case PartialRefundOperation() => {
                logger.info("Success: Partial Refund operation")
                handleRefundResponse(subTx, chargeId, request,AutorizadaAsterisco(),response, operation, user)
              }
              case PartialRefundBeforeClosureOperation() => {
                logger.info("Success: Partial Refund BeforeClosure operation")
                val status = if (newamount == 0L) {
                  TxAnulada()
                } else Autorizada()
                handleRefundResponse(subTx, chargeId, request,status,response, operation, user)
              }
              case CancelOperation() => {
                logger.info("Success: Cancel operation")
                handleCancelResponse(subTx, chargeId, request,TxAnulada(),response, operation, user)
              }
              case CancelOperationPostPayment() => {
                logger.info("Success: Cancel operation post payment")
                handleCancelResponse(subTx, chargeId, request,AnuladaPorGrupo(),response ,operation, user)
              }
              case CancelOperationPostPaymentWithCs() => {
                logger.info("Success: Cancel operation post payment and CS")
                handleCancelResponse(subTx, chargeId, request,TxAnulada(),response, operation, user)
              }
              case ReverseOperationPostPayment() => {
                logger.info("Success: Cancel operation post payment")
                val historicals = List(HistoricalStatus(EstadoOperacionSPS.getEstadoOperacionCreada().getIdEstado,-1,new Date()), 
                    HistoricalStatus(EstadoOperacionSPS.getEstadoOperacionAProcesar().getIdEstado,-1,new Date()))
                val responseFixed = if(response.statusCode == 200){
                  response.copy(historicalStatusList = historicals)
                } else {
                  //Falla la reversa != 200
                  response.copy(historicalStatusList = historicals ++ List(HistoricalStatus(EstadoOperacionSPS.getEstadoOperacionTimeOut.getIdEstado,-1,new Date())))
                } 
                handleCancelResponse(subTx, chargeId, request,Rechazada(),responseFixed, operation, user)
              }
            }
          }
          case DOPXResponse(request,operation,Failure(ProtocolError(authCode, reason))) => {
               val historicalStatus = List(HistoricalStatus(EstadoOperacionSPS.getEstadoOperacionCreada().getIdEstado,-1,new Date()),
                  HistoricalStatus(EstadoOperacionSPS.getEstadoOperacionAProcesar().getIdEstado,-1,new Date()))
            val subTx = operationData.resource.sub_transactions.find { sub => sub.site_id == request.site.id }.getOrElse{
              logger.error(s"error al obtener monto de las subtransacciones ${operationData.resource.sub_transactions}")
              throw new Exception("error al obtener la subtransaccion, esto no deberia pasar.")
            }
            val chargeId = operationData.chargeId      
                  
            operation match {
              case RefundOperation() => {
                logger.info("Failure: Total refund operation")
                handleFailureResponse(subTx, chargeId, request, operation, operationResponseFailure(operation.id, request.site.id, historicalStatus), user)
              }
              case PartialRefundOperation() => {
                logger.info("Failure: Partial Refund operation")
                handleFailureResponse(subTx, chargeId, request, operation, operationResponseFailure(operation.id, request.site.id, historicalStatus), user)
              }
              case PartialRefundBeforeClosureOperation() => {
                logger.info("Failure: Partial Refund BeforeClosure operation")
                handleFailureResponse(subTx, chargeId, request, operation, operationResponseFailure(operation.id, request.site.id, historicalStatus), user)
              }
              case CancelOperation() =>{
                logger.info("Failure: Cancel operation")
                handleFailureResponse(subTx, chargeId, request, operation, operationResponseFailure(operation.id, request.site.id, historicalStatus), user)
              }
              case CancelOperationPostPayment() => {
                logger.info("Failure: Cancel operation post payment")
                handleFailureResponse(subTx, chargeId, request, operation, operationResponseFailure(operation.id, request.site.id, historicalStatus), user)
              }
              case CancelOperationPostPaymentWithCs() => {
                logger.info("Failure: Cancel operation post payment and CS")
                handleFailureResponse(subTx, chargeId, request, operation, operationResponseFailure(operation.id, request.site.id, historicalStatus), user)
              }
              case ReverseOperationPostPayment() => {
                logger.info("Failure: Reverse operation post payment")
                handleFailureResponse(subTx, chargeId, request, operation, operationResponseFailure(operation.id, request.site.id, historicalStatus), user)
              }
              
            }
          }
          case DOPXResponse(request,operation,Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param)))))) => {
             logger.error(s"Failure Operation with id: ${operation.id}")
             handleValidationError(operationData, request, ARevisar())
          }
     }}
  }
  
  private def getResponseState(responses: List[DOPXResponse]): Try[OperationResponse] = {
    val allSuccessResponses = responses.collect{ case DOPXResponse(_, _, Success(response)) => Success(response)}
    val successOperationResponse = allSuccessResponses.find { successResponse => successResponse.get.statusCode == 200 }
    val failureOperationResponse = allSuccessResponses.find { successResponse => successResponse.get.statusCode != 200 }
    val failureProtocolSuccessResponses = responses.collect{ case DOPXResponse(request,operation, Failure(ProtocolError(authCode, reason))) => Failure(ProtocolError(authCode, reason))}.lift(0)
    val failureApiExceptionSuccessResponses = responses.collect{ case DOPXResponse(request,operation, Failure(ApiException(invalidRequestError))) =>  Failure(ApiException(invalidRequestError)) }.lift(0)
    
    successOperationResponse
    .getOrElse(failureOperationResponse
        .getOrElse(failureProtocolSuccessResponses
            .getOrElse(failureApiExceptionSuccessResponses.get)))
    
  }
  
  private def operationResponseFailure(operationId: Int, siteId: String, historicalStatus: List[HistoricalStatus]) = {
    OperationResponse(statusCode = 402, idMotivo = Motivo.ID_MOTIVO, terminal = None,
    nro_trace = None, nro_ticket = None, cod_aut = None , tipoOperacion = operationId, historicalStatusList = historicalStatus, 
    site_id = siteId, cardErrorCode = Some(ProcessingError()), idOperacionMedioPago = "")
  }
  
   private def getState(state : TransactionState, operationSucces: List[(Operation, OperationResponse)], responses: List[DOPXResponse]) = {
    operationSucces match {
      case Nil => {
        logger.debug("No se realizo cambios sobre transacciones")
        responses.find(response => response.operationType match {
          case RefundOperation() | PartialRefundOperation() |
               PartialRefundBeforeClosureOperation() | CancelOperation() => false
          case _ => true
          //En caso de que las operaciones resulten rechazadas en la DTX, queda rechazada la operacion pero no aplica
          //cambios en la base de datos.
        }).map(isPaymentFlow => state).getOrElse(Rechazada())
      }
      case sucessResponses => {
        val operationSuccesPostPayment = sucessResponses.find(os => os._1 match {
          case CancelOperationPostPayment() | ReverseOperationPostPayment() | CancelOperationPostPaymentWithCs() => true
          case _ => false
        })
        
        operationSuccesPostPayment.map{os => state
        }.getOrElse(state match {
            case TxAnulada() | TxDevuelta() | AutorizadaAsterisco() | Acreditada() => Autorizada()
            case other => other
        })
      } 
    }

  }

  private def handleValidationError(parentOpdataData: OperationData, request:OperationData, operationResult:TransactionState):(RefundSubPaymentResponse, SubTransaction, TransactionState) = {
    val subtx = parentOpdataData.resource.sub_transactions.find { sub => sub.site_id == request.site.id }.get
    val stateWithOutChange = subtx.status.getOrElse(operationResult.id)

    (RefundSubPaymentResponse(id = request.chargeId, amount = request.resource.monto.get, refund_id = None, status = TransactionState.apply(stateWithOutChange), card_error_code = Some(ProcessingError())),
      SubTransaction(request.site.id,subtx.amount, subtx.original_amount, request.resource.cuotas, None, Some(request.chargeId), Some(stateWithOutChange)),
        TransactionState.apply(stateWithOutChange))
  }
  
  def getFatherStatus(subTransactions:List[SubTransaction], chargeId:Long, operationSucces: List[(Operation, OperationResponse)]) : TransactionState = {
    val operationSuccesAfterClose = operationSucces.find(os => os._1 match {
      case RefundOperation() | PartialRefundOperation() => true
      case _ => false
    })
    
    operationSuccesAfterClose.map(oSucess => TransactionState.apply(subTransactions.head.status.get))
    .getOrElse(getFatherStatus(subTransactions, chargeId))
  }
  
  def getFatherStatus(subTransactions:List[SubTransaction], chargeId:Long) : TransactionState = {
    val allSubpaymentState = transactionRepository.getSubpaymentState(chargeId)
      val subTransactionsState = allSubpaymentState.map(subpaymentState => {
        val subTransaction = subTransactions.find(st => st.subpayment_id.get == subpaymentState.subpaymentId)
        subTransaction.map(st => SubpaymentState(st.subpayment_id.get, st.status.get)).getOrElse(subpaymentState)
      })
    TransactionState.apply(subTransactionsState.maxBy(st => TransactionState.apply(st.status.toInt).priority).status.toInt)
  }
  
  private def handleCancelResponse(subtx: SubTransaction, chargeId: Long, request:OperationData,operationResult:TransactionState,response:OperationResponse, operation: Operation,  user: Option[String] = None):(RefundSubPaymentResponse, SubTransaction, TransactionState) = {
    response.statusCode match {
      case 200 => {  
        val refundId = operationRepository.newRefundId
    	  legacyOpxService.insert(InsertDOPx(request, response, subtx.subpayment_id.get, Some(refundId), chargeId, None, user))
        legacyTxService.update(UpdateTxOnOperation(request.copy(resource = request.resource.copy(monto = Some(subtx.amount))), operationResult,
            Some("C"),response, None))
        //legacyTxService.insert(InsertTxHistorico(None, Some(request.chargeId), request.cuenta.idProtocolo, operationResult.id, Some("C")))

        operation match {
          case ReverseOperationPostPayment() | CancelOperationPostPayment() => {
            operationResult match {
              case Rechazada() | AnuladaPorGrupo() => {
                logger.debug(s"handleCancelResponse ReverseOperationPostPayment($operationResult)")
                // Si es un Rechazo/Anulacion Por Grupo NO se inserta en historico porque se realiza en la actualizacion
                // de la transaccion, caso contrario duplicaria el registro
              }
              case _ => {
                logger.debug(s"handleCancelResponse ReverseOperationPostPayment(_) operationResult = $operationResult")
                legacyTxService.insert(InsertTxHistorico(None, Some(request.chargeId), request.cuenta.idProtocolo, operationResult.id, Some("C"), Some(System.currentTimeMillis()), None, request.resource.nro_operacion))
              }
            }
          }
          case _ => {
            logger.debug(s"handleCancelResponse operation = $operation operationResult = $operationResult")
            legacyTxService.insert(InsertTxHistorico(None, Some(request.chargeId), request.cuenta.idProtocolo, operationResult.id, Some("C"), Some(System.currentTimeMillis()), None, request.resource.nro_operacion))
          }
        }

         (RefundSubPaymentResponse(id = request.chargeId, amount = request.resource.monto.get, refund_id = Some(refundId), status = operationResult),
           subtx.copy(nro_trace = response.nro_trace, status = Some(operationResult.id)), operationResult)
      }
      case other => {
      	legacyOpxService.insert(InsertDOPx(request, response, subtx.subpayment_id.get, None, chargeId, None, user))
  	    val stateWithOutChange = operation match {
  	      case CancelOperation() => Rechazada().id
  	      case _ => subtx.status.getOrElse(ARevisar().id) //ARevisar() Seteado cuando falla reversa
  	    }
        (RefundSubPaymentResponse(id = request.chargeId, amount = request.resource.monto.get, refund_id = None, status = TransactionState.apply(stateWithOutChange), card_error_code = response.cardErrorCode),
          subtx.copy(nro_trace = response.nro_trace, status = Some(stateWithOutChange)),
            TransactionState.apply(stateWithOutChange))
      }
    }
  }
    
  private def handleRefundResponse(subtx: SubTransaction, chargeId: Long, request:OperationData,operationResult:TransactionState, response:OperationResponse, operation: Operation,  user: Option[String] = None):(RefundSubPaymentResponse, SubTransaction, TransactionState) = {
	  response.statusCode match {
      case 200 => {
        val refundId = operationRepository.newRefundId
    	  legacyOpxService.insert(InsertDOPx(request, response, subtx.subpayment_id.get, Some(refundId), /*parentOpdataData.chargeId*/chargeId, None, user))
        val newamount = Some(subtx.amount - request.resource.monto.get)
        legacyTxService.update(UpdateTxOnOperation(request.copy(resource = request.resource.copy(monto = newamount)), operationResult, Some("C"), response, None))
        operation match {
          case PartialRefundOperation() | RefundOperation() => legacyTxService.insert(InsertTxHistorico(None, Some(request.chargeId), request.cuenta.idProtocolo, operationResult.id, Some("C"), Some(System.currentTimeMillis()), None, request.resource.nro_operacion))
          case _ => logger.debug(s"Not save Historical state in subpayment: ${subtx.subpayment_id.get}")
        }  

        (RefundSubPaymentResponse(id = request.chargeId, amount = request.resource.monto.get, refund_id = Some(refundId), status = operationResult),
          subtx.copy(amount = newamount.getOrElse(0), nro_trace = response.nro_trace, status = Some(operationResult.id)),
            operationResult)
      }

      case other => {
    	legacyOpxService.insert(InsertDOPx(request, response, subtx.subpayment_id.get, None, /*parentOpdataData.chargeId*/chargeId, None, user))
        val stateWithOutChange = subtx.status.getOrElse(ARevisar().id)
        (RefundSubPaymentResponse(id = request.chargeId, amount = request.resource.monto.get, refund_id = None, status = Rechazada(), card_error_code = response.cardErrorCode),
          subtx.copy(nro_trace = response.nro_trace, status = Some(stateWithOutChange)),
            TransactionState.apply(stateWithOutChange))
      }
    }
  }
  
  //TODO: Aca deberiamos diferenciar el timeout del medio de pago, de la no posibilidad de obtener una terminal
  private def handleFailureResponse(subtx: SubTransaction, chargeId: Long, request:OperationData, operation: Operation,response:OperationResponse,  user: Option[String] = None):(RefundSubPaymentResponse, SubTransaction, TransactionState) = {
      legacyOpxService.insert(InsertDOPx(request, response, subtx.subpayment_id.get, None, chargeId, None, user))
      
      val stateWithOutChange = operation match {
        case CancelOperationPostPayment() | CancelOperationPostPaymentWithCs() => TransactionState.apply(subtx.status.get)
        case _ => ARevisar() 
      }

      (RefundSubPaymentResponse(id = request.chargeId, amount = request.resource.monto.get, refund_id = None, status = stateWithOutChange, card_error_code = response.cardErrorCode),
        subtx.copy(nro_trace = response.nro_trace, status = Some(stateWithOutChange.id)),
          stateWithOutChange)
  }

  private def extractReferer(operation: OperationResource) =
    operation.datos_site.flatMap(_.referer)

  private def extractedReferer(operation: OperationResource) =
    extractReferer(operation).getOrElse("Referer not sent")
    
  private def prepareDistributedOperations(operationData: OperationData, subPaymentsList:List[RefundSubPaymentOperation], fatherState: TransactionState): List[DOPX] = {
    validSubpayments(subPaymentsList, operationData, fatherState)
    val referer = extractedReferer(operationData.resource)

    subPaymentsList.map { subPay => getDistributedOperation(subPay, operationData) }
  }
  
  private def getSubTransaction(op:OperationResource, id:Long) = {
    op.sub_transactions.find { osubTx => osubTx.subpayment_id.get == id } match {
      case Some(subTx) => subTx
      case None => {
        logger.error(s"Subpayment: ${id} is not part of Payment: ${op.charge_id.get}")
        throw ErrorFactory.validationException("subpayment_id",s"Subpayment: ${id} is not part of Payment: ${op.charge_id.get}") 
      }
    }
  }
  
  private def getDistributedOperation(refundSubPayment:RefundSubPaymentOperation, operationData:OperationData):DOPX = {
    val subTx = getSubTransaction(operationData.resource,refundSubPayment.id)
    val subSite = siteRepository.retrieve(subTx.site_id).get
    val resource = operationData.resource.copy(datos_medio_pago = operationData.resource.datos_medio_pago.map( dmp => dmp.copy(nro_trace = subTx.nro_trace, nro_ticket = subTx.nro_ticket, nro_terminal = subTx.terminal, id_operacion_medio_pago = subTx.id_operacion_medio_pago)))
    val fixedOpData = operationData.copy(resource = resource,site = subSite).replaceMonto(refundSubPayment.amount).replaceChargeId(Some(refundSubPayment.id)).replaceCuotas(subTx.installments)
    DOPX(fixedOpData,refundSubPayment.operation, refundSubPayment.id)
  }
  
  private def validSubpayments(subPaymentsList:List[RefundSubPaymentOperation], operationData:OperationData, fatherState: TransactionState){
    val errors = ArrayBuffer[ValidationError]()
    subPaymentsList.map(subPayment => validSubPayment(subPayment, operationData, fatherState, errors))
    if (!errors.isEmpty) {
      throw ApiException(InvalidRequestError(errors.toList))
    }
  }
  
  private def validSubPayment(refundSubPayment:RefundSubPaymentOperation, operationData:OperationData, fatherState: TransactionState, errors: ArrayBuffer[ValidationError]){
    val subTx = getSubTransaction(operationData.resource,refundSubPayment.id)
    val newAmount = subTx.amount - refundSubPayment.amount
    if (newAmount < 0 || refundSubPayment.amount <= 0) {
      logger.error(s"invalid amount of subpayment: ${refundSubPayment.id}")
      errors += ValidationError(s"subpayment: ${refundSubPayment.id}", "amount")
    }
    fatherState match {
      case TxAnulada() => refundSubPayment.operation match {
        case PartialRefundBeforeClosureOperation() => {
          logger.error(s"invalid operation: ${refundSubPayment.operation.toString()} of subpayment: ${refundSubPayment.id}. Father state: ${TxAnulada().toString()},")
          errors += ValidationError(s"subpayment: ${refundSubPayment.id}", "operation")
        }
        case other =>
      }
      case other => 
    }
      
  }
  
  private def buildStateOfSubpayments(operationData: OperationData, refundPayment: Option[RefundPaymentRequest], meanPayment: Int, bin: String) = {
    val subTransactions = operationData.resource.sub_transactions
    val isMpos = operationData.site.mensajeriaMPOS.getOrElse("N").equals("S")
    val errors = ArrayBuffer[ValidationError]()
    val refundSubPaymentOperations = refundPayment match {
      case Some(RefundPaymentRequest(_, Some(amount), None, _)) => throw ErrorFactory.missingDataException(List("sub_payments"))
      case _ => {
        refundPayment.map(_.sub_payments.map(_.map { subPayment =>
          val opDataSubPayment: SubTransaction = subTransactions.find(_.subpayment_id.get == subPayment.id).getOrElse(throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "subpayment_id"))
          val state = TransactionState.apply(transactionRepository.retrieveSubPaymentState(subPayment.id).toInt)
          /*
           * opDataSubPayment.original_amount.getOrElse(opDataSubPayment.amount) resuelto asi, ya que
           * original_amount fue agregado recientemente
           */
          val amountToRefund = subPayment.amount.getOrElse(opDataSubPayment.amount)
          val newAmount = opDataSubPayment.amount - amountToRefund
          val originalAmountAnnulment = opDataSubPayment.original_amount.getOrElse(opDataSubPayment.amount) == amountToRefund 
          val operationType = refundStateService.createOperationType(state, originalAmountAnnulment, newAmount, meanPayment, bin, isMpos)
          operationType match {
            case NonExistanceOperation(id) => {
              errors += ValidationError(s"subpayment: ${subPayment.id} - invalid status: ${TransactionState.apply(id).toString()}", "operation")
              logger.error(s"not exist operation: ${TransactionState.apply(id).toString()} for subpayment: ${subPayment.id}")
              None
            }
            case MeanPaymentErrorOperation(id) => {
              errors += ValidationError(s"State error, invalid meanPayment for operation", "operation")
            	logger.error(s"unsupported operation for subpayment: ${subPayment.id} - meanPayment: ${meanPayment}")
            	None
            }
            case _ => {
              Some(RefundSubPaymentOperation(
                subPayment.id,
                subPayment.amount.getOrElse(opDataSubPayment.amount),
                None,
                operationType))
            }
          }
        }.toList).getOrElse(buildStateOfSubpaymentsForAllSubTransactions(subTransactions, meanPayment, errors, bin, isMpos)))
        .getOrElse(buildStateOfSubpaymentsForAllSubTransactions(subTransactions, meanPayment, errors, bin, isMpos))
      }
    }
    
    if (!errors.isEmpty) {
      throw ApiException(InvalidRequestError(errors.toList))
    }
    
    refundSubPaymentOperations flatten
  }

  private def buildStateOfSubpaymentsForAllSubTransactions(subTransactions: List[SubTransaction], meanPayment: Int, errors: ArrayBuffer[ValidationError], bin: String, isMpos: Boolean) = {
    subTransactions.map { subTx =>
      val state = TransactionState.apply(transactionRepository.retrieveSubPaymentState(subTx.subpayment_id.get).toInt)
      /*
       * subTx.original_amount.getOrElse(subTx.amount) resuelto asi, ya que
       * original_amount fue agregado recientemente
       */      
      val originalAmountAnnulment = subTx.original_amount.getOrElse(subTx.amount) == subTx.amount
      val transactionStatus = refundStateService.createOperationType(state, originalAmountAnnulment, 0, meanPayment, bin, isMpos)
      transactionStatus match {
        case NonExistanceOperation(id)   => {
              logger.error(s"not exist operation for subpayment: ${subTx.subpayment_id.get}")
              errors += ValidationError(s"subpayment: ${subTx.subpayment_id.get} - invalid status: ${TransactionState.apply(id).toString()}", "operation")
              None
        }
        case MeanPaymentErrorOperation(id) => {
              logger.error(s"unsupported operation: ${TransactionState.apply(id).toString()} for subpayment: ${subTx.subpayment_id.get}")
              errors += ValidationError(s"State error, invalid meanPayment for operation", "operation")
              None
        }
        case _ => {
          Some(RefundSubPaymentOperation(
            subTx.subpayment_id.get,
            subTx.amount,
            None,
            transactionStatus))
        }
      }
    }
  }
  
}
