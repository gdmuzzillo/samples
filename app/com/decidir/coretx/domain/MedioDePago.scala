package com.decidir.coretx.domain

import javax.inject.Singleton
import com.decidir.coretx.utils.JedisPoolProvider
import com.decidir.coretx.utils.JedisUtils
import javax.inject.Inject
import scala.collection.JavaConverters._
import com.decidir.coretx.utils.RedisDao
import com.decidir.coretx.utils.Storeable
import com.decidir.coretx.utils.ApiSupport
import redis.clients.jedis.Transaction
import scala.util.Try
import play.api.libs.json.Json
import controllers.utils.OperationJsonFormat._
import scala.util.Failure
import com.decidir.coretx.api.ErrorFactory
import play.api.libs.json.JsError
import scala.util.Success
import com.google.common.base.Splitter


@Singleton
class MedioDePagoRepository @Inject() (jedisPoolProvider: JedisPoolProvider) 
  extends RedisDao[MedioDePago](jedisPoolProvider, 
      (map => MedioDePago(map("id"), 
          map("descripcion"), 
          map.get("idMoneda"), 
          map.get("idMarcaTarjeta").map(_.toInt), 
          map("cardBrand"),
          map("limite").toDouble,
          map("backend").toInt, 
          map("protocol").toInt, 
          CardBrandOperations(
              map("operations.annulment").toBoolean,
              map("operations.annulment_pre_approved").toBoolean,
              map("operations.refundPartialBeforeClose").toBoolean,
              map("operations.refundPartialBeforeCloseAnnulment").toBoolean,
              map("operations.refundPartialAfterClose").toBoolean,
              map("operations.refundPartialAfterCloseAnnulment").toBoolean,
              map("operations.refund").toBoolean,
              map("operations.refundAnnulment").toBoolean,
              map("operations.twoSteps").toBoolean),
          map("bin_regex"),
          map("hasBlackList").toBoolean,
          map("hasWhiteList").toBoolean,
          map("validateLuhn").toBoolean,
          map("cyberSource").toBoolean,
          map("tokenized").toBoolean,
          map("isAgro").toBoolean
          ))) {

  val entityPrefix = "mediospago:"
  val entityAllBins = entityPrefix + "bins"
  
  override def store(medioPago: MedioDePago)(implicit otx: Option[Transaction]) = {
    super.store(medioPago)
  }
  
 def limitBinStore(limitBinMedioPago: LimitBinMedioPago) = {
     doWithRedis {redis => {
          redis.del(blackListBinKey(limitBinMedioPago.idmediopago))
          redis.del(whiteListBinKey(limitBinMedioPago.idmediopago))
          
          if (limitBinMedioPago.validate) {
            limitBinMedioPago.black.foreach { rango =>
              redis.lpush(blackListBinKey(limitBinMedioPago.idmediopago), s"${rango.min}-${rango.max}")
            }
            limitBinMedioPago.white.foreach { rango =>
              redis.lpush(whiteListBinKey(limitBinMedioPago.idmediopago), s"${rango.min}-${rango.max}")
            }            
          }
     }}
 }
 
 def allBinsStore(bins :Bins) = {
   doWithRedis {redis => 
     bins.list.map(bin => redis.sadd(entityAllBins, bin))
   }
 }
 
 def getAllBins() = evalWithRedis { _.smembers(entityAllBins) }.asScala.toList
 
 private def blackListBinKey(idmediopago: String) = s"$entityPrefix${idmediopago}:filterbin:black"
 private def whiteListBinKey(idmediopago: String) = s"$entityPrefix${idmediopago}:filterbin:white"
 
 val dashSplitter = Splitter.on('-')
 
 def isBinBlack(idmediopago: String, bin: String) = {
    
    val rangos = evalWithRedis { _.lrange(blackListBinKey(idmediopago), 0, -1) }.
                    asScala.toList.map(dashSplitter.split(_).asScala.toSeq).map(seq => (seq(0), seq(1)))
    
    rangos.find(r => r._1 <= bin && r._2 >= bin).isDefined
  }

  def isBinWhite(idmediopago: String, bin: String) = {

    val rangos = evalWithRedis { _.lrange(whiteListBinKey(idmediopago), 0, -1) }.
      asScala.toList.map(dashSplitter.split(_).asScala.toSeq).map(seq => (seq(0), seq(1)))

    rangos.find(r => r._1 <= bin && r._2 >= bin).isDefined
  }
  
}

object MedioDePago {
  def fromMap(map: Map[String, String]) = 
    MedioDePago(map("id"), 
          map("descripcion"), 
          map.get("idMoneda"), 
          map.get("idMarcaTarjeta").map(_.toInt), 
          map("cardBrand"),
          map("limite").toDouble,
          map("backend").toInt, 
          map("protocol").toInt,
          CardBrandOperations(
              map("operations.annulment").toBoolean,
              map("operations.annulment_pre_approved").toBoolean,
              map("operations.refundPartialBeforeClose").toBoolean,
              map("operations.refundPartialBeforeCloseAnnulment").toBoolean,
              map("operations.refundPartialAfterClose").toBoolean,
              map("operations.refundPartialAfterCloseAnnulment").toBoolean,
              map("operations.refund").toBoolean,
              map("operations.refundAnnulment").toBoolean,
              map("operations.twoSteps").toBoolean),
          map("bin_regex"),
          map("hasBlackList").toBoolean,
          map("hasWhiteList").toBoolean,
          map("validateLuhn").toBoolean,
          map("cyberSource").toBoolean,
          map("tokenized").toBoolean,
          map("isAgro").toBoolean
          )

  def fromJson(jsonString: String): Try[MedioDePago] = {
    val json = Json.parse(jsonString) 
    // TODO 
    json.validate[MedioDePago].fold(errors => ErrorFactory.uncategorizedFailure(new Exception("TODO")), mp => Success(mp))
  }

}

case class MedioDePago(id: String, descripcion: String, idMoneda: Option[String], idMarcaTarjeta: Option[Int], cardBrand: String,
                        limite: Double, backend: Int, protocol: Int, operations: CardBrandOperations, 
                        bin_regex: String, hasBlackList: Boolean, hasWhiteList: Boolean, validateLuhn: Boolean,
                        cyberSource: Boolean, tokenized: Boolean, isAgro: Boolean) extends Storeable {
  def toMap = {
    ApiSupport.toMap(this)
  }
  
}

case class RangosMedioPago(limiteinferior: String, 
    limitesuperior: String, 
    idmediopago: String,
    idbackend: String, 
    idprotocolo: Integer, 
    descri: String, 
    blacklist: Boolean)

case class LimitPair(min: String, max: String)

case class LimitBinMedioPago(idmediopago: String, 
    white: List[LimitPair],  
    black: List[LimitPair],
    validate: Boolean)
    
case class Bins(list: List[String])    

case class CardBrandOperations(
  annulment: Boolean,
  annulment_pre_approved: Boolean,
	refundPartialBeforeClose: Boolean,
	refundPartialBeforeCloseAnnulment: Boolean,
	refundPartialAfterClose: Boolean,
	refundPartialAfterCloseAnnulment: Boolean,
	refund: Boolean,
	refundAnnulment: Boolean,
	twoSteps: Boolean)

object CardBrandOperations {
  def isMpos(cboFromDB: CardBrandOperations): CardBrandOperations =
    cboFromDB.copy(
      refundPartialBeforeClose = true
    )
}
