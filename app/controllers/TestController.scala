package controllers

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.ws.WSClient
import javax.inject.Inject
import services.FutureCompanionOps._
import scala.concurrent.ExecutionContext



/**
 * @author martinpaoletta
 */
class TestController @Inject() (implicit context: ExecutionContext, ws: WSClient) extends Controller {
  
  def check(httpCodeString: String) = Action {
    
    val httpCode = httpCodeString.toInt
    if(httpCode < 0) {
      throw new Exception("Prueba " + httpCode)      
    }
    else {
    	Status(httpCode)("Custom test")
    }
    
  }  
  
  
  def verify() = Action.async {
    
    val pruebas = List(999, -1, 200, 201, 202, 400, 404, 403, 500, 501, 503)
    
    val futureResponses = pruebas map { httpCode =>
      if(httpCode == 999) "http://localhost:9999/falla"
      else "http://localhost:9002/pruebas/response/" + httpCode 
    } map { ws.url(_).get }
    println(futureResponses.size)    
    
    val ftr = allAsTrys(futureResponses)
    
    ftr.map { frs =>
      
      println(frs.size)
      println(frs)
      frs.foreach(println(_))
      
      val somethingWentWrong = frs.exists { _.isFailure } 
      if(somethingWentWrong) BadRequest("Bar") else Ok("Foo")
    }
    
  }

}