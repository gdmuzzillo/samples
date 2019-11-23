package services.tokens

import java.net.URLEncoder
import java.util.UUID

import javax.inject.Singleton
import javax.inject.Inject
import services.converters.{CardTokenMessageConverter, OperationResourceConverter}
import services.KafkaClient

import scala.concurrent.{Await, ExecutionContext, Future}
import com.decidir.coretx.api._
import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.domain.MedioDePagoRepository
import controllers.MDCHelperTrait

import scala.util.{Failure, Success, Try}
import com.decidir.coretx.domain.SiteRepository
import com.decidir.coretx.messaging.IdempotentStore
import play.api.Configuration
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import com.decidir.coretx.api.CardTokenJsonFormats._

import scala.concurrent.duration._

private object CardTokenListener {
  
  val topic = "card-token-topic"
  
}

@Singleton
class TokensService @Inject() (implicit executionContext: ExecutionContext,
    operationResourceConverter: OperationResourceConverter,
    medioDePagoRepository: MedioDePagoRepository,
    siteRepository: SiteRepository,
    kafkaClient: KafkaClient, 
    idempotentStore: IdempotentStore,
    ws: WSClient,
    config: Configuration,
    cardTokenMessageConverter: CardTokenMessageConverter) extends MDCHelperTrait {

  val urlTokenization: String = config.getString("sps.tokenization.url").getOrElse(throw new Exception("No se configuro sps.tokenization.url"))
  val timeoutMillis: FiniteDuration = (config.getLong("sps.tokenization.timeoutMillis").getOrElse(5000l)) millis

  def getToken(siteId: String, userId: String, cardNumber: String): Future[Try[CardTokens]] = {
    logger.debug("Calling Tokenization for token")
    //val user_id = op.user_id.getOrElse(throw new Exception("Missing user_id"))
    //val nro_tarjeta = op.datos_medio_pago.flatMap(_.nro_tarjeta).getOrElse(throw new Exception("Missing nro_tarjeta"))
    val encodedUser = URLEncoder.encode(userId, "UTF-8").replace("+", "%20")
    val url = s"${urlTokenization}/sites/$siteId/usersite/${encodedUser}/cardtokens?cardNumber=${cardNumber}"
    handleCardToken(ws.url(url).withRequestTimeout(timeoutMillis).get())
  }

  def handleCardToken(futureResponse: Future[WSResponse]): Future[Try[CardTokens]] = {
    futureResponse.map { response: WSResponse =>
      logger.debug("handleCardTokenResponse " + response)

      val tokenResponse = response.json.validate[CardTokens] match {
        case e: JsError => Failure(new Exception("Error validando CardToken" + e))
        case JsSuccess(ct, _) => Success(ct)
      }
      tokenResponse
    }.recover{ case x =>
      logger.error("Error procesando respuesta de tokenizacion", x)
      Failure(x)
    }
  }

  def sendToken(operationData: OperationData): Future[Option[String]] = {
    isTokenizaitionEnabled(operationData) match {
      case false => Future.successful(None)
      case true => {
        operationData.resource.user_id match {
          case Some(userId) if userId.trim.nonEmpty => {
            val userId = operationData.resource.user_id.getOrElse(throw new Exception("Missing user_id"))
            val cardNumber = operationData.resource.datos_medio_pago.flatMap(_.nro_tarjeta).getOrElse(throw new Exception("Missing nro_tarjeta"))

            getToken(operationData.resource.siteId, userId, cardNumber) map {
              case Success(cardTokens) => {
                val tokenId = if (cardTokens.tokens.isEmpty) {
                  //crear uuid hasheado 256
                  org.apache.commons.codec.digest.DigestUtils.sha256Hex(UUID.randomUUID().toString)
                } else {
                  cardTokens.tokens.head.token
                }
                storeToken(operationData, tokenId) // Se manda a persisitir por si se debe actualizar el merchant
                Option(tokenId)
              }
              case Failure(error) => {
                logger.error("Error while get Card Token", error)
                None
              }
            }


          }
          case _ =>
            logger.warn("The site contains tokenization true but the costumer id is empty")
            Future.successful(None)
        }
      }
    }
  }
  
  private def isTokenizaitionEnabled(operationData: OperationData): Boolean = {
    val or = operationData.resource
    or.user_id.map(userId => {
      //Valida si se esta intentando tokenizar un siteMerchant
      val siteId = or.datos_site.flatMap(site => {
        if (site.origin_site_id.equals(site.site_id)) {
          site.origin_site_id
        } else {
          site.site_id
        }
      }).getOrElse(or.siteId)

      val site = siteRepository.retrieve(siteId).getOrElse(throw new Exception("Error grave, intentando obtener informacion del sitio"))
      val paymentMethod = medioDePagoRepository.retrieve(operationData.medioDePago.id).getOrElse(throw new Exception("Error grave, intentando obtener el medio de pago"))

      (site.isTokenized, paymentMethod.tokenized) match {
        case (false, _) => {
          logger.debug(s"Tokenization is not configured for site ${site.id}")
          false
        }
        case (_, false) => {
          logger.debug(s"Tokenization is not configured for payment method ${paymentMethod.id}")
          false
        }
        case (true, true) => {
          logger.debug("Pay tokenizated, save token")
          true
        }
      }
    }).getOrElse {
      logger.warn("userId not available for tokenization")
      false //No user no tokeniza
    }
  }

  def storeToken(operationData: OperationData, token: String) = {
    idempotentStore.createId("tokenization", operationData.chargeId)
    val message = cardTokenMessageConverter.operationResource2CardTokenMessage(operationData.resource, token)
    kafkaClient.send(cardTokenMessage = message, topic = CardTokenListener.topic)
  }
}