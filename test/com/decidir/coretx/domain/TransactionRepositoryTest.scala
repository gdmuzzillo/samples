package com.decidir.coretx.domain

import com.decidir.coretx.utils.JedisUtils
import play.api.db.Databases
import com.decidir.coretx.utils.JedisPoolProvider
import org.scalatest.FlatSpec
import play.api.Configuration
import org.scalatest.Matchers
import com.decidir.encrypt.EncryptionService
import com.decidir.encrypt.EncryptionRepository

class TransactionRepositoryTest extends FlatSpec with Matchers { //with JedisUtils {
  // TODO Armar configuracion para test
  
//  val jedisPoolProvider = new JedisPoolProvider(Configuration.empty)
////  val operationRepository = new OperationResourceRepository(jedisPoolProvider, Configuration.empty)
//  val jedisPool = jedisPoolProvider.get
//  
//  val database = Databases(
//    driver = "com.mysql.jdbc.Driver",
//    url = "jdbc:mysql://localhost/sps433",
//    config = Map(
//        "user" -> "spsT_usr",
//        "password" -> "veef8Eed"
//    )    
//  )  
// 
//  val marcaTarjetaRepository = new MarcaTarjetaRepository(jedisPoolProvider)
//  val encripcionService = new EncripcionService(new EncripcionRepository(jedisPoolProvider), Configuration.from(Map("sps.encryption.key" -> "b8c2ca4a7baed8e334dc49c01c7ea22d016f83bf66b288333a163233ed46565e")) )
//  val transactionRepository = new TransactionRepository(database, marcaTarjetaRepository, encripcionService, Configuration.empty)
//  val operationResourceRepository = new OperationResourceRepository(jedisPoolProvider, Configuration.empty, encripcionService)
//  
//  "Una operacion" should "poder guardarse como Json y obtenerse nuevamente" in {
//
//      database.getConnection().createStatement().execute("delete from transaccion_operacion_xref where operation_id = '1234'")
//    
//      val op = OperationResourceTests.operacion
//      
//      val chargeId = operationResourceRepository.newChargeId
//      
////      transactionRepository.insertTransaccionOperacionXref(4, "1234", chargeId, op)
//      // TODO FIX!!!
//      
////      val ope = transactionRepository.retrieveCharge(chargeId)
////    
////      ope.get shouldBe op
//    
//  }
//  
  
  
  
}