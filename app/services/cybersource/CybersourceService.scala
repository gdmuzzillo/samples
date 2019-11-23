package services.cybersource

import scala.concurrent.ExecutionContext
import javax.inject.Inject
import controllers.MDCHelperTrait
import com.decidir.coretx.domain.TransactionRepository
import scala.util.Success
import scala.util.Failure
import scala.concurrent.Future
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.ReviewCS
import com.decidir.coretx.api.FraudDetectionDecision
import javax.inject.Singleton
import services.converters.OperationResourceConverter
import scala.util.Try
import com.decidir.coretx.api.OperationExecutionResponse
import services.payments.LegacyTransactionServiceClient
import services.payments.UpdateXref
import services.payments.UpdateCS
import services.payments.InsertTxHistorico
import services.PaymentMethodService
import com.decidir.coretx.api.Green
import com.decidir.coretx.api.Red
import services.refunds.RefundService
import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.api.RefundPaymentResponse
import com.decidir.protocol.api.OperationResponse

@Singleton
class CybersourceService @Inject() (context: ExecutionContext,
    transactionRepository: TransactionRepository,
    operationResourceConverter: OperationResourceConverter,
    legacyTxService: LegacyTransactionServiceClient,
    paymentMethodService: PaymentMethodService,
    postbackCSService: PostbackCSService,
    refundService: RefundService) extends MDCHelperTrait {
  
  implicit val ec = context
  
  def changeState(reviewCS: ReviewCS): Future[Try[OperationExecutionResponse]] = {
    loadMDC()
    transactionRepository.retrieveCharge(reviewCS.id) match {
      case Success(oer) => {
        loadMDCFromOperation(oer.operationResource.get)
        reviewCS.decision match {
          case FraudDetectionDecision.red => cancel(oer).map {_ match {
              case Failure(fail) => {
                logger.error("process: Throwable", fail)
                val or = oer.operationResource.getOrElse(throw new Exception("undefined operationResource"))
                val csr = or.fraud_detection.flatMap(_.status).getOrElse(throw new Exception("undefined CyberSourceResponse"))
                legacyTxService.update(UpdateCS(oer, csr, true))
              	Failure(fail)
              } 
              case Success(oerRed) => {
                logger.info("cs set Red value")
                val operationExecutionResponse = oerRed.copy(operationResource = oerRed.operationResource.map( or => or.copy(
                    last_update = Some(new java.util.Date),
                    fraud_detection = or.fraud_detection.map(fd => 
                        fd.copy(status = fd.status.map(state => state.copy(decision = FraudDetectionDecision.red, review = Some(reviewCS.review))))))))
                val or = operationExecutionResponse.operationResource.getOrElse(throw new Exception("undefined operationResource"))
                val csr = operationExecutionResponse.operationResource.flatMap(_.fraud_detection.flatMap(_.status)).getOrElse(throw new Exception("undefined CyberSourceResponse"))
                val chargeId = operationExecutionResponse.operationResource.flatMap(_.charge_id).getOrElse(0l)
                legacyTxService.update(UpdateXref(chargeId, operationExecutionResponse))
                legacyTxService.update(UpdateCS(operationExecutionResponse, csr, false))
                legacyTxService.insert(InsertTxHistorico(None, Some(chargeId), paymentMethodService.getProtocolId(or), Red().id, None, Some(System.currentTimeMillis()), None, or.nro_operacion))
              	postbackCSService.doPost(operationExecutionResponse)
                Success(operationExecutionResponse)
              }
            }
          }
          case FraudDetectionDecision.green => {
              logger.info("cs set Green value")
              val operationExecutionResponse = oer.copy(operationResource = oer.operationResource.map( or => or.copy(
                    last_update = Some(new java.util.Date),
                    fraud_detection = or.fraud_detection.map(fd => 
                        fd.copy(status = fd.status.map(state => state.copy(decision = FraudDetectionDecision.green, review = Some(reviewCS.review))))))))
              val or = operationExecutionResponse.operationResource.getOrElse(throw new Exception("undefined operationResource"))
              val csr = operationExecutionResponse.operationResource.flatMap(_.fraud_detection.flatMap(_.status)).getOrElse(throw new Exception("undefined CyberSourceResponse"))
              val chargeId = operationExecutionResponse.operationResource.flatMap(_.charge_id).getOrElse(0l)
              legacyTxService.update(UpdateXref(chargeId, operationExecutionResponse))
              legacyTxService.update(UpdateCS(operationExecutionResponse, csr, false))
              legacyTxService.insert(InsertTxHistorico(None, Some(chargeId), paymentMethodService.getProtocolId(or), Green().id, None, Some(System.currentTimeMillis()), None, or.nro_operacion))
            	postbackCSService.doPost(operationExecutionResponse)
              Future(Success(operationExecutionResponse))
          }
          case other => {
            logger.error(s"Invalid state: ${other.toString}")
            Future(Failure(ErrorFactory.notFoundException("requestId", reviewCS.id)))
          }
        }
      }
      case Failure(error) => {
        logger.warn(s"retrieve charge with requestId ${reviewCS.id} failure")
        Future(Failure(ErrorFactory.notFoundException("requestId", reviewCS.id)))
      }
    }
  }
  
  private def cancel(oer: OperationExecutionResponse): Future[Try[OperationExecutionResponse]] = {
    val operationData = operationResourceConverter.operationResource2OperationData(oer.operationResource.get)
    refundService.cancelOnCSResponse(operationData).map(rprTor => 
    canceled2OperationExecutionResponse(oer, rprTor._1, rprTor._2))
  }
  
  
  private def canceled2OperationExecutionResponse(oer: OperationExecutionResponse, refundPaymentResponse : Option[RefundPaymentResponse], tor: Try[OperationResponse]): Try[OperationExecutionResponse] = {
    tor map{ or => {
        val rPaymentResponse = refundPaymentResponse.get
        oer.copy(authorized = false, 
          status = rPaymentResponse.status.id,
          operationResource = oer.operationResource.map(or => or.copy(sub_transactions = or.sub_transactions
              .map(st => st.copy(status = Some(rPaymentResponse.sub_payments.get.find{sp => sp.id == st.subpayment_id.get}.get.status.id)))))
        )
      }
    }
  } 
}
