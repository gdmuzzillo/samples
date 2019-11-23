
package services

import org.scalatestplus.play.PlaySpec
import org.scalatest.mock.MockitoSugar
import akka.actor.ActorSystem
import services.protocol.ProtocolService
import com.decidir.coretx.domain.SiteRepository
import services.protocol.Operation2ProtocolConverter
import com.decidir.coretx.domain.TransactionRepository
import com.decidir.coretx.utils.JedisPoolProvider
import com.decidir.coretx.domain.MonedaRepository
import com.decidir.coretx.domain.MarcaTarjetaRepository
import com.decidir.coretx.domain.OperationResourceRepository
import com.decidir.coretx.domain.MedioDePagoRepository

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.mockito.Mockito.when
import com.decidir.coretx.api.ApiException

import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import com.decidir.coretx.api.OperationResource
import java.text.SimpleDateFormat

import com.decidir.coretx.api.SubTransaction
import com.decidir.coretx.api.DatosSiteResource

import scala.util.Try
import org.mockito.Mockito.spy
import java.util

import com.decidir.coretx.domain.Site
import com.decidir.coretx.api.ErrorMessage
import services.payments.LegacyTransactionServiceClient
import com.decidir.coretx.api.InvalidRequestError
import com.decidir.coretx.api.ValidationError
import services.payments.LegacyOperationServiceClient
import services.refunds.DistributedOperationProcessor
import services.refunds.RefundService
import services.refunds.RefundStateService
import services.converters.OperationResourceConverter
import com.decidir.coretx.domain.OperationRepository
import com.decidir.coretx.domain.MotivoRepository
import com.decidir.util.RefundsLockRepository
import play.api.Configuration
import services.validations.MPOSValidator

class RefundServiceTest extends PlaySpec with MockitoSugar {
  
  val context = scala.concurrent.ExecutionContext.global
  val actorSystem : ActorSystem = mock[ActorSystem]
  val transactionRepository : TransactionRepository = mock[TransactionRepository]
  val protocolService : ProtocolService = mock[ProtocolService]
  val jedisPoolProvider : JedisPoolProvider = mock[JedisPoolProvider]
  val siteRepository : SiteRepository = mock[SiteRepository]
  val medioDePagoRepository: MedioDePagoRepository = mock[MedioDePagoRepository]
  val monedaRepository : MonedaRepository = mock[MonedaRepository]
  val marcaTarjetaRepository : MarcaTarjetaRepository = mock[MarcaTarjetaRepository]
  val legacyOpxService : LegacyOperationServiceClient = mock[LegacyOperationServiceClient]
  val operationRepository : OperationResourceRepository = mock[OperationResourceRepository]
  val distributedOperationProcessor : DistributedOperationProcessor = mock[DistributedOperationProcessor]
  val legacyTxService : LegacyTransactionServiceClient = mock[LegacyTransactionServiceClient]
  val refundStateService: RefundStateService = mock[RefundStateService]
  val operationResourceConverter: OperationResourceConverter = mock[OperationResourceConverter]
  val oRepository: OperationRepository = mock[OperationRepository]
  val motivoRepository: MotivoRepository = mock[MotivoRepository]
  val mPOSValidator: MPOSValidator = mock[MPOSValidator]
  val refundsLockRepository = mock[RefundsLockRepository]
  val configuration = mock[Configuration]

  when(configuration.getBoolean("lock.refunds.allowed")).thenReturn(Some(true))

  val refundService = new RefundService(context,
      actorSystem,
      transactionRepository,
      protocolService,
      jedisPoolProvider,
      siteRepository,
      legacyOpxService,
      operationRepository,
      distributedOperationProcessor,
      legacyTxService,
      refundStateService,
      operationResourceConverter,
      oRepository,
      motivoRepository,
      mPOSValidator,
      refundsLockRepository,
      configuration)
  
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
//      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
//                     Some("nro_operacion"),
//                     Some(1234),
//                     Some("fechavto_cuota_1"), 
//                     Some(1000),
//                     Some(1),
//                     subTransaction,
//                     None, 
//                     None, 
//                     Some(datosSiteResource),
//                     None,
//                     None,
//                     Some(1),
//                     None)
//                 
                     // TODO FIx
//        when(transactionRepository.retrieveCharges(chargeId)) thenReturn (Try.apply(List(operationResource)))
//        when(siteRepository.retrieve(SITE_ID)) thenReturn (None)
// FIXME       
//        val fpr: Future[(OperationResource, Try[OperationResponse])] = refundService.process(chargeId)
//        ScalaFutures.whenReady(fpr) { pr =>
//            pr._2 match {
//              case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
//                assert(typeName == "invalid_param")
//                assert(param == ErrorMessage.NUMBER_BUSINESS)
//              }
//            }
//        }
//      }
//  }
  
}