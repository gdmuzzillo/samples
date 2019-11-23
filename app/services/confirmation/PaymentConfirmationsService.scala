package services.confirmation

import java.util.Date

import com.decidir.coretx.api._
import com.decidir.coretx.domain._
import com.decidir.protocol.api.OperationResponse
import controllers.MDCHelperTrait
import javax.inject.Inject
import services.converters.OperationResourceConverter
import services.payments._
import services.protocol.{Operation2ProtocolConverter, ProtocolService}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import play.api.Configuration
import com.decidir.util.RefundsLockRepository

class PaymentConfirmationsService @Inject() (context: ExecutionContext,
    transactionRepository: TransactionRepository,
    operationResourceConverter: OperationResourceConverter,
    operationRepository: OperationResourceRepository,
    protocolService: ProtocolService,
    legacyTxService: LegacyTransactionServiceClient,
    legacyOpxService: LegacyOperationServiceClient,
    configuration: Configuration,
    refundsLockRepository: RefundsLockRepository) extends MDCHelperTrait {

  implicit val ec: ExecutionContext = context

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

  def confirm(siteId: String, chargeId: Long, operationResource: OperationResource, user: Option[String] = None): Future[(Option[OperationExecutionResponse], Try[OperationResponse])] = {

    transactionRepository.retrieveCharge(siteId, chargeId) match {
      case Success(oer) =>
        logger.info("retrieve charge success")
        val operation = oer.operationResource.getOrElse {
          logger.error("operationResource not available in operationExecuteResource")
          throw new Exception("operationResource not available in operationExecuteResource")
        }
        loadMDCFromOperation(operation)
        val state = TransactionState.apply(transactionRepository.retrieveTransState(operation.idTransaccion.get).toInt)
        state match {
          case PreAutorizada() =>
          	logger.debug(s"OperationExecutionResponse.status: $state")
          	paymentConfirm(oer, operationResource.monto, user)

          case otherState =>
            logger.error(s"OperationExecutionResponse.status: $state")
            Future(None, Failure(ErrorFactory.validationException("State error", otherState.toString)))
        }
      case Failure(_) =>
        logger.error("retrieve charge failure")
        Future(None, Failure(ErrorFactory.notFoundException("confirm payment", chargeId.toString)))
    }
  }
  
  private def paymentConfirm(oer: OperationExecutionResponse, amount: Option[Long], user: Option[String] = None) = {
    val opData = operationResourceConverter.operationResource2OperationData(oer.operationResource.get)
    val originAmount = opData.resource.monto.getOrElse(0L)
    val amountToConfirm = amount.getOrElse(originAmount) 
    validate(oer, opData.cuenta,  amountToConfirm)
    
    val protocolCall = Operation2ProtocolConverter.convert(opData.copy(resource = opData.resource.copy(monto = Some(amountToConfirm))))
    operationRepository.transitionOperation(opData.resource.id, InProcess(PaymentConfirm()))
    protocolService.paymentConfirm(protocolCall) map { response =>
      val (updatedOperationResource, updatedOperationResponse) = response match {
        case Success(result) =>
          logger.info(s"payment confirm Success Result: $result")
          result.statusCode match {
            case 200 =>
              val confirmPaymentResponse = ConfirmPaymentResponse(id = operationRepository.newPaymentConfirmId, origin_amount = originAmount, new Date)
              val newOpData = opData.copy(resource = opData.resource.copy(monto = Some(amountToConfirm), confirmed = Some(confirmPaymentResponse)))

              legacyOpxService.insert(InsertConfirmationOpx(newOpData, result, confirmPaymentResponse, user))
              legacyTxService.update(UpdateTxOnOperation(newOpData, Autorizada(), None, result, opData.resource.idTransaccion, user))

              tryRefundsLock(opData.chargeId)

              legacyTxService.insert(InsertTxHistorico(None, Some(opData.chargeId), opData.cuenta.idProtocolo, Autorizada().id, None, Some(System.currentTimeMillis()), None, opData.resource.nro_operacion))

              operationRepository.transitionOperation(opData.resource.id, PaymentConfirm())
              (Some(oer.copy(operationResource = Some(newOpData.resource), status = Autorizada().id)), response)

            case other =>
              logger.warn("payment confirm operation: " + other)
              //TODO: q hacemos? se guarda intento de operacion?
              operationRepository.transitionOperation(opData.resource.id, PaymentConfirmFailed())
              (Some(oer.copy(cardErrorCode = result.cardErrorCode)), response)
          }
        case Failure(_) =>
          logger.error("PaymentConfirmationsService.cancel Failure")
          operationRepository.transitionOperation(opData.resource.id, PaymentConfirmFailed())
          (Some(oer.copy(cardErrorCode = Some(ProcessingError()))), response)
      }
    (updatedOperationResource, updatedOperationResponse)
    }
  }
  
  private def validate(oer: OperationExecutionResponse, cuenta: Cuenta, amount: Long): Unit = {
    TransactionState.apply(oer.status) match {
      case PreAutorizada() => logger.debug(s"OperationExecutionResponse.status: ${oer.status}")
      case otherState => throw ErrorFactory.validationException("State error", otherState.toString)
    }
    val currentAmount = oer.operationResource.flatMap(_.monto).getOrElse(throw new Exception("Error grave, no se encontro el monto"))
    val amountLimitInf = (currentAmount * (1 - cuenta.autoriza2PLimiteInferior.toDouble/100)).toLong
    val amountLimitSup = (currentAmount * (1 + cuenta.autoriza2PLimiteSuperior.toDouble/100)).toLong
    
    if(amount < amountLimitInf || amount > amountLimitSup) {
      logger.warn(s"Amount: $amount out of ranges: $amountLimitInf - $amountLimitSup. CurrentAmount: $currentAmount")
      throw ErrorFactory.validationException("amount", s"Amount out of ranges: $amountLimitInf - $amountLimitSup")
    }
  }
}
