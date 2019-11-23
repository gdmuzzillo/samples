package services

import java.util.Calendar
import javax.inject.{Inject, Singleton}

import com.decidir.coretx.api.{OperationExecutionResponse, _}
import com.decidir.coretx.domain._
import com.decidir.protocol.api.TransactionResponse
import controllers.MDCHelperTrait
import legacy.decidir.sps.offline.{ProtocoloCobroExpress, ProtocoloRapiPago, ProtocoloPagoFacil, ProtocoloCajaDePagos}
import services.payments.{InsertTx, LegacyTransactionServiceClient, UpdateTx}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class OfflineTransactionProcessor @Inject() (context: ExecutionContext,
                                             legacyTxService: LegacyTransactionServiceClient,
                                             operationRepository: OperationResourceRepository,
                                             transactionProcessor: TransactionProcessor) extends MDCHelperTrait {
  implicit val ec = context

  def opData2DatosOffline(opData: OperationData) : DatosOffline = {

    val formatter = new java.text.SimpleDateFormat("yyMMdd")
    val fechavto = formatter.parse(opData.resource.datos_offline.flatMap(_.fechavto).getOrElse(""))
    var fechaSegVto: String = ""

    if(opData.medioDePago.id != ProtocoloCajaDePagos.idmediopago.toString){
      val calendar = Calendar.getInstance()
      calendar.setTime(fechavto)
      calendar.add(Calendar.DAY_OF_YEAR, opData.resource.datos_offline.flatMap(_.cod_p3.map(_.toInt)).getOrElse(0))
      fechaSegVto = formatter.format(calendar.getTime)
    }else {
      fechaSegVto = opData.resource.datos_offline.flatMap(_.fechavto2).getOrElse("")
    }

    new DatosOffline(nroOperacion = opData.nroOperacionSite,
      nroTienda = opData.cuenta.nroId,
      medioPago = opData.datosMedioPago.medio_de_pago.toString,
      nroDoc = opData.datosTitular.nro_doc.getOrElse(""),
      monto = opData.resource.monto.map(_.toString).getOrElse(""),
      recargo = opData.resource.datos_offline.flatMap(_.recargo).getOrElse(""),
      fechaVto = opData.resource.datos_offline.flatMap(_.fechavto).getOrElse(""),
      fechaVto2 = fechaSegVto,
      codp1 = opData.resource.datos_offline.flatMap(_.cod_p1).getOrElse(""),
      codp2 = opData.resource.datos_offline.flatMap(_.cod_p2).getOrElse(""),
      codp3 = opData.resource.datos_offline.flatMap(_.cod_p3).getOrElse(""),
      codp4 = opData.resource.datos_offline.flatMap(_.cod_p4).getOrElse(""),
      cliente = opData.resource.datos_offline.flatMap(_.cliente).getOrElse(""))
  }

  def generateBarCode(opData: OperationData) = {

    val datosOffline = opData2DatosOffline(opData)

    opData.datosMedioPago.medio_de_pago match {
      case ProtocoloCajaDePagos.idmediopago => new ProtocoloCajaDePagos().obtenerCodigo(datosOffline)
      case ProtocoloPagoFacil.idmediopago => new ProtocoloPagoFacil().obtenerCodigo(datosOffline)
      case ProtocoloRapiPago.idmediopago => new ProtocoloRapiPago().obtenerCodigo(datosOffline)
      case ProtocoloCobroExpress.idmediopago => new ProtocoloCobroExpress().obtenerCodigo(datosOffline)
      case other => throw new Exception("Payment method not supported")
    }
  }

  def process(opdata: OperationData): OperationExecutionResponse = {
    val chargeId = opdata.chargeId
    val site = opdata.site
    val op = opdata.resource
    updateNroOperationAProcesar(opdata)
    var opr = transactionProcessor.transactionResponse2OperationExecutionResponse(None, None, opdata, Nil, None)
    legacyTxService.insert(InsertTx(chargeId, site, None, Ingresada(), opr))
    try {
      val code = generateBarCode(opdata)
      val tr = TransactionResponse(statusCode = 200,
        idMotivo = -1,
        terminal = None,
        nro_trace = None,
        nro_ticket = None,
        None,
        None,
        None,
        site_id = opdata.site.id,
        idOperacionMedioPago = "",
        barcode = Some(code))
      val opCode = op.copy(datos_offline = op.datos_offline.map(_.copy(barcode = Some(code))))

      legacyTxService.update(UpdateTx(chargeId, site, None, FacturaGenerada(), opr.copy(operationResource = Some(opCode)), Some(tr)))
      opr = transactionProcessor.transactionResponse2OperationExecutionResponse(Some(tr), None, opdata, Nil, None)
      updateNroOperationStatus(opr,Some(FacturaGenerada()))
      opr
    } catch {
      case ae: ApiException => {
        val tr = TransactionResponse(statusCode = 402,
          idMotivo = -1,
          terminal = None,
          nro_trace = None,
          nro_ticket = None,
          None,
          None,
          None,
          site_id = opdata.site.id,
          idOperacionMedioPago = "",
          barcode = None)

        opr = transactionProcessor.transactionResponse2OperationExecutionResponse(Some(tr), None, opdata, Nil, None)
        legacyTxService.update(UpdateTx(chargeId, site, None, FacturaNoGenerada(), opr, Some(tr)))
        updateNroOperationStatus(opr,Some(FacturaNoGenerada()))
        throw ae
      }
      case e: Exception => {
        val tr = TransactionResponse(statusCode = 500,
          idMotivo = -1,
          terminal = None,
          nro_trace = None,
          nro_ticket = None,
          None,
          None,
          None,
          site_id = opdata.site.id,
          idOperacionMedioPago = "",
          barcode = None)

        opr = transactionProcessor.transactionResponse2OperationExecutionResponse(Some(tr), None, opdata, Nil, None)
        legacyTxService.update(UpdateTx(chargeId, site, None, FacturaNoGenerada(), opr, Some(tr)))
        updateNroOperationStatus(opr,Some(FacturaNoGenerada()))
        throw e
      }
    }
  }
  
  
  // FIXME se trajo este codigo desde PaymentActor ya que en pago offline no se estaba invocando 
  // y no se aplicaban las reglas de reutilizacion de transacciones
  private def updateNroOperationStatus(oer: OperationExecutionResponse, state: Option[TransactionState] = None) = {
    val operation = oer.operationResource.getOrElse(throw new Exception("Error grave, intentando obtener operationResource"))
    val site = operation.datos_site.map(_.site_id
        .getOrElse(throw new Exception("Error grave, intentando obtener site")))
        .getOrElse(throw new Exception("Error grave, intentando obtener datosSiteResource"))
    val nroOperation = operation.nro_operacion.getOrElse(throw new Exception("Error grave, intentando obtener nro_operacion"))
    val modality = operation.datos_site.get.id_modalidad
    operationRepository.updateNroOperacionStatus(nroOperation, site, state.getOrElse(TransactionState.apply(oer.status)), getPaymentType(modality), getCountSubpayments(modality, operation.sub_transactions), None)
  }
  
  private def updateNroOperationAProcesar(operation: OperationData) = {
    val site = operation.site.id
    val nroOperation = operation.resource.nro_operacion.getOrElse(throw new Exception("Error grave, intentando obtener nro_operacion"))
    val modality = operation.datosSite.id_modalidad
    operationRepository.updateNroOperacionStatus(nroOperation, site, AProcesar(), getPaymentType(modality), None, None)
  }
  
  private def getPaymentType(modality: Option[String]) = {
    modality match {
      case Some("S") => "S" //"Distrubuted"
      case _ => "N" //"Single"
    }
  }
  
  private def getCountSubpayments(modality: Option[String], subTransaction: List[SubTransaction]) = {
    modality match {
      case Some("S") => Some(subTransaction)
      case _ =>  None
    }
  }
}