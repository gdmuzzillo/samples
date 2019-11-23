package services.cybersource

import com.decidir.coretx.api.CybersourceJsonMessages._
import controllers.utils.OperationJsonFormat._

import play.api.libs.json.JsArray
import play.api.libs.json.Json
import services.WSServiceTest
import com.decidir.coretx.api.TicketingTransactionData
import com.decidir.coretx.api.FraudDetectionData
import com.decidir.coretx.api.Item
import com.decidir.coretx.api.CustomerInSite
import com.decidir.coretx.api.PurchaseTotals
import com.decidir.coretx.api.BillingData
import org.scalatest.Matchers
import org.scalatest.FlatSpec
import play.api.Configuration
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext
import com.decidir.coretx.api.SubTransaction
import com.decidir.coretx.api.DatosSiteResource
import com.decidir.coretx.api.DatosMedioPagoResource
import com.decidir.coretx.api.DatosTitularResource
import com.decidir.coretx.api.OperationResource
import java.text.SimpleDateFormat
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.Future
import scala.util.Try
import scala.concurrent.duration._
import scala.concurrent.Await
import com.decidir.coretx.api.PurchaseTotals
import com.decidir.coretx.domain._
import org.scalatest.Ignore

/**
 * 
 * @author martinpaoletta
 */
@Ignore
class CybersourceServiceTest {//extends FlatSpec with Matchers with WSServiceTest{

