package controllers

import javax.inject.Inject
import com.decidir.coretx.api.OperationJsonFormats._
import com.decidir.coretx.domain._
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}


class ReferenceDataController @Inject()(monedaRepository: MonedaRepository,
                                        marcaTarjetaRepository: MarcaTarjetaRepository,
                                        tipoDocumentoRepository: TipoDocumentoRepository,
                                        bancoRepository: BancoRepository,
                                        transactionRepository: TransactionRepository)
  extends Controller with MDCHelperTrait {

  implicit val codDescWriter = Json.writes[CodeDescription]
  implicit val paymentMethodWriter = Json.writes[PaymentMethod]


  def tiposDocumento = Action {

    val tipos = tipoDocumentoRepository.retrieveAll map (m => CodeDescription(m.id.toString, m.description))

    Ok(Json.toJson(tipos))

  }

  def monedas = Action {

    val monedas = monedaRepository.retrieveAll map (m => CodeDescription(m.id, m.descripcion))

    Ok(Json.toJson(monedas))

  }

  def marcasTarjeta = Action {

    val marcas = marcaTarjetaRepository.retrieveAll map (m => CodeDescription(m.id.toString, m.descripcion))

    Ok(Json.toJson(marcas))

  }

  def bancos = Action {

    val bancos = bancoRepository.retrieveAll

    Ok(Json.toJson(Bancos(list = bancos)))
  }

  def paymentMethods(siteId: String) = Action {
    val paymentMethods = transactionRepository.retrievePaymentMethods(siteId)

    Ok(Json.toJson(paymentMethods))
  }

}

case class CodeDescription(id: String, description: String)

case class PaymentMethod(payment_method_id: Int, description: String)