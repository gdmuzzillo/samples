package services.replication

import akka.actor.ActorSystem
import javax.inject.Singleton
import javax.inject.Inject
import play.api.Configuration
import akka.actor.Props
import play.api.libs.json.Json
import com.decidir.coretx.domain.MotivoRepository
import com.decidir.coretx.domain.MarcaTarjetaRepository
import com.decidir.coretx.domain.MonedaRepository
import com.decidir.coretx.domain.InfoFiltrosRepository
import legacy.decidir.sps.domain.LegacyDao
import legacy.decidir.sps.domain.DBSPSProvider
import com.decidir.coretx.domain.MedioDePagoRepository
import com.decidir.coretx.domain.IdComercioLocationRepository
import com.decidir.coretx.domain.SiteRepository
import com.decidir.coretx.domain.TipoActividadRepository
import com.decidir.coretx.domain.TerminalRepository
import com.decidir.coretx.domain.InfoReglasRepository
import akka.actor.ActorRef
import play.Logger
import com.decidir.coretx.messaging.ConfigurableKafkaConsumerActor
import com.decidir.coretx.utils.JedisPoolProvider
import com.decidir.coretx.messaging.ConfigurableRedisOffsetsRepository
import com.decidir.coretx.messaging.KafkaConfigurationProvider

/**
 * TODO Cambiar implementacion por algo mas seguro que invocar a los actores viejos de replicacion
 * (algo que no comitee la respuesta hasta que realmente se hayan replicado los datos)
 * 
 * OJO que está incompleto!!!!
 */
@Singleton
class LegacyReplicationListener @Inject() (configuration: Configuration,
                                           kafkaConfigurationProvider: KafkaConfigurationProvider,
                                           actorSystem: ActorSystem, 
                                           dbspsProvider: DBSPSProvider, 
                                           legacyDao: LegacyDao,
                                           replicationService: LegacyReplicationService, 
                                           redisPoolProvider: JedisPoolProvider) {

  
  Logger.info("Iniciando listener de replicacion")
  
  val dbsps = dbspsProvider.dbsps
  val dbsac = dbspsProvider.dbsac
  
  val topics =  List("replicationUpsertTopic")
  val bootstrapServers = configuration.getString("sps.kafka.bootstrapServers").getOrElse(throw new Exception("No se definieron bootstrap servers"))
//  val groupId = "replicationClient"
  val listener = actorSystem.actorOf(Props(
    new ConfigurableKafkaConsumerActor(
          kafkaConfigurationProvider.consumerConf("legacy-replication", true), 
          topics, 
          onRecord, 
          Some(new ConfigurableRedisOffsetsRepository("sac:replication:offsets", "@", redisPoolProvider.get)))))
  
  Logger.info("Listener de replicacion iniciado")

          
  implicit val entidadReads = Json.reads[Entidad]
  
  def onRecord(key: Option[String], msg: String, consumerActor: Option[ActorRef]) = {
    
    // TODO Manejar error
    Json.parse(msg).validate[Entidad].fold(
      errors => Logger.error("Error en mensaje de replicacion: " + errors),
      entidad => {
        Logger.info("Recibido evento de replicacion de " + msg)
        entidad match {
          case Entidad(name, _) if (name == null || name.trim.isEmpty) => Logger.warn(s"Invalid replication event message was omitted. => $msg")
          case Entidad(_, Some(id)) if (id == null || id.trim.isEmpty) => Logger.warn(s"Invalid replication event message was omitted. => $msg")
          case _ =>  replicationService.replicate(entidad)   
        }
      }
    )
  
  }
  
  
}