package services.payments

import play.api.libs.json.JsSuccess
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import com.decidir.coretx.api.OperationExecutionResponse
import play.api.libs.json.JsValue
import com.decidir.coretx.domain.OperationData
import play.api.libs.json.Json
import com.decidir.coretx.api.FDBlack
import com.decidir.coretx.api.FraudDetectionDecision
import com.decidir.coretx.api.FDYellow
import com.decidir.coretx.api.FDBlue
import com.decidir.coretx.api.FDGreen
import com.decidir.protocol.api.TransactionResponse
import com.decidir.coretx.api.FDRed
import com.decidir.coretx.api.CyberSourceResponse
import com.decidir.coretx.api.OperationResource
import com.decidir.protocol.api.OperationResponse
import com.decidir.coretx.domain.Cuenta
import java.sql.Timestamp
import com.decidir.coretx.domain.Site
import LegacyTxJsonFormat._
import com.decidir.coretx.messaging.IdempotentId
import com.decidir.coretx.api._
import scala.collection.mutable.ArrayBuffer


object LegacyDBMessage {
  def apply(payload: LegacyDBCommandTrait): LegacyDBMessage = LegacyDBMessage(payload.getClass.getSimpleName, payload, IdempotentId("legacytx", -1))
  def apply(payload: LegacyDBCommandTrait, idempotentId: IdempotentId): LegacyDBMessage = LegacyDBMessage(payload.getClass.getSimpleName, payload, idempotentId)
}

case class LegacyDBMessage(messageType: String, payload: LegacyDBCommandTrait, idempotentId: IdempotentId) {
  def chargeId: Long = payload.chargeId
  def siteId: Option[String] = payload.siteId 
  def transactionId: Option[String] = payload.transactionId
  def merchantTransactionId: String = payload.merchantTransactionId
}
//reutilized
case class InsertTx(chargeId: Long, site: Site, distribuida: Option[String], estadoFinal: TransactionState, oer: OperationExecutionResponse) extends LegacyDBCommandTrait {
  def enEstado(estado: TransactionState) = copy(estadoFinal = estado)
  override def toJson = Json.toJson(this.copy(oer = OperationExecutionResponse.removeCvv(this.oer)))
  override def siteId: Option[String] = oer.operationResource.map(_.siteId)
  override def transactionId: Option[String] = oer.operationResource.flatMap(_.idTransaccion)
  override def merchantTransactionId: String = oer.operationResource.flatMap(_.nro_operacion).getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
} 

case class UpdateTx(chargeId: Long, site: Site, distribuida: Option[String], estadoFinal: TransactionState, oer: OperationExecutionResponse, transactionResponse: Option[TransactionResponse]) extends LegacyDBCommandTrait {
  def enEstado(estado: TransactionState) = copy(estadoFinal = estado)
  override def toJson = Json.toJson(this.copy(oer = OperationExecutionResponse.removeCvv(this.oer)))
  override def siteId: Option[String] = oer.operationResource.map(_.siteId)
  override def transactionId: Option[String] = oer.operationResource.flatMap(_.idTransaccion)
  override def merchantTransactionId: String = oer.operationResource.flatMap(_.nro_operacion).getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
} 

case class InsertDistributedTx(chargeId: Long, parent: InsertTx, subtxs: List[DistributedTxElement], estadoFinal: TransactionState) extends LegacyDBCommandTrait {
	override def toJson = Json.toJson(this.copy(parent = this.parent.copy(oer = OperationExecutionResponse.removeCvv(this.parent.oer)), subtxs = this.subtxs.map(subtx => subtx.copy(operation = OperationResource.removeCvv(subtx.operation)))))
	override def siteId: Option[String] = parent.oer.operationResource.map(_.siteId)
  override def transactionId: Option[String] = parent.oer.operationResource.flatMap(_.idTransaccion)
  override def merchantTransactionId: String = parent.oer.operationResource.flatMap(_.nro_operacion).getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
}

case class UpdateDistributedTx(chargeId: Long, parent: UpdateTx, subtxs: List[DistributedTxElement], estadoFinal: TransactionState) extends LegacyDBCommandTrait {
	override def toJson = Json.toJson(this.copy(parent = this.parent.copy(oer = OperationExecutionResponse.removeCvv(this.parent.oer)), subtxs = this.subtxs.map(subtx => subtx.copy(operation = OperationResource.removeCvv(subtx.operation)))))
	override def siteId: Option[String] = parent.oer.operationResource.map(_.siteId)
  override def transactionId: Option[String] = parent.oer.operationResource.flatMap(_.idTransaccion)
  override def merchantTransactionId: String = parent.oer.operationResource.flatMap(_.nro_operacion).getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
}

