package services

import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import com.decidir.coretx.domain.MedioDePagoRepository
import javax.inject.Inject
import controllers.MDCHelperTrait
import com.decidir.coretx.api.OperationResource
import decidir.sps.core.Protocolos
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.ErrorMessage

@Singleton
class PaymentMethodService @Inject() (context: ExecutionContext, 
    medioDePagoRepository: MedioDePagoRepository) extends MDCHelperTrait {

  implicit val ec = context
  
 /**
 * protocolId: puede ser distinto para MedioDePago.id = 42
 */ 
  def getProtocolId(operation :OperationResource):Int = {
    updateMDCFromOperation(operation)
    operation.datos_medio_pago.map{_.medio_de_pago.map{ _ match {
      case 42 => { operation.datos_medio_pago.flatMap(_.nro_tarjeta).map(cardNumber => {
          if(cardNumber.take(1) == "5")
            Protocolos.codigoProtocoloMastercard
          else
            Protocolos.codigoProtocoloVisa}).getOrElse{
              logger.error("Undefined ProtocoloId: OperationResource.datos_medio_pago.medio_de_pago not found")
              throw new Exception(s"undefined ProtocoloId: OperationResource.datos_medio_pago.nro_tarjeta not found, in operation: ${operation}")}
      }
      case medioDePagoId => medioDePagoRepository.retrieve(medioDePagoId).map(_.protocol)
      .getOrElse{
        logger.error("Undefined ProtocoloId: Means of payment not found")
        throw new Exception(s"undefined ProtocoloId: MedioDePagoId: ${medioDePagoId} in operation: ${operation}")}
    }}.getOrElse{
      logger.error("Undefined ProtocoloId: OperationResource.datos_medio_pago.medio_de_pago not found")
      throw new Exception(s"undefined ProtocoloId: OperationResource.datos_medio_pago.medio_de_pago not found, in operation: ${operation}")}
    }.getOrElse{
      logger.error("Undefined ProtocoloId: OperationResource.datos_medio_pago not found")
      throw new Exception(s"undefined ProtocoloId: OperationResource.datos_medio_pago not found, in operation: ${operation}")}
  }
  
 /**
 * backenId: puede ser distinto para MedioDePago.id = 42
 */
  def getBackenId(operation :OperationResource) = {
    operation.datos_medio_pago.map{_.medio_de_pago.map{ _ match {
      case 42 => { 
        getProtocolId(operation) match {
            case Protocolos.codigoProtocoloVisa => 6
            case Protocolos.codigoProtocoloMastercard => 7
            case other => {
              logger.error(s"Undefined backenId: OperationResource.datos_medio_pago.medio_de_pago not found, protocolId: ${other}")
              throw new Exception(s"undefined backenId: OperationResource.datos_medio_pago.medio_de_pago not found, protocolId: ${other}, in operation: ${operation}")
            }
          }
      }
      case medioDePagoId =>  medioDePagoRepository.retrieve(medioDePagoId).map(_.backend)
      .getOrElse{
        logger.error("Undefined backenId: Means of payment not found")
        throw new Exception(s"undefined backenId: MedioDePagoId: ${medioDePagoId} in operation: ${operation}")}
    }}.getOrElse{
      logger.error("Undefined backenId: OperationResource.datos_medio_pago.medio_de_pago not found")
      throw new Exception(s"undefined backenId: OperationResource.datos_medio_pago.medio_de_pago not found, in operation: ${operation}")}
    }.getOrElse{
      logger.error("Undefined backenId: OperationResource.datos_medio_pago not found")
      throw new Exception(s"undefined backenId: OperationResource.datos_medio_pago not found, in operation: ${operation}")}
  }
}