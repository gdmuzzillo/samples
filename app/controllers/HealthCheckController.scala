package controllers

import play.api.mvc.{Action, Controller}

/**
  * @author martinpaoletta
  */
class HealthCheckController extends Controller {

  def check = Action {
    val objectName = "controllers.BuildInfo$"
    val cons = Class.forName(objectName).getDeclaredConstructors()
    cons(0).setAccessible(true)
    val buildInfo = cons(0).newInstance().asInstanceOf[ToJson].toJson
    Ok(buildInfo).as("application/json")
  }
}

trait ToJson {
  def toJson: String
}