case class DistributedTxElement(site: Site, cuotas: Int, percent: Option[Double], monto: Long, transactionResponse:Option[TransactionResponse], operation:OperationResource) extends LegacyDBCommandTrait {
	override def toJson = Json.toJson(this.copy(operation = OperationResource.removeCvv(this.operation)))
	override def chargeId = operation.charge_id.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene chargeId"))
  override def siteId: Option[String] = Option(operation.siteId)
  override def transactionId: Option[String] = operation.idTransaccion
  override def merchantTransactionId: String = operation.nro_operacion.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
}

case class InsertTxHistorico(oidTransaccion:Option[Long], oChargeId:Option[Long], protocolId: Int, estado:Int, distribuida:Option[String], changeStateDate:Option[Long], reasonCode: Option[Int], mTransactionId: Option[String]) extends LegacyDBCommandTrait {
  override def toJson = Json.toJson(this)
  override def chargeId = oChargeId.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene chargeId"))
  override def siteId: Option[String] = None
  override def transactionId: Option[String] = oidTransaccion.map(_.toString)
  override def merchantTransactionId: String = mTransactionId.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
  def operationChangeStateDate: Option[Long] = changeStateDate
}

case class UpdateTxOnOperation(opData:OperationData, estado:TransactionState, distribuida: Option[String], result:OperationResponse, idTransaccion: Option[String], user: Option[String] = None) extends LegacyDBCommandTrait {
  override def toJson = Json.toJson(this.copy(opData = this.opData.removeCvv))
  override def chargeId = opData.resource.charge_id.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene chargeId"))
  override def siteId: Option[String] = Option(opData.resource.siteId)
  override def transactionId: Option[String] = opData.resource.idTransaccion
  override def merchantTransactionId: String = opData.resource.nro_operacion.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
}

case class InsertCS(resource: OperationResource, csr: CyberSourceResponse) extends LegacyDBCommandTrait {
  override def toJson = Json.toJson(this.copy(resource = OperationResource.removeCvv(this.resource)))
  override def chargeId = resource.charge_id.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene chargeId"))
  override def siteId: Option[String] = Option(resource.siteId)
  override def transactionId: Option[String] = resource.idTransaccion
  override def merchantTransactionId: String = resource.nro_operacion.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
}

case class UpdateCS(oer: OperationExecutionResponse, csr: CyberSourceResponse, annulmentPending: Boolean) extends LegacyDBCommandTrait {
  override def toJson = Json.toJson(this.copy(oer = OperationExecutionResponse.removeCvv(this.oer)))
  override def chargeId = oer.operationResource.flatMap(_.charge_id).getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene chargeId"))
  override def siteId: Option[String] = oer.operationResource.map(_.siteId)
  override def transactionId: Option[String] = oer.operationResource.flatMap(_.idTransaccion)
  override def merchantTransactionId: String = oer.operationResource.flatMap(_.nro_operacion).getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
}

case class UpdateXref(chargeId: Long, oer: OperationExecutionResponse) extends LegacyDBCommandTrait {
  override def toJson = Json.toJson(this.copy(oer = OperationExecutionResponse.removeCvv(this.oer)))
  override def siteId: Option[String] = oer.operationResource.map(_.siteId)
  override def transactionId: Option[String] = oer.operationResource.flatMap(_.idTransaccion)
  override def merchantTransactionId: String = oer.operationResource.flatMap(_.nro_operacion).getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
}

case class InsertOpx(opData: OperationData, respuesta: OperationResponse, oidTransaccion:Option[String], refundId:Option[Long], cancelId:Option[Long] = None, user: Option[String] = None) extends LegacyDBCommandTrait {
  override def toJson = Json.toJson(this.copy(opData = this.opData.removeCvv))
  override def chargeId = opData.resource.charge_id.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene chargeId"))
  override def siteId: Option[String] = Option(opData.resource.siteId)
  override def transactionId: Option[String] = opData.resource.idTransaccion
  override def merchantTransactionId: String = opData.resource.nro_operacion.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
}

case class InsertDOPx(opData:OperationData, respuesta: OperationResponse, subpaymentId:Long, refundId:Option[Long], chargeId:Long, cancelId:Option[Long] = None, user: Option[String] = None) extends LegacyDBCommandTrait {
  override def toJson = Json.toJson(this.copy(opData = this.opData.removeCvv))
  override def siteId: Option[String] = Option(opData.resource.siteId)
  override def transactionId: Option[String] = opData.resource.idTransaccion
  override def merchantTransactionId: String = opData.resource.nro_operacion.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
}

case class InsertConfirmationOpx(opData:OperationData,  respuesta: OperationResponse, confirmationPaymentResponse: ConfirmPaymentResponse, user: Option[String] = None) extends LegacyDBCommandTrait {
  override def toJson = Json.toJson(this.copy(opData = this.opData.removeCvv))
  override def chargeId = opData.resource.charge_id.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene chargeId"))
  override def siteId: Option[String] = Option(opData.resource.siteId)
  override def transactionId: Option[String] = opData.resource.idTransaccion
  override def merchantTransactionId: String = opData.resource.nro_operacion.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
}

/*case class UpdateReverse(opData:OperationData, distribuida: Option[String], oidTransaccion: Option[String], state:TransactionState) extends LegacyDBCommandTrait {
  override def toJson = Json.toJson(this)
  override def chargeId = opData.resource.charge_id.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene chargeId"))
  override def siteId: Option[String] = Option(opData.resource.siteId)
  override def transactionId: Option[String] = opData.resource.idTransaccion
  override def merchantTransactionId: String = opData.resource.nro_operacion.getOrElse(throw new Exception("Error grave enviando mensaje a kafka. OperationResource no tiene nro_operacion"))
}*/

object TransactionFSM {
  
  def estadosPara(estadoFinal: TransactionState): List[TransactionState] = {
    estadoFinal match {
      case Ingresada() => List(Ingresada())
      case RechazadaDatosInvalidos() => List(RechazadaDatosInvalidos())
        //TODO:Revisar si ingresada no se duplica para AProcesar
      case AProcesar() => List(Ingresada(), AProcesar())
      case Autorizada() => List( Autorizada())
      case PreAutorizada() => List( PreAutorizada())
      case Rechazada() => List( Rechazada())
      case RebotadaPorGrupo() => List( RebotadaPorGrupo())
      case AnuladaPorGrupo() => List(Autorizada(), TxAnulada(), AnuladaPorGrupo())
      case AReversar() => List(AReversar(), Rechazada())
      case ARevisar() => List(AReversar())
      case FalloComunicacion() => List( AReversar(), Rechazada())
        //TODO:Revisar si ingresada no se duplica para TxAnulada
      case TxAnulada() => List(Ingresada(), AProcesar(), AReversar(), Rechazada())
      case Black() => List(Ingresada())
      case FacturaGenerada() => List(FacturaGenerada())
      case FacturaNoGenerada() => List(FacturaNoGenerada())
      case Acreditada() => List(Acreditada())
      case other => throw new RuntimeException("Caso no manejado para historial de transiciones automaticas: " + other)
    }
  }
}

object CybersourceFSM {
    
  def estadosPara(estadoFinal: FraudDetectionDecision, retries: Option[Int] =  None): List[TransactionState] = {
    estadoFinal match {
      case FDGreen() => List(SentValidacionCS(),SuccesValidacionCS(),Green())
      case FDBlue() => {
        retries.map(rs => {
          val csList = ArrayBuffer[TransactionState]()
          csList ++= List(SentValidacionCS(),SuccesValidacionCS())
          1 to rs foreach { _ => csList ++= List(ReintentoCS()) }
          if (rs == 3) {
            csList ++= List(ErrorComunicacionCS())
          } else {
            csList ++= List(Blue())
          }
          csList.toList
          }).getOrElse(throw new RuntimeException("Caso no manejado para historial de transiciones automaticas: retries no enviado"))
      }
      case FDBlack() => List(SentValidacionCS(),FailureValidacionCS(),Black())
      case FDRed() => List(SentValidacionCS(),SuccesValidacionCS(),Red())
      case FDYellow() => List(SentValidacionCS(),SuccesValidacionCS(),Yellow())
      case other => throw new RuntimeException("Caso no manejado para historial de transiciones automaticas: " + other)
    }
  }
}
