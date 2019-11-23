package services.validations.operation

import javax.inject.{Inject, Singleton}

import com.decidir.coretx.api.{DatosSiteResource, ErrorFactory, ErrorMessage, OperationResource}
import com.decidir.coretx.domain.{Site, SiteRepository}
import services.PaymentMethodService

@Singleton
class SiteValidation @Inject() (siteRepository: SiteRepository,
                                paymentMethodService: PaymentMethodService) extends Validation {

  override def validate(implicit op: OperationResource, site: Site) = {

    val siteData = op.datos_site.getOrElse{
      logger.error(s"datos_site required. nro op: ${op.nro_operacion}")
      throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE)
    }

    val siteId = op.datos_site.flatMap { _.site_id }.getOrElse {
      logger.error("siteId required")
      throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE_SITE_ID)
    }

    val originSiteId = op.datos_site.flatMap { _.origin_site_id }.getOrElse {
      logger.error("originSiteId required")
      throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE_ORIGIN_SITE_ID)
    }

    validateDisabledSite
    validateURL

    siteData match {
      case DatosSiteResource(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, origin_site) => {
        origin_site match {
          case None =>  throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE_ORIGIN_SITE_ID)
          case Some(origin_site_id) => {
            if ( origin_site_id != site.id && !(site.parentSiteId contains origin_site_id) ) {
              logger.error(s"""No existe merchant ${site.id} para site ${siteData.origin_site_id.orNull}""")
              throw ErrorFactory.validationException(ErrorMessage.INVALID_SITE, ErrorMessage.DATA_SITE_ORIGIN_SITE_ID)
            }
          }
        }
      }
    }

    validateDistributed
  }

  private def validateDisabledSite(implicit site: Site) = {
    if (!site.habilitado) {
      logger.error(s"Invalid Site. site ${site.id} disabled")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SITE_DISABLED)
    }
  }

  private def validateURL(implicit op: OperationResource, site: Site) = {
    op.datos_site match {
      case Some(DatosSiteResource(_, Some(urlDinamica), _, _, _, _, _, _, _, _, _, _, _, _, _, _)) => {
        if(site.ppb.usaUrlDinamica &&
          (urlDinamica == "" || urlDinamica == null))
          throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "URL Dinamica")
      }
    }
  }

  def validateDistributed(implicit op: OperationResource, site: Site) = {
    op.datos_site match {
      case Some(DatosSiteResource(_,_,_,_,_,_,Some("S"),_,_,_,_,_,_,_,_,_)) => {
        if (site.transaccionesDistribuidas != "S") {
          logger.error("site of type transaction distributed")
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PAYMENT_TYPE, ErrorMessage.DISTRIBUTED_TRANSACTIONS)
        }

        // por monto
        if (site.montoPorcent == "M") {
          val subSites = siteRepository.findSubSitesBySite(site).map(_.idSite).toSet

          // Busco si algun subsite ingresado por el usuario no esta entre los subsite del site y no es el site padre
          val siteNotFound = op.sub_transactions.find(stx => !subSites.contains(stx.site_id) & stx.site_id != site.id)

          siteNotFound.foreach { siteId => throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.SUB_PAYMENTS_SITE_ID) }

          op.sub_transactions.foreach { subTx =>
            // Valida que cada subsite existe como site y este habilitado
            val subSite = siteRepository.retrieve(subTx.site_id).getOrElse {
              logger.error("site Id not retrived")
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SUB_SITE_TX_ID)
            }
            validateDisabledSite(subSite)
            // Valida que el subsite tenga el medio de pago seleccionado
            // TODO ver toString
            if (op.datos_medio_pago.get.medio_de_pago.isDefined) {
              validateSitePaymentMeans(subSite, op.datos_medio_pago.get.medio_de_pago.get.toString, paymentMethodService.getProtocolId(op), paymentMethodService.getBackenId(op), ErrorMessage.DATA_SUB_SITE_OPERATION_METHOD_ID)
            }
            if (subTx.amount <= 0) {
              logger.error("invalid amount")
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.AMOUNT)
            }
          }

          if (op.monto.get != op.sub_transactions.foldLeft(0L)((r, c) => r + c.amount)) {
            logger.error("different amounts")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DIFFERENT_AMOUNTS)
          }
          // por porcentaje
        } else {
          val subSites = siteRepository.findSubSitesBySite(site).map(_.idSite).toSet
          subSites.foreach { subSiteId =>
            // Valida que cada subsite existe como site y este habilitado
            val subSite = siteRepository.retrieve(subSiteId).getOrElse {
              logger.error("subSite id not retrived, not exist or this disabled")
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SUB_SITE_ID)
            }
            validateDisabledSite(subSite)
            // Valida que el subsite tenga el medio de pago seleecionado
            if (op.datos_medio_pago.get.medio_de_pago.isDefined) {
              validateSitePaymentMeans(subSite, op.datos_medio_pago.get.medio_de_pago.get.toString, paymentMethodService.getProtocolId(op), paymentMethodService.getBackenId(op), ErrorMessage.DATA_SUB_SITE_OPERATION_METHOD_ID)
            }
          }
        }
      }
    }
  }

  private def validateSitePaymentMeans(site: Site, medioPagoId: String, protocoloId: Int, backendId: Int, errorMessage: String) = {
    site.cuenta(medioPagoId, protocoloId, backendId).map(cuenta =>
      if (!cuenta.habilitado) {
        logger.error(s"El site ${site.id} tiene deshabilitado el medio de pago $medioPagoId")
        throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, errorMessage)
      }
    ).getOrElse{
      logger.warn(s"El site ${site.id} no tiene configurado el medio de pago: ${medioPagoId}, protocolo: ${protocoloId}, backend ${backendId}")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, errorMessage)
    }
  }
}
