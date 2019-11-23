package com.decidir.coretx.domain

import com.decidir.coretx.utils.RedisDao
import com.decidir.coretx.utils.JedisPoolProvider
import javax.inject.Inject
import javax.inject.Singleton
import com.google.common.base.Splitter
import scala.collection.JavaConverters._
import com.decidir.coretx.utils.Storeable
import com.decidir.coretx.utils.ApiSupport
import controllers.utils.OperationJsonFormat._
import scala.util.Try
import play.api.libs.json.Json
import com.decidir.coretx.api.ErrorFactory
import scala.util.Success

object MarcaTarjetaRepository {
  
  val slashSplitter = Splitter.on('/')
  val commaSplitter = Splitter.on(',')
  
  def formatRangos(in: List[Pair]) = {
    in.map(par => par.e1 + "/" + par.e2).mkString(",")
  }
  
  def parseRangos(in: String): List[Pair] = {
    commaSplitter.split(in).asScala.toList.map{parString =>
      val par = slashSplitter.split(parString).asScala.toSeq 
      if(par.size == 2) {
    	  Some(Pair(par(0), par(1)))
      }
      else None
    }.flatten
  }
  
}

@Singleton
class MarcaTarjetaRepository @Inject() (jedisPoolProvider: JedisPoolProvider) 
  extends RedisDao[MarcaTarjeta](jedisPoolProvider, 
      (map => MarcaTarjeta(map("id").toInt, 
                          map("descripcion"), 
                          map("codAlfaNum"), 
                          map.get("urlServicio"), 
                          map.get("sufijoPlantilla"), 
                          map.get("verificaBin"), 
                          MarcaTarjetaRepository.parseRangos(map("rangosNacionales"))))) {

  val entityPrefix = "marcastarjeta:"
  
}

case class Pair(e1: String, e2: String)

object MarcaTarjeta {
  def fromJson(jsonString: String): Try[MarcaTarjeta] = {
    val json = Json.parse(jsonString) 
    // TODO 
    json.validate[MarcaTarjeta].fold(errors => ErrorFactory.uncategorizedFailure(new Exception("TODO")), mp => Success(mp))
  }  
}

case class MarcaTarjeta(id: Int, 
                        descripcion: String, 
                        codAlfaNum: String, 
                        urlServicio: Option[String], 
                        sufijoPlantilla: Option[String], 
                        verificaBin: Option[String], 
                        rangosNacionales: List[Pair]) 
                        extends Storeable {
  def toMap = {
    val map = ApiSupport.toMap(this)
    map + ("rangosNacionales" -> MarcaTarjetaRepository.formatRangos(rangosNacionales)) 
  }
  
  def esNacional(oNroTarjeta: Option[String]): Boolean = {
    oNroTarjeta map { nroTarjeta =>
      rangosNacionales.find { rango =>
        (nroTarjeta.compareTo(rango.e1) >= 0 && nroTarjeta.compareTo(rango.e2) <= 0)
      } isDefined
    } getOrElse(true)
  }

}