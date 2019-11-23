import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent._
import com.decidir.coretx.api.ErrorFactory
import controllers.MDCHelperTrait

class ErrorHandler extends HttpErrorHandler with MDCHelperTrait {

  /**
   * 400 - Bad Request The request was unacceptable, often due to missing a required parameter.
   * 401 - Unauthorized  No valid API key provided.
   * 402 - Request Failed  The parameters were valid but the request failed.
   * 404 - Not Found The requested resource doesn't exist.
   * 409 - Conflict  The request conflicts with another request (perhaps due to using the same idempotent key).
   * 429 - Too Many Requests Too many requests hit the API too quickly.
   */
  def onClientError(request: RequestHeader, statusCode: Int, message: String) = {
    Future.successful{
      logger.error("onClientError: " + message)
      Status(statusCode)(message)
        
//      statusCode match {
//        case 400 => BadRequest(InvalidRequestError(Nil).toJson)
//        case 401 => Status(statusCode)(AuthenticationError().toJson)
//        case 402 => Status(statusCode)(InvalidRequestError(Nil).toJson)
//        case 404 => Status(statusCode)(NotFoundError("", "").toJson)
//        case 409 => Status(statusCode)(ApiError("Conflict").toJson)
//        case 429 => Status(statusCode)(RateLimitError().toJson)
//      }  
    }
  }

  def onServerError(request: RequestHeader, exception: Throwable) = {
    Future.successful{
      logger.error("Error no manejado", exception)
      InternalServerError(ErrorFactory.wrap(exception).error.toJson)
    }
  }
}