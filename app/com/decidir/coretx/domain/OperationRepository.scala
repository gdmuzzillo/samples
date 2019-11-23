package com.decidir.coretx.domain

import javax.inject.Inject
import play.api.db.Database
import com.decidir.coretx.utils.JdbcDaoUtils
import java.util.HashMap
import com.decidir.coretx.api.OperationResource
import org.springframework.jdbc.core.simple.SimpleJdbcInsert
import collection.JavaConverters._
import com.decidir.protocol.api.OperationResponse
import com.decidir.protocol.api.HistoricalStatus
import java.sql.Timestamp
import com.decidir.protocol.api.HistoricalStatus
import java.util.Calendar
import scala.util.Try
import com.decidir.coretx.api.ConfirmPaymentResponse
import com.decidir.coretx.api.Autorizada
import java.text.SimpleDateFormat
import java.math.BigDecimal

class OperationRepository  @Inject() (db: Database, operationRepository: OperationResourceRepository) extends JdbcDaoUtils(db) {
  
  def insertNewOperation(opData:OperationData, respuesta: OperationResponse, oidTransaccion:Option[String], refundId:Option[Long], cancelId:Option[Long], user: Option[String] = None) = {
    
    val idTransaccion = oidTransaccion.getOrElse {
      evalWithJdbcTemplate { jdbcTemplate =>  
        jdbcTemplate.queryForObject(
            """select transaccion_id from transaccion_operacion_xref where charge_id = ? """.stripMargin, 
            Array(opData.chargeId.asInstanceOf[Object]), classOf[String])
      }
    }
            
    val operationId = insertOperation(opData,respuesta,respuesta.historicalStatusList.last, user)
    insertOperaTransac(operationId, opData, respuesta, idTransaccion)
    respuesta.historicalStatusList.reverse.foreach {estado => insertOperaEstadoHist(operationId, estado, opData, respuesta)}
    
    refundId.map { id => 
      insertRefundsTransacOpXref(id, operationId.longValue(), opData.chargeId, None, cancelId, respuesta, opData)
    }
  }
  
  def updateCancelledOperation(refundId:Long, respuesta: OperationResponse, opData:OperationData, idOperacionCompuesta:Long) = {
    val idOperacion = {
      evalWithJdbcTemplate { jdbcTemplate =>  
        jdbcTemplate.queryForObject(
            """select operation_id from refunds_transaccion_operacion_xref where refund_id = ? """.stripMargin, 
            Array(refundId.asInstanceOf[Object]), classOf[String])
      }
    }

    // estado historico para updatear operacion anulada.
    val hStatus = new HistoricalStatus(-1,108,new Timestamp(System.currentTimeMillis()))
    
    evalWithJdbcTemplate { jdbcTemplate =>
        jdbcTemplate.setDataSource(db.dataSource)
        jdbcTemplate.update("""UPDATE operacion SET idestado = ?, idmotivo = ?, fecha = ? WHERE idoperacion = ? """
            ,hStatus.estadoId.asInstanceOf[Object], hStatus.motivoId.asInstanceOf[Object], hStatus.fecha.asInstanceOf[Object], idOperacion.asInstanceOf[Object])
    }
    
    insertOperaEstadoHist(idOperacion.toInt, hStatus, opData, respuesta)
    
      val params: Map[String, Any] = Map(
       "idoperacioncompuesta"-> idOperacionCompuesta,
       "idoperacionsimple"-> idOperacion) 
      
      val insertActor = new SimpleJdbcInsert(db.dataSource).withTableName("operacionesenoperacion")
      insertActor.execute(params.asJava)
    
    
    operationRepository.removeOperationBeenExecuted(opData.site.id,refundId)
  }
  
  def insertNewDistributedOperation(opData:OperationData, respuesta: OperationResponse, subpaymentId:Long, refundId:Option[Long], chargeId:Long, cancelId:Option[Long], user: Option[String] = None) = {
    val idTransaccion = {
      evalWithJdbcTemplate { jdbcTemplate =>  
        jdbcTemplate.queryForObject(
            """select transaccion_id from subpayment_transaccion_operacion_xref where subpayment_id = ? """.stripMargin, 
            Array(subpaymentId.asInstanceOf[Object]), classOf[String])
      }
    }
            
    val operationId = insertOperation(opData,respuesta,respuesta.historicalStatusList.last, user)
    insertOperaTransac(operationId, opData, respuesta, idTransaccion)
    respuesta.historicalStatusList.reverse.foreach {estado => insertOperaEstadoHist(operationId, estado, opData, respuesta)}
    
    refundId.map { id => 
      insertRefundsTransacOpXref(id, operationId.longValue(), chargeId, Some(subpaymentId), cancelId, respuesta: OperationResponse, opData)
    }
  }
  
  def insertRefundsTransacOpXref(refundId: Long, operationId: Long, chargeId: Long, subpaymentId:Option[Long], cancelId:Option[Long], respuesta: OperationResponse, opData:OperationData){
        var map = new HashMap[String, Any]()
        map.put("refund_id", refundId)
        map.put("operation_id", operationId)
        map.put("charge_id", chargeId)
        map.put("subpayment_id", subpaymentId.getOrElse(null))
        map.put("cancel_id", null)
        map.put("date", new Timestamp(System.currentTimeMillis()))
                
        val insertStatement = new SimpleJdbcInsert(db.dataSource).withTableName("refunds_transaccion_operacion_xref")
        insertStatement.execute(map)
        
        cancelId match {
          case Some(id) => {
            val sql = "UPDATE refunds_transaccion_operacion_xref SET cancel_id = ? WHERE refund_id = ?"
            evalWithJdbcTemplate { jdbcTemplate =>
                jdbcTemplate.setDataSource(db.dataSource)
                jdbcTemplate.update(sql, refundId.asInstanceOf[Object], id.asInstanceOf[Object])
            }
            updateCancelledOperation(id,respuesta, opData, operationId)
          }
          case _ => 
        }
  }
  
  private def insertOperation(opData:OperationData, respuesta: OperationResponse, estado:HistoricalStatus, user: Option[String] = None): Number = {
    //FIXME Se utiliza java.math.BigDecimal ya que al utilizar scala.math.BigDecimal se pierde la precision al igual que usar un Double
     val amount = opData.resource.monto.map(new BigDecimal(_).divide(new BigDecimal(100)))  
     
     val params: Map[String, Any] = Map(
     "idtipooperacion"-> respuesta.tipoOperacion,
     "idsite"-> opData.site.id,
     "idmediopago"-> opData.datosMedioPago.medio_de_pago,
     "idbackend"-> opData.cuenta.idBackend,
     "nroticket"->respuesta.nro_ticket.orNull,
     "nrotrace"-> respuesta.nro_trace.orNull,
     "monto"-> amount.orNull,
     "idopmediopago" -> respuesta.idOperacionMedioPago,
     "idprotocolo"-> opData.cuenta.idProtocolo,
     "idestado"-> estado.estadoId,
     "idmotivo"-> respuesta.idMotivo ,   
     "fecha"-> new Timestamp(System.currentTimeMillis()),
     "idusuario" -> user.getOrElse("API-REST")
     ) 
      
      val insertActor = new SimpleJdbcInsert(db.dataSource).withTableName("operacion").usingGeneratedKeyColumns("idoperacion")
      val idoperacion = insertActor.executeAndReturnKey(params.asJava)
      idoperacion
  }
  
  private def insertOperaTransac(operationId:Number,opData:OperationData, respuesta: OperationResponse, transactionId:String) = {

     val params: Map[String, Any] = Map(
     "idoperacion"-> operationId,
     "idtransaccion"-> transactionId,
     "idtipooperacion"-> respuesta.tipoOperacion,
     "idmotivo"-> respuesta.idMotivo,
     "idprotocolo"-> opData.cuenta.idProtocolo,
     "nrosecuencia"-> 1,
     "txpropia"-> null
     )
      
      val insertActor = new SimpleJdbcInsert(db.dataSource).withTableName("opertransac")
      insertActor.execute(params.asJava)
      
  }
  
  def insertOperaEstadoHist(operationId:Number,estado:HistoricalStatus,opData:OperationData,respuesta:OperationResponse) = {
    
       val params: Map[String, Any] = Map(
     "idoperacion"-> operationId,
     "fecha"-> estado.fecha,
     "idestado"-> estado.estadoId,
     "idmotivo"-> estado.motivoId,
     "idprotocolo"-> opData.cuenta.idProtocolo,
     "idtipooperacion"-> respuesta.tipoOperacion
     )
     
     val insertActor = new SimpleJdbcInsert(db.dataSource).withTableName("operestadoshist")
     insertActor.execute(params.asJava)
  }
  
  def insertNewPaymentConfirmation(opData:OperationData,  respuesta: OperationResponse, confirmationPaymentResponse: ConfirmPaymentResponse, user: Option[String] = None) = {
      val operationId = insertOperation(opData,respuesta,respuesta.historicalStatusList.last, user)
      insertOperaTransac(operationId, opData, respuesta, opData.resource.idTransaccion.get)
      respuesta.historicalStatusList.reverse.foreach {estado => insertOperaEstadoHist(operationId, estado, opData, respuesta)}

      insertPaymentConfirmation(opData, confirmationPaymentResponse)
  }
    
  private def insertPaymentConfirmation(opData: OperationData, confirmationPaymentResponse: ConfirmPaymentResponse) {
      var map = new HashMap[String, Any]()
      map.put("confirmation_id", confirmationPaymentResponse.id)
      map.put("charge_id", opData.resource.charge_id.get)
      map.put("amount", opData.resource.monto.get)
      map.put("origin_amount", confirmationPaymentResponse.origin_amount)
      map.put("date", confirmationPaymentResponse.date)
              
      val insertStatement = new SimpleJdbcInsert(db.dataSource).withTableName("payment_confirmation_transaccion_operacion_xref")
      insertStatement.execute(map)
  }
    
  def beforeOfClose(chargeId: Long, refundId: Long) = {
    
      val sql = """select rtox.date as refund_date, hist.fecha as close_date 
          from refunds_transaccion_operacion_xref rtox, 
          transaccion_operacion_xref tox,
          spsestadoshist hist, 
          spstransac trans
        where tox.transaccion_id = trans.idtransaccion and 
          tox.transaccion_id =  hist.idtransaccion and
          hist.idestado = 6 and
          tox.charge_id = rtox.charge_id and 
          tox.charge_id = ? and
          rtox.refund_id = ?"""
          
      val jsonMap = evalWithJdbcTemplate { jdbcTemplate =>
        jdbcTemplate.queryForList(sql, chargeId.toString, refundId.toString)
      }.asScala.toList
    
      if (jsonMap.nonEmpty) {
        val json = jsonMap(0)
        val dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS")
    	  val refundDate = new Timestamp(dateFormat.parse(json.get("refund_date").toString).getTime)
        val closeDate = new Timestamp(dateFormat.parse(json.get("close_date").toString).getTime)
        refundDate.before(closeDate)
      } else {
        false
      }
  }
}