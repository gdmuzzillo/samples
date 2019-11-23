package services

import services.protocol.ProtocolService
import services.protocol.Operation2ProtocolConverter
import akka.actor.Actor
import com.decidir.coretx.domain.OperationData
import scala.concurrent.Future
import scala.util.Try
import com.decidir.protocol.api.OperationResponse
import scala.util.Success
import scala.util.Failure
import akka.util.Timeout
import akka.actor.ActorRef
import com.decidir.coretx.domain.ProcessingError
import akka.pattern.pipe
import controllers.MDCHelperTrait
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.domain.Operation
import com.decidir.coretx.domain.CancelOperation
import com.decidir.coretx.domain.RefundOperation
import com.decidir.coretx.domain.PartialRefundOperation
import com.decidir.coretx.domain.PartialRefundBeforeClosureOperation
import com.decidir.coretx.domain.NonExistanceOperation
import com.decidir.coretx.domain.MeanPaymentErrorOperation
import com.decidir.coretx.api.TransactionState
import com.decidir.coretx.api.ApiException
import com.decidir.coretx.api.InvalidRequestError
import com.decidir.coretx.api.ValidationError
import com.decidir.coretx.domain.CancelOperationPostPayment
import com.decidir.coretx.domain.ReverseOperationPostPayment
import com.decidir.coretx.domain.CancelOperationPostPaymentWithCs

class DOPXHandlerActor (protocolService: ProtocolService) extends Actor with MDCHelperTrait {

  var oclient: Option[ActorRef] = None
  var requests: List[DOPX] = Nil
  var responses: List[DOPXResponse] = Nil
  var oCurrentRequest:Option[OperationData] = None
  var oCurrentOperationType:Option[Operation] = None

  def receive = {

    case DOPXs(reqs) => withMDC {
      oclient = Some(sender)
      requests = reqs
      sendOne
    }

    case Success(pr: OperationResponse) => withMDC {
      responses = DOPXResponse(currentRequest,currentOperation,Success(pr)) :: responses
      sendOne
    }

    case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => withMDC {
      responses = DOPXResponse(currentRequest,currentOperation,Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param)))))) :: responses
      sendOne
    }
        
    case Failure(e) => withMDC {
      responses = DOPXResponse(currentRequest,currentOperation,Failure(e)) :: responses
      sendOne
    }
  }

  private def sendOne() = {
    requests match {
      case Nil => {
        oclient foreach (_ ! responses.reverse)
      }

      case dopx :: rest => {
        import context.dispatcher
        oCurrentRequest = Some(dopx.req)
        oCurrentOperationType = Some(dopx.operationType)
        requests = requests.tail
        
        updateMDC(Some(currentRequest.resource.siteId), Some(currentRequest.resource.id), currentRequest.resource.nro_operacion, Some(currentRequest.resource.referer))
        val protocolCall = Operation2ProtocolConverter.convert(dopx.req)
        dopx.operationType  match {
            case CancelOperation() => {
              logger.info("Cancel operation")
              protocolService.cancel(protocolCall) pipeTo self
            }
            case RefundOperation() => {
              logger.info("Total refund operation")
              protocolService.refundAfter(protocolCall) pipeTo self
            }
            case PartialRefundOperation() => {
              logger.info("Partial Refund operation")
              protocolService.refundAfter(protocolCall) pipeTo self
            }
            case PartialRefundBeforeClosureOperation() => {
              logger.info("Partial Refund BeforeClosure operation")
              protocolService.refund(protocolCall) pipeTo self
            }
            case CancelOperationPostPayment() => {
              logger.info("Cancel operation post payment")
              protocolService.cancel(protocolCall, Some(true)) pipeTo self
            }
            case CancelOperationPostPaymentWithCs() => {
              logger.info("Cancel operation post payment and cs call")
              protocolService.cancel(protocolCall) pipeTo self
            }
            case ReverseOperationPostPayment() => {
              logger.info("Reverse operation post payment")
              protocolService.reverse(protocolCall) pipeTo self
            }
            case NonExistanceOperation(id) => {
              logger.error(s"none match Operation: $id")
              self ! Failure(ErrorFactory.validationException("State error", TransactionState.apply(id).toString())) 
            }
            case MeanPaymentErrorOperation(id) => {
              logger.error(s"none match Operation: $id")
              self ! Failure(ErrorFactory.validationException("State error, invalid MeanPayment for operation", TransactionState.apply(id).toString())) 
            }
         }
      }
    }
  }
  
  private def currentRequest = oCurrentRequest.getOrElse {
    val msg = "Error grave: no existe currentRequest en una transaccion distribuida"
    logger.error(msg)
    throw new Exception(msg)
  }
  
  private def currentOperation = oCurrentOperationType.getOrElse {
    val msg = "Error grave: no existe currentOperation en una transaccion distribuida"
    logger.error(msg)
    throw new Exception(msg)
  }
  
}

case class DOPXs(requests:List[DOPX])
case class DOPX(req: OperationData, operationType: Operation, subpaymentId:Long)
case class DOPXResponse(request:OperationData, operationType: Operation, response:Try[OperationResponse])

