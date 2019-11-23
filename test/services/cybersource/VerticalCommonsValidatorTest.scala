package services.cybersource

import java.util.Date

import scala.collection.mutable.ArrayBuffer
import scala.math.BigDecimal.double2bigDecimal

import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec

import com.decidir.coretx.api.BillingData
import com.decidir.coretx.api.CustomerInSite
import com.decidir.coretx.api.DatosMedioPagoResource
import com.decidir.coretx.api.DatosSiteResource
import com.decidir.coretx.api.DatosTitularResource
import com.decidir.coretx.api.FraudDetectionData
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.api.PurchaseTotals
import com.decidir.coretx.api.TicketingTransactionData
import com.decidir.coretx.api.ValidationError
import com.decidir.coretx.domain.Cuenta
import com.decidir.coretx.domain.CyberSourceConfiguration
import com.decidir.coretx.domain.InfoSite
import com.decidir.coretx.domain.MarcaTarjeta
import com.decidir.coretx.domain.MedioDePago
import com.decidir.coretx.domain.Moneda
import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.domain.RangosPermitidosTarjeta
import com.decidir.coretx.domain.Site

import javax.inject.Inject

class VerticalCommonsValidatorTest extends PlaySpec with MockitoSugar {
  
  val context = scala.concurrent.ExecutionContext.global
  
  val verticalCommonsValidator = new VerticalCommonsValidator(context)
  
  val datosSite = DatosSiteResource(Some("site_id"), Some("url_dinamica"), Some("param_sitio"), Some(true), 
                          Some("url_origen"), Some("referer"))
  
//  var billTo = Some(BillingData(
//              "Buenos Aires", 
//              "ARGENTINA", 
//              "maxiid",
//              "maxi@redb.ee", 
//              "Maxi",
//              "PPP", 
//              "2322323232",
//              "B", 
//              "Buenos Aires",
//              "Lavalle 12",
//              Some("Lavalle 1234"),
//              Some("127.0.0.1")))
//  var fdd = 
//      FraudDetectionData(
//          bill_to = billTo, 
//           purchase_totals = Some(PurchaseTotals("ARS", 12444)), 
//           channel = Some("Web"), 
//           customer_in_site = Some(CustomerInSite(
//               Some(243),
//               Some(false),
//               Some("abracadabra"),
//               Some(1), 
//               Some("12121"))),
//           device_unique_id = Some("devicefingerprintid"),
//           ticketing_transaction_data = Some(mock[TicketingTransactionData])) 
////  var site = Site("id", Some("descrip"), true, "url", true, "transaccionesDistribuidas", "M", true,"",true,"",false,"","", Nil, Nil, "mensajeria", Nil, None, None)
//  var medioDePago = mock[MedioDePago] 
//  var marcaTarjeta = mock[MarcaTarjeta]
//  var monedas = mock[Moneda]
//  var datosMedioPagoResource = DatosMedioPagoResource(
//                Some(1),
//                Some(1),
//                Some(1),
//                Some("nro_tarjeta"),
//                Some("nombre_en_tarjeta"),
//                Some("07"),
//                Some("2016"),
//                Some("security_code"),
//                Some("bin_for_validation"),
//                Some("nro_trace"),
//                Some("cod_autorizacion"),
//                Some(1))
//  
//  
//  
//  "validate" should{
//      "return OperationResource" in {
//        var operationResource = OperationResource(
//                            id = "123", 
//                            nro_operacion = Some("nro_operacion"), 
//                            fechavto_cuota_1 = Some("yyyyMMdd"), 
//                            monto = Some(5000), 
//                            cuotas = Some(3),
//                            datos_titular = Some(mock[DatosTitularResource]), 
//                            datos_medio_pago = Some(datosMedioPagoResource), 
//                            datos_site = Some(datosSite),
//                            creation_date = Some(new Date),
//                            last_update = Some(new Date),
//                            ttl_seconds = Some(50),
//                            fraud_detection = Some(fdd)) 
//        
//        var operationData = OperationData(operationResource, site, medioDePago, marcaTarjeta, monedas)
////        var errors = ArrayBuffer[ValidationError]()
//        
//        val errors = verticalCommonsValidator.validate(operationData)
//        
//        errors.size mustBe(0)        
//      }
//  }
  
}