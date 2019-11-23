package com.decidir.coretx.domain

import com.decidir.coretx.utils.Storeable
import com.decidir.coretx.utils.RedisDao
import javax.inject.Singleton
import javax.inject.Inject
import com.decidir.coretx.utils.JedisPoolProvider
import play.api.libs.json.Json
import com.decidir.coretx.utils.JedisUtils
import play.Logger
import play.api.libs.json.JsError
import scala.collection.JavaConverters._
import decidir.sps.sac.vista.utils.info.{InfoReglas => LegacyInfoReglas}
import decidir.sps.sac.vista.utils.info.{InfoReglasDetalle => LegacyInfoReglasDetalle}


object InfoReglasJsonFormats {
  
  implicit val infoReglasDetalleReads = Json.reads[InfoReglasDetalle]
  implicit val infoReglasDetalleWrites = Json.writes[InfoReglasDetalle]
  implicit val infoReglasReads = Json.reads[InfoReglas]
  implicit val infoReglasWrites = Json.writes[InfoReglas]
  implicit val infoReglasListReads = Json.reads[InfoReglasList]
  implicit val infoReglasListWrites = Json.writes[InfoReglasList]
  
}

@Singleton
class InfoReglasRepository @Inject() (jedisPoolProvider: JedisPoolProvider) extends JedisUtils {
  
  val jedisPool = jedisPoolProvider.get
  
  import InfoReglasJsonFormats._
  
  def retrieve(idSite: String) = {
    val reglasString = evalWithRedis { _.get("inforeglas:" + idSite) }
    if(reglasString == null || reglasString.isEmpty()) None
    else {
      val reglasJson = Json.parse(reglasString)
      val reglasValidation = reglasJson.validate[InfoReglasList]
      val reglas = reglasValidation.fold(
        errors => {
          val jsonErrors = JsError.toJson(errors)
          val msg = "Error en formato de InfoReglas. Revisar replicacion " + jsonErrors
          Logger.error(msg)
          throw new Exception(msg)
        }, 
        reglas => {    
          reglas
        }) 
        
      Some(reglas)
    }
  }
  
  def store(reglasList: InfoReglasList) = {
    val json = Json.toJson(reglasList)
    doWithRedisTx { redis =>
      redis.set("inforeglas:" + reglasList.idSite, json.toString) 
      redis.sadd("inforeglas:all", reglasList.idSite)
    }
  }
  
  def retrieveAll = {
    
    val allIds = evalWithRedis { _.smembers("inforeglas:all") } asScala 
    val all = allIds.toList.flatMap(retrieve)
    all
    
  }
  
}


//  private String idsite;
//  private String descsite;
//  private String idregla;
//  private int orden;
//  private String estado;
//  private String sitesalida;
//  private String descsitesalida;
//  private List<InfoReglasDetalle> detalle;

object InfoReglas {
  def fromLegacy(ir: LegacyInfoReglas) = {
    InfoReglas(ir.getIdsite, ir.getDescsite, ir.getIdregla, ir.getOrden, ir.getEstado, ir.getSitesalida, 
               ir.getDescsitesalida, ir.getDetalle.asScala.toList.map(InfoReglasDetalle.fromLegacy))
  }
}

case class InfoReglas(idSite: String, descSite: String, idRegla: String, orden: Int, estado: String, siteSalida: String, 
                      descSiteSalida: String, detalle: List[InfoReglasDetalle]) {
  
  def toLegacy = new LegacyInfoReglas(idSite, siteSalida,  idRegla, orden, estado, 
                                      detalle.map(_.toLegacy).asJava, descSite, descSiteSalida)  
  
}


//  private String idregla;
//  private String campo;
//  private String descricampo;
//  private String operador;
//  private String valor;
//  private List<String> descri;

object InfoReglasDetalle {
  def fromLegacy(d: LegacyInfoReglasDetalle) = {
    InfoReglasDetalle(d.getIdregla, d.getCampo, d.getDescricampo, d.getOperador, d.getValor, d.getDescri.asScala.toList)
  }
}

case class InfoReglasDetalle(idRegla: String, campo: String, descriCampo: String, operador: String, valor: String, 
                             descri: List[String]) {
  
  def toLegacy = new LegacyInfoReglasDetalle(idRegla, campo, operador, valor, descri.asJava, descriCampo)
  
}


case class InfoReglasList(idSite: String, reglas: List[InfoReglas])

