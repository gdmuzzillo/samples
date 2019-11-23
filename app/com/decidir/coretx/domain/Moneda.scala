package com.decidir.coretx.domain

import javax.inject.Singleton
import com.decidir.coretx.utils.JedisUtils
import com.decidir.coretx.utils.JedisPoolProvider
import javax.inject.Inject
import scala.collection.JavaConverters._
import scala.collection.JavaConverters._
import com.decidir.coretx.utils.RedisDao
import com.decidir.coretx.utils.Storeable
import com.decidir.coretx.utils.ApiSupport
import scala.util.Try
import play.api.libs.json.Json
import com.decidir.coretx.api.ErrorFactory
import scala.util.Success
import controllers.utils.OperationJsonFormat._

///**
// * TODO, pasar a RedisDao
// */
//@Singleton
//class MonedaRepository @Inject() (jedisPoolProvider: JedisPoolProvider) extends JedisUtils {
//
//  val jedisPool = jedisPoolProvider.get
//  val entityPrefix = "moneda:"
//  def entityKey(id: Any) = entityPrefix + id
//  def allKey = entityPrefix + "all"
//  
//  def exists(id: String) = {
//    evalWithRedis { _.exists(entityKey(id)) }
//  }
//  
//  def store(entity: Moneda) = {
//    val opMap = entity.toMap
//    storeMapInRedis(entityKey(entity.id), opMap)
//  }
//  
//  def retrieve(id: Any): Option[Moneda] = {
//    val map = evalWithRedis { _.hgetAll(entityKey(id)) }.asScala.toMap
//    if(map.isEmpty) None
//    else Some(Moneda.fromMap(map))
//  }
//  
//  def retrieveAll: List[Moneda] = {
//    evalWithRedis { _.smembers(allKey) }.asScala.toList.map(key => retrieve(key)).flatten
//  }  
//  
//}

object Moneda {
  def fromJson(jsonString: String): Try[Moneda] = {
    val json = Json.parse(jsonString) 
    // TODO 
    json.validate[Moneda].fold(errors => ErrorFactory.uncategorizedFailure(new Exception("TODO")), mp => Success(mp))
  }  
}


case class Moneda(id: String, descripcion: String, simbolo: String, idMonedaIsoAlfa: String, idMonedaIsoNum: String) 
  extends Storeable {
  def toMap = {
    ApiSupport.toMap(this)
  }
}

@Singleton
class MonedaRepository @Inject() (jedisPoolProvider: JedisPoolProvider) 
  extends RedisDao[Moneda](jedisPoolProvider, (map => Moneda(map("id"), map("descripcion"), map("simbolo"), map("idMonedaIsoAlfa"), map("idMonedaIsoNum")))) {
  
  val entityPrefix = "monedas:"
} 


//
//object Moneda {
//  def fromMap(map: Map[String, String]) = {
//    Moneda(map("id"), map("descripcion"), map("simbolo"), map("idMonedaIsoAlfa"), map("idMonedaIsoNum"))
//  }
//}

