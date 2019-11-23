package com.decidir.coretx.domain

import com.decidir.coretx.utils.JedisUtils
import com.decidir.coretx.utils.JedisPoolProvider
import org.scalatest.FlatSpec
import play.api.Configuration
import org.scalatest.Matchers

class SiteTests  extends FlatSpec with Matchers { // with JedisUtils {
// @Inject() (jedisPoolProvider: JedisPoolProvider, operationRepository: OperationResourceRepository) 
  // TODO Armar configuracion para test
//  
//  val jedisPoolProvider = new JedisPoolProvider(Configuration.empty)
//  val siteRepository = new SiteRepository(jedisPoolProvider)
//  val jedisPool = jedisPoolProvider.get
//  
//  "Un Site" should "poder leerse desde Redis" in {
//    
//    val siteId = "00220714"
//    val retrieved = siteRepository.retrieve("00220714")
//    retrieved shouldBe defined
//    retrieved.get.cuentas.size shouldBe 2 
//    
//  }    
  
}