package controllers

import javax.inject.Inject
import javax.inject.Singleton
import com.decidir.coretx.domain.TerminalRepository
import play.api.mvc.Action
import scala.util.Try
import scala.util.Success
import play.api.mvc.Controller
import scala.util.Failure
import com.decidir.coretx.domain.NroTraceRepository
import org.slf4j.LoggerFactory
import com.decidir.coretx.domain.TransactionRepository
import com.decidir.coretx.domain.NrosTraceSite
import scala.util.Success
import scala.util.Failure


@Singleton
class TerminalsController @Inject() (terminalRepository: TerminalRepository,
    transactionRepository: TransactionRepository,
    nroTraceRepository: NroTraceRepository) extends Controller {

  val logger = LoggerFactory.getLogger(getClass)
  
  def acquireSpecificTerminal(idSite: String, idMedioPago: Int, idProtocolo: Int, txId: String, terminal: String) = Action {
    val v = terminalRepository.acquireSpecificTerminal(idSite, idMedioPago, idProtocolo, txId, terminal) 
    println (v)
    if (v) Ok else Locked
  }
  
  def acquire(idSite: String, idMedioPago: Int, idProtocolo: Int, txId: String) = Action {
    
    Try{terminalRepository.acquireTerminal(idSite, idMedioPago, idProtocolo, txId)} match {
      case Success(termId) => Ok(termId)
      case Failure(e) => Locked
    }
    
  }

  
  def release(idSite: String, idMedioPago: Int, idProtocolo: Int, idTerminal: String) = Action {
    try{
      terminalRepository.releaseTerminal(idSite, idMedioPago, idProtocolo, idTerminal)
      Ok
    } 
    catch {
      case e: Exception => Gone
    }
  }
  
  
  def nextTrace(idSite: String, idBackEnd: String, nroTerminal: String, tipo: String) = Action {
    Try {nroTraceRepository.getProximoNro(idSite, idBackEnd, nroTerminal, tipo)} match {
      case Success(nro) => {
        tipo match {
          case "2" => {
            /*
             * Cualquier cosa verlo con Biandra
             * Nro Batch actualizada automaticamente, ya q es requerido por el viejo sac.
             * A futuro será removida esta opcion, cuando se realice el nuevo cierre.
             */
            Try{transactionRepository.updateNroBatch(NrosTraceSite(idSite, idBackEnd.toInt, nroTerminal, -1, -1, nro))} match {
              case Success(_) => Ok(nro.toString)
              case Failure(error) => {
                logger.error("Error al almacenar el nro de batch", error)
                InternalServerError
              }
            }
          }
          case _ => Ok(nro.toString)
        }
      }
      case Failure(e) => { 
        logger.error("Error al obtener numero de trace",e)
        InternalServerError
      }
    }
  }
  
  
}