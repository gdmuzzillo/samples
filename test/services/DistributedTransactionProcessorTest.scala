package services

import org.scalatestplus.play.PlaySpec
import org.scalatest.mock.MockitoSugar
import akka.actor.ActorSystem
import services.protocol.ProtocolService
import com.decidir.coretx.domain.SiteRepository
import services.protocol.Operation2ProtocolConverter
import com.decidir.coretx.api.SubTransaction
import com.decidir.coretx.api.DatosSiteResource
import java.text.SimpleDateFormat
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.domain.Site
import com.decidir.coretx.domain.MedioDePago
import com.decidir.coretx.domain.MarcaTarjeta
import com.decidir.coretx.domain.Moneda
import scala.util.Try
import com.decidir.coretx.api.ApiException
import scala.concurrent.Future
import com.decidir.protocol.api.TransactionResponse
import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures
import scala.util.Failure
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import com.decidir.coretx.api.ValidationError
import com.decidir.coretx.api.InvalidRequestError
import services.payments.LegacyTransactionServiceClient
import services.refunds.RefundService

class DistributedTransactionProcessorTest extends PlaySpec with MockitoSugar {
  
  val context = scala.concurrent.ExecutionContext.global
  val actorSystem : ActorSystem = mock[ActorSystem]
  val protocolService : ProtocolService = mock[ProtocolService]
  val siteRepository : SiteRepository = mock[SiteRepository]
  val legacyTxService : LegacyTransactionServiceClient = mock[LegacyTransactionServiceClient]
  val refundService: RefundService = mock[RefundService]

//  val distributedTransactionProcessor = new DistributedTransactionProcessor(context, 
//      actorSystem, 
//      protocolService, 
//      siteRepository, 
//      legacyTxService, 
//      refundService,
//      operation2ProtocolConverter)
  
  val sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss")
  val SITE_ID1 = "00300815"
  val AMOUNT1 = -1
  val INSTALLMENTS1 = Some(1)
  val NUMERO_TRACE1 = Some("1234") 
  val SUBPAYMENT_ID1= Some(123l)
  val SITE_ID = "00290815"
  val OPERATION_RESOURCE_ID = "id"
  val subTransaction = List(SubTransaction(SITE_ID1, AMOUNT1, Some(AMOUNT1), INSTALLMENTS1,NUMERO_TRACE1,SUBPAYMENT_ID1, None))
  val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID))
  
  
//  case class Site (id: String,
//                 description : Option[String],
//                 habilitado: Boolean,
//                 url: String,
//                 validaRangoNroTarjeta: Boolean,
//                 transaccionesDistribuidas: String,
//                 montoPorcent: String,                 
//                 reutilizaTransaccion: Boolean,
//                 enviarResumenOnLine: String,
//                 usaUrlDinamica: Boolean,
//                 urlPost: String,
//                 mandarMailAUsuario: Boolean,
//                 mail: String,
//                 replyMail: String,
//                 cuentas: List[Cuenta],
//                 rangos: List[RangosPermitidosTarjeta],
//                 mensajeria: String,
//                 subSites: List[InfoSite],
//                 encryptedForm: Option[EncryptedForm],
//                 cyberSourceConfiguration: Option[CyberSourceConfiguration])
  
//  "processDistributedTx with validationException nro_operacion amount < 0" should{
//      "throw ValidationError" in {
//        val site = Site("id", Some("descrip"), true, "url", true, "transaccionesDistribuidas", "M", true,"",true,"",false,"","", Nil, Nil, "mensajeria", Nil, None, None)
//        val operationResource = OperationResource(OPERATION_RESOURCE_ID,
//                               Some("nro_operacion"),
//                               Some(1234),
//                               Some("fechavto_cuota_1"), 
//                               Some(1000),
//                               Some(1),
//                               subTransaction,
//                               None, 
//                               None, 
//                               Some(datosSiteResource),
//                               None,
//                               None,
//                               Some(1),
//                               None)
//        val operationData = OperationData(operationResource, 
//                         site, 
//                         mock[MedioDePago], 
//                         mock[MarcaTarjeta],
//                         mock[Moneda])
//
//                        // TODO FIX
////        val fpr: Future[Try[TransactionResponse]] = distributedTransactionProcessor.processDistributedTx(operationData)
////        ScalaFutures.whenReady(fpr) { pr =>
////            pr match {
////              case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
////                assert(typeName == "invalid_param")
////                assert(param == "monto")
////              }
////            }
////        }
//      }
//  }
    
}