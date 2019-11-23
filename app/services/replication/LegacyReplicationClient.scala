package services.replication

import akka.actor.Actor
import com.decidir.coretx.utils.JedisUtils
import akka.actor.ActorSystem
import akka.actor.Props
import com.decidir.events.EntityUpdatedEvent
import play.api.libs.json.Json
import com.decidir.coretx.domain._
import controllers.utils.OperationJsonFormat._
import play.api.libs.json.JsSuccess
import play.Logger
import play.api.libs.json.JsError
import com.decidir.coretx.domain.InfoReglasJsonFormats._
import javax.inject.Singleton
import javax.inject.Inject
import com.decidir.encrypt.CryptoData
import com.decidir.encrypt.EncryptionRepository
import com.decidir.coretx.api.OperationJsonFormats._

/**
 * @author martinpaoletta
 * Nota: este servicio va del lado de coretx
 */

@Singleton
class LegacyReplicationClient @Inject() (
          siteRepository: SiteRepository, 
          tipoActividadRepository: TipoActividadRepository,
          medioDePagoRepository: MedioDePagoRepository, 
          monedaRepository: MonedaRepository,
          marcaTarjetaRepository: MarcaTarjetaRepository,
          infoReglasRepository: InfoReglasRepository,
          infoFiltrosRepository: InfoFiltrosRepository, 
          terminalRepository: TerminalRepository,
          idComercioLocationRepository: IdComercioLocationRepository, 
          motivoRepository: MotivoRepository, 
          encryptionRepository: EncryptionRepository,
          nroTraceRepository: NroTraceRepository,
          tipoDocumentoRepository: TipoDocumentoRepository,
          bancoRepository: BancoRepository) {
  
  def entityUpdated(event: EntityUpdatedEvent) = {
    
    event match {
      
      case EntityUpdatedEvent(name, jsonStringList) => {
        
        jsonStringList.foreach { jsonString =>
          Logger.info(s"Recibido evento de replicación: EntityUpdatedEvent($name, $jsonString)")
          
          name match {
            
            case "Encripcion" => {
              val cd = CryptoData.fromJson(Json.parse(jsonString))
              encryptionRepository.store(cd)
              // TODO Agregar validaciones
            }        
            
            case "Motivo" => {
              val json = Json.parse(jsonString).validate[Motivo]
              json.fold(
                errors => {Logger.error("Error en replicación de Motivo: " + JsError.toJson(errors) + " en " + jsonString)}, 
                motivo => {motivoRepository.store(motivo)})          
            }
            
            case "IdComercioLocation" => {
              val json = Json.parse(jsonString).validate[Pair]
              json.fold(
                errors => {Logger.error("Error en replicación de IdComercioLocation: " + JsError.toJson(errors) + " en " + jsonString)}, 
                pair => {idComercioLocationRepository.store(pair)})
            }        
            
            
            case "Terminal" => {
              val json = Json.parse(jsonString).validate[List[TerminalesSite]]
              json.fold(
                errors => {Logger.error("Error en replicación de terminales: " + JsError.toJson(errors) + " en " + jsonString)}, 
                terminales => {terminalRepository.store(terminales)})
            }        
  
            case "NrosTrace" => {
              val json = Json.parse(jsonString).validate[List[NrosTraceSite]]
              json.fold(
                errors => {Logger.error("Error en replicación de nros de trace: " + JsError.toJson(errors) + " en " + jsonString)}, 
                nrostrace => {nroTraceRepository.store(nrostrace)})
            }            
            
            case "InfoFiltros" => {
              val json = Json.parse(jsonString).validate[InfoFiltros]
              json.fold(
                errors => {Logger.error("Error en replicación de tipo de actividad: " + JsError.toJson(errors) + " en " + jsonString)}, 
                infoFiltros => {infoFiltrosRepository.store(infoFiltros)})
            }
            
            
            case "TipoActividad" => {
              val json = Json.parse(jsonString).validate[TipoActividad]
              json.fold(
                errors => {Logger.error("Error en replicación de tipo de actividad: " + JsError.toJson(errors) + " en " + jsonString)}, 
                tipoActividad => {tipoActividadRepository.store(tipoActividad)})
            }
    
            case "Site" => {
              val json = Json.parse(jsonString).validate[Site]
              json.fold(
                errors => {Logger.error("Error en replicación de site: " + JsError.toJson(errors) + " en " + jsonString)}, 
                site => {siteRepository.store(site)})
            }        
    
            case "MedioDePago" => {
              val json = Json.parse(jsonString).validate[MedioDePago]
              json.fold(
                errors => {Logger.error("Error en replicación de medio de pago: " + JsError.toJson(errors) + " en " + jsonString)}, 
                medioPago => {medioDePagoRepository.store(medioPago)})
            }      
            
            case "Moneda" => {
              val json = Json.parse(jsonString).validate[Moneda]
              json.fold(
                errors => {Logger.error("Error en replicación de moneda: " + JsError.toJson(errors) + " en " + jsonString)}, 
                moneda => {monedaRepository.store(moneda)})
            }         
    
            case "MarcaTarjeta" => {
              println(jsonString)
              val json = Json.parse(jsonString).validate[MarcaTarjeta]
              json.fold(
                errors => {Logger.error("Error en replicación de marca de tarjeta: " + JsError.toJson(errors) + " en " + jsonString)}, 
                marcaTarjeta => {marcaTarjetaRepository.store(marcaTarjeta)})
            }    
            
            case "IdentificadorDePagos" => {
              nroTraceRepository.storeIdentificadorDePago(jsonString)
            }
     
            case "BinFilter" => {
              val json = Json.parse(jsonString).validate[LimitBinMedioPago]
              json.fold(
                errors => {Logger.error("Error en replicación de los limites de bines: " + JsError.toJson(errors) + " en " + jsonString)}, 
                limitsMedioPago => {medioDePagoRepository.limitBinStore(limitsMedioPago)})
            }
            
            case "Bins" => {
              val json = Json.parse(jsonString).validate[Bins]
              json.fold(
                errors => {Logger.error("Error en replicación de los limites de bines: " + JsError.toJson(errors) + " en " + jsonString)}, 
                bins => {medioDePagoRepository.allBinsStore(bins)})
            }
            
            case "TransactionsStatus" => {
              val json = Json.parse(jsonString).validate[TransactionsStatus]
              json.fold(
                errors => {Logger.error("Error en replicación de los estados de las transactions legacy: " + JsError.toJson(errors) + " en " + jsonString)}, 
                transactionsStatus => {siteRepository.store(transactionsStatus)})
            }
  
            case "TipoDocumento" => {
              val json = Json.parse(jsonString).validate[TipoDocumento]
              json.fold(
                errors => {Logger.error("Error en replicación de los tipos documento: " + JsError.toJson(errors) + " en " + jsonString)},
                tipoDoc => {tipoDocumentoRepository.store(tipoDoc)})
            }
  
            case "Banco" => {
              val json = Json.parse(jsonString).validate[Banco]
              json.fold(
                errors => {Logger.error("Error en replicación de los bancos: " + JsError.toJson(errors) + " en " + jsonString)},
                banco => {bancoRepository.store(banco)})
            }
                        
            case other => {
              Logger.error("No se persistir cambios de replicacion de " + other)
            }
          }
        }
      }
    }
  }  
}


