package controllers.replication

import play.api.mvc.Controller
import javax.inject.Inject
import services.migration.DecidirMigrator
import play.api.mvc.Action
import scala.util.Failure
import scala.util.Success
import com.decidir.util.MDCHelperTrait
import play.api.libs.json.Json
import play.api.libs.json.JsError
import com.decidir.coretx.api.ApiException
import com.decidir.coretx.api.ErrorFactory

class MigratorController @Inject() (migrator: DecidirMigrator) extends Controller with MDCHelperTrait{
  
  def site(id: String) = Action {
    migrator.migrate(id) match {
      case Success(unit) => {
        logger.info("PaymentConfirmationsController.retrieve success")
        Ok(Json.toJson("Migration Success"))
      }      
      case Failure(ApiException(error)) => {
        logger.error(s"MigratorController.site ${id} failure ApiException", error)
        InternalServerError(Json.toJson(error))      
      }
      case Failure(exception) => {
        logger.error(s"MigratorController.site ${id} failure", exception)
        InternalServerError(ErrorFactory.uncategorizedError(exception).toJson)      
      }
    }
  }
}