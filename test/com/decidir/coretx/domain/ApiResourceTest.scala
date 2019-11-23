package com.decidir.coretx.domain

import org.scalatest.Matchers
import org.scalatest.FlatSpec
import org.scalatest._
import org.scalatest.OptionValues._
import org.scalatest.Matchers
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.utils.ApiSupport
import com.decidir.coretx.api.DatosTitularResource
import com.decidir.coretx.api.DatosMedioPagoResource
import com.decidir.coretx.api.DatosSiteResource

//import com.decidir.coretx.domain.OperationResourceRepository

/**
 * @author martinpaoletta
 */
class ApiResourceTest extends FlatSpec with Matchers {
  
  val datosTitular = DatosTitularResource(
                                  Some("prueba@test.com"),
                                  Some(1),
                                  Some("23123456"),
                                  Some("Av Mitre"),
                                  Some(4567),
                                  Some("29/04/1974"),
                                  ip = Some("192.168.1.1"))
  
//  case class DatosMedioPagoResource(medio_de_pago: Option[Int] = None,
//                          id_moneda: Option[Int] = None, 
//                          marca_tarjeta: Option[Int] = None,
//                          nro_tarjeta: Option[String] = None,
//                          nombre_en_tarjeta: Option[String] = None,
//                          expiration_month: Option[String] = None,
//                          expiration_year: Option[String] = None, 
//                          security_code: Option[String])                                 
  val datosMedioPago = DatosMedioPagoResource(
                                      medio_de_pago = Some(1),
                                      id_moneda = Some(2),
                                      marca_tarjeta = Some(3),
                                      nro_tarjeta = Some("1nro_tarjeta234"),
                                      nombre_en_tarjeta = Some("nombre_en_tarjeta Perez"),
                                      expiration_month = Some("04"),
                                      expiration_year = Some("18"),
                                      security_code = Some("123"))                                

                                      
  val datosSite = DatosSiteResource(Some("site_id"), Some("url_dinamica"), Some("param_sitio"), Some(true), 
                            Some("url_origen"), Some("referer"))
                            
                            
//case class OperationResource(transactionId: String = "",
//                             nro_operacion: Option[String] = None,
//                             fechavto_cuota_1: Option[String] = None, 
//                             monto: Option[BigDecimal] = None,
//                             cuotas: Option[Int] = None,
//                             imp_dist: Option[String] = None,
//                             cuotas_dist: Option[String] = None,
//                             datos_titular: Option[DatosTitularResource] = None, 
//                             datos_medio_pago: Option[DatosMedioPagoResource] = None, 
//                             datos_site: Option[DatosSiteResource] = None) {                            
                            
  val operacion = OperationResource(
                            id = "123", 
                            nro_operacion = Some("nro_operacion"), 
                            fechavto_cuota_1 = Some("yyyyMMdd"), 
                            monto = Some(5000), 
                            cuotas = Some(3),
                            datos_titular = Some(datosTitular), 
                            datos_medio_pago = Some(datosMedioPago), 
                            datos_site = Some(datosSite))  
  
  
  "Una OperationResource" should "poder convertirse a mapa" in {
    
    val map = ApiSupport.toMap(operacion)
    val unmarshalled = OperationResourceRepository.fromMap(map)
    
//    retrieved shouldBe defined
    unmarshalled shouldBe operacion 

    val withNones = unmarshalled.copy(datos_site = Some(DatosSiteResource(None, Some("url_dinamica1"), None, None, None, None)), 
                                      monto=None, datos_medio_pago = None)
    val mapWithNones = ApiSupport.toMap(withNones)
    val restored = OperationResourceRepository.fromMap(mapWithNones)
    restored shouldBe withNones
  }    
  
}