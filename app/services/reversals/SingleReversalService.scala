package services.reversals
/*
import scala.concurrent.ExecutionContext
import controllers.MDCHelperTrait
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
import decidir.sps.core.Motivo
import decidir.sps.core.Protocolos
import services.payments.UpdateReverse
import services.payments.InsertOpx
import com.decidir.protocol.api.HistoricalStatus
import com.decidir.core.EstadoOperacionSPS
import java.util.Date


class SingleReversalService @Inject() (
    context: ExecutionContext,
    operationResourceConverter: OperationResourceConverter,
    protocolService: ProtocolService,
    legacyTxService: LegacyTransactionServiceClient,
    legacyOpService: LegacyOperationServiceClient) extends MDCHelperTrait {
  
  implicit val ec = context
  
  def reverseTx(oer: OperationExecutionResponse, chargeId: Long): Future[ReversePaymentResponse] = {
    logger.info(s"Reverse transaction with chargeId: ${chargeId}")
    val operation = oer.operationResource.getOrElse(throw new Exception("operationResource not available in operationExecuteResource"))
    val opData = operationResourceConverter.operationResource2OperationData(operation)
    val amount = opData.resource.monto.getOrElse(throw new Exception("amount not available in operationExecuteResource"))
    val protocolCall = Operation2ProtocolConverter.convert(opData)
    val isMascercad = opData.cuenta.idProtocolo.equals(Protocolos.codigoProtocoloMastercard)
    protocolService.reverse(protocolCall) map {
    	loadMDCFromOperation(operation)
      _ match {
        case Failure(exception) => {
          logger.error(s"Reverse error", exception)
          ReversePaymentResponse(amount = amount, status = Rechazada(), card_error_code = Some(ProcessingError()))
        }
        case Success(response) => {
          val masterNotExisted = mastercardOriginalNotExisted(isMascercad, response)
          if (response.authorized || masterNotExisted){
            logger.info("reverse Success")
            legacyTxService.update(UpdateReverse(opData, None, opData.resource.idTransaccion, Rechazada()))
            //TODO: revisar donde obtiene el estado historico, y mejorarlo
            legacyOpService.insert(InsertOpx(opData, response.copy(historicalStatusList = List(HistoricalStatus(EstadoOperacionSPS.getEstadoReversada.getIdEstado,-1,new Date()))), opData.resource.idTransaccion, None))
            ReversePaymentResponse(amount = amount, status = Autorizada(), reversed_in_brand = Some(!masterNotExisted))
          } else {
            logger.warn(s"Reverse operation failure: state: ${response.statusCode}")
            ReversePaymentResponse(amount = amount, status = Rechazada(), card_error_code = response.cardErrorCode)
          }
        }
      }
    }
  }
  
  private def mastercardOriginalNotExisted(isMascercad:Boolean, or: OperationResponse):Boolean = {
    (!or.authorized && isMascercad && or.idMotivo == Motivo.TRX_ORIGINAL_INEXISTENTE)
  }
  
  
}*/