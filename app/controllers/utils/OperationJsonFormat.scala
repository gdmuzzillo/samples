package controllers.utils

import com.decidir.coretx.domain._
import com.decidir.protocol.api._
import play.api.libs.json._
import play.api.libs.ws.WSResponse
import play.api.libs.functional.syntax._

import scala.util.{Failure, Success, Try}
import ai.x.play.json.Jsonx
import com.decidir.coretx.api.{TipoDocumento => _, _}
import play.api.libs.json.Reads._

object OperationJsonFormat {

  // Internals
  implicit val pairReads = Json.reads[Pair]
  implicit val pairWrites = Json.writes[Pair]  
  
  implicit val tipoActividadReads = Json.reads[TipoActividad]
  implicit val tipoActividadWrites = Json.writes[TipoActividad]    

  implicit val cardBrandOperationsReads = Json.reads[CardBrandOperations]
  implicit val cardBrandOperationsWrites = Json.writes[CardBrandOperations]

  implicit val ppbReads = Json.reads[PostbackConf]
  implicit val ppbWrites = Json.writes[PostbackConf]
  
  implicit val medioPagoReads = Json.reads[MedioDePago]
  implicit val medioPagoWrites = Json.writes[MedioDePago]

  implicit val cuentaReads = Json.reads[Cuenta]
  implicit val cuentaWrites = Json.writes[Cuenta]    
	implicit val infoSiteReads = Json.reads[InfoSite]
	implicit val infoSiteWrite = Json.writes[InfoSite]
  implicit val rangosReads = Json.reads[RangosPermitidosTarjeta]
  implicit val rangosWrites = Json.writes[RangosPermitidosTarjeta]
  
  implicit val cscReads = Json.reads[CyberSourceConfiguration]
  implicit val cscWrites = Json.writes[CyberSourceConfiguration]

  implicit val mailReads = Json.reads[MailConfiguration]
  implicit val mailWrites = Json.writes[MailConfiguration]

  implicit val hashReads = Json.reads[HashConfiguration]
  implicit val hashWrites = Json.writes[HashConfiguration]

  implicit val encryptionReads = Json.reads[Encryption]
  implicit val encryptionWrites = Json.writes[Encryption]

  implicit val encryptReads = Json.reads[Encrypt]
  implicit val encryptWrites = Json.writes[Encrypt]
  
  implicit val siteJsonFormat = Jsonx.formatCaseClass[Site]
  
  implicit val monedaReads = Json.reads[Moneda]
  implicit val monedaWrites = Json.writes[Moneda]

  implicit val marcaTarjetaReads = Json.reads[MarcaTarjeta]
  implicit val marcaTarjetaWrites = Json.writes[MarcaTarjeta]

  implicit val compradorReads = Json.reads[Comprador]
  implicit val compradorWrites = Json.writes[Comprador]

  implicit val idsReads = Json.reads[Ids]
  implicit val idsWrites = Json.writes[Ids]

  implicit val fechasReads = Json.reads[Fechas]
  implicit val fechasWrites = Json.writes[Fechas]

  implicit val termTicketsWrites = Json.writes[TerminalYTickets]
  implicit val termTicketsReads = Json.reads[TerminalYTickets]

  implicit val aggregatorWrites = Json.writes[Aggregator]
  implicit val aggregatorReads = Json.reads[Aggregator]

  implicit val offlineReads = Json.reads[OfflinePayment]
  implicit val offlineWrites = Json.writes[OfflinePayment]

  implicit val bsaReads = Json.reads[Bsa]
  implicit val bsaWrites = Json.writes[Bsa]

  implicit val datosBandaTarjetaReads = Json.reads[DatosBandaTarjeta]
  implicit val datosBandaTarjetaWrites = Json.writes[DatosBandaTarjeta]

  implicit val installmentDataReads = Json.reads[InstallmentData]
  implicit val installmentDataWrites = Json.writes[InstallmentData]

  implicit  val agroDataReads = Json.reads[AgroData]
  implicit val agroDataWrites = Json.writes[AgroData]

  implicit val installmentSpvReads = Json.reads[InstallmentSPV]
  implicit val installmentSpvWrites = Json.writes[InstallmentSPV]

  implicit val spvReads = Json.reads[DatosSPV]
  implicit val spvWrites = Json.writes[DatosSPV]

  implicit val protocolExtensionReads = Json.reads[ProtocolResourceExtension]
  implicit val protocolExtensionWrites = Json.writes[ProtocolResourceExtension]

  //implicit val protocolResourceReads = Json.reads[ProtocolResource]
  //implicit val protocolResourceWrites = Json.writes[ProtocolResource]

  implicit val transactionResponseReads = Json.reads[TransactionResponse]
  implicit val transactionResponseWrites = Json.writes[TransactionResponse]

  implicit val historicalResponseWrites = Json.writes[HistoricalStatus]
  implicit val historicalResponseReads = Json.reads[HistoricalStatus]

  implicit val operationResponseReads = Json.reads[OperationResponse]
  implicit val operationResponseWrites = Json.writes[OperationResponse]

  implicit val infoFiltrosReads = Json.reads[InfoFiltros]
  implicit val infoFiltrosWrites = Json.writes[InfoFiltros]

  implicit val terminalesReads = Json.reads[TerminalesSite]
  implicit val terminalesWrites = Json.writes[TerminalesSite]

  implicit val nrosTraceReads = Json.reads[NrosTraceSite]
  implicit val nrosTraceWrites = Json.writes[NrosTraceSite]

  implicit val motivoReads = Json.reads[Motivo]
  implicit val motivoWrites = Json.writes[Motivo]
  
  implicit val limitPairReads = Json.reads[LimitPair]
  implicit val limitPairWrites = Json.writes[LimitPair]

  implicit val limitBinMedioPagoReads = Json.reads[LimitBinMedioPago]
  implicit val limitBinMedioPagoWrites = Json.writes[LimitBinMedioPago]
  
  implicit val transactionStatusReads = Json.reads[TransactionStatus]
  implicit val transactionStatusWrites = Json.writes[TransactionStatus]

  implicit val transactionsStatusReads = Json.reads[TransactionsStatus]
  implicit val transactionsStatusWrites = Json.writes[TransactionsStatus]
  
  implicit val binsReads = Json.reads[Bins]
  implicit val binsWrites = Json.writes[Bins]

  implicit val tipoDocumentoReads = Json.reads[TipoDocumento]
  implicit val tipoDocumentoWrites = Json.writes[TipoDocumento]
  
  def handleJsonResponse[T](response: WSResponse, validateJson: JsValue => JsResult[T]): Try[T] = {
    try {
      validateJson(response.json) match {
        case e: JsError => throw new JsonValidationException(e)
        case JsSuccess(t, _) => Success(t) 
      }
    }
    catch {
      case e: Exception => {
        Failure(e)
      }
    }
  }  
  
}

class JsonValidationException(jserr: JsError) extends RuntimeException("Json errors: " + JsError.toJson(jserr).toString())