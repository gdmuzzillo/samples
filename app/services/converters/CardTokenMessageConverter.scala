package services.converters

import javax.inject.Inject

import com.decidir.coretx.api._
import play.Logger

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class CardTokenMessageConverter @Inject() (context: ExecutionContext) {

  implicit val ec = context
  def logger = Logger.underlying()

  def operationResource2CardTokenMessage(or: OperationResource, token: String): CardTokenMessage = {
    val cardTokenStore = operationResource2CardTokenStore(or, token) match {
      case Success(cardTokenStore) => cardTokenStore
      case Failure(error) => {
        logger.warn("Error convirtiendo OperationResource to CardTokenStore", error)
        throw error
      }
    }

    CardTokenMessage(
      userId = or.user_id,
      chargeId = or.charge_id,
      siteId = or.siteId,
      transactionId = or.id,
      merchantTransactionId = or.nro_operacion,
      referer = or.datos_site.flatMap(_.referer),
      cardTokenStore = cardTokenStore
    )
  }

  def operationResource2CardTokenStore(or: OperationResource, token: String): Try[CardTokenStore] =  Try{
    val siteId = or.datos_site.flatMap(_.site_id).getOrElse(throw new Exception("site_id not available"))
    val parentSiteId = getParentSiteId(or)
    val datosMedioPago = or.datos_medio_pago.getOrElse(throw new Exception("not seted means of payment"))
    val datosTitularResource = or.datos_titular.getOrElse(throw new Exception("not seted data of titular"))
    val identification = Identification.fromId(datosTitularResource.tipo_doc.getOrElse(0), datosTitularResource.nro_doc)
    val cardHolder = CardholderData(identification.getOrElse(throw new Exception("identification not available")),
      datosMedioPago.nombre_en_tarjeta.getOrElse(throw new Exception("nombre_en_tarjeta not available")).take(60), //truncate
      datosTitularResource.fecha_nacimiento,
      datosTitularResource.nro_puerta)
    CardTokenStore(
      //        token = UUID.randomUUID().toString(),
      token = token,
      user_id = or.user_id.getOrElse(throw new Exception("user_id not available")),
      site_id = siteId,
      parent_site_id = parentSiteId,
      card_number = datosMedioPago.nro_tarjeta.getOrElse(throw new Exception("card_number_encrypted not available")),
      payment_method_id = datosMedioPago.medio_de_pago.getOrElse(throw new Exception("medio_de_pago not available")),
      bin = datosMedioPago.bin_for_validation.getOrElse(throw new Exception("bin_for_validation not available")),
      last_four_digits = datosMedioPago.last_four_digits.getOrElse(throw new Exception("last_four_digits not available")),
      expiration_month = datosMedioPago.expiration_month.getOrElse(throw new Exception("expiration_month not available")),
      expiration_year = datosMedioPago.expiration_year.getOrElse(throw new Exception("expiration_year not available")),
      card_holder = cardHolder
    )
  }

  /**
    * En caso del q siteId y siteMerchantId sean iguales en el parent mete None
    * Es muy feo esto. Mal modelado. Refactorizarlo!!
    */
  private def getParentSiteId(or: OperationResource): Option[String] = {
    val siteId = or.datos_site.flatMap(_.site_id).getOrElse(throw new Exception("site_id not available"))
    val pSiteId = or.datos_site.flatMap(_.origin_site_id)
    if (pSiteId.map(parentSite => parentSite.equals(siteId)).getOrElse(false)) {
      None
    } else {
      pSiteId
    }
  }

}
