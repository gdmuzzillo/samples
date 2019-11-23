package services

import com.decidir.coretx.domain.OperationData
import scala.util.Failure
import scala.util.Try
import akka.actor.Actor
import scala.util.Success
import akka.actor.ActorRef
import akka.util.Timeout
import services.protocol.ProtocolService
import akka.pattern.pipe
import scala.concurrent.duration._
import services.protocol.Operation2ProtocolConverter
import com.decidir.protocol.api.TransactionResponse
import com.decidir.coretx.domain.InvalidCard
import com.decidir.coretx.domain.GroupRejected
import akka.actor.ActorLogging
import controllers.MDCHelperTrait
import com.decidir.protocol.api.ProtocolResource
import akka.actor.PoisonPill


class DTXHandlerActor(protocolService: ProtocolService) extends Actor with MDCHelperTrait {

  var oclient: Option[ActorRef] = None
  var requests: List[OperationData] = Nil
  var responses: List[DTXResponse] = Nil
  var oCurrentRequest:Option[OperationData] = None

  def receive = {

    case DTX(reqs) => {
      oclient = Some(sender)
      requests = reqs
      sendOne
    }
    case Success(pr: TransactionResponse) => {
       responses = DTXResponse(currentRequest,Success(pr)) :: responses
        if (pr.authorized)
          sendOne
        else {
          oclient foreach (_ ! completeWithFailures)
          self ! PoisonPill
        }
    }
    case Failure(e) => {
      responses = DTXResponse(currentRequest,Failure(e)) :: responses
      oclient foreach (_ ! completeWithFailures)
      self ! PoisonPill
    }
  }

  private def completeWithFailures = responses.reverse ++ requests.map(r => {
    val sdf = r.resource.datos_medio_pago.flatMap(_.nro_terminal)
    DTXResponse(r,Success(TransactionResponse(402, -1, r.resource.datos_medio_pago.flatMap(_.nro_terminal), r.resource.datos_medio_pago.flatMap(_.nro_trace), 
        r.resource.datos_medio_pago.flatMap(_.nro_ticket), None, None, Some(GroupRejected(-1)), r.site.id, "")))
  })

  private def sendOne() = {
    
    requests match {
      case Nil => {
        oclient foreach (_ ! responses.reverse)
        self ! PoisonPill
      }
      case opdata :: rest => {
        import context.dispatcher
        implicit val timeout = Timeout(10000 millis)
        requests = requests.tail
        oCurrentRequest = Some(opdata)
        val protocolCall = Operation2ProtocolConverter.convert(opdata)
        protocolService.postTx(protocolCall) pipeTo self
      }
    }
  }
  
  private def currentRequest = oCurrentRequest.getOrElse {
    val msg = "Error grave: no existe currentRequest en una transaccion distribuida"
    logger.error(msg)
    throw new Exception(msg)
  }
  
}

case class DTX(requests: List[OperationData])
case class DTXResponse(request:OperationData,response:Try[TransactionResponse])
