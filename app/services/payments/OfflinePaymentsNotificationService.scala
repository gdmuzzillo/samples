package services.payments

import javax.inject.{Inject, Singleton}

import com.decidir.coretx.api._
import com.decidir.coretx.domain.{OperationData, TransactionRepository}
import services.OperacionService

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
  * Created by gustavo on 9/25/17.
  */
@Singleton
class OfflinePaymentsNotificationService @Inject()(implicit executionContext: ExecutionContext,
                                                   transactionRepository: TransactionRepository,
                                                   legacyTxService: LegacyTransactionServiceClient,
                                                   operationService: OperacionService) {

  def notifyOfflinePayment(cod_trx: String, barra: String,transactionState: TransactionState): Try[Try[OperationExecutionResponse]] = Try{
    val operationExecutionResponseRes = transactionRepository.retrieveOERFromIdTrSiteNCod(cod_trx: String, barra: String)
    val operationExecutionResponse = operationExecutionResponseRes.getOrElse(throw ErrorFactory.missingDataException(List("operation execution response")))
    val datosSite: Option[DatosSiteResource] = operationExecutionResponse.operationResource.getOrElse(throw ErrorFactory.missingDataException(List("operation resource"))).datos_site
    val datosSite2: OperationResource = operationExecutionResponse.operationResource.getOrElse(throw ErrorFactory.missingDataException(List("operation resource"))).copy(datos_site = (datosSite.map(x => x.copy(origin_site_id = x.site_id))))
    val operationData: OperationData = operationService.validateOfflineButSessionTimeout(datosSite2)

    val chargeId = operationExecutionResponse.operationResource.getOrElse(throw ErrorFactory.missingDataException(List("operation resource"))).charge_id.getOrElse(throw ErrorFactory.missingDataException(List("Charge Id")))

    legacyTxService.update(
      UpdateTx(
        chargeId,
        operationData.site,
        distribuida = None,
        estadoFinal = transactionState,
        operationExecutionResponse,
        None
      )
    )

    operationExecutionResponseRes

  }

}
