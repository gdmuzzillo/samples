package controllers

import javax.inject.Inject

import com.decidir.coretx.api.{ErrorFactory}
import com.decidir.coretx.domain.DecidirConfiguration
import play.api.libs.json.{JsError, Json}
import play.api.mvc.{Action, BodyParsers, Controller}
import services.ConfigurationService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Created by ivalek on 4/26/18.
  */
class ConfigurationController @Inject()(implicit context: ExecutionContext,
                                        configurationService: ConfigurationService)
                                        extends Controller with MDCHelperTrait {

  def replace() = Action.async(BodyParsers.parse.json) { implicit request =>

    val configuration = request.body.validate[DecidirConfiguration]

    configuration.fold(
      errors => {
        val jsonErrors = JsError.toJson(errors)
        logger.error("bad request " + jsonErrors)
        Future(BadRequest(Json.obj("status" ->"KO", "message" -> jsonErrors)))
      },
      {
        config =>
          val json = Json.toJson(config)
          configurationService.put(config) match {
            case Failure(error) => {
              logger.error(s"Error in PUT /configuration with payload = ${json}", error)
              Future(InternalServerError(ErrorFactory.uncategorizedError(error).toJson))
            }
            case Success(x) => {
              logger.info(s"Successful PUT /configuration with payload = ${json}")
              Future(Ok(json))
            }
          }
      })
  }

//  def retrieve(id: String) = Action.async(BodyParsers.parse.json) { implicit request =>
//
//    configurationService.get(id) match {
//      case Failure(error) => {
//        logger.error(s"Error in GET /configuration/$id", error)
//        Future(InternalServerError(ErrorFactory.uncategorizedError(error).toJson))
//      }
//      case Success(None) => {
//        logger.warn(s"NOT FOUND = GET /configuration/$id")
//        Future(NotFound)
//      }
//      case Success(Some(config)) => {
//        val json = Json.toJson(config)
//        logger.debug(s"GET /configuration/$id = $json")
//        Future(Ok(json))
//      }
//    }
//  }
}

