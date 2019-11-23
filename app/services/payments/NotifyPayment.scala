package services.payments

import javax.inject.{Inject, Singleton}

import com.decidir.coretx.api.OperationExecutionResponse
import com.decidir.coretx.domain.SiteRepository
import controllers.MDCHelperTrait
import services.KafkaClient

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

private object NotifyPaymentListener {

  val topic = "payment-notification-topic"

}

@Singleton
class NotifyPayment @Inject()(implicit executionContext: ExecutionContext,
                              kafkaClient: KafkaClient,
                              siteRepository: SiteRepository) extends MDCHelperTrait {

  def evalEncryption(oer: OperationExecutionResponse): OperationExecutionResponse = {
    val or = oer.operationResource.getOrElse(throw new Exception("Missing operationResource"))
    val oenc = or.origin.flatMap(_.isEncrypted) match {
      case Some(true) => {
        if (or.origin.map(_.encryptionType).isDefined)
          Some(siteRepository.getDefaultEncryptor(or.origin.get.encryptionType.get))
        else
          Some(siteRepository.getEncryptor(or.datos_site.flatMap(_.site_id).getOrElse(throw new Exception("Missing siteId"))))

      }
      case other => None
    }
    if (oenc.isDefined) {
      val enc = oenc.get match {
        case Success(encriptador) => encriptador
        case Failure(error) => {
          logger.error("error missing encrypt", error)
          throw new Exception("Missing encrypt")
        }
      }

      val postback = oer.postbackHash.getOrElse(throw new Exception("Missing postbackHash"))
      oer.copy(postbackHash = Some(postback.map(el => (el._1, enc.encriptar(el._2)))))
    } else
      oer
  }

  def sendPaymentNotifications(oer: OperationExecutionResponse) = {
    val or = oer.operationResource.getOrElse(throw new Exception("Missing operationResource"))
    if (or.origin.flatMap(_.app).getOrElse("").equals("WEBTX")) {
      logger.warn(s"NotifyPayment not sent - request from WEBTX")
    }
    else {
      logger.debug("NotifyPayment")
      kafkaClient.send(operationExecutionResponse = evalEncryption(oer), topic = NotifyPaymentListener.topic)
    }
  }

  def sendConfirmationPaymentOffline(oer: OperationExecutionResponse) = {
    logger.info("Confirmation Payment Offline Begin...")
    kafkaClient.send(operationExecutionResponse = evalEncryption(oer), topic = NotifyPaymentListener.topic)
    logger.info("Confirmation Payment Offline Success!")
  }

  def sendUpdate(oer: OperationExecutionResponse) = {
    logger.debug("Sending update")
    kafkaClient.send(operationExecutionResponse = evalEncryption(oer), topic = NotifyPaymentListener.topic)
  }
}