package controllers.refund

import javax.inject.Inject

import com.decidir.coretx.domain.TransactionRepository
import controllers.MDCHelperTrait
import play.api.mvc.Controller
import services.refunds.{InconsistentTransactionService, RefundService}
import play.api.mvc.{Action, BodyParsers, Controller}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Controller que permite procesar anulaciones de transacciones que estan en un estado inconsistente.
  *
  *
  * Created by gustavo on 9/29/17.
  */
class AnnulInvalidTransactionController @Inject()(implicit context: ExecutionContext,
                                                  refundService: RefundService,
                                                  inconsistentTransactionService: InconsistentTransactionService)
  extends Controller with MDCHelperTrait {

  /**
    * Metodo que realiza la devolucion de las transacciones que por algun motivo quedaron en un estado inconsistente.
    *
    * @return
    */
  def process() = Action {request =>
    logger.info("AnnulInvalidTransactionController.process")

    this.inconsistentTransactionService.annulInconsistentTransactions()

    Ok("Realizando anulacion de transacciones pendientes de anulacion")
  }

}
