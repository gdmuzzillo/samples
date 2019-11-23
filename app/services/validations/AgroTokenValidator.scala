package services.validations

import javax.inject.{Inject, Singleton}

import com.decidir.coretx.api._
import com.decidir.coretx.api.AgroJsonFormats._
import controllers.MDCHelperTrait

import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._


@Singleton
class AgroTokenValidator @Inject() (implicit context: ExecutionContext, configuration: Configuration,  ws: WSClient)
  extends MDCHelperTrait {

  val agroValidationUrl = configuration.getString("sps.agro.url").getOrElse("http://localhost:10117/validateToken/")
  val agroValidationTimeout = configuration.getInt("sps.agro.timeoutMillis").getOrElse(5000)

  def validate(operationResource: OperationResource): Future[AgroTokenValidatorResponse] = {

    try{
      val fresponse = ws.url(agroValidationUrl).withRequestTimeout(agroValidationTimeout millis) post (Json.toJson(operationResource))

      fresponse.map(response => {
        val json = response.json
        AgroJsonFormats.agroTokenValidatorResponseReads.reads(json) fold(
          errors => AgroTokenValidatorResponse("3", Some("Unable to process Banelco Token Agro validations response")),
          agroTokenValidationResp => agroTokenValidationResp
        )
      }).recover({case x =>
        logger.error("Error en acceso a Agro Token Validator Microservice: ", x)
        AgroTokenValidatorResponse("3", Some("Error while trying to send request to Banelco Token Agro validator"))
      })
    }
    catch {
      case ApiException(_) => {
        Future(AgroTokenValidatorResponse("3", Some("Unable to resolve Banelco connection")))
      }
    }

  }

}
