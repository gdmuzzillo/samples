package services.protocol

import com.decidir.coretx.domain.OperationData
import com.decidir.protocol.api._
import java.util.Date

import com.decidir.coretx.api.GDSResource
import org.apache.commons.lang3.StringUtils

object Operation2ProtocolConverter {

  def convert(opData: OperationData): ProtocolResource = {

    val datosMedioPago = opData.datosMedioPago
    val datosTitular = opData.datosTitular

    val comprador = Comprador(
      nro_tarj_vis = if(!opData.nroTarjeta.isEmpty) opData.nroTarjetaVisible else "",
      nro_tarj = opData.nroTarjeta,
      vencimiento_tarjeta = datosMedioPago.expiration,
      nombre_en_tarjeta = datosMedioPago.nombre_en_tarjeta,
      calle = datosTitular.calle,
      nro_puerta = datosTitular.nro_puerta,
      email = datosTitular.email_cliente,
      nro_doc = datosTitular.nro_doc,
      fecha_nacimiento = datosTitular.fecha_nacimiento,
      tipo_doc = datosTitular.tipo_doc,
      cuotas = opData.resource.cuotas.getOrElse(1),
      cod_seguridad =
        //En el caso de las operaciones MPOS siempre se envia el codigo de seguridad aunque el sitio este configurado con SCT
        (StringUtils.trimToEmpty(opData.site.mensajeriaMPOS.getOrElse("N")), StringUtils.trimToEmpty(opData.site.mensajeria)) match {
          case ("N", "SCT") => None
          case (_) => datosMedioPago.security_code
        },
      nombre_establecimiento = opData.resource.datos_medio_pago.get.establishment_name,
      ip = datosTitular.ip.getOrElse(""))

    val ids = Ids(
      id_protocolo = opData.cuenta.idProtocolo,
      id_site = opData.site.id,
      id_transaccion = 0,
      id_marcatarjeta = opData.marcaTarjeta.id.toString(),
      id_cliente = 0,
      id_mediopago = opData.medioDePago.id,
      id_backend = opData.cuenta.idBackend,
      opData.chargeId.toString,
      id_plan = opData.resource.datos_medio_pago.flatMap(_.id_plan),
      id_operacion_medio_pago = opData.resource.datos_medio_pago.get.id_operacion_medio_pago,
      nro_devolucion = opData.resource.datos_medio_pago.get.nro_devolucion,
      nro_id_destinatario = Some(opData.cuenta.nroIdDestinatario),
      banco = opData.resource.datos_site.flatMap(_.banco))

    val gds = opData.resource.datos_gds

    val fechas = Fechas(
      fecha_original = new Date(),
      fecha_inicio = opData.resource.creation_date.getOrElse(throw new RuntimeException("No se definio una fecha de creacion de la operacion")),
      fechavto_cuota_1 = opData.resource.fechavto_cuota_1, // agregar fechvto_cuota_1
      fecha_pago_diferido = None // agregar fecha_pago_diferido
      )

    val terminal_tickets = TerminalYTickets(
      
      nro_terminal = opData.resource.datos_medio_pago.get.nro_terminal.getOrElse(""),
      nro_trace = opData.resource.datos_medio_pago.get.nro_trace.map(_.toLong).getOrElse(0),
      nro_ticket = opData.resource.datos_medio_pago.get.nro_ticket.map(_.toLong).getOrElse(0)

    )

    val extended_data = ProtocolResourceExtension(opData.resource.datos_bsa, opData.resource.datos_banda_tarjeta, opData.resource.agro_data, opData.resource.datos_spv)

    val mpos = opData.site.mensajeriaMPOS == Some("S")

    val protocol = ProtocolResource(
      id_operacion = opData.resource.id,
      nro_id = Some(opData.cuenta.nroId),
      nro_operacion_site = opData.nroOperacionSite,
      utiliza_autenticacion_externa = opData.cuenta.utilizaAutenticacionExterna,
      pago_diferido_habilitado = opData.cuenta.pagoDiferidoHabilitado,
      es_autorizada_en_dos_pasos = opData.cuenta.autorizaEnDosPasos,
      monto = opData.resource.monto.get,
      codigo_iso_num = Some(opData.codigo_iso_num), //codigo iso num agregar
      plan_cuotas = Some(opData.cuenta.planCuotas),
      referer = opData.resource.referer,
      comprador = comprador,
      gds = gds,
      mensajeria = opData.site.mensajeria,
      codigo_alfa_num = opData.marcaTarjeta.codAlfaNum,
      codigo_autorizacion = opData.resource.datos_medio_pago.get.cod_autorizacion.getOrElse(""),
      ids = ids,
      fechas = fechas,
      terminal_y_tickets = Some(terminal_tickets),
      aggregate_data = opData.resource.aggregate_data,
      sitio_agregador = opData.site.agregador,
      pago_offline = opData.resource.datos_offline,
      isMPOS = mpos,
      extended_data = extended_data)

    protocol
  }
}