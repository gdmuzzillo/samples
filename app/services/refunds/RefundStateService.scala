package services.refunds

import scala.concurrent.ExecutionContext
import javax.inject.Inject

import com.decidir.coretx.api.TransactionState
import com.decidir.coretx.api.Autorizada
import com.decidir.coretx.domain._
import com.decidir.coretx.api.Acreditada
import com.decidir.coretx.api.TxDevuelta
import com.decidir.coretx.api.AutorizadaAsterisco

import scala.annotation.implicitNotFound
import com.decidir.coretx.api.PreAutorizada
import com.decidir.coretx.api.ErrorMessage
import play.Logger
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.AReversar

class RefundStateService @Inject() (context: ExecutionContext, medioDePagoRepository: MedioDePagoRepository){
  
  implicit val ec = context
  def logger = Logger.underlying()
  
  /**
   * Operaciones sobre una transaccion
   */
  def createOperationType(status: TransactionState, originalAmountAnnulment: Boolean, newAmount: Long, meanPaymentId: Long, bin: String, isMpos: Boolean): Operation = {
    val operations = getMeanPaymentOperations(meanPaymentId, isMpos)
    
    status match {
      case PreAutorizada() => {
        if (operations.annulment_pre_approved && hasValidCardBrandBin(meanPaymentId, bin)) {
          CancelPreApprovedOperation()
        } else {
          MeanPaymentErrorOperation(PreAutorizada().id)
        }
      }
      case Autorizada() => {
        if (originalAmountAnnulment) {
          if (operations.annulment) {
        	  CancelOperation()          
          } else {
        	  MeanPaymentErrorOperation(Autorizada().id)
          }
        } else if (operations.refundPartialBeforeClose && hasValidCardBrandBin(meanPaymentId, bin)) {
          PartialRefundBeforeClosureOperation()
        } else {
          MeanPaymentErrorOperation(Autorizada().id)
        }
      }
      case Acreditada() => {
        if (newAmount == 0L) {
          if (operations.refund) {            
        	  RefundOperation()
          } else {
            MeanPaymentErrorOperation(Acreditada().id)
          }
        } else {
          if (operations.refundPartialAfterClose) {
            PartialRefundOperation()
          } else {
            MeanPaymentErrorOperation(Acreditada().id)
          }
        }
      }
      case other => NonExistanceOperation(other.id)
    }

  }
  
  /**
   * Anulacion de operaciones
   */
  def createOperationType(status: TransactionState, meanPaymentId: Long, bin: String, isMpos: Boolean): Operation = {
    val operations = getMeanPaymentOperations(meanPaymentId, isMpos)
    
    if (hasValidCardBrandBin(meanPaymentId, bin)) {
      status match {
        case TxDevuelta()          => {
          if (operations.refundAnnulment) {
            CancelRefundAfterClosureOperation() 
          } else {
            MeanPaymentErrorOperation(TxDevuelta().id)
          }
        }
        case AutorizadaAsterisco() => {
        	if (operations.refundPartialAfterCloseAnnulment) {
        	  CancelPartialRefundAfterClosureOperation()
        	} else {
            MeanPaymentErrorOperation(AutorizadaAsterisco().id)
          }
        }
        case Autorizada()          => {
          if (operations.refundPartialBeforeCloseAnnulment) {
        	  CancelPartialRefundBeforeClosureOperation()            
          } else {
            MeanPaymentErrorOperation(AutorizadaAsterisco().id)
          }         
        }
        case other                 => NonExistanceOperation(other.id)
      }
    } else {
      MeanPaymentErrorOperation(meanPaymentId.toInt)
    }
  }
  
  private def getMeanPaymentOperations(meanPaymentId: Long, isMpos: Boolean) = {
    val oMedioDePago = medioDePagoRepository.retrieve(meanPaymentId)
    val medioDePago = oMedioDePago.getOrElse{
      logger.error("invalid payment_method_id, medioDePago not existed")
      throw new Exception("RefundStateService medioDePago not existed")
    }
    if(isMpos)
      CardBrandOperations.isMpos(medioDePago.operations)
    else
      medioDePago.operations
  }
  
  private def hasValidCardBrandBin(meansPayment: Long, bin: String): Boolean = {
    meansPayment match {
      case 42 => {
        logger.debug(s"nativa-visa or nativa-master - bin: ${bin}")
        bin.equals("487017")
      }
      case _ => true
    }
  }
  
}