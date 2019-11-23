package services

import com.decidir.coretx.domain._
import com.decidir.protocol.api.TransactionResponse

import scala.util.Failure
import scala.util.Success
import com.decidir.coretx.api._

import scala.concurrent.Future
import scala.util.Try
import controllers.MDCHelperTrait
import java.text.{DecimalFormat, SimpleDateFormat}
import javax.inject.Singleton
import javax.inject.Inject
import services.payments.DistributedTxElement
import decidir.sps.core.{AfipPPB, Protocolos}
import java.math.BigDecimal

class TransactionProcessor @Inject() (motivoRepository: MotivoRepository,
    medioDePagoRepository: MedioDePagoRepository,
    paymentMethodService: PaymentMethodService) extends MDCHelperTrait{

  def transactionResponse2OperationExecutionResponse(transactionResponse: Option[TransactionResponse], ocsresponse: Option[CyberSourceResponse], operation: OperationData, subPayments:List[DistributedTxElement], is2Steps: Option[Boolean]): OperationExecutionResponse = {
    
    def getOriginalAmount(siteId: String, op: OperationResource):Option[Long] = {
      val subTransactions = op.sub_transactions.find(s => s.site_id == siteId)
      subTransactions.map { st =>  st.original_amount.getOrElse(st.amount)}
    }
    
    def getListOfSubTransactions(distributedElements: List[DistributedTxElement], op: OperationResource) = {
      distributedElements.map { tx => SubTransaction(
        site_id = tx.site.id,
        amount = tx.monto,
        original_amount = getOriginalAmount(tx.site.id, op),
        installments = Some(tx.cuotas),
        nro_trace = tx.transactionResponse.flatMap(_.nro_trace),
        subpayment_id = tx.operation.charge_id,
        status = tx.transactionResponse.map(tr=> Some(getState(protoStatusCode = Some(tr.statusCode), is2Steps = is2Steps.getOrElse(false), cardErrorCode = tr.cardErrorCode).id)).getOrElse(None),
        nro_ticket = tx.transactionResponse.flatMap(_.nro_ticket),
        reason_id = tx.transactionResponse.map(_.idMotivo.toString),
        additional_reason = tx.transactionResponse.flatMap(_.motivoAdicional),
        authorization_code = tx.transactionResponse.flatMap(_.cod_aut),
        reason = tx.transactionResponse.flatMap(tr => getMotivo(tr,operation.resource).map(_.descripcion)),
        terminal = tx.transactionResponse.flatMap(_.terminal),
        lot = None,
        id_operacion_medio_pago = tx.transactionResponse.map(_.idOperacionMedioPago))
      }
    }
    
    def getListOfPayments(distributedElements: List[DistributedTxElement]) = {
      distributedElements.map { tx => (Subpayment(
          tx.site.id,
          Some(tx.cuotas),
          Some(tx.monto),
          tx.transactionResponse.flatMap(_.nro_trace), 
          tx.transactionResponse.flatMap(_.nro_ticket),  
          tx.transactionResponse.flatMap(_.cod_aut),
          tx.operation.charge_id,
          tx.transactionResponse.map(_.idOperacionMedioPago),
          tx.transactionResponse.map(tr=> Some(TransactionState.apply(getState(protoStatusCode = Some(tr.statusCode), is2Steps = is2Steps.getOrElse(false)).id))).getOrElse(None),
          tx.transactionResponse.flatMap(_.terminal),
          None)) // TODO No se puede obtener, quizas sea necesario construirlos a partir de los subtx que si tiene el Lote.
      }
    }
    
    val tr = transactionResponse.getOrElse(TransactionResponse(0,-1,operation.resource.datos_medio_pago.flatMap(_.nro_terminal),operation.resource.datos_medio_pago.flatMap(_.nro_trace),operation.resource.datos_medio_pago.flatMap(_.nro_ticket),None,None,None,"","", None))
      val protoStatusCode = transactionResponse.map(tr => Some(tr.statusCode)).getOrElse(None)
      val motivo = getMotivo(tr,operation.resource)
      tr.cardErrorCode.map(_.descripcionMotivo = motivo.map(_.descripcion))
      tr.cardErrorCode.map(_.motivoAdicional = tr.motivoAdicional)
      OperationExecutionResponse(
        status = getState(protoStatusCode = protoStatusCode, is2Steps = is2Steps.getOrElse(false), isOffline = operation.resource.datos_medio_pago.flatMap(_.nro_tarjeta).isEmpty).id,
        authorizationCode = tr.cod_aut.getOrElse(""),
        cardErrorCode = tr.cardErrorCode,
        authorized = tr.authorized,
        validacion_domicilio = tr.validacion_domicilio,
        operationResource = Some(operation.resource.copy(
          cuotas = operation.resource.cuotas,
          datos_medio_pago = Some(operation.resource.datos_medio_pago.get.copy(
            card_brand = Some(getCardBrand(operation.resource)),
            nro_trace = tr.nro_trace,
            nro_ticket = tr.nro_ticket,
            motivo = motivo.map(_.descripcion),
            nro_terminal = tr.terminal,
            motivo_adicional = tr.motivoAdicional,
            id_operacion_medio_pago = Some(tr.idOperacionMedioPago))),
          sub_transactions = getListOfSubTransactions(subPayments, operation.resource),
          fraud_detection = operation.resource.fraud_detection.map(fd => fd.copy(status = ocsresponse)),
          datos_offline = operation.resource.datos_offline.map(_.copy(barcode = tr.barcode)))),
        postbackHash = Some(createPostBack(tr, ocsresponse, operation)),
        subPayments = Some(getListOfPayments(subPayments)))
  }

  private def getMotivo(tr: TransactionResponse,or: OperationResource) : Option[Motivo] = {
    if(tr.idMotivo != -1){
      motivoRepository.retrieve(paymentMethodService.getProtocolId(or), 0, tr.idMotivo)
    } else {
      None
    }
  }
  
  private def getState(protoStatusCode: Option[Int], is2Steps: Boolean, cardErrorCode: Option[CardErrorCode] = None, isOffline: Boolean = false): TransactionState = protoStatusCode match {
    case None => AProcesar()
    case Some(status) => status match {
      case 0 => AProcesar()
      case 200 => if(is2Steps)
        PreAutorizada()
      else {
        if(isOffline)
          FacturaGenerada()
        else
          Autorizada()
      }
      case 400|402 => cardErrorCode.map(
          cErrorCode => cErrorCode.error_code match {
           case "group_rejected" => RebotadaPorGrupo() //TODO: refactor de match
           case _ => Rechazada()
      }).getOrElse(Rechazada())
      case other => {
        val translated = ARevisar()
        logger.error(s"Recibido status code no manejado: $other. Devolviendo rejected ($translated)")
        translated
      }
    }
  } 

  def getResultado(resultado: Boolean): String = if(resultado) "APROBADA" else "RECHAZADA"

  private def createPostBack(transactionResponse: TransactionResponse, ocsresponse: Option[CyberSourceResponse], operation: OperationData): Map[String, String] = {

    val validacionDomicilio = transactionResponse.validacion_domicilio.map(_ match {
      case "" => "NO_VALIDATION"
      case "100" => "NO_VALIDATION"
      case validation: String => validation
      case other => "NO_VALIDATION"
    }).getOrElse("NO_VALIDATION")
    
    def formatAmount(amount: BigDecimal) = {
      new DecimalFormat("#.00").format(amount).replaceAll("\\.", ",")
    }
    val isVisa = operation.resource.datos_medio_pago.flatMap(_.marca_tarjeta).getOrElse(0) == 4
    //FIXME Se utiliza java.math.BigDecimal ya que al utilizar scala.math.BigDecimal se pierde la precision al igual que usar un Double
    val amount =  new BigDecimal(operation.resource.monto.getOrElse(0l)).divide(new BigDecimal(100))

    operation.resource.ticket_request match {
      case Some(_) => {
        val map = AfipPPB.toMap(operation.resource, transactionResponse, formatAmount(amount))
        logger.info(s"Creating PostBack for Afip: map: $map")
        map
      }
      case None =>
        logger.info("Creating common postback")
        //Los campos en "" estan en desuso, pero se debe crear de todas maneras para realizar un postback.
        Map("codautorizacion" -> (if (transactionResponse.authorized) transactionResponse.cod_aut.getOrElse("") else ""),
          "nombreentrega" -> "",
          "tarjeta" -> getCardBrand(operation.resource),
          "idmotivo" -> transactionResponse.idMotivo.toString,
          "emailcomprador" -> operation.resource.datos_titular.flatMap(_.email_cliente).getOrElse(""),
          "resultado" -> getResultado(transactionResponse.authorized),
          "telefonocomprador" -> "",
          "validanropuerta" -> (if (isVisa) codificarValidacion(validacionDomicilio.substring(5, 6)) else ""),
          "pedido" -> "",
          "nroticket" -> transactionResponse.nro_ticket.getOrElse(""),
          "direccionentrega" -> "",
          "fechaentrega" -> "",
          "nrodoc" -> operation.resource.datos_titular.flatMap(_.nro_doc).getOrElse(""),
          "cuotas" -> operation.resource.cuotas.map(_.toString).getOrElse(""),
          "fechahora" -> operation.resource.creation_date.map(new SimpleDateFormat("dd/MM/yyyy hh:mm:ss").format(_)).getOrElse(""),
          "titular" -> operation.resource.datos_medio_pago.flatMap(_.nombre_en_tarjeta).getOrElse(""),
          "validafechanac" -> (if (isVisa && transactionResponse.authorized) codificarValidacion(validacionDomicilio.substring(6, 7)) else ""),
          "fechacontracargo" -> "",
          "validanrodoc" -> (if (isVisa && transactionResponse.authorized) codificarValidacion(validacionDomicilio.substring(4, 5)) else ""),
          "noperacion" -> operation.resource.nro_operacion.getOrElse(""),
          "paisentrega" -> "",
          "tipodoc" -> operation.resource.datos_titular.flatMap(_.tipo_doc).getOrElse(0).toString,
          "zipentrega" -> "",
          "barrioentrega" -> "",
          "estadoentrega" -> "",
          "validaciondomicilio" -> (if (isVisa && transactionResponse.authorized) transactionResponse.validacion_domicilio.getOrElse("") else ""),
          "motivo" -> getMotivo(transactionResponse, operation.resource).map(_.descripcion).getOrElse(""),
          "validatipodoc" -> (if (isVisa && transactionResponse.authorized) codificarValidacion(validacionDomicilio.substring(3, 4)) else ""),
          "moneda" -> idMonedaToNombre(operation.resource.datos_medio_pago.flatMap(_.id_moneda).getOrElse(0)),
          "ciudadentrega" -> "",
          "mensajeentrega" -> "",
          "resultadoautenticacionvbv" -> (if (isVisa) "0" else ""), // TODO: Cambiar cuando este vbv
          "paramsitio" -> operation.resource.datos_site.flatMap(_.param_sitio).getOrElse(""),
          "tipodocdescri" -> operation.resource.datos_titular.flatMap(datosTitular => datosTitular.tipo_doc.flatMap(tipoDoc => datosTitular.tipoDocumento)).getOrElse(""),
          "nrotarjetavisible" -> operation.resource.datos_medio_pago.flatMap(dmp =>
            dmp.nro_tarjeta.map(nro => {
               operation.site.getNroTarjetaVisible(nro,dmp.medio_de_pago.fold("0")(_.toString),
                paymentMethodService.getProtocolId(operation.resource),
                paymentMethodService.getBackenId(operation.resource));
            })).getOrElse(""),
          "codigopedido" -> "",
          "motivoadicional" -> "",
          "motivocontracargo" -> "",
          "sitecontracargo" -> "",
          "monto" -> formatAmount(amount),
          "paymentid" -> operation.resource.charge_id.map(_.toString).getOrElse(""))
    }
  }
  
  private def codificarValidacion(codVisa: String): String =  {
    codVisa match {
      case "0" => "SI"
      case "1" => "NO"
      case "2" => "NV"
      case other => ""
    }
  }

  private def getCardBrand(or: OperationResource) = {
    if(or.datos_medio_pago.flatMap(_.medio_de_pago).get != 41) {
      val medioPago = or.datos_medio_pago.flatMap(_.medio_de_pago.flatMap(mpId => medioDePagoRepository.retrieve(mpId)))
      medioPago.map(mp => mp.cardBrand).getOrElse("")
    } else
      "PagoMisCuentas"
  }

  private def idMonedaToNombre(idMoneda: Int) = idMoneda match {
    case 1 => "Pesos"
    case other => ""
  }
  
}