  // TODO REVISAR ESTO! <urn:merchantReferenceCode>soapui_3010aq</urn:merchantReferenceCode>
  
  
/*
                        <urn:item id="0">
                                <urn:unitPrice>100.00</urn:unitPrice>
                                <urn:quantity>1</urn:quantity>
                                <urn:productCode>10</urn:productCode>
                                <urn:productName>Dia 1 Campo</urn:productName>
                                <urn:productSKU>10</urn:productSKU>
                                <urn:totalAmount>100.00</urn:totalAmount>
                                <urn:productDescription>null</urn:productDescription>
                        </urn:item>
*/  
  
/* 
  <urn:billTo>
          <urn:firstName>Ronny</urn:firstName>
          <urn:lastName>De Jesus</urn:lastName>
          <urn:street1>1295 Charleston Road</urn:street1>
          <urn:street2/>
          <urn:city>Mountain View</urn:city>
          <urn:state>CA</urn:state>
          <urn:postalCode>94043</urn:postalCode>
          <urn:country>US</urn:country>
          <urn:phoneNumber>1536369987</urn:phoneNumber>
          <urn:email>nnydjesus@gmail.com
          </urn:email>
          <urn:ipAddress>127.0.0.1</urn:ipAddress>
          <urn:customerID>3</urn:customerID>
  </urn:billTo>  
*/
//    val billTo = BillingData(
//                  first_name = "Ronny",
//                  last_name = "De Jesus",
//                  street1 = "1295 Charleston Road",
//                  city = "Mountain View",
//                  state = "CA",
//                  postal_code = "94043",
//                  country = "US",
//                  phone_number = "1536369987",
//                  email = "nnydjesus@gmail.com",
//                  ip_address= Some("127.0.0.1"),
//                  customer_id = "3")
//  
//    val items = List(
//              Item(
//                  unit_price = Some(100),
//                  quantity = Some(1),
//                  code = Some("10"),
//                  name = Some("Dia 1 Campo"),
//                  sku = Some("10"),
//                  total_amount = Some(100),
//                  description = None))
//    
//                  /*
//                        <urn:purchaseTotals>
//                                <urn:currency>ARS</urn:currency>
//                                <urn:grandTotalAmount>21.90</urn:grandTotalAmount>
//                        </urn:purchaseTotals>    
//                  */
//    val purchaseTotals = PurchaseTotals("ARS", 2190)
//    
//    /*
//                        <urn:card>
//                                <urn:accountNumber>4507990000004905</urn:accountNumber>
//                                <urn:expirationMonth>08</urn:expirationMonth>
//                                <urn:expirationYear>18</urn:expirationYear>
//                                <urn:cardType>001</urn:cardType>
//                        </urn:card>
//    */
//    
//    // card data            
//  val MEDIO_DE_PAGO = Some(1)
//  val ID_MONEDA = Some(1)
//  val MARCA_TARJETA = Some(7) // TODO revisar=> 001 
//  val NRO_TARJETA =Some("4507990000004905")
//  val NOMBRE_EN_TARJETA = Some("visa")
//  val EXPIRATION_MONTH = Some("08")
//  val EXPIRATION_YEAR = Some("2018")
//  val SECURITY_CODE = Some("code")
//  val BIN_FOR_VALIDATION = Some("1111")
//  val NRO_TRACE = Some("nro_trace")
//  val COD_AUTORIZACION = Some("cod_autorizacion")
//  val OPERATION_RESOURCE_ID = "id"
//    
//  
////case class DatosMedioPagoResource(medio_de_pago: Option[Int] = None,
////                          id_moneda: Option[Int] = None, 
////                          marca_tarjeta: Option[Int] = None,
////                          nro_tarjeta: Option[String] = None,
////                          nombre_en_tarjeta: Option[String] = None,
////                          expiration_month: Option[String] = None,
////                          expiration_year: Option[String] = None, 
////                          security_code: Option[String] = None,
////                          bin_for_validation: Option[String] = None,
////                          nro_trace: Option[String] = None,
////                          cod_autorizacion: Option[String] = None,
////                          nro_devolucion: Option[Int] = None) {  
//    val datosMedioPagoResource = 
//      DatosMedioPagoResource(
//        medio_de_pago = MEDIO_DE_PAGO, 
//        id_moneda = ID_MONEDA, 
//        marca_tarjeta = MARCA_TARJETA, 
//        nro_tarjeta = NRO_TARJETA, 
//        nombre_en_tarjeta = NOMBRE_EN_TARJETA, 
//        expiration_month = EXPIRATION_MONTH, 
//        expiration_year = EXPIRATION_YEAR,
//        security_code = Some("123"))
//        
//    val ticketingData = 
//      TicketingTransactionData(
//          55, 
//          "Pick up",
//          items)
//          
//    val fdd = 
//      FraudDetectionData(
//          bill_to = Some(billTo), 
//           purchase_totals = Some(purchaseTotals), 
//           channel = Some("Web"), 
//           customer_in_site = Some(CustomerInSite(
//               Some(243),
//               Some(false),
//               Some("abracadabra"),
//               Some(1), 
//               Some("12121"))),
//           device_unique_id = Some("devicefingerprintid"),
//           ticketing_transaction_data = Some(ticketingData))
//  
//  val SITE_ID1 = "00300815"
//  val AMOUNT1 = 800
//  val INSTALLMENTS1 = 1
//  val NUMERO_TRACE1 = Some("1234")
//  val SUBPAYMENT_ID1 = Some(123l)
//  
//  val SITE_ID = "00290815"
//
//  val SITE_ID2 = "00310815"
//  val AMOUNT2 = 800
//  val INSTALLMENTS2 = 1
//  val NUMERO_TRACE2 = Some("4321")
//  val SUBPAYMENT_ID2 = Some(321l)
//
//  val EMAIL_CLIENTE = Some("maxi@gmail.com")
//  val TIPO_DOC = Some(1)
//  val NRO_DOC = Some("12121212")
//  val CALLE = Some("av corrientes")
//  val NRO_PUERTA = Some(1)
//  val FECHA_NACIMIENTO = Some("12-12-1234")
//  
//  val subTransaction = List(SubTransaction(SITE_ID1, AMOUNT1, INSTALLMENTS1,NUMERO_TRACE1,SUBPAYMENT_ID1), SubTransaction(SITE_ID2, AMOUNT2, INSTALLMENTS2,NUMERO_TRACE2,SUBPAYMENT_ID2))
//  val datosTitularResource = DatosTitularResource(EMAIL_CLIENTE, TIPO_DOC, NRO_DOC, CALLE, NRO_PUERTA, FECHA_NACIMIENTO)
//  val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID))
//  
//  val sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss")
//  
////  val operationResource = OperationResource(OPERATION_RESOURCE_ID,
////                               Some("nro_operacion"),
////                               Some(1234),
////                               Some("fechavto_cuota_1"), 
////                               Some(1000),
////                               Some(1),
////                               subTransaction,
////                               Some(datosTitularResource), 
////                               Some(datosMedioPagoResource), 
////                               Some(datosSiteResource),
////                               Some(sdf.parse("08-06-2016 00:00:00")),
////                               Some(sdf.parse("09-06-2016 00:00:00")),
////                               Some(1),
////                               Some("2"),
////                               Some(fdd))  
//
//
//  val site = Site.fromJson("""{"id":"00020515","description":"","habilitado":true,"url":"https://sps433.decidir.net;http://dexter.vtexcommercestable.com.br;http://stockcenter.vtexcommercestable.com.br;https://sandbox.decidir.com;http://semexpert.vtexcommercestable.com.br","validaRangoNroTarjeta":false,"transaccionesDistribuidas":"","montoPorcent":"","reutilizaTransaccion":false,"cuentas":[{"medioPago":{"id":"1","descripcion":"Visa","idMoneda":"1","idMarcaTarjeta":4,"limite":0},"idProtocolo":7,"idBackend":"6","nroId":"03659307","estaHabilitadaParaOperarConPlanN":false,"habilitado":true,"numeroDeTerminal":"99002002","utilizaAutenticacionExterna":false,"autorizaEnDosPasos":false,"planCuotas":"0","pasoAutenticacionExterna":true,"pasoAutenticacionExternaSinServicio":true,"formatoNroTarjetaVisible":"","password":"","pagoDiferidoHabilitado":false,"aceptaSoloNacional":false,"filtrarPorBin":false,"tipoPlantilla":"1","nroIdDestinatario":""},{"medioPago":{"id":"20","descripcion":"Mastercard Test","idMoneda":"1","idMarcaTarjeta":6,"limite":0},"idProtocolo":8,"idBackend":"7","nroId":"1124","estaHabilitadaParaOperarConPlanN":false,"habilitado":true,"numeroDeTerminal":"0","utilizaAutenticacionExterna":false,"autorizaEnDosPasos":false,"planCuotas":"","pasoAutenticacionExterna":false,"pasoAutenticacionExternaSinServicio":true,"formatoNroTarjetaVisible":"","password":"","pagoDiferidoHabilitado":false,"aceptaSoloNacional":false,"filtrarPorBin":false,"tipoPlantilla":"1","nroIdDestinatario":""}],"rangos":[],"mensajeria":"","subSites":[],"cyberSourceConfiguration":{"enabled":true,"withAutoReversals":false,"vertical":"Retail","mid":"decidir_agregador","securityKey":"7INYIZLob1xKu4W4xhE7l8HYRgf13MQhIA8XtuRCxW0L9VNqIHXBJCvWMbHzbbv0n771oi3jMEL27xOtt0uD5fbeBRqR0N66QevdhXAQ+5KFHJvmoBZ3Xgq2jFTltP6flXN6PPlN3X6XYmPuBhJoqlrFfuo7sD24pM0qRbZFeRf1/13ZrYA+K3vNe6ATvyHr6TF2jMiK8RkstPl97AE95l7B2zYqxWpL2cpPh/WAFFReOFkgqnpo2NYqe3ZRfcYFMBTpJtmZVJu5gnEDBY7cxCp8jmLz5V9cphX5FO19KaBPkMvHFvVjGcXp8JOcL162sNo5hcKZNFCFIZ3ZFizVnQ=="}}""").get
//                               
//  val medioDePago = MedioDePago.fromJson("""{"id":"1","descripcion":"Visa","idMoneda":"1","idMarcaTarjeta":4,"limite":0}""").get
//  
//  val moneda = Moneda.fromJson("""{"id":"6","descripcion":"East Carribean Dollar","simbolo":"$CD","idMonedaIsoAlfa":"XCD","idMonedaIsoNum":"951"}""").get
//
//  val marcaTarjeta = MarcaTarjeta.fromJson("""{"id":2,"descripcion":"American Express","codAlfaNum":"AMEX","sufijoPlantilla":"_amex","rangosNacionales":[{"e1":"376632955971003","e2":"376632955971003"}]}""").get
//  
////  val opdata = OperationData(operationResource, site, medioDePago, marcaTarjeta, moneda)
//  
//  val configuration = Configuration(ConfigFactory.parseString(
//    """
//    |sps.cybersource.url="https://ics2wstest.ic3.com:443/commerce/1.x/transactionProcessor"
//  """.stripMargin))    
//        
//  val ws = wsClient
////  val csService = new CybersourceClient(ws, configuration, ExecutionContext.global)
////           
////           
////  // JA
////  "CyberSourceService" should "work" in {
////      
////      val futureDecision = csService.call(opdata, Some(true))
////      val decision = Await.result(futureDecision, 20 seconds)
////      println(decision)
////      
////      decision.decision shouldBe Green()
////      
//////      ScalaFutures.whenReady(futureDecision) { decision =>
//////        println(decision)
//////        ws.close
//////      }
////      ws.close
////  }
//  
  
}