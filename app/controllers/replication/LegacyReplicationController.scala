package controllers.replication

import play.api.mvc.Controller
import javax.inject.Inject
import play.api.mvc.Action
import akka.actor.ActorSystem
import services.replication._
import play.Logger


/**
 * @author martinpaoletta
 */
class LegacyReplicationController @Inject() (system: ActorSystem, 
                                   replicationService: LegacyReplicationService) extends Controller {

  val logger = Logger.underlying()
  def replicateAllEntities = Action {
    logger.info("Replicacion manual. replication/all")
    val entities = List(
    		"IdComercioLocation", 
        "TipoActividad", 
        "Site", 
        "MedioPago", 
        "Moneda", 
        "MarcaTarjeta",             
        "InfoFiltros", 
//        "Terminal", => este setea nro de trace
        "TipoDocumento",
        "Motivo", 
        "Encripcion",
        "IdentificadorDePagos", 
        "BinFilter", 
//        "TransactionsStatus",
        "Notifications",
        "Banco")
    
    entities.map(Entidad(_, None)).foreach(replicationService.replicate)
    
    Ok("Replicando todo")
  }
  
  
  def replicateAll(entidad: String) = Action {
    logger.info(s"Replicacion manual. replication/$entidad")
    replicationService.replicate(Entidad(entidad, None))
    Ok("Replicando todos/as los/as " + entidad)
  }

  def replicateOne(entidad: String, id: String) = Action {
    logger.info(s"Replicacion manual. replication/$entidad/$id")
    replicationService.replicate(Entidad(entidad, Some(id)))
    Ok(s"Replicando $entidad ($id)")
  }

  def deleteOne(entidad: String, id: String) = Action {
//    replicationActor ! Entidad(entidad, Some(id))
    Ok(s"Borrando $entidad ($id) - TODO")
  }  
  
}