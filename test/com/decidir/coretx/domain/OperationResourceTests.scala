package com.decidir.coretx.domain

import org.scalatest.Matchers
import org.scalatest.FlatSpec
import org.scalatest._
import org.scalatest.OptionValues._
import javax.inject.Inject
import com.decidir.coretx.utils.JedisUtils
import com.decidir.coretx.utils.JedisPoolProvider
import com.decidir.coretx.api.OperationResource
import redis.clients.jedis.JedisPool
import com.decidir.coretx.utils.JedisPoolProvider
import com.typesafe.config.ConfigFactory
import play.api.Configuration
import play.api.libs.json.Json
import java.util.Date
import com.decidir.coretx.api.DatosSiteResource
import com.decidir.coretx.api.DatosMedioPagoResource
import com.decidir.coretx.api.DatosTitularResource
import com.decidir.coretx.api.TicketingTransactionData
import com.decidir.coretx.api.FraudDetectionData
import com.decidir.coretx.api.BillingData
import com.decidir.coretx.api.CustomerInSite
import com.decidir.coretx.api.PurchaseTotals
import com.decidir.coretx.api.Item
import com.decidir.coretx.api.CyberSourceResponse
import com.decidir.coretx.api.FraudDetectionDecision
import com.decidir.coretx.api.ErrorFactory
import com.decidir.encrypt.EncryptionService
import com.decidir.encrypt.EncryptionRepository
import com.decidir.coretx.api.Autorizada

object OperationResourceTests {
  val datosTitular = DatosTitularResource(
                                  Some("prueba@test.com"),
                                  Some(1),
                                  Some("23123456"),
                                  Some("Av Mitre"),
                                  Some(4567),
                                  Some("29/04/1974"),
                                  ip = Some("192.168.1.1"))
  
                                  
  val datosMedioPago = DatosMedioPagoResource(
                                      Some(1),
                                      Some("Visa"),
                                      Some(1),
                                      Some(1),
                                      Some("1"),
                                      Some("Juan Perez"),
                                      Some("04"),Some("18"),
                                      Some("123"))                                

                                      
  val datosSite = DatosSiteResource(Some("site_id"), Some("url_dinamica"), Some("param_sitio"), Some(true), 
                            Some("url_origen"), Some("referer"))
 
   val ticketingData = 
      TicketingTransactionData(
          Some(55), 
          Some("Pick up"), 
          List(
              Item(
                  Some("popblacksabbat2016"),
                  Some("Popular Black Sabbath 2016"),
                  Some("popblacksabbat2016ss"),
                  Some("asas"),
                  Some(121212*2), 
                  Some(2),
                  Some(121212)), 
              Item(
                  Some("popblacksdssabbat2016"),
                  Some("Popular Blasdsck Sabbath 2016"),
                  Some("popblacksabbatdsds2016ss"),
                  Some("aswewas"),
                  Some(111212), 
                  Some(1),
                  Some(111212)) 
                  ))
          
// case class CyberSourceResponse(decision: FraudDetectionDecision, reason_code: String, description: String, details: Option[ErrorType] = None) 
                  
    val csStatus = CyberSourceResponse(FraudDetectionDecision.red, None, 100, "descr", Some(ErrorFactory.unauthorized()))
                  
    val fdd = 
      FraudDetectionData(
          bill_to = Some(BillingData(
              Some("Buenos Aires"), 
              Some("Argentina"), 
              Some("martinid"),
              Some("martin@redb.ee"), 
              Some("Martin"),
              Some("PPP"), 
              Some("2322323232"),
              Some("1223"), 
              Some("Buenos Aires"),
              Some("Italia 1234"))), 
           purchase_totals = Some(PurchaseTotals(Some("ars"), Some(12444))), 
//           channel = Some("Web"), 
           customer_in_site = Some(CustomerInSite(
               Some(243),
               Some(false),
               Some("abracadabra"),
               Some(1), 
               Some("12121"))),
           device_unique_id = Some("devicefingerprintid"),
           ticketing_transaction_data = Some(ticketingData),
           status = Some(csStatus))
                            
  val operacion = OperationResource(
                            id = "123", 
                            nro_operacion = Some("nro_operacion"), 
                            fechavto_cuota_1 = Some("yyyyMMdd"), 
                            monto = Some(5000), 
                            cuotas = Some(3),
                            datos_titular = Some(datosTitular), 
                            datos_medio_pago = Some(datosMedioPago), 
                            datos_site = Some(datosSite),
                            creation_date = Some(new Date),
                            last_update = Some(new Date),
                            ttl_seconds = Some(50),
                            fraud_detection = Some(fdd))   
}

class OperationResourceTests extends FlatSpec with Matchers{ // with JedisUtils {
// @Inject() (jedisPoolProvider: JedisPoolProvider, operationRepository: OperationResourceRepository) 
  // TODO Armar configuracion para test
  

//  val jedisPoolProvider = new JedisPoolProvider(Configuration.empty)
//  val encripcionService = new EncripcionService(new EncripcionRepository(jedisPoolProvider), Configuration.from(Map("sps.encryption.key" -> "b8c2ca4a7baed8e334dc49c01c7ea22d016f83bf66b288333a163233ed46565e")) )
//  val operationRepository = new OperationResourceRepository(jedisPoolProvider, Configuration.empty, encripcionService)
//  val jedisPool = jedisPoolProvider.get
//  
//  val operacion = OperationResourceTests.operacion
//  
//  // TODO Revisar que es lo que está rompiendo, son iguales los objetos
//  "Una OperationResource" should "poder guardarse en Redis" in {
//    
//    val txId = "123"
//    doWithRedis { _.del("operation:" + txId) }
//    doWithRedis { _.srem("sites:site_id:txs", "nro_operacion") }
//    
//    operationRepository.store(operacion)
//    val retrieved = operationRepository.retrieve(txId)
//    //retrieved shouldBe defined
//    val toCompare = retrieved.value.copy(ttl_seconds = None, last_update = operacion.last_update,
//          datos_medio_pago = Some(retrieved.get.datos_medio_pago.get.copy(nro_tarjeta = operacion.datos_medio_pago.flatMap { _.nro_tarjeta })))
//    toCompare.toString() shouldBe operacion.toString()
//    
//    val decrypted = operationRepository.retrieveDecrypted(txId)
//    val toCompareDecrypted = decrypted.value.copy(ttl_seconds = None, last_update = operacion.last_update)
//    toCompareDecrypted.toString() shouldBe operacion.toString() 
//
//    // TODO Revisar porque está fallando, porque pareciera que son iguales
//    Thread.sleep(operacion.ttl_seconds.getOrElse(1)*1000)
//    val shouldntExist = operationRepository.retrieve(txId)
//    shouldntExist shouldBe empty
//  }  
//  
//  "Una OperationResource" should "poder marshall/unmarshall en json" in {
//    
//    import com.decidir.coretx.api.OperationJsonFormats._
//    val json = Json.toJson(operacion)
//    val fromJson = json.validate[OperationResource]
//    fromJson.get shouldBe operacion
//    
//  }
//  
  
  //FUNCTIONAL TEST
//  "update all OperationResource" should "save all in Redis" in {
//    
//    val countNroOp = 1000000
//    
//    val status = 4
//    for (siteId <- 0 to 10) {
//      var siteTxKey = siteTransactionsKey(siteId.toString)
//      var siteTxDetailKey = siteTransactionDetailKey(siteId.toString)
//      println(siteId)
//      var nroOperacionInit = siteId * countNroOp
//      var nroOperacionFinish = (siteId+1) * countNroOp
//      doWithRedis { redis =>
//          for (nroOperacion <- nroOperacionInit to nroOperacionFinish) {
//            redis.sadd(siteTxKey, nroOperacion.toString)
//            redis.hincrBy(siteTxDetailKey, s"${nroOperacion}:reps", 1)
//            import scala.collection.JavaConversions._
//            redis.hmset(siteTransactionDetailKey(siteId.toString), Map(s"${nroOperacion}:status" -> status.toString))
//          }
//      }
//    }
//    val repeated = doWithRedis { _.sismember("0", "1000")}
//    
//    repeated shouldBe true
//
//  }  
  
  
  // TODO Testear como varios pasos colaboran en el armado de la operacion
}