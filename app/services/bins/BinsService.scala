package services.bins

import scala.concurrent.ExecutionContext
import javax.inject.Inject
import javax.inject.Singleton
import com.decidir.coretx.domain.InfoFiltrosRepository
import com.decidir.coretx.domain.MedioDePago
import controllers.MDCHelperTrait
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.ErrorMessage
import com.decidir.coretx.domain.MedioDePagoRepository
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.api.DatosMedioPagoResource
import com.decidir.coretx.domain.OperationResourceRepository
import com.decidir.coretx.domain.SiteRepository
import com.decidir.coretx.domain._
import services.PaymentMethodService
import scala.util.Try
import scala.util.Success
import scala.util.Failure

@Singleton
class BinsService @Inject() (context: ExecutionContext,
    medioDePagoRepository: MedioDePagoRepository,
    operationRepository: OperationResourceRepository,
    siteRepository: SiteRepository,
    infoFiltrosRepository: InfoFiltrosRepository,
    paymentMethodService: PaymentMethodService) extends MDCHelperTrait {
  
    implicit val ec = context
  
    def validateBin(bin:String, oMedioDePago: Option[MedioDePago]) = {
      oMedioDePago match {

        case Some(mpId) => {
          if (!bin.matches(mpId.bin_regex)) {
            logger.error("invalid bin, bin not existed")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "bin")
          }

          mpId.id match {
            case "1" => { //Validar que el bin de VISA CREDITO(1) no se encuentre en la lista Blanca de VISA DEBITO(31)
              if (medioDePagoRepository.isBinWhite("31", bin)) {
                logger.error("invalid payment_method_id, bin is in Visa Debit White List")
                throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "bin")
              }

              if (mpId.hasBlackList && medioDePagoRepository.isBinBlack(mpId.id, bin)) {
                logger.error("invalid payment_method_id, is Black bin")
                throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "bin")
              }
            }
            case _ => {
              if (mpId.hasBlackList && medioDePagoRepository.isBinBlack(mpId.id, bin) /*&& // Validacion para el 2do paso
            (decrypted.datos_medio_pago.flatMap(_.medio_de_pago).isDefined ||
              operation.datos_medio_pago.flatMap(_.marca_tarjeta).isDefined)*/) {
                logger.error("invalid payment_method_id, is Black bin")
                throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "bin")
              }

              if (mpId.hasWhiteList && !medioDePagoRepository.isBinWhite(mpId.id, bin)){
                logger.error("invalid payment_method_id, is White bin")
                throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "bin")
              }
            }
          }
        }

        case None => {
          val bins = medioDePagoRepository.getAllBins()
          val oBin = bins.find(binStored => bin.matches(binStored))
          oBin.getOrElse {
            logger.error("invalid payment_method_id, card not existed")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "payment_method_id")
          }
        }
      }
    }
    
    
    def validateBin(op : OperationResource): (Option[String], Try[Unit]) = {
      val site = retrieveSite(op)
      val isMPOS = site.mensajeriaMPOS.getOrElse("N").equalsIgnoreCase("S")

      if(op.datos_medio_pago.get.medio_de_pago.get != 41 && !isMPOS) {
        op.datos_site.map(datosSite => {
          op.datos_medio_pago.map(_ match {
            case DatosMedioPagoResource(Some(medioDePagoId), _, _, _, Some(nroTarjeta), _, _, _, _, _, Some(binParaValidar), _, _, _, _, _,_, _, _, _, _, _, _) => {
              val medioPago = medioDePagoRepository.retrieve(medioDePagoId).getOrElse(throw new Exception(s"Medio de pago no encontrado medioPagoId: ${medioDePagoId}"))
              val protocolId = paymentMethodService.getProtocolId(op)
              val backendId = paymentMethodService.getBackenId(op)
              val carBrandId = medioPago.idMarcaTarjeta.getOrElse{
                logger.error("Undefined idMarcaTarjeta")
                throw new Exception(s"undefined idMarcaTarjeta: MedioDePagoId: ${medioDePagoId} in operation: ${op}")
              }
              val bin = operationRepository.decrypted(nroTarjeta).take(6)
              var site = retrieveSite(datosSite.site_id)

              datosSite.id_modalidad match {
                case Some("S") => {
                  logger.debug("Distrubuted payment, validate subpayments")
                  Try { op.sub_transactions.map(st => {
                      site = retrieveSite(Some(st.site_id))
                      validateBinXSiteAndCard(site, medioPago.id, protocolId, backendId, carBrandId, bin, nroTarjeta)
                    })} match {
                    case Success(_) => (None, Success((): Unit))
                    case Failure(fail) => (Some(site.id), Failure(fail))
                  }
                }
                case _ => {
                  Try {validateBinXSiteAndCard(site, medioPago.id, protocolId, backendId, carBrandId, bin, nroTarjeta)} match {
                    case Success(_) => (None, Success((): Unit))
                    case Failure(fail) => (Some(site.id), Failure(fail))
                  }
                }
              }
            }
          }).getOrElse{
                logger.error("Undefined datos_medio_pago")
                throw new Exception(s"undefined datos_medio_pago in operation: ${op}")}
       }).getOrElse{
                logger.error("Undefined datosSite")
                throw new Exception(s"undefined datosSite in operation: ${op}")}
      }else
        (None, Success((): Unit))
    }
  
    def validateBinXSiteAndCard(site: Site, medioPagoId: String, protocoloId: Int, backendId: Int, marcaTarjetaId: Int, bin: String, nroTarjeta: String) = {
      val isMPOS = site.mensajeriaMPOS.getOrElse("N").equalsIgnoreCase("S")

      if (!isMPOS) {
        if(!infoFiltrosRepository.pasaFiltroBin(site.id, marcaTarjetaId, bin, nroTarjeta))
        {
          logger.warn("Bin not accepted. Filter on sit: " + site.id + " card brand: " + marcaTarjetaId);
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "bin")
        }
        else if(site.validaRangoNroTarjeta) // esquema "viejo" de filtros, por rangos
        {
          if(!siteRepository.validarRangoNroTarjeta(site.id, marcaTarjetaId, nroTarjeta))
          {
            logger.warn("Bin not accepted. Out of range on sit: " + site.id + " card brand: " + marcaTarjetaId);
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "bin")
          }
        }
      }
    }
    
    private def retrieveSite(siteId: Option[String]): Site =
      siteId match {
        case None => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE_SITE_ID)
        case Some(id) =>
          siteRepository.retrieve(id).getOrElse(throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SITE_SITE_ID))
    }

    private def retrieveSite(operation: OperationResource): Site = {
      val siteId = operation.datos_site.flatMap(_.site_id).getOrElse(operationRepository.retrieve(operation.id).flatMap { _.datos_site }.flatMap { _.site_id }.getOrElse(throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE_SITE_ID)))
      retrieveSite(Some(siteId))
    }

}