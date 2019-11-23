package decidir.sps.core

import java.text.SimpleDateFormat
import java.util.Date

import com.decidir.coretx.api.OperationResource
import com.decidir.protocol.api.TransactionResponse

object AfipPPB {

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  def toMap(operation: OperationResource, transactionResponse: TransactionResponse, amount: String): Map[String, String] = {
    Map(
      "card_number" -> operation.datos_medio_pago.flatMap(_.nro_tarjeta).getOrElse("").toString,
      "user_cuit" -> operation.ticket_request.flatMap(_.cuit.owner).getOrElse("").toString,
      "number_vep" -> operation.ticket_request.map(_.vep.number.getOrElse("")).getOrElse("").toString,
      "posting_date" -> operation.ticket_request.map(_.vep.creation_date.fold(""){formatDate(_)}).getOrElse(""),
      "transaction_id"-> operation.nro_operacion.getOrElse("").toString,
      "payment_entity" -> operation.ticket_request.flatMap(_.cp.payment_entity).getOrElse("").toString,
      "payer_bank" -> operation.ticket_request.flatMap(_.cp.payer_bank).getOrElse("").toString,
      "nro_ticket" ->  operation.datos_medio_pago.flatMap(_.nro_ticket).getOrElse("").toString,
      "control_code" -> transactionResponse.cod_aut.getOrElse(""),
      "payment_format" -> operation.ticket_request.map(_.cp.payment_format).getOrElse("").toString,
      "payment_datetime" -> operation.creation_date.map(formatDate(_)).getOrElse("").toString,
      "payment_form" -> operation.ticket_request.map(_.vep.form).getOrElse("").toString,
      "branch_office_type" -> operation.ticket_request.flatMap(_.cp.branch_office_type).getOrElse("").toString,
      "taxpayer" -> operation.ticket_request.flatMap(_.cuit.taxpayer).getOrElse("").toString,
      "currency" -> operation.datos_medio_pago.flatMap(_.id_moneda).getOrElse("").toString,
      "amount" -> amount)
  }

  def formatDate(date: Date): String = {
    dateFormat.format(date)
  }

}