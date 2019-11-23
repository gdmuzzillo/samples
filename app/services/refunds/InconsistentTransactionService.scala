package services.refunds

import javax.inject.Inject

import com.decidir.coretx.api._
import com.decidir.coretx.domain.TransactionRepository
import play.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Servicio que permite manejar transacciones con estados inconsistentes.
  *
  * Created by gustavo on 9/30/17.
  */
class InconsistentTransactionService @Inject()(implicit executionContext: ExecutionContext,
                                               transactionRepository: TransactionRepository,
                                               refundService: RefundService) {

  def logger = Logger.underlying()

  /**
    * Realiza la anulacion de transacciones autorizadas pero que tuvieron algun problema en la anulacion de si
    * mismas o de alguno de sus hijos.
    * Los casos a procesar son:
    *   Anulacion Automatica: pago simple, falla anulacion por CS
    *   Pago Distribuido: Falla la segunda y no puede anular la primera
    *   Anulacion Automatica: falla anulacion de todas las las hijas (luego de pago)
    *   Anulacion Automatica: falla anulacion de una subt (luego de pago)
    */
  def annulInconsistentTransactions() = {
    val transactionsRejectedByCS = transactionRepository.findTransactionsRejectedByCS()

    //Procesa la devolucion de transacciones de pago simple cuando falla la anulacion por CS.
    transactionsRejectedByCS.flatMap(tx => {
      tx.operationResource.map(or => {
        //Arma el cardtrackinfo si es que se trata de una operacion de ese tipo
        val cardTrackInfo = or.datos_banda_tarjeta.map(cardTrack => {
          CardTrackInfo(cardTrack.card_track_1, cardTrack.card_track_2, cardTrack.input_mode)
        })

        val security_code = or.datos_medio_pago.flatMap(_.security_code)

        val refundPaymentRequest = RefundPaymentRequest(cardTrackInfo, or.monto, None, security_code)

        val future = refundService.process(or.siteId, or.charge_id.get, Some(refundPaymentRequest))
        future.map(response =>
          response match {
            case (Some(refundResponse), Success(operationResponse), Some(transactionState)) => {
              logger.info(s"Transaccion ${or.idTransaccion.get} anulada correctamente")
            }
            case (None, Failure(fail), None) => {
              logger.warn(s"No se pudo realizar la anulacion de la transaccion ${or.idTransaccion.get}, message = ${fail.getMessage}")
            }
            case (Some(refundResponse), Success(operationResponse), None) => { //No se pudo realizar el rechazo
              logger.warn(s"No se pudo realizar la anulacion de la transaccion ${or.idTransaccion.get}, status = ${operationResponse.statusCode.toString} motivo = ${operationResponse.idMotivo}")
            }
          }
        )
      })
    })

    //Procesa la devolucion de transacciones distribuidas en el caso de que falla la segunda y no puede anular la primera
    val transactionsPartiallyRejected = transactionRepository.findDistributedTransactionsPartiallyRejected()
    annulDistributedTransaction(transactionsPartiallyRejected)

    //Procesa la devolucion luego de la falla de la anulacion de una o todas las transacciones hijas
    val distributedTransactionsRejectedByCS = transactionRepository.findDistributedTransactionsRejectedByCS()
    annulDistributedTransaction(distributedTransactionsRejectedByCS)
  }

  /**
    * Procesa las anulaciones de las transacciones distribuidas con hijos en estado Aprobado.
    *
    * @param transactions
    * @return
    */
  private def annulDistributedTransaction(transactions: List[OperationExecutionResponse]) = {
    transactions.map(tx => {
      tx.operationResource.map(or => {
        if (or.sub_transactions.size > 0) {
          logger.debug(s"Distributed transaction has ${or.sub_transactions.size}")

          val acceptedSubPayments = or.sub_transactions.filter(subpayment=> subpayment.status.get == 4)

          if (!acceptedSubPayments.isEmpty) {
            val authorizedSubPayments = acceptedSubPayments.map(subpayment => RefundSubPaymentRequest(subpayment.subpayment_id.get))
            val cardTrackInfo = or.datos_banda_tarjeta.map(cardTrack => {
              CardTrackInfo(cardTrack.card_track_1, cardTrack.card_track_2, cardTrack.input_mode)
            })

            val security_code = or.datos_medio_pago.flatMap(_.security_code)

            val refundPaymentRequest = RefundPaymentRequest(cardTrackInfo, or.monto, Some(authorizedSubPayments), security_code)
            val future = refundService.process(or.siteId, or.charge_id.get, Some(refundPaymentRequest))
            future.map(response => response match {
              case (Some(refundResponse), Success(operationResponse), Some(transactionState)) => {
                logger.info(s"Transaccion distribuida ${or.idTransaccion.get} anulada correctamente")
              }
              case (None, Failure(fail), None) => {
                logger.warn(s"No se pudo realizar la anulacion de la transaccion ${or.idTransaccion.get}, message = ${fail.getMessage}")
              }
              case (Some(refundResponse), Success(operationResponse), None) => { //No se pudo realizar el rechazo
                logger.warn(s"No se pudo realizar la anulacion de la transaccion ${or.idTransaccion.get}, status = ${operationResponse.statusCode.toString} motivo = ${operationResponse.idMotivo}")
              }
            })
          }
        } else {
          logger.info(s"Distributed transaction chargeId = ${or.charge_id.get} does not have subtransactions")
        }
      })
    })
  }

}
