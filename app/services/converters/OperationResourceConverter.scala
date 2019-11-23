package services.converters


import scala.concurrent.ExecutionContext
import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.domain.MedioDePago
import com.decidir.coretx.api.DatosMedioPagoResource
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.ErrorMessage
import javax.inject.Inject
import javax.inject.Singleton
import com.decidir.coretx.domain.MedioDePagoRepository
import com.decidir.coretx.domain.MarcaTarjetaRepository
import com.decidir.coretx.domain.MonedaRepository
import com.decidir.coretx.domain.SiteRepository
import play.Logger
import decidir.sps.core.Protocolos
import com.decidir.coretx.api.CardTokenStore
import com.decidir.coretx.api.Identification
import com.decidir.coretx.api.CardholderData
import java.util.UUID
import scala.util.Try

@Singleton
class OperationResourceConverter @Inject() (context: ExecutionContext,
    medioDePagoRepository: MedioDePagoRepository,
    marcaTarjetaRepository: MarcaTarjetaRepository,
    monedaRepository: MonedaRepository,
    siteRepository: SiteRepository) {
  
  implicit val ec = context
  
  def logger = Logger.underlying()
  
  def operationResource2OperationData(operation: OperationResource): OperationData = {

    val referer = operation.datos_site.flatMap(_.referer)
    val datosSite = operation.datos_site.getOrElse(throw ErrorFactory.validationException("param_required", "datos_site"))
    val site = retrieveSite(operation, datosSite.site_id, referer.getOrElse("Referer not sent"))

    val (datosMedioPago, medioPago: MedioDePago) = { // Por algun motivo, si no especifico el tipo esta resolviendo a Any
      val oMedioPago = operation.datos_medio_pago match {
        // Si el medio de pago esta seteado pisa la moneda y la tarjeta con la que esta en la base
        case Some(DatosMedioPagoResource(Some(idMedioPago), _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)) => {
          medioDePagoRepository.retrieve(idMedioPago)
        }
        case None | _ => {
          logger.error("operationResource2OperationData: datos_medio_pago required")
          throw ErrorFactory.validationException("param_required", "datos_medio_pago")
        }
      }

      (operation.datos_medio_pago.getOrElse{
        logger.error("operationResource2OperationData: DatosMedioPagoResource of operation required")
        throw ErrorFactory.validationException("param_required", "DatosMedioPagoResource")
      }, oMedioPago.getOrElse{
        logger.error("operationResource2OperationData: MedioDePago required")
        throw ErrorFactory.validationException("param_required", "MedioDePago")  
      })
    }

    val currentMedioPago = datosMedioPago
    val nuevosDatosMedioPago = currentMedioPago.copy(marca_tarjeta = medioPago.idMarcaTarjeta, id_moneda = medioPago.idMoneda.map(_.toInt))
    val nuevoOperation = operation.copy(datos_medio_pago = Some(nuevosDatosMedioPago)) //TODO: get

    val marcaTarjeta = medioPago.idMarcaTarjeta.
      flatMap(marcaTarjetaRepository.retrieve(_)).
      getOrElse(throw new Exception("No se encontro la marca de tarjeta"))

    val moneda = nuevoOperation.datos_medio_pago.flatMap(_.id_moneda).flatMap(monedaRepository.retrieve(_)).get
        
    OperationData(nuevoOperation, site, medioPago, marcaTarjeta, moneda)
  }
  
  
  private def retrieveSite(operation: OperationResource, siteId: Option[String], referer: String) =
    siteId match {
      case None => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE_SITE_ID)
      case Some(id) =>
        siteRepository.retrieve(id).getOrElse(throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.NUMBER_BUSINESS))
    }
  
}