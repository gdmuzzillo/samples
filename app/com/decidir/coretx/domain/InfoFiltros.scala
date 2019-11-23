package com.decidir.coretx.domain

import com.decidir.coretx.utils.JedisUtils
import com.decidir.coretx.utils.JedisPoolProvider
import javax.inject.Inject
import controllers.utils.OperationJsonFormat._
import play.libs.Json
import com.decidir.coretx.utils.ApiSupport
import scala.collection.JavaConverters._
import play.Logger


/**
 * @author martinpaoletta
 */

object InfoFiltros {
  def fromMap(map: Map[String, String]) = 
    InfoFiltros(map("idSite"), map("idMarcaTarjeta").toInt, map("filtraXBin").toBoolean, map("listaNegra").toBoolean, Nil)
}


case class InfoFiltros(idSite: String, idMarcaTarjeta: Int, filtraXBin: Boolean, listaNegra: Boolean, bines: List[String]) 


class InfoFiltrosRepository @Inject() (jedisPoolProvider: JedisPoolProvider) extends JedisUtils {
  
  val logger = Logger.underlying()
  val jedisPool = jedisPoolProvider.get
  val entityPrefix = "infofiltros:"
  def entityKey(idSite: String) = s"$entityPrefix${idSite}"
  def cardKey(idSite: String, idMarcaTarjeta: Int) =  entityKey(idSite) + s""":${idMarcaTarjeta}"""
  def binesKey(idSite: String, idMarcaTarjeta: Int) = cardKey(idSite, idMarcaTarjeta) + ":bines"
  import InfoReglasJsonFormats._

  def store(iff: InfoFiltros) = {
    val map = ApiSupport.toMap(iff)
    
    //SET DE CLAVES HASH
    doWithRedis { redis => {
      redis.sadd(entityKey(iff.idSite), iff.idMarcaTarjeta.toString)
      }
    }
    
    val cKey = cardKey(iff.idSite, iff.idMarcaTarjeta)
    
    storeMapInRedis(cKey, map)
    doWithRedis { redis => {
      redis.del(binesKey(iff.idSite, iff.idMarcaTarjeta))
      iff.bines.foreach { bin => redis.sadd(binesKey(iff.idSite, iff.idMarcaTarjeta), bin) }
      }
    }
  }
  
  def retrieve(idSite: String, idMarcaTarjeta: Int) = {
    val map = evalWithRedis { _.hgetAll(cardKey(idSite, idMarcaTarjeta)) }.asScala.toMap
    if(map.isEmpty) None
    else Some(InfoFiltros.fromMap(map))
  }
  
  
  def cleanData(idSite: String) = {
     val cards = evalWithRedis { _.smembers(entityKey(idSite)) }.asScala.toList
     cards.foreach(card =>  doWithRedis { redis => redis.del(cardKey(idSite, card.toInt)) 
                                               redis.del(binesKey(idSite, card.toInt)) 
                                               redis.srem(entityKey(idSite), card)})
  }  
  
  def existeBinEnFiltro(idSite: String, idMarcaTarjeta: Int, bin: String) = 
    evalWithRedis { _.sismember(binesKey(idSite, idMarcaTarjeta), bin) }
  
  def pasaFiltroBin(idSite: String, idMarcaTarjeta: Int, bin: String, nroTarjeta: String) = {
    
    def buscarBin = {
      if(existeBinEnFiltro(idSite, idMarcaTarjeta, nroTarjeta)) true
      if(existeBinEnFiltro(idSite, idMarcaTarjeta, bin)) true
      else if(bin.size >= 5 && existeBinEnFiltro(idSite, idMarcaTarjeta, bin take 5)) true
      else if(bin.size >= 4 && existeBinEnFiltro(idSite, idMarcaTarjeta, bin take 4)) true
      else false
    }
    
    def logBin(msj: String) = {
      logger.info("Site " + idSite + ", MT " + idMarcaTarjeta + " -> Bin " + bin + "  " + msj)
    }    
    
    val oiff = retrieve(idSite, idMarcaTarjeta)
    oiff match {
      case None => true
      case Some(InfoFiltros(_, _, false, _, _)) => true
      case Some(InfoFiltros(_, _, true, listaNegra, _)) => {
        
        val existe = buscarBin
        
        (existe, listaNegra) match {
          case (true, true) => {
            logBin("no pasa (esta en LN)")
            false
          }
          case (true, false) => {
            logBin("pasa (no esta en LN)")
            true
          }
          case (false, true) => {
            logBin("pasa (esta en LB)")
            true
          }
          case (false, false) => {
            logBin("no pasa (no esta en LB)")
            false
          }
        }
        
        
      } 
    }
  }
  
}
  