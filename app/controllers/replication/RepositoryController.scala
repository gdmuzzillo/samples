package controllers.replication

import com.decidir.coretx.domain.MarcaTarjetaRepository
import com.decidir.coretx.domain.MonedaRepository
import com.decidir.coretx.domain.MedioDePagoRepository
import com.decidir.coretx.domain.TipoActividadRepository
import play.api.mvc.Controller
import com.decidir.coretx.domain.SiteRepository
import javax.inject.Inject
import com.decidir.coretx.api.OperationJsonFormats._
import controllers.utils.OperationJsonFormat._

import play.api.mvc.Action
import play.api.libs.json.Json
import com.decidir.coretx.domain.OperationResourceRepository
import com.decidir.coretx.domain.Site

class RepositoryController @Inject() (
                                   siteRepository: SiteRepository, 
                                   tipoActividadRepository: TipoActividadRepository,
                                   medioPagoRepository: MedioDePagoRepository, 
                                   monedaRepository: MonedaRepository, 
                                   marcaTarjetaRepository: MarcaTarjetaRepository, 
                                   operationRepository: OperationResourceRepository) extends Controller {

  
  def site(id: String) = Action {
    siteRepository.retrieve(id) match {
      case Some(entity) => {
        val cuentas = entity.cuentas
        val cuentasJson = Json.toJson(cuentas)
        val json = Json.toJson(entity)        
    	  Ok(json)      
      }
      case None => InternalServerError
    }
  }
  
  def sites = Action {
    val all = siteRepository.retrieveAll
    Ok(Json.toJson((all)))   
  }

  
  def tipoActividad(id: String) = Action {
    tipoActividadRepository.retrieve(id) match {
      case Some(entity) => Ok(Json.toJson((entity)))      
      case None => InternalServerError
    }
  }
  
  def tiposActividad = Action {
    val all = tipoActividadRepository.retrieveAll
    Ok(Json.toJson((all)))   
  }
  

  def marcaTarjeta(id: String) = Action {
    marcaTarjetaRepository.retrieve(id) match {
      case Some(entity) => Ok(Json.toJson((entity)))      
      case None => InternalServerError
    }
  }
  
  def marcasTarjeta = Action {
    val all = marcaTarjetaRepository.retrieveAll
    Ok(Json.toJson((all)))   
  }
  
  

  def medioPago(id: String) = Action {
    medioPagoRepository.retrieve(id) match {
      case Some(entity) => Ok(Json.toJson((entity)))      
      case None => InternalServerError
    }
  }
  
  def mediosPago = Action {
    val all = medioPagoRepository.retrieveAll
    Ok(Json.toJson((all)))   
  }  
  
  
  def moneda(id: String) = Action {
    monedaRepository.retrieve(id) match {
      case Some(entity) => Ok(Json.toJson((entity)))      
      case None => InternalServerError
    }
  }
  
  def monedas = Action {
    val all = monedaRepository.retrieveAll
    Ok(Json.toJson((all)))   
  }  
  
}