package services.refunds

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import com.decidir.coretx.api._
import com.decidir.coretx.domain._
import com.decidir.protocol.api.OperationResponse
import com.decidir.protocol.api.TransactionResponse
import akka.actor.ActorSystem
import controllers.MDCHelperTrait
import javax.inject.Inject

import services.protocol.Operation2ProtocolConverter
import services.protocol.ProtocolService
import com.decidir.coretx.domain.NonExistanceOperation
import com.decidir.coretx.api.AutorizadaAsterisco
import com.decidir.coretx.api.Rechazada
import services.payments.LegacyTransactionServiceClient
import services.payments.LegacyOperationServiceClient
import services.payments.UpdateTx
import services.payments.InsertOpx
import services.payments.UpdateTxOnOperation
import services.payments.InsertDOPx
import com.decidir.protocol.api.ProtocolResource
import services.converters.OperationResourceConverter
import services.payments.InsertTxHistorico
import com.decidir.coretx.api.TxAnulada
import com.decidir.coretx.api.TxAnulada
import com.decidir.coretx.api.RefundPaymentRequest
import com.decidir.coretx.utils.JedisPoolProvider
import services.validations.MPOSValidator
import com.decidir.util.RefundsLockRepository
import play.api.Configuration

class RefundService @Inject() (context: ExecutionContext,
                               actorSystem: ActorSystem,
                               transactionRepository: TransactionRepository,
                               protocolService: ProtocolService,
                               jedisPoolProvider: JedisPoolProvider,
                               siteRepository: SiteRepository,
                               legacyOpxService: LegacyOperationServiceClient,
                               operationRepository: OperationResourceRepository,
                               distributedOperationProcessor: DistributedOperationProcessor,
                               legacyTxService: LegacyTransactionServiceClient,
                               refundStateService: RefundStateService,
                               operationResourceConverter: OperationResourceConverter,
                               oRepository: OperationRepository,
                               motivoRepository: MotivoRepository,
                               mPOSValidator: MPOSValidator,
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

  def isLocked(chargeId: Long): Try[Boolean] = {
    if (refundLockAllowed) {
      refundsLockRepository.isLocked(chargeId.toString) match {
        case Failure(error) => {
          logger.error(s"Cannot retrieve refundsLock for chargeId: $chargeId", error)
          Failure(error)
        }
        case Success(locked) => {
          logger.debug(s"Success refundsLockRepository.isLocked($chargeId) = $locked")
          Success(locked)
        }
      }
    } else Success(false)
  }

  def reverse(operationData: OperationData): Future[Try[OperationResponse]] = {
    logger.info(s"Reversando operacion chargeId: ${operationData.chargeId}")
    val operation = operationData.resource
    operationRepository.transitionOperation(operation.id, InProcess(Cancelled()))
    
    val protocolCall = Operation2ProtocolConverter.convert(operationData)
    protocolService.reverse(protocolCall) map { _ match {
        case Failure(exception) => {
          logger.error(s"Error grave procesando la reversa de la operacion ${operationData.resource.id} / ${operationData.chargeId}", exception)
          val statusCode = 400
          legacyTxService.update(updateTxReverse(operationData, "", ARevisar(), 400, None))
          Failure(exception)
        }
        case Success(or) => {
          if (or.authorized) {
            val statusCode = 200
            legacyTxService.update(updateTxReverse(operationData, or.cod_aut.getOrElse(""), Rechazada(), statusCode, Some(operation2TransactionResponse(or, statusCode))))
            Success(or)
          } else  {
            logger.warn(s"Reverse operation: state: ${or.statusCode}")
            val statusCode = 400
            legacyTxService.update(updateTxReverse(operationData, or.cod_aut.getOrElse(""), ARevisar(), statusCode, Some(operation2TransactionResponse(or, statusCode))))
            Success(or)
          }
        }
      }
    }
  }
  
  private def updateTxReverse(operationData: OperationData, authorizationCode: String, state: TransactionState, statusCode: Int, tr :Option[TransactionResponse]) = {
     UpdateTx(chargeId = operationData.chargeId, site = operationData.site, distribuida = None, 
        estadoFinal = state match { //Para estados historicos
          case Rechazada() => AReversar()
          case other => ARevisar()
        },
        oer = OperationExecutionResponse(
          status = state.id,
          authorizationCode = authorizationCode,
          cardErrorCode = Some(ProcessingError()),
          authorized = false,
          operationResource = Some(operationData.resource)),
        tr)
  }
  
  private def operation2TransactionResponse(or: OperationResponse, statusCode: Int) = {
    TransactionResponse(
      statusCode = statusCode,
      idMotivo = or.idMotivo,
      terminal = or.terminal,
      nro_trace = or.nro_trace,
      nro_ticket = or.nro_ticket,
      cod_aut = or.cod_aut,
      validacion_domicilio = None,
      cardErrorCode = Some(ProcessingError()), //!authorized
      site_id = or.site_id,
      idOperacionMedioPago = or.idOperacionMedioPago)
  }

  def process(siteId: String, chargeId: Long, refundPayment: Option[RefundPaymentRequest], user: Option[String] = None): Future[(Option[RefundPaymentResponse], Try[OperationResponse], Option[TransactionState])] = {

    val op = transactionRepository.retrieveCharge(siteId, chargeId)
    op match {
      case Success(oer) => {
        logger.info("retrieve charge success")
        
        val operation = oer.operationResource.getOrElse {
          logger.error("operationResource not available in operationExecuteResource")
          throw new Exception("operationResource not available in operationExecuteResource")
        }
        
        val state = TransactionState.apply(transactionRepository.retrieveTransState(operation.idTransaccion.get).toInt)
        state match {
          case Rechazada() => throw ErrorFactory.validationException("State error", TransactionState.apply(oer.status).toString())
          case _           => logger.debug(s"OperationExecutionResponse.status: $oer.status")
        }

        val new_datos_medio_pago = operation.datos_medio_pago.map(_.copy(security_code = refundPayment.flatMap(_.security_code)))

        val newOperation = operation.copy(datos_medio_pago = new_datos_medio_pago, datos_banda_tarjeta =
          refundPayment.flatMap(_.card_track_info).map(banda => DatosBandaTarjeta(card_track_1 = banda.card_track_1,
            card_track_2 = banda.card_track_2,
            input_mode = banda.input_mode)))

        mPOSValidator.validate(newOperation, siteId)

        loadMDCFromOperation(newOperation)
        val opData = operationResourceConverter.operationResource2OperationData(newOperation)
        val meanPayment = getMeanPayment(opData.resource.datos_medio_pago)
        
        if (newOperation.datos_site.get.id_modalidad.getOrElse("N").equals("S")) {
          distributedOperationProcessor.processDistributedOPx(chargeId, state, opData, refundPayment, meanPayment, None, user) map { _ match {
            case (refundPaymentResponse, Success(operationResponse), subTransactionState) => (Some(refundPaymentResponse), Success(operationResponse), Some(subTransactionState))
            case (refundPaymentResponse, Failure(failure), subTransactionState) => { // Ninguna operacion se ejecuto satisfactoriamente
              logger.error(s"Refund service failure", failure)
              (Some(RefundPaymentResponse(refund_id = None, status = Rechazada(), card_error_code = Some(ProcessingError()), amount = opData.resource.monto.getOrElse(0L))), Failure(failure), Some(subTransactionState))
            }
          }}
        } else {
          /*
           * opData.resource.original_amount.getOrElse(opData.resource.monto.getOrElse(0L)) resuelto asi, ya que
           * original_amount fue agregado recientemente
           */
          val amountToRefund = refundPayment.map(_.amount.orElse(opData.resource.monto)).getOrElse(opData.resource.monto).getOrElse(0L)
          val newAmount = opData.resource.monto.getOrElse(0L) - amountToRefund
          val originalAmountAnnulment = opData.resource.original_amount.getOrElse(opData.resource.monto.getOrElse(0L)) == amountToRefund
          val bin = opData.resource.datos_medio_pago.flatMap(_.bin).getOrElse{
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_BIN)
          }
          val operation = refundStateService.createOperationType(state, originalAmountAnnulment, newAmount, meanPayment, bin, opData.site.mensajeriaMPOS.getOrElse("N").equals("S"))
          operation match {
            case CancelOperation() => {
              logger.info("Cancel (Annulment) operation")
              cancel(opData, CancelOperation(), user, state)
            }
            case CancelPreApprovedOperation() => {
              logger.info("Cancel (Annulment) pre approved operation")
              cancel(opData, CancelPreApprovedOperation(), user, state)
            }
            case RefundOperation() => {
              logger.info("Total refund operation")
              refund(opData, refundPayment, operation, true, user)
            }
            case PartialRefundOperation() => {
              logger.info("Partial Refund AfterClosure operation")
              refund(opData, refundPayment, operation, true, user)
            }
            case PartialRefundBeforeClosureOperation() => {
              logger.info("Partial Refund BeforeClosure operation")
              refund(opData, refundPayment, operation, false, user)
            }
            case NonExistanceOperation(id) => {
              logger.error(s"not exist operation: ${TransactionState.apply(id).toString()}")
              Future(None, Failure(ErrorFactory.InvalidStatusException(TransactionState.apply(id).toString())), Some(state))
            }
            case MeanPaymentErrorOperation(id) => {
              logger.error(s"unsupported operation - meanPayment: ${meanPayment}")
              Future(None, Failure(ErrorFactory.validationException("State error, invalid meanPayment for operation", "operation")), Some(state))
            }
          }
        }
      }
      case Failure(op) => {
        logger.error("retrieve charge failure", op.getCause)
        Future(None, Failure(ErrorFactory.notFoundException("payments", chargeId.toString())), None)
      }
    }
  }

  //devolver
  private def refund(opData: OperationData, refundPayment: Option[RefundPaymentRequest], operation: Operation, refundAfterClosure: Boolean = false, user: Option[String] = None): Future[(Option[RefundPaymentResponse], Try[OperationResponse], Option[TransactionState])] = {
    val subtractAmount = refundPayment.flatMap(_.amount).getOrElse(opData.resource.monto.getOrElse(throw new Exception("OperationData.resource dont have amount")))
    val newAmount = opData.resource.monto.getOrElse(0L) - subtractAmount
    val originalState = TransactionState.apply(transactionRepository.retrieveTransState(opData.resource.idTransaccion.get).toInt)

    if (newAmount < 0 || subtractAmount <= 0L) {
      operationRepository.transitionOperation(opData.resource.id, RefundFailed())
      Future(None, Failure(ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "amount")), Some(originalState))
    } else {
      val protocolCall = Operation2ProtocolConverter.convert(opData.copy(resource = opData.resource.copy(monto = Some(subtractAmount))))
      operationRepository.transitionOperation(opData.resource.id, InProcess(Refund()))

      val result = if (refundAfterClosure){
        protocolService.refundAfter(protocolCall)
      }
      else{
        protocolService.refund(protocolCall)
      }
      result map { response =>
        val (updatedRefundPayment, updatedProtocolResponse, transactionState) = response match {
          case Success(result) => {
            logger.info(s"refund Success Result: $result")
            result.statusCode match {
              case 200 => {
                val refundId = operationRepository.newRefundId
                legacyOpxService.insert(InsertOpx(opData.copy(resource = opData.resource.copy(monto = Some(subtractAmount))), result, opData.resource.idTransaccion, Some(refundId), None, user))
                val statusTransaction = operation match {
                  case RefundOperation()                     => TxDevuelta()
                  case PartialRefundOperation()              => AutorizadaAsterisco()
                  case PartialRefundBeforeClosureOperation() => if (newAmount == 0L) {
                                                                  TxAnulada()
                                                                } else Autorizada()
                }
                legacyTxService.update(UpdateTxOnOperation(opData.copy(resource = opData.resource.copy(monto = Some(newAmount))), statusTransaction, None, result, opData.resource.idTransaccion, user))

                tryRefundsLock(opData.chargeId)

                operation match {
                  case PartialRefundOperation() | RefundOperation() => legacyTxService.insert(InsertTxHistorico(None, Some(opData.chargeId), opData.cuenta.idProtocolo, statusTransaction.id, None, Some(System.currentTimeMillis()), None, opData.resource.nro_operacion))
                  case _ => logger.info(s"Not save Historical state")
                }
                operationRepository.transitionOperation(opData.resource.id, Refund())
                (Some(RefundPaymentResponse(refund_id = Some(refundId), status = Autorizada(), amount = subtractAmount)), response, Some(statusTransaction))
              }
              case other => {
                legacyOpxService.insert(InsertOpx(opData.copy(resource = opData.resource.copy(monto = Some(subtractAmount))), result, opData.resource.idTransaccion, None, None, user))
                operationRepository.transitionOperation(opData.resource.id, RefundFailed())
                val motivo = getMotivo(opData.protocolId, result.cardErrorCode.getOrElse(ProcessingError()).code)
                val cardErrorCode = result.cardErrorCode.map(card_error_code =>
                  ProcessingError(
                    code = motivo.map(_.id).getOrElse(-1),
                    descripcion = motivo.map(_.descripcion),
                    adicional = motivo.map(_.descripcion_display))).orElse(result.cardErrorCode)

                (Some(RefundPaymentResponse(refund_id = None, status = Rechazada(), card_error_code = cardErrorCode, amount = subtractAmount)), response, Some(originalState))
              }
            }
          }
          case Failure(error) => {
            logger.error("RefundService.refund Failure", error)
            operationRepository.transitionOperation(opData.resource.id, RefundFailed())
            (Some(RefundPaymentResponse(refund_id = None, status = Rechazada(), card_error_code = Some(ProcessingError()), amount = subtractAmount)), response, Some(originalState))
          }
        }
        (updatedRefundPayment, updatedProtocolResponse, transactionState)
      }
    }
  }

  //anular
  def cancel(opData: OperationData, operation: Operation, user: Option[String] = None, originalState: TransactionState):Future[(Option[RefundPaymentResponse], Try[OperationResponse], Option[TransactionState])] = {
    val protocolCall = Operation2ProtocolConverter.convert(opData)
    operationRepository.transitionOperation(opData.resource.id, InProcess(Cancelled()))
    cancelStrategy(operation, protocolCall) map { response =>
      val (updatedRefundPayment, updatedProtocolResponse, transactionState) = response match {
        case Success(result) => {
          logger.info(s"cancel Success Result: $result")
          result.statusCode match {
            case 200 => {
              val refundId = operationRepository.newRefundId
              legacyOpxService.insert(InsertOpx(opData, result, opData.resource.idTransaccion, Some(refundId), None, user))
              val stateResult = operation match {
                case CancelPreApprovedOperation() => AnulacionConfirmada()
                case CancelOperation() | CancelOperationPostPaymentWithCs() => TxAnulada()
              }
              legacyTxService.update(UpdateTxOnOperation(opData, stateResult, None, result, opData.resource.idTransaccion, user))

              tryRefundsLock(opData.chargeId)

              legacyTxService.insert(InsertTxHistorico(None, Some(opData.chargeId), opData.cuenta.idProtocolo, stateResult.id, None, Some(System.currentTimeMillis()), None, opData.resource.nro_operacion))
              operationRepository.transitionOperation(opData.resource.id, Cancelled())
              val refundPaymentResponseState = operation match {
                case CancelOperationPostPaymentWithCs() => TxAnulada()
                case other => Autorizada()
              }
              (Some(RefundPaymentResponse(refund_id = Some(refundId), status = refundPaymentResponseState, amount = opData.resource.monto.getOrElse(0L))), response, Some(stateResult))
            }
            case other => {
              logger.warn("Cancel operation: " + other)
              legacyOpxService.insert(InsertOpx(opData, result, opData.resource.idTransaccion, None))
              operationRepository.transitionOperation(opData.resource.id, CancelFailed())
              val stateResult = operation match {
                case CancelOperationPostPaymentWithCs() => ARevisar()
                case other => Rechazada()
              }
              val motivo = getMotivo(opData.protocolId, result.cardErrorCode.getOrElse(ProcessingError()).code)
              val card_error_code = result.cardErrorCode.map(card_error_code =>
                ProcessingError(
                  code = motivo.map(_.id).getOrElse(-1),
                  descripcion = motivo.map(_.descripcion),
                  adicional = motivo.map(_.descripcion_display))).orElse(result.cardErrorCode)
              (Some(RefundPaymentResponse(refund_id = None, status = stateResult, card_error_code = card_error_code, amount = opData.resource.monto.getOrElse(0L))), response, Some(originalState))
            }
          }
        }
        case Failure(result) => {
          logger.error("RefundService.cancel Failure")
          operationRepository.transitionOperation(opData.resource.id, CancelFailed())
          (Some(RefundPaymentResponse(refund_id = None, status = Rechazada(), card_error_code = Some(ProcessingError()), amount = opData.resource.monto.getOrElse(0L))), response, Some(originalState))
        }
      }
      (updatedRefundPayment, updatedProtocolResponse, transactionState)
    }
  }
  
  private def cancelStrategy(operation: Operation, protocolCall: ProtocolResource) = {
    operation match {
      case CancelPreApprovedOperation() => protocolService.cancelPreApproved(protocolCall)
      case CancelOperation() | CancelOperationPostPaymentWithCs() => protocolService.cancel(protocolCall)
    }
  }

  def rollback(siteId: String, chargeId: Long, id: Long, user: Option[String] = None): Future[(Option[AnnulRefundResponse], Try[OperationResponse], Option[TransactionState])] = {

    val op = transactionRepository.retrieveCharge(siteId, chargeId)
    op match {
      case Success(oer) => {
        logger.info("RefundService.rollback retrieve charge success")
        val operation = oer.operationResource.getOrElse {
          logger.error("operationResource not available in operationExecuteResource")
          throw new Exception("operationResource not available in operationExecuteResource")
        }
        loadMDCFromOperation(operation)
        val refund = transactionRepository.retrieveRefund(siteId, chargeId, id)
        refund match {
          case Success(oERefundR) => {
            logger.info("RefundService.rollback retrieve refund success")
            val opData = operationResourceConverter.operationResource2OperationData(operation)
            val meanPayment = getMeanPayment(opData.resource.datos_medio_pago)
            operation.datos_site.get.id_modalidad match {
              case Some("S") => {
                val subpaymentId = transactionRepository.retrieveSubPaymentIdOnCancelRefund(oERefundR.id).toInt
                operation.sub_transactions.find { subTx => subTx.subpayment_id.get == subpaymentId } match {
                  case Some(subPayment) => {
                    val subSite = siteRepository.retrieve(subPayment.site_id).get
                    val state = TransactionState.apply(transactionRepository.retrieveSubPaymentState(subPayment.subpayment_id.get).toInt)
                    val fixedResource = opData.resource.copy(datos_medio_pago = Some(opData.resource.datos_medio_pago.get.copy(nro_trace = subPayment.nro_trace, nro_terminal = subPayment.terminal)))
                    val fixedOpData = opData.copy(resource = fixedResource,site = subSite).replaceChargeId(Some(subPayment.subpayment_id.get)).replaceCuotas(subPayment.installments).replaceMonto(subPayment.amount)
                    handleRollback(opData, oERefundR, state, meanPayment, Some(fixedOpData), user)
                  }
                }  
              }
              case _ =>{
                val state = TransactionState.apply(transactionRepository.retrieveTransState(operation.idTransaccion.get).toInt)
                handleRollback(opData, oERefundR, state, meanPayment, None, user)
              }  
            }
          }
          case Failure(op) => {
            logger.error("retrieve refund failure")
            operationRepository.removeOperationBeenExecuted(siteId, id)
            Future(None, Failure(ErrorFactory.notFoundException("refunds", id.toString())), None)
          }
        }
      }
      case Failure(op) => {
        logger.error("retrieve charge failure")
        operationRepository.removeOperationBeenExecuted(siteId, id)
        Future(None, Failure(ErrorFactory.notFoundException("payments", chargeId.toString())), None)
      }
    }
  }

  private def handleRollback(opData: OperationData, oERefundR: OperationExecutionRefundResponse, state: TransactionState, meanPayment: Int,
                             subPaymentData: Option[OperationData], user: Option[String] = None): Future[(Option[AnnulRefundResponse], Try[OperationResponse], Option[TransactionState])] = {

    if (operationRepository.addOperationBeenExecuted(opData.site.id, oERefundR.id) == 0) {
      Future(None, Failure(ErrorFactory.validationException("State error, operation being executed", oERefundR.id.toString())), None)
    } else if (oRepository.beforeOfClose(opData.chargeId, oERefundR.id)) {
        logger.error(s"State error, cancel operation before of Close")
        Future(None, Failure(ErrorFactory.validationException("State error, cancel operation before of Close", "operation")), None)
      } else {
        val transactionStatus = refundStateService.createOperationType(state, meanPayment, opData.resource.datos_medio_pago.get.bin.get, opData.site.mensajeriaMPOS.getOrElse("N").equals("S"))
        transactionStatus match {
          case CancelRefundAfterClosureOperation() => {
            logger.info("rollback total operation after to closure")
            cancelRefund(opData, oERefundR, subPaymentData, transactionStatus, false, user)
          }
          case CancelPartialRefundAfterClosureOperation() => {
            logger.info("rollback partial operation after to closure")
            cancelRefund(opData, oERefundR, subPaymentData, transactionStatus, false, user)
          }
          case CancelPartialRefundBeforeClosureOperation() => {
            logger.info("rollback partial operation before to closure")
            cancelRefund(opData, oERefundR, subPaymentData, transactionStatus, true, user)
          }
          case NonExistanceOperation(id) => {
            logger.error(s"none match Operation: $id")
            operationRepository.removeOperationBeenExecuted(opData.site.id, oERefundR.id)
            Future(None, Failure(ErrorFactory.InvalidStatusException(TransactionState.apply(id).toString())), None)
          }
          case MeanPaymentErrorOperation(id) => {
            logger.error(s"none match Operation: $id")
            operationRepository.removeOperationBeenExecuted(opData.site.id, oERefundR.id)
            Future(None, Failure(ErrorFactory.validationException("State error, invalid meanPayment for operation", "operation")), None)
          }
        }
    }
  }
  
  private def protocolCancel(protocolCall: ProtocolResource, isBeforeClosure: Boolean) = {
    if (isBeforeClosure) {
      protocolService.cancelRefundBeforeClosure(protocolCall)
    } else {
      protocolService.cancelRefundAfterClosure(protocolCall)
    }
  }

  private def cancelRefund(opData: OperationData, oERefundR: OperationExecutionRefundResponse, subPaymentData: Option[OperationData], operation: Operation,
      isBeforeClosure: Boolean, user: Option[String] = None): Future[(Option[AnnulRefundResponse], Try[OperationResponse], Option[TransactionState])] = {
    val amount = oERefundR.amount
    val installment = subPaymentData match {
      case Some(subPaymentD) => subPaymentD.resource.cuotas 
      case None => Some(opData.cuotas)
    }

    val protocolCall = Operation2ProtocolConverter.convert(subPaymentData.getOrElse(opData).replaceMonto(amount).replaceCuotas(installment))
    operationRepository.transitionOperation(opData.resource.id, InProcess(Cancelled()))
    val stateResult = if(isBeforeClosure) Autorizada() else AutorizadaAsterisco()
    protocolCancel(protocolCall, isBeforeClosure) map { response =>
      val (updatedRefundPayment, updatedProtocolResponse, transactionState) = response match {
        case Success(result) => {
          logger.info(s"RefundService.cancel, isBeforeClosure: ${isBeforeClosure}: Success Result: $result")
          result.statusCode match {
            case 200 => {
              subPaymentData match {
                case Some(subPaydata) => {
                  legacyOpxService.insert(InsertDOPx(subPaydata.replaceMonto(amount), result, subPaydata.chargeId, Some(operationRepository.newRefundId), opData.chargeId, Some(oERefundR.id), user))
                  legacyTxService.update(UpdateTxOnOperation(subPaydata.copy(resource = subPaydata.resource.copy(monto = Some(subPaydata.resource.monto.getOrElse(0L) + amount))), stateResult, Some("C"), result, None, user))

                  operation match {
                    case CancelRefundAfterClosureOperation() => legacyTxService.insert(InsertTxHistorico(None, Some(subPaydata.chargeId), opData.cuenta.idProtocolo, stateResult.id, Some("C"), Some(System.currentTimeMillis()), None, opData.resource.nro_operacion))
                    case _ => logger.info(s"Not save Historical state")
                  }
                  val subTransactions = opData.resource.sub_transactions.collect {
                    case subTx => {
                      if (subTx.subpayment_id.get == subPaydata.chargeId)
                        subTx.copy(amount = subTx.amount + amount, nro_trace = result.nro_trace, status = Some(stateResult.id))
                      else
                        subTx
                    }
                  }
                  val fatherState = if(isBeforeClosure) 
                      distributedOperationProcessor.getFatherStatus(List(subTransactions.find { st => st.subpayment_id.get == subPaydata.chargeId}.get), opData.chargeId)
                    else {
                      AutorizadaAsterisco()
                    }
                  legacyTxService.update(UpdateTxOnOperation(opData.copy(resource = opData.resource.copy(monto = Some(opData.resource.monto.getOrElse(0L) + amount), sub_transactions = subTransactions)), fatherState, Some("F"), result, opData.resource.idTransaccion, user))

                  tryRefundsLock(opData.chargeId)

                  operation match {
                    case CancelRefundAfterClosureOperation() => legacyTxService.insert(InsertTxHistorico(None, Some(opData.chargeId), opData.cuenta.idProtocolo, stateResult.id, Some("F"), Some(System.currentTimeMillis()), None, opData.resource.nro_operacion))
                    case _ => logger.info(s"Not save Historical state")
                  }
                }
                case None => {
                  legacyOpxService.insert(InsertOpx(opData.replaceMonto(amount), result, opData.resource.idTransaccion, Some(operationRepository.newRefundId), Some(oERefundR.id), user))
                  legacyTxService.update(UpdateTxOnOperation(opData.copy(resource = opData.resource.copy(monto = Some(opData.resource.monto.getOrElse(0L) + amount))), stateResult, None, result, opData.resource.idTransaccion, user))

                  tryRefundsLock(opData.chargeId)

                  operation match {
                    case CancelRefundAfterClosureOperation() => legacyTxService.insert(InsertTxHistorico(None, Some(opData.chargeId), opData.cuenta.idProtocolo, stateResult.id, None, Some(System.currentTimeMillis()), None, opData.resource.nro_operacion))
                    case _ => logger.info(s"Not save Historical state")
                  }
                
                }
              }

              val statusTransaction = operation match {
                case CancelPartialRefundAfterClosureOperation() => AutorizadaAsterisco()
                case CancelPartialRefundBeforeClosureOperation() => Autorizada()
                case CancelRefundAfterClosureOperation() => AutorizadaAsterisco()
              }

              operationRepository.transitionOperation(opData.resource.id, Cancelled())
              (Some(AnnulRefundResponse(status = statusTransaction, amount = amount)), response, Some(statusTransaction))
            }
            case other => {
              subPaymentData match {
                case Some(subdata) => {
                  legacyOpxService.insert(InsertDOPx(subdata.replaceMonto(amount), result, subdata.chargeId, None, opData.chargeId, None, user))
                }
                case None => {
                  legacyOpxService.insert(InsertOpx(opData.replaceMonto(amount), result, opData.resource.idTransaccion, None, None, user))
                }
              }
              logger.warn(s"cancel, isBeforeClosure: ${isBeforeClosure} - operation: " + other)
              operationRepository.transitionOperation(opData.resource.id, CancelFailed())
              (Some(AnnulRefundResponse(status = Rechazada(), card_error_code = result.cardErrorCode, amount = amount)), response, None)
            }
          }
        }
        case Failure(result) => {
          logger.error(s"RefundService.cancel, isBeforeClosure: ${isBeforeClosure}: Failure", result)
          operationRepository.removeOperationBeenExecuted(opData.site.id, oERefundR.id)
          operationRepository.transitionOperation(opData.resource.id, CancelFailed())
          (Some(AnnulRefundResponse(status = Rechazada(), card_error_code = Some(ProcessingError()), amount = amount)), response, None)
        }
      }
      (updatedRefundPayment, updatedProtocolResponse, transactionState)
    }
  }

  def cancelOnCSResponse(opData: OperationData): Future[(Option[RefundPaymentResponse], Try[OperationResponse], Option[TransactionState])] = {
    val operation = CancelOperationPostPaymentWithCs()
    if (opData.resource.datos_site.get.id_modalidad.getOrElse("N").equals("S")) {
    	val meanPayment = getMeanPayment(opData.resource.datos_medio_pago)
    	val refundSubPaymentOperation = opData.resource.sub_transactions.map( st => 
    	  RefundSubPaymentOperation(id = st.subpayment_id.get,
    	      amount = st.amount,
    	      refundId = None,
            operation = operation))
            
      distributedOperationProcessor.processDistributedOPx(opData.resource.charge_id.get, TxAnulada(), opData, None, meanPayment, Some(refundSubPaymentOperation)) map { _ match {
            case (refundPaymentResponse, Success(operationResponse), transactionState) => (Some(refundPaymentResponse), Success(operationResponse), Some(transactionState))
            case (refundPaymentResponse, Failure(failure), transactionState) => {
              logger.error(s"Refund service failure post CS call", failure)
              (None, Failure(failure), Some(transactionState))
            }
          }}
    } else {
      cancel(opData, operation, None, Autorizada())
    }
  }
  
  private def getMeanPayment(dmpr :Option[DatosMedioPagoResource]) = {
    dmpr.flatMap(_.medio_de_pago.orElse{
      logger.error("getMeanPayment medio_de_pago undefined")
      throw new Exception("getMeanPayment medio_de_pago undefined")
    }).getOrElse{
      logger.error("getMeanPayment DatosMedioPagoResource undefined")
      throw new Exception("getMeanPayment DatosMedioPagoResource undefined")
    }
  }

  private def getMotivo(idProtocolo: Int, idMotivo: Int) : Option[Motivo] = {
    if(idMotivo != -1){
      motivoRepository.retrieve(idProtocolo, 0, idMotivo)
    } else {
      None
    }
  }

}
