package services.protocol

import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import controllers.MDCHelperTrait
import com.decidir.coretx.domain.TerminalRepository
import com.decidir.protocol.api.ProtocolResource
import scala.util.Try
import scala.util.Success
import com.decidir.coretx.domain.NroTraceRepository
import scala.util.Failure
import com.decidir.protocol.api.TerminalYTickets
import com.decidir.coretx.api.OperationExecutionResponse
import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.api.SubTransaction
import scala.collection.mutable.ArrayBuffer
import services.PaymentMethodService

@Singleton
class TerminalService @Inject() (implicit executionContext: ExecutionContext,
    terminalRepository: TerminalRepository,
    nroTraceRepository: NroTraceRepository,
    paymentMethodService: PaymentMethodService)  extends MDCHelperTrait {
  
  def acquireTerminal(opData: OperationData): Try[OperationData] = {
    updateMDCFromOperation(opData.resource)
	  val paymentMethodId = opData.medioDePago.id.toInt
	  val protocoloId = opData.cuenta.idProtocolo
	  val backendId = opData.cuenta.idBackend
	  val isCardP = isCardPresent(opData)
	  
    if(!opData.datosSite.id_modalidad.getOrElse("N").equals("S")){
	    val siteId = opData.site.id
	    acquireTerminal(siteId, paymentMethodId, protocoloId, backendId, isCardP) match {
	      case Success(terminalYTickets) => {
	    	  Success(opData.copy(resource = opData.resource.copy(datos_medio_pago = opData.resource.datos_medio_pago.map(
	    	      dmp => dmp.copy(nro_terminal = Some(terminalYTickets.nro_terminal), 
	    	      nro_ticket = Some(terminalYTickets.nro_ticket.toString), 
	    	      nro_trace = Some(terminalYTickets.nro_trace.toString))))))      
	      }
	      case Failure(exception) => Failure(exception)
	    }
    } else {
      subTransactionsWithTerminal(opData, isCardP) match {
        case Success(subTransactions) => Success(opData.copy(resource = opData.resource.copy(sub_transactions = subTransactions)))
        case Failure(exception) => Failure(exception)
      }
    }
  }
  
  private def subTransactionsWithTerminal(opData: OperationData, isCardP: Boolean):Try[List[SubTransaction]] = {
	  val paymentMethodId = opData.medioDePago.id.toInt
	  val protocoloId = opData.cuenta.idProtocolo
	  val backendId = opData.cuenta.idBackend
	  val subT = ArrayBuffer[SubTransaction]()
	  
	  Try{
	    opData.resource.sub_transactions.map(st =>{
        val siteId = st.site_id
        acquireTerminal(siteId, paymentMethodId, protocoloId, backendId, isCardP) match {
          case Success(terminalYTickets) => {
            subT.+=(st.copy(terminal = Some(terminalYTickets.nro_terminal), 
      	      nro_ticket = Some(terminalYTickets.nro_ticket.toString), 
      	      nro_trace = Some(terminalYTickets.nro_trace.toString)))
          } 
          case Failure(exception) => {
            throw exception
          }
        }
      })
    } match {
      case Success(_) => Success(subT.toList)
      case Failure(error) =>{
        subT.map(st => releaseTerminal(st.site_id, paymentMethodId, protocoloId, st.terminal.get))
        Failure(error)
      }  
    }
  }
  
  private def acquireTerminal(siteId: String, paymentMethodId: Int, protocoloId: Int, backendId: String, isCardPresent: Boolean): Try[TerminalYTickets] = {
    val transactionId = "0"
    Try{terminalRepository.acquireTerminal(siteId, paymentMethodId, protocoloId, transactionId)} match {
      case Success(terminal) => {
        try {
          val (nroTicket, nroTrace) = getTicketAndTrace(siteId, terminal, protocoloId, backendId, isCardPresent)
          Success(TerminalYTickets(terminal, nroTrace, nroTicket))
        } catch {
          case exception: Throwable => {
            logger.error("Acquire terminal, nroTicket and nroTrace error:", exception)
            releaseTerminal(siteId, paymentMethodId, protocoloId, terminal)
            Failure(exception)
          }
        }  
      }
      case Failure(error) => {
        logger.error("Acquire terminal error:", error)
        Failure(error)
      }  
    }
  }
  
  /**
   * Para master (En caso de no ser tarjeta presente) nro de trace y ticket es seteado con el mismo numero de trace
   * Para cualquier otro caso se setea el trace y ticket con su correspondiente valor
   */
  private def getTicketAndTrace(siteId: String, terminal: String, protocoloId: Int, backendId: String, isCardPresent: Boolean): (Long, Long) = {
    if(protocoloId == 8 && !isCardPresent){
      val nroTrace = nroTraceRepository.getNextTraceNro(siteId, backendId, terminal)
      (nroTrace, nroTrace)
    } else {
      val nroTicket = nroTraceRepository.getNextTicketNro(siteId, backendId, terminal)
      val nroTrace = nroTraceRepository.getNextTraceNro(siteId, backendId, terminal)
      (nroTicket, nroTrace)
    }
  }
  
  private def isCardPresent(opData: OperationData) = {
    val dbt = opData.resource.datos_banda_tarjeta
    (!dbt.flatMap(_.card_track_1).isEmpty || !dbt.flatMap(_.card_track_1).isEmpty) && !dbt.map(_.input_mode).isEmpty
  }
  
  def releaseTerminal(siteId: String, paymentMethodId: Int, protocoloId: Int, terminalId: String)={
    terminalRepository.releaseTerminal(siteId, paymentMethodId, protocoloId, terminalId)
  }
  
  def releaseTerminal(opData: OperationData): Unit = {
    updateMDCFromOperation(opData.resource)
	  val paymentMethodId = opData.medioDePago.id.toInt
	  val protocoloId = opData.cuenta.idProtocolo
    if(!opData.datosSite.id_modalidad.getOrElse("N").equals("S")){
      val siteId = opData.site.id
      val terminalId = opData.resource.datos_medio_pago.flatMap(_.nro_terminal).getOrElse(throw new Exception("not found terminal"))
      releaseTerminal(siteId, paymentMethodId, protocoloId, terminalId)
    } else {
      opData.resource.sub_transactions.map(st => {
        val siteId = st.site_id  
        val terminalId = st.terminal.getOrElse(throw new Exception(s"not found terminal in subtransaction ${siteId}"))
        releaseTerminal(siteId, paymentMethodId, protocoloId, terminalId)
      })
    }
  }
  
}