package com.decidir.coretx.domain

import com.decidir.coretx.utils.JedisUtils
import javax.inject.Inject
import com.decidir.coretx.utils.JedisPoolProvider
import com.decidir.coretx.api.OperationResource
import scala.collection.JavaConverters._
import javax.inject.Singleton
import com.decidir.coretx.utils.ToMap
import com.decidir.coretx.utils.ApiSupport

/**
 * @author martinpaoletta
 * TODO Generalizar (o reemplazar por otra cosa)
 */
@Singleton
class TipoActividadRepository @Inject() (jedisPoolProvider: JedisPoolProvider) extends JedisUtils {

  val jedisPool = jedisPoolProvider.get
  val entityPrefix = "tiposactividad:"
  def entityKey(id: Any) = entityPrefix + id
  
  def store(entity: TipoActividad) = {
    val opMap = entity.toMap
    storeMapInRedis(entityKey(entity.id), opMap)
    evalWithRedis { _.sadd(allKey, entity.id.toString)}
    
  }
  
  def retrieve(id: Any): Option[TipoActividad] = {
    val map = evalWithRedis { _.hgetAll(entityKey(id)) }.asScala.toMap
    if(map.isEmpty) None
    else Some(TipoActividad.fromMap(map))
  }
  
  def allKey = entityPrefix + "all"
  
  def retrieveAll: List[TipoActividad] = {
    evalWithRedis { _.smembers(allKey) }.asScala.toList.map(key => retrieve(key)).flatten
  }   
  
  
}


object TipoActividad {
  
  def fromMap(map: Map[String, String]) = {
    TipoActividad(map("id").toInt, map("descripcion"))
  }
  
}

case class TipoActividad(id: Int, descripcion: String) extends ToMap {
  def toMap = {
    ApiSupport.toMap(this)
  }
}