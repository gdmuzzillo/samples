package com.decidir.coretx.domain

import com.decidir.coretx.utils.JedisUtils
import com.decidir.coretx.domain.OperationResourceRepository
import com.decidir.coretx.utils.JedisPoolProvider
import org.scalatest.FlatSpec
import play.api.Configuration
import org.scalatest.Matchers
import com.decidir.coretx.domain.MarcaTarjetaRepository
import com.decidir.coretx.domain.MonedaRepository
import com.decidir.coretx.domain.MedioDePago
import com.decidir.coretx.domain.Cuenta
import com.decidir.coretx.domain.Site
import com.decidir.coretx.domain.SiteRepository
import com.decidir.encrypt.EncryptionService
import com.decidir.encrypt.EncryptionRepository

class RepositoriesTest extends FlatSpec with Matchers { //with JedisUtils {
  // TODO Armar configuracion para test
  
//  val jedisPoolProvider = new JedisPoolProvider(Configuration.empty)
//  val encripcionService = new EncripcionService(new EncripcionRepository(jedisPoolProvider), Configuration.from(Map("sps.encryption.key" -> "b8c2ca4a7baed8e334dc49c01c7ea22d016f83bf66b288333a163233ed46565e")) )
//  val operationRepository = new OperationResourceRepository(jedisPoolProvider, Configuration.empty, encripcionService)
//  val jedisPool = jedisPoolProvider.get
//  val monedaRepository = new MonedaRepository(jedisPoolProvider)
//  val siteRepository = new SiteRepository(jedisPoolProvider)
//  val marcaTarjetaRepository = new MarcaTarjetaRepository(jedisPoolProvider)
//  
////  "SiteRepository" should "persist and retrieve sites" in {
////    
//////    1) "cuentas"
//////    2) "List(Cuenta(MedioDePago(1,Visa,Some(1),Some(4),0.0),7,6,03659307,false,true,99002002,false,false,0,true,true,,,false,false,false,1,), Cuenta(MedioDePago(20,Mastercard Test,Some(1),Some(6),0.0),8,7,1124,false,true,0,false,false,,false,true,,,false,false,false,1,))"
//////    3) "habilitado"
//////    4) "true"
//////    5) "url"
//////    6) "http://177.69.58.25;https://sps433.decidir.net;http://200.155.23.139;http://200.155.23.140;http://201.77.195.181;http://201.77.195.182"
//////    7) "id"
//////    8) "00300615"    
//////case class Cuenta(medioPago: MedioDePago, idProtocolo: Int, idBackend: String, nroId: String, 
//////                  estaHabilitadaParaOperarConPlanN: Boolean, habilitado: Boolean, numeroDeTerminal: String, 
//////                  utilizaAutenticacionExterna: Boolean, autorizaEnDosPasos: Boolean, planCuotas: String,
//////                  pasoAutenticacionExterna: Boolean, pasoAutenticacionExternaSinServicio: Boolean, 
//////                  formatoNroTarjetaVisible: String, password: String, pagoDiferidoHabilitado: Boolean, 
//////                  aceptaSoloNacional: Boolean, filtrarPorBin: Boolean, tipoPlantilla: String, nroIdDestinatario: String)
////    val mp = MedioDePago("1", "Visa", Some("1"), Some(4), 0.0)
////    val cuenta = Cuenta(mp, 7, "6", "03659307", 
////                        false, true, "99002002", 
////                        false, false, "0", 
////                        true, true, 
////                        "", "", false, 
////                        false, false, "1", "")
//////case class Site (id: String, habilitado: Boolean, url: String, cuentas: List[Cuenta]) extends ToMap {
////          val site = Site("id", Some("descrip"), true, "url", true, "transaccionesDistribuidas", "M", true,"",true,"",false,"","", Nil, Nil, "mensajeria", Nil, None, None)
//////val id: String, habilitado: Boolean, url: String, val validaRangoNroTarjeta: Boolean, 
//////                 val transaccionesDistribuidas: String, val montoPorcent: String,
//////                 reutilizaTransaccion: Boolean, 
//////                 cuentas: List[Cuenta], rangos: List[RangosPermitidosTarjeta], subSites: List[InfoSite]                        
////                    
////                    
////    siteRepository.store(site)
////    
////    val osite = siteRepository.retrieve("00300615")
////    
////    osite shouldBe defined
////    osite.get shouldBe site
////    
////  }  
//  
//  "MonedaReposiory" should "listar monedas" in {
//    
//    val list = monedaRepository.retrieveAll
//    list.foreach { println }
//    
//  }   
//  
//  
//  "MarcaTarjetaReposiory" should "listar marcas de tarjeta" in {
//    
//    val list = marcaTarjetaRepository.retrieveAll
//    list.foreach { println }
//    
//  }  
//  
  

}