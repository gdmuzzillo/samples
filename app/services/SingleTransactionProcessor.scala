package services

import java.text.{DecimalFormat, SimpleDateFormat}
import javax.inject.Singleton
import javax.inject.Inject
import com.decidir.coretx.domain
import decidir.sps.core.Protocolos
import services.protocol.Operation2ProtocolConverter
import com.decidir.coretx.domain._
import services.protocol.ProtocolService
import scala.concurrent.ExecutionContext
import com.decidir.protocol.api.TransactionResponse
import scala.util.Failure
import scala.util.Success
import com.decidir.coretx.api.{Subpayment, OperationExecutionResponse, CyberSourceResponse, OperationResource}
import scala.concurrent.Future
import scala.util.Try
import controllers.MDCHelperTrait
import com.decidir.coretx.api._
import services.payments.DistributedTxElement
import services.payments.LegacyTransactionServiceClient
import services.payments.InsertTx
import services.payments.UpdateTx
import com.decidir.protocol.api.ProtocolResource


@Singleton
class SingleTransactionProcessor @Inject() (
                                  context: ExecutionContext, 
                                  legacyTxService: LegacyTransactionServiceClient,
                                  protocolService: ProtocolService,
                                  terminalRepository: TerminalRepository,
                                  transactionProcessor: TransactionProcessor) extends MDCHelperTrait {
  implicit val ec = context
  
  def processSinglePayment(opdata: OperationData,  ocsresponse: Option[CyberSourceResponse]): Future[Try[OperationExecutionResponse]] = {
    val chargeId = opdata.chargeId
    val site = opdata.site
    val op = opdata.resource
    val is2Steps = opdata.cuenta.autorizaEnDosPasos
    
    val fakeResponse = transactionProcessor.transactionResponse2OperationExecutionResponse(None, ocsresponse, opdata, Nil, Some(is2Steps))
    legacyTxService.insert(InsertTx(chargeId, site, None, AProcesar(), fakeResponse))
    
    val protocolCall = Operation2ProtocolConverter.convert(opdata)
    val futureResponse = protocolService.postTx(protocolCall)
    futureResponse.map { tpr =>

      
      tpr match {
          // Caso PMC
        case Success(tr) if opdata.protocolId == 21 && tr.authorized => {
          //Los estados ya fueron actualizados en PMC asincronicamente
        }

        case Success(tr) if tr.authorized => {
          // Caso ok
        	val opr = transactionProcessor.transactionResponse2OperationExecutionResponse(Some(tr), ocsresponse, opdata, Nil, Some(is2Steps))
          legacyTxService.update(UpdateTx(chargeId, site, None, if(is2Steps) PreAutorizada() else Autorizada(), opr, Some(tr)))
        }

        case Success(tr) if tr.idMotivo == 10001 => {
          val opr = transactionProcessor.transactionResponse2OperationExecutionResponse(Some(tr), ocsresponse, opdata, Nil, Some(is2Steps))
          legacyTxService.update(UpdateTx(chargeId, site, None, FalloComunicacion(), opr, Some(tr)))
        }

        case Success(tr) => {
          val opr = transactionProcessor.transactionResponse2OperationExecutionResponse(Some(tr), ocsresponse, opdata, Nil, Some(is2Steps))

          // Caso no autorizada
          legacyTxService.update(UpdateTx(chargeId, site, None, Rechazada(), opr, Some(tr)))
        }

        case Failure(ProtocolError(authCode, reason)) => {
          logger.error(s"Error enviando transaccion a protocolo ${chargeId}, ${op.id}")
        }
      }

      tpr map { tr => transactionProcessor.transactionResponse2OperationExecutionResponse(Some(tr), ocsresponse, opdata, Nil, Some(is2Steps)) }

    }.recover { case ex => {
      logger.error("single transaction", ex)
      Failure(ex)
    }}
  }    
  
}