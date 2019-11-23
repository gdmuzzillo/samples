package services

import org.scalatestplus.play.PlaySpec
import org.scalatest.mock.MockitoSugar
import akka.actor.ActorSystem
import services.protocol.ProtocolService
import com.decidir.coretx.domain.SiteRepository
import com.decidir.coretx.domain.OperationResourceRepository
import services.protocol.Operation2ProtocolConverter
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.domain.Site
import com.decidir.coretx.domain.MedioDePago
import com.decidir.coretx.domain.MarcaTarjeta
import com.decidir.coretx.domain.Moneda
import com.decidir.coretx.domain.Site
import org.mockito.Mockito.when

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import com.decidir.coretx.api.ApiException

import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import com.decidir.protocol.api.OperationResponse
import org.scalatest._
import java.text.SimpleDateFormat

import com.decidir.coretx.api.SubTransaction
import com.decidir.coretx.api.DatosTitularResource
import com.decidir.coretx.api.DatosMedioPagoResource
import com.decidir.coretx.api.DatosSiteResource
import com.decidir.coretx.api.ValidationError
import com.decidir.coretx.api.InvalidRequestError
import services.payments.LegacyTransactionServiceClient
import services.payments.LegacyOperationServiceClient
import services.refunds.DistributedOperationProcessor
import services.refunds.RefundStateService
import com.decidir.coretx.domain.TransactionRepository
import com.decidir.util.RefundsLockRepository
import play.api.Configuration

class DistributedOperationProcessorTest extends PlaySpec with MockitoSugar {//with MockFactory{
  
  val context = scala.concurrent.ExecutionContext.global
  val actorSystem : ActorSystem = mock[ActorSystem]
  val protocolService : ProtocolService = mock[ProtocolService]
  val siteRepository : SiteRepository = mock[SiteRepository]
  val legacyOPxService : LegacyOperationServiceClient = mock[LegacyOperationServiceClient]
  val legacyTxService : LegacyTransactionServiceClient = mock[LegacyTransactionServiceClient]
  val operationRepository : OperationResourceRepository = mock[OperationResourceRepository]
  val refundStateService: RefundStateService = mock[RefundStateService]
  val transactionRepository: TransactionRepository = mock[TransactionRepository]
  val refundsLockRepository: RefundsLockRepository = mock[RefundsLockRepository]
  val configuration = mock[Configuration]

  when(configuration.getBoolean("lock.refunds.allowed")).thenReturn(Some(true))

  val distributedOperationProcessor = new DistributedOperationProcessor(context, actorSystem, protocolService,
      siteRepository, legacyOPxService, legacyTxService, operationRepository, transactionRepository,
      refundStateService, refundsLockRepository, configuration)
  
  val chargeId = 1L
  val sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss")
  val SITE_ID1 = "00300815"
  val AMOUNT1 = -1
  val INSTALLMENTS1 = Some(1)
  val NUMERO_TRACE1 = Some("1234")
  val SUBPAYMENT_ID1 = Some(123l)
  val SITE_ID = "00290815"
  val OPERATION_RESOURCE_ID = "id"
  val subTransaction = List(SubTransaction(SITE_ID1, AMOUNT1, Some(AMOUNT1), INSTALLMENTS1,NUMERO_TRACE1,SUBPAYMENT_ID1, None))
  val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID))
  

//  "processDistributedOPx with validationException nro_operacion amount < 0" should{
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
//                         // FIXME
////        val fpr: Future[(OperationResource, Try[OperationResponse])] = distributedOperationProcessor.processDistributedOPx(chargeId, operationData, mock[Operation])
////        ScalaFutures.whenReady(fpr) { pr =>
////            pr._2 match {
////              case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
////                assert(typeName == "invalid_param")
////                assert(param == "monto")
////              }
////            }
////        }
//      }
//  }
  
}