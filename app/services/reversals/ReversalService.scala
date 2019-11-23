package services.reversals
/*
import javax.inject.Inject
import javax.inject.Singleton
import akka.actor.Props
import com.decidir.coretx.api._
import com.decidir.coretx.domain._
import com.decidir.protocol.api.{OperationResponse, TransactionResponse}
import services.protocol.ProtocolService
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import controllers.MDCHelperTrait
import services.validations.MPOSValidator
import java.util.Date


@Singleton
class ReversalService @Inject()(implicit executionContext: ExecutionContext,
                                protocolService: ProtocolService,
                                transactionRepository: TransactionRepository,
                                singleReversalService: SingleReversalService,
                                siteRepository: SiteRepository,
                                distributedReversalService: DistributedReversalService,
                                siteValidator: MPOSValidator) extends MDCHelperTrait{

  def reverse(siteId:String, chargeId: Long):Future[Try[ReversePaymentResponse]] = {
      transactionRepository.retrieveCharge(siteId, chargeId) match {
        case Success(oer) => {
      	  val operation = oer.operationResource.getOrElse(throw new Exception("operationResource not available in operationExecuteResource"))
          Try{siteValidator.validate(operation, siteId)} match {
            case Success(_) => {
              val state = TransactionState.apply(transactionRepository.retrieveTransState(operation.idTransaccion.getOrElse(throw new Exception("idTransaccion not available in operationResource"))).toInt)
              state match {
                case AReversar() => {
                  updateMDCFromOperation(operation)
                  revers(oer, chargeId)
                }
                case _ => {
              	  logger.warn(s"transaction status: $oer.status, not reversed")
                  Future(throw ErrorFactory.validationException("State error", TransactionState.apply(oer.status).toString()))
                }
              }
            }
            case Failure(error) => {
              updateMDC(siteId = Some(siteId), paymentId = Some(chargeId.toString))
              logger.error("retrive site", error)
              Future(Failure(ErrorFactory.notFoundException("payments", chargeId.toString())))
            } 
        }
      }
      case Failure(error) => {
        logger.error("error to retrieve site", error)
        Future(Failure(error))
      }
    }
  }
  
  private def revers(oer: OperationExecutionResponse, chargeId: Long):Future[Try[ReversePaymentResponse]] = {
    val oerFixed = oer.copy(operationResource = oer.operationResource.map(or => or.copy(last_update = Some(new Date()))))
    (oer.operationResource.flatMap(_.datos_site.flatMap(_.id_modalidad)).getOrElse("N") match {
      case "N" => singleReversalService.reverseTx(oerFixed, chargeId)
      case "S" => distributedReversalService.reverseTx(oerFixed, chargeId)
    }) map (response => Success(response))
  }
}*/

