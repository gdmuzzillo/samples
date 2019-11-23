package services

import javax.inject.Singleton
import javax.inject.Inject
import play.api.Configuration
import com.decidir.coretx.domain.TerminalRepository
import akka.actor.ActorSystem
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.Success
import org.slf4j.LoggerFactory
import scala.util.Failure

/**
 * @author martinpaoletta
 */
@Singleton
class HungTerminalsReleaser @Inject() (configuration: Configuration, terminalRepository: TerminalRepository, system: ActorSystem, context: ExecutionContext) {

  val pollPeriod = configuration.getLong("sps.terminales.ghostCollector.pollPeriodMillis").getOrElse(5000l)
  val timeoutMillis = configuration.getLong("sps.terminales.ghostCollector.timeoutMillis").getOrElse(50000l)
  val logger = LoggerFactory.getLogger(getClass)
  implicit val ec = context
  
  system.scheduler.schedule(1000 millis, pollPeriod millis)(releaseHungTerminals())
  
  def releaseHungTerminals() = {
    terminalRepository.releaseHungTerminals(timeoutMillis) match {
      case Success(_) => logger.debug("hung terminals")
      case Failure(error) => logger.error("hung terminals", error)
    }
  }
  
}