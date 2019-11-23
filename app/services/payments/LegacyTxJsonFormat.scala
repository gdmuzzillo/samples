package services.payments

import play.api.libs.json.JsSuccess
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import com.decidir.coretx.api.{LegacyDBCommandTrait, OperationExecutionResponse}
import play.api.libs.json.JsValue
import com.decidir.coretx.domain.OperationData
import play.api.libs.json.Json
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import com.decidir.coretx.messaging.IdempotentId

object LegacyTxJsonFormat {
  
  import controllers.utils.OperationJsonFormat._
 
  
  import com.decidir.coretx.api.CybersourceJsonMessages._
  import com.decidir.coretx.api.OperationJsonFormats._
  
  implicit val oerReads = Json.reads[OperationExecutionResponse]    
  implicit val oerWrites = Json.writes[OperationExecutionResponse]    
  
  implicit val insertTxReads = Json.reads[InsertTx]
  implicit val insertTxWrites = Json.writes[InsertTx]
  
  implicit val distributedTxElementReads = Json.reads[DistributedTxElement]
  implicit val distributedTxElementWrites = Json.writes[DistributedTxElement]
  
  implicit val insertDtxReads = Json.reads[InsertDistributedTx]
  implicit val insertDtxWrites = Json.writes[InsertDistributedTx]
  
  implicit val opdataReads = Json.reads[OperationData]
  implicit val opdataWrites = Json.writes[OperationData]
  
  implicit val updateTxReads = Json.reads[UpdateTx]
  implicit val updateTxWrites = Json.writes[UpdateTx]
  
  implicit val insertCSReads = Json.reads[InsertCS]
  implicit val insertCSWrites = Json.writes[InsertCS]
  
  implicit val updateCSReads = Json.reads[UpdateCS]
  implicit val updateCSWrites = Json.writes[UpdateCS]
  
  implicit val updateXrefReads = Json.reads[UpdateXref]
  implicit val updateXrefWrites = Json.writes[UpdateXref]
  
  implicit val updateTxOnOperationReads = Json.reads[UpdateTxOnOperation]
  implicit val updateTxOnOperationWrites = Json.writes[UpdateTxOnOperation]
  
  implicit val insertTxHistoricoReads = Json.reads[InsertTxHistorico]
  implicit val insertTxHistoricoWrites = Json.writes[InsertTxHistorico]
  
  implicit val idempotentIdReads = Json.reads[IdempotentId]
  implicit val idempotentIdWrites = Json.writes[IdempotentId]
  
  implicit val updateDistributedTxReads = Json.reads[UpdateDistributedTx]
  implicit val updateDistributedTxWrites = Json.writes[UpdateDistributedTx]
  
  implicit val insertOpxReads = Json.reads[InsertOpx]
  implicit val insertOpxWrites = Json.writes[InsertOpx]
  
  implicit val insertDOPxReads = Json.reads[InsertDOPx]
  implicit val insertDOPxWrites = Json.writes[InsertDOPx]
  
  implicit val insertConfirmationOpxReads = Json.reads[InsertConfirmationOpx]
  implicit val insertConfirmationOpxWrites = Json.writes[InsertConfirmationOpx]
  
//  implicit val updateReverseReads = Json.reads[UpdateReverse]
//  implicit val updateReverseWrites = Json.writes[UpdateReverse]
  
  implicit val legacyDbMsgWrites = new Writes[LegacyDBMessage] {
    def writes(m: LegacyDBMessage) = {
      Json.obj(
          "messageType" -> m.payload.getClass.getSimpleName, 
          "payload" -> m.payload.toJson, 
          "idempotentId" -> Json.toJson(m.idempotentId))
    }
  }

  implicit val legacyDbMsgReads = new Reads[LegacyDBMessage] {
    def reads(js: JsValue) = {
      
      val messageType = (js \ "messageType").as[String]
      val payload: JsValue = (js \ "payload").as[JsValue]
      val idempotentIdJsResult = idempotentIdReads.reads((js \ "idempotentId").as[JsValue])
      
      val cmd: JsResult[LegacyDBCommandTrait] = messageType match {

        case "InsertTx" => insertTxReads.reads(payload)

        case "InsertDistributedTx" => insertDtxReads.reads(payload) //   JsSuccess(LegacyDBMessage(messageType,payload.validate[InsertDistributedTx]))
        
        case "UpdateTx" => updateTxReads.reads(payload) // LegacyDBMessage(messageType,payload.validate[UpdateTx]))      

        case "InsertCS" => insertCSReads.reads(payload) // LegacyDBMessage(messageType,payload.validate[InsertCS]))  
        
        case "UpdateCS" => updateCSReads.reads(payload)

        case "UpdateXref" => updateXrefReads.reads(payload) //  LegacyDBMessage(messageType,payload.validate[UpdateXref]))

        case "UpdateTxOnOperation" => updateTxOnOperationReads.reads(payload) // LegacyDBMessage(messageType,payload.validate[UpdateTxOnOperation]))       

        case "InsertTxHistorico" => insertTxHistoricoReads.reads(payload) //  LegacyDBMessage(messageType,payload.validate[InsertTxCSHistorico]))
        
        case "UpdateDistributedTx"=> updateDistributedTxReads.reads(payload)
        
        case "InsertOpx" => insertOpxReads.reads(payload)

        case "InsertDOPx" => insertDOPxReads.reads(payload)
        
        case "InsertConfirmationOpx" => insertConfirmationOpxReads.reads(payload)

        case "TransactionStateMessage" => transactionStateMessageReads.reads(payload)
        
//        case "UpdateReverse" => updateReverseReads.reads(payload)
        
        case other => JsError(s"$other is not a subtype of LegacyDBCommandTrait")
      }
      
      for {
        lcmd <- cmd
        idempotentId <- idempotentIdJsResult
      } yield(LegacyDBMessage(messageType, lcmd, idempotentId))
//      cmd.map(LegacyDBMessage(messageType,_, idempotentId))
      
    }
  }  
  
  
}