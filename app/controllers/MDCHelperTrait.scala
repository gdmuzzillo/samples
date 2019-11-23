package controllers

import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.MDCHelperTraitCommon
import services.payments.LegacyDBMessage

trait MDCHelperTrait extends MDCHelperTraitCommon{

  def updateMDCFromOperation(operation: OperationResource): Unit = {
    updateMDC(siteId = Some(operation.siteId),
        transactionId = Some(operation.id),
        merchantTransactionId = operation.nro_operacion,    
        referer = operation.datos_site.flatMap(_.referer), 
        paymentId = operation.charge_id.map(_.toString)
//        paymentStatus = 
            ) 
  }
  
  def loadMDCFromOperation(operation: OperationResource): Unit = {
    loadMDC(siteId = Some(operation.siteId),
        transactionId = Some(operation.id),
        merchantTransactionId = operation.nro_operacion,    
        referer = operation.datos_site.flatMap(_.referer), 
        paymentId = operation.charge_id.map(_.toString)
//        paymentStatus = 
            ) 
  }
  
  def loadMDCFromLegacyDBMessage(msg: LegacyDBMessage): Unit = {
    loadMDC(siteId = msg.siteId,
        transactionId = msg.transactionId,
        merchantTransactionId = msg.transactionId,    
        paymentId = Option(msg.chargeId).map(_.toString)
    ) 
  }
  

}

