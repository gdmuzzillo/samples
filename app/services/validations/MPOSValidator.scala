package services.validations

import javax.inject.{Inject, Singleton}

import com.decidir.coretx.api.{ErrorFactory, ErrorMessage, OperationResource}
import com.decidir.coretx.domain.{Site, SiteRepository}
import controllers.MDCHelperTrait
import play.Logger

import scala.concurrent.ExecutionContext

@Singleton
class MPOSValidator @Inject() (context: ExecutionContext,
                               siteRepository: SiteRepository) extends MDCHelperTrait {

  implicit val ec = context

  //TODO: hacer bien la validacion. None si pasa la validacion??
  def validate(operation: OperationResource, originSiteId: String) = {
    val siteId = operation.datos_site.flatMap(_.site_id).getOrElse(originSiteId)
    val site = retrieveSite(siteId)
    val mpos = site.mensajeriaMPOS.getOrElse("N")
    val card_track = operation.datos_banda_tarjeta

    (card_track, mpos) match {
      case (Some(_), "N") => throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SITE_MPOS_DISABLED)
      case (None, "S") => throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SITE_MPOS_ENABLED)
      case _ => None
    }
  }

  private def retrieveSite(siteId: String): Site =
    siteId match {
      case "" => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE_SITE_ID)
      case id =>
        siteRepository.retrieve(id).getOrElse(throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SITE_SITE_ID))
  }
}
