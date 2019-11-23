package services.cybersource

import java.net.InetAddress
import javax.inject.Inject

import play.Logger

import scala.concurrent.ExecutionContext

//TODO: Mover los converters a mensaje SOAP
class CybersourceConverter @Inject()(context: ExecutionContext) {

  implicit val ec = context
  def logger = Logger.underlying()

  def normalizeIP(ip: String): String = {
    if("""((([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5]))""".r.unapplySeq(ip).isEmpty){
      logger.debug("ip version 6")
      InetAddress.getByName(ip).getHostAddress
    } else{
      logger.debug("ip version 4")
      ip
    }
  }
}
