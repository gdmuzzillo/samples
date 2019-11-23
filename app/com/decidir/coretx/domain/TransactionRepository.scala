package com.decidir.coretx.domain

import java.math.BigDecimal
import java.sql.{Date, Timestamp, Types}
import java.util
import javax.inject.Inject

import com.decidir.coretx.api.OperationJsonFormats._
import com.decidir.coretx.api._
import com.decidir.coretx.utils.{JdbcDaoUtils, StringUtil}
import com.decidir.encrypt.EncryptionService
import com.decidir.protocol.api._
import controllers.PaymentMethod
import org.springframework.dao.{DataAccessException, EmptyResultDataAccessException}
import org.springframework.jdbc.core.{CallableStatementCreator, JdbcTemplate}
import org.springframework.jdbc.core.simple.SimpleJdbcInsert
import play.api.{Configuration, Logger}
import play.api.db.Database
import play.api.libs.json.Json
import services.PaymentMethodService
import services.payments.{DistributedTxElement, InsertTx, TransactionFSM}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}


class TransactionRepository @Inject()(db: Database,
                                      marcaTarjetaRepository: MarcaTarjetaRepository,
                                      encryptionService: EncryptionService,
                                      paymentMethodService: PaymentMethodService,
                                      configuration: Configuration) extends JdbcDaoUtils(db) {

  val encryptionKeyIndex = configuration.getInt("sps.encryption.keyndx").getOrElse(17)

  def retrieveOERFromIdTrSiteNCod(cod_trx:String, barra: String): Try[OperationExecutionResponse] = {
    val jsonString = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForMap(
        """
          |SELECT
          |  CAST(xref.operation_data as char(10000)) operation_data, sp.idestado
          |FROM pagooffline po
          |INNER JOIN spstransac sp ON po.idtransaccion = sp.idtransaccion
          |INNER JOIN transaccion_operacion_xref xref ON sp.idtransaccion = xref.transaccion_id
          |WHERE idtransaccionsite = ? AND codigo = ?;
        """.stripMargin, cod_trx, barra)
    }

    convertToOERFromJson(jsonString)
  }

  def updateTransaccionOperacion(opData: OperationData, status: TransactionState, distribuida: Option[String], result: OperationResponse, oidTransaccion: Option[String], user: Option[String] = None) = {

    loadMDC(siteId = Some(opData.resource.siteId),
      transactionId = Some(opData.resource.id),
      merchantTransactionId = opData.resource.nro_operacion,
      referer = opData.resource.datos_site.flatMap(_.referer),
      paymentId = opData.resource.charge_id.map(_.toString))


    evalTxWithJdbcTemplate { implicit jdbcTemplate =>

      distribuida match {
        case Some("F") | None => {

          val idTransaccion = oidTransaccion.getOrElse {
            evalWithJdbcTemplate { jdbcT =>
              jdbcT.queryForObject(
                """select transaccion_id from transaccion_operacion_xref where charge_id = ? """.stripMargin,
                Array(opData.chargeId.asInstanceOf[Object]), classOf[String])
            }
          }

          logger.debug("Update Transaccion con operacion - spstransac")
          updateAfterOperation(opData, status, distribuida, result, idTransaccion)

          logger.debug("Update Transaccion con operacion xref - transaccion_operacion_xref")
          updateTransaccionXref(opData, status)
        }
        case _ => {
          val idTransaccion = oidTransaccion.getOrElse {
            evalWithJdbcTemplate { jdbcT =>
              jdbcT.queryForObject(
                """select transaccion_id from subpayment_transaccion_operacion_xref where subpayment_id = ? """.stripMargin,
                Array(opData.chargeId.asInstanceOf[Object]), classOf[String])
            }
          }

          logger.debug("Update Transaccion con operacion - spstransac")
          updateAfterOperation(opData, status, distribuida, result, idTransaccion)
          logger.debug("NO Update transaccion_operacion_xref Transaccion es un child")
        }
      }
    } // jdbcTemplate

  }

  private def updateAfterOperation(opData: OperationData, status: TransactionState, distribuida: Option[String], result: OperationResponse, idTransaccion: String)(implicit jdbcTemplate: JdbcTemplate) = {

    val sqlUpd = "UPDATE spstransac SET idestado = ?, idmotivo = ?, fecha = ?, idprotocolo = ?, nuevasops = ?, monto = ? WHERE idtransaccion = ?"
    //FIXME Se utiliza java.math.BigDecimal ya que al utilizar scala.math.BigDecimal se pierde la precision al igual que usar un Double
    val amount = new BigDecimal(opData.resource.monto.get).divide(new BigDecimal(100))
    jdbcTemplate.update(sqlUpd, Integer.valueOf(status.id), Integer.valueOf(getMotivo(result.idMotivo)), new Timestamp(System.currentTimeMillis()), Integer.valueOf(opData.cuenta.idProtocolo), "S", amount, idTransaccion)

    val updCS = "update spstransac_cybersource set estadofinaltransaccion = ? where idtransaccion = ?"
    jdbcTemplate.update(updCS, Integer.valueOf(status.id), idTransaccion)
  }

  //Fix para que SAC no muestre la descripcion del estado
  private def getMotivo(id: Int) = id match {
    case 0 => -1
    case _ => id
  }

  def insertTransaccionBasico(chargeId: Long, op: OperationResource, site: Site, distribuida: Option[String], estados: List[Int], oer: OperationExecutionResponse) = {
    val idTx = evalTxWithJdbcTemplate { implicit jdbcTemplate =>
      doInsertTransaccionBasico(chargeId, op, site, distribuida, estados, oer)
    }
    idTx
  }


  def doInsertTransaccionBasico(chargeId: Long, op: OperationResource, site: Site, distribuida: Option[String], estados: List[Int], oer: OperationExecutionResponse)(implicit jdbcTemplate: JdbcTemplate) = {

    loadMDC(siteId = Some(op.siteId),
      transactionId = Some(op.id),
      merchantTransactionId = op.nro_operacion,
      referer = op.datos_site.flatMap(_.referer),
      paymentId = op.charge_id.map(_.toString))

    val tarjetaEsNacional = marcaTarjetaRepository.retrieve(op.datos_medio_pago.get.marca_tarjeta.get.toString).get.esNacional(op.datos_medio_pago.get.nro_tarjeta)

    val cuenta: Cuenta = site.cuenta(op.datos_medio_pago.flatMap(_.medio_de_pago).map(_.toString).getOrElse(throw new Exception("undefined MedioDePagoId")),
      paymentMethodService.getProtocolId(op), paymentMethodService.getBackenId(op)).getOrElse(throw new Exception("Not found Cuenta"))

    var idTransaccion: Long = 0
    val op2 = op.copy(datos_site = Some(op.datos_site.get.copy(site_id = Some(site.id))))
    val oer2 = oer.copy(operationResource = Some(op2))

    logger.debug("Insert Transaccion - spstransac")

    if(op.retries.getOrElse(0) > 0){
      idTransaccion = updateTransaccionOnReuse(oer2, cuenta, tarjetaEsNacional, distribuida, estados.last)

      op.agro_data.foreach(ad => {
        val periodicity = ad.getPeriodicidad()
        ad.installments.foreach( idata => insertAgroInstallmentData(idata, periodicity, idTransaccion))
      })
    }
    else{
      idTransaccion = insertTransaccion(oer2, cuenta, tarjetaEsNacional, distribuida, estados.last)
      op.agro_data.foreach(ad => {
        val periodicity = ad.getPeriodicidad()
        ad.installments.foreach( idata => insertAgroInstallmentData(idata, periodicity, idTransaccion))
      })
    }

    val op3 = op.copy(idTransaccion = Some(idTransaccion.toString()), datos_medio_pago = Some(op.datos_medio_pago.get.copy(cod_autorizacion = Some(oer.authorizationCode))))
    val oer3 = oer.copy(operationResource = Some(op3))

    estados.foreach {estado => insertEstadoHistorico(Some(idTransaccion), None, cuenta.idProtocolo, estado, None, Some(System.currentTimeMillis()), None)}

    insertIfNotExistDomicilioTitular(op, idTransaccion)
    
    insertIfNotExistNomEstablecimiento(op, idTransaccion)
    
    if(site.agregador == "S") {
      insertIfNotExistAgregador(op, idTransaccion)
    }
    
    if (op.datos_bsa.isDefined) {
      insertDatosBsa(op, idTransaccion)
    }

    if (op.datos_spv.isDefined)
      insertDatosSPV(op, idTransaccion)

    if (op.datos_gds.isDefined) {
      insertDatosGDS(op, idTransaccion)
    }

    if (site.hashConfiguration.useHash && site.hashConfiguration.firstHashDate.isEmpty)
      updateFirstHashDate(op, site)

    distribuida match {
      case Some("F") => {
        logger.debug("Insert Transaccion xref - transaccion_operacion_xref")
        transaccionOXRReused(op.nro_operacion.get)
        insertTransaccionOperacionXref(idTransaccion, op.nro_operacion.get, chargeId, oer3)
      }
      case None => {
        logger.debug("Insert Transaccion xref - transaccion_operacion_xref")
        transaccionOXRReused(op.nro_operacion.get)
        insertTransaccionOperacionXref(idTransaccion, op.nro_operacion.get, chargeId, oer3)

      }
      case _ => {
        logger.info("Insert Sub Transaccion xref - subpayment_transaccion_operacion_xref")
        insertSubTransaccionOperacionXref(op.charge_id.get, chargeId, idTransaccion, op.nro_operacion.get)
      }
    }

    idTransaccion
  }

  private def transaccionOXRReused(nroOperation: String)(implicit jdbcTemplate: JdbcTemplate) {
    getTransaccionOperacionXrefNotUsed(nroOperation).map(cId => {
      val sql = "UPDATE transaccion_operacion_xref SET reused = true WHERE charge_id = ?"
      jdbcTemplate.update(sql, cId.asInstanceOf[Object])
    })
  }

  private def getTransaccionOperacionXrefNotUsed(operationNro: String) = {
    evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForList(
        """select charge_id from transaccion_operacion_xref where operation_id = ?""".stripMargin,
        Array(operationNro.asInstanceOf[Object]), classOf[Long])
    }.asScala.toList
  }

  private def insertSubTransaccionOperacionXref(subpayment_id: Long, charge_id: Long, transaccion_id: Number, operation_id: String)(implicit jdbcTemplate: JdbcTemplate) = {

    var map = new util.HashMap[String, Any]()
    map.put("subpayment_id", subpayment_id)
    map.put("charge_id", charge_id)
    map.put("transaccion_id", transaccion_id)
    map.put("operation_id", StringUtil.safeString(operation_id, 45, Some(logger)))

    val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("subpayment_transaccion_operacion_xref")
    insertStatement.execute(map)
  }

  private def insertTransaccion(oer: OperationExecutionResponse, cuenta: Cuenta, tarjetaEsNacional: Boolean, distribuida: Option[String], estado: Int)(implicit jdbcTemplate: JdbcTemplate): Long = {

    val op = oer.operationResource.get
    val nrotarjetaencriptado = if (op.datos_medio_pago.get.nro_tarjeta.isDefined) encryptionService.encriptar(op.datos_medio_pago.get.nro_tarjeta.get) else ""
    val esautorizadaendospasos = if (cuenta.autorizaEnDosPasos) "S" else "N"
    val plann = if (cuenta.estaHabilitadaParaOperarConPlanN) "S" else "N"
    val estarjetanacional = if (tarjetaEsNacional) "S" else "N"
    val vencimiento = op.datos_medio_pago map { dmp =>
      if (dmp.expiration_year.isDefined && dmp.expiration_month.isDefined)
        dmp.expiration_year.get + dmp.expiration_month.get
      else
        None
    }
    val nrotarj = if (op.datos_medio_pago.get.nro_tarjeta.isDefined) op.datos_medio_pago.get.nro_tarjeta.get.takeRight(4) else ""
    //FIXME Se utiliza java.math.BigDecimal ya que al utilizar scala.math.BigDecimal se pierde la precision al igual que usar un Double
    val amount = new BigDecimal(op.monto.get).divide(new BigDecimal(100))
    val reazonId = oer.operationResource.flatMap(_.fraud_detection.flatMap(_.status.flatMap(state => {
      if (state.decision.status.id == FDBlack().status.id) {
        Some(state.reason_code)
      } else None
    }))).getOrElse(
      oer.cardErrorCode.map(cd => if(cd.code == 12035) cd.code else -1).getOrElse(-1)) //TODO Revisar y mejorar la condicion

    //val fecha = new Timestamp(System.currentTimeMillis())
    val fecha = op.creation_date.getOrElse(throw new RuntimeException("No se definio fecha de creacion de la operacion"))

    val mapa: Map[String, Any] = Map(
      "idsite" -> op.datos_site.get.site_id.get,
      "monto" -> amount,
      "cantcuotas" -> op.cuotas.getOrElse(1),
      "idmediopago" -> op.datos_medio_pago.get.medio_de_pago.get,
      "titular" -> StringUtil.safeString(op.datos_medio_pago.get.nombre_en_tarjeta.orNull, 60, Some(logger)),
      "idtipodoc" -> op.datos_titular.flatMap(_.tipo_doc).orNull,
      "nrodoc" -> StringUtil.safeString(op.datos_titular.flatMap(_.nro_doc).orNull, 20, Some(logger)) ,
      "mailusu" -> StringUtil.safeString(op.datos_titular.flatMap(_.email_cliente).getOrElse(""), 80, Some(logger)),
      "nrotarjetaencriptado" -> nrotarjetaencriptado,
      "nrotarj" -> nrotarj, //Últimos 4 números
      "venctarj" -> vencimiento.orNull,
      //      "codaut"-> respuesta.flatMap(_.cod_aut).orNull,
      "urldinamica" -> StringUtil.safeString(op.datos_site.get.url_dinamica.orNull, 500, Some(logger)),
      "idbackend" -> cuenta.idBackend,
      "esautorizadaendospasos" -> esautorizadaendospasos,
      "idprotocolo" -> cuenta.idProtocolo,
      "plann" -> plann,
      "enc_keyid" -> encryptionKeyIndex,
      "nuevasops" -> "N", // TODO VERIFICAR CON @Fernando
      "idtipooperacion" -> 0,
      "idtransaccionsite" -> StringUtil.safeString(op.nro_operacion.get, 40, Some(logger)),
      "utilizaautenticacionexternar" -> cuenta.utilizaAutenticacionExterna,
      "idmodalidad" -> "N",
      "idestado" -> estado,
      "idmotivo" -> reazonId,
      //      "resultadovalidaciondomicilio"-> respuesta.flatMap(_.validacion_domicilio).orNull,
      "celular" -> "",
      "pin" -> "",
      "montooriginal" -> amount,
      "iddomicilio" -> 0,
      "terminal" -> op.datos_medio_pago.flatMap(_.nro_terminal).orNull,
      //      "idopmediopago"-> if(withBlackError) null else respuesta.map(_.idOperacionMedioPago).orNull,
      "idcliente" -> op.datos_spv.flatMap(_.client_id).getOrElse(0),
      "utilizavbv" -> "N",
      "intentos" -> 0,
      "estarjetanacional" -> estarjetanacional,
      "nroticket" -> op.datos_medio_pago.flatMap(_.nro_ticket).orNull,
      "nrotrace" -> op.datos_medio_pago.flatMap(_.nro_trace).orNull,
      "fecha" -> fecha, // TODO
      "fechaoriginal" -> op.creation_date.getOrElse(fecha), // TODO
      "fechainicio" -> op.creation_date.getOrElse(fecha), // TODO
      "ipcomprador" -> op.datos_titular.flatMap(_.ip).orNull,
      "fechavtocuota1" -> op.fechavto_cuota_1.orNull,
      "distribuida" -> distribuida.orNull,
      "idplan" -> op.datos_medio_pago.flatMap(_.id_plan).orNull)

    logger.debug(s"insert spstransac values: " + (mapa.+("venctarj" -> "XXXX")))

    val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("spstransac").usingGeneratedKeyColumns("idtransaccion")
    val idTransaccion = insertStatement.executeAndReturnKey(mapa.asJava)

    idTransaccion.longValue()
  }


  private def updateTransaccionOnReuse(oer: OperationExecutionResponse, cuenta: Cuenta, tarjetaEsNacional: Boolean, distribuida: Option[String], estado: Int)(implicit jdbcTemplate: JdbcTemplate) = {
    val op = oer.operationResource.get
    loadMDC(siteId = Some(op.siteId),
      transactionId = Some(op.id),
      merchantTransactionId = op.nro_operacion,
      referer = op.datos_site.flatMap(_.referer),
      paymentId = op.charge_id.map(_.toString))

    var sqlIdTransaction = """select idtransaccion from spstransac where idtransaccionsite = ? and idsite = ?"""
    distribuida match {
      case Some("C") => sqlIdTransaction = sqlIdTransaction.concat(""" and distribuida = 'C' """)
      case Some("F") => sqlIdTransaction = sqlIdTransaction.concat(""" and distribuida = 'F' """)
      case _ => sqlIdTransaction
    }

    val idTransaccion =
      jdbcTemplate.queryForObject(
        sqlIdTransaction.stripMargin,
        Array(op.nro_operacion.get.asInstanceOf[Object], op.datos_site.flatMap(_.site_id).get.asInstanceOf[Object]), classOf[String])

    logger.info("Update - Reused - Transaction - spstransac")

    val nrotarjetaencriptado = encryptionService.encriptar(op.datos_medio_pago.get.nro_tarjeta.get)
    val esautorizadaendospasos = if (cuenta.autorizaEnDosPasos) "S" else "N"
    val plann = if (cuenta.estaHabilitadaParaOperarConPlanN) "S" else "N"
    val estarjetanacional = if (tarjetaEsNacional) "S" else "N"
    val vencimiento = op.datos_medio_pago map { dmp =>
      dmp.expiration_year.get + dmp.expiration_month.get
    }

    val sql = "UPDATE spstransac " +
      "SET idtipooperacion = ?, idtransaccionsite = ?, monto = ?, " +
      "montofinal = ?, montooriginal = ?, cantcuotas = ?, plann = ?, " +
      " codart = ?, idplan = ?, paramsitio = ?, " +
      "idsite = ?, idmediopago = ?, idbackend = ?, " +
      " idprotocolo = ?, titular = ?, idtipodoc = ?, nrodoc = ?, " +
      "mailusu = ?, nrotarj = ?, nrotarjetaencriptado = ?, " +
      "enc_keyid = ?, venctarj = ?, estarjetanacional = ?, " +
      "codaut = ?, idopbackend = ?, idopmediopago = ?, " +
      "esautorizadaendospasos = ?, intentos = ?, utilizavbv = ?, " +
      "fechainicio = ?, " +
      "nroticket = ?, nrotrace = ?, resultadovalidaciondomicilio = ?, " +
      "sexotitular = ?, " + "celular = ?, pin= ?, diaspagodiferido = ?, " +
      "urldinamica = ?, tipoEncripcion = ? , idbanco = ?, nuevasops = ?, terminal= ?, distribuida= ? " +
      "WHERE idtransaccion = ?";
    //FIXME Se utiliza java.math.BigDecimal ya que al utilizar scala.math.BigDecimal se pierde la precision al igual que usar un Double
    val amount = new BigDecimal(op.monto.get).divide(new BigDecimal(100))
    val sdf = op.datos_site.get.url_dinamica.orNull
    jdbcTemplate.update(sql,
      Integer.valueOf(0),
      op.nro_operacion.get,
      amount,
      amount,
      amount,
      op.cuotas.get.asInstanceOf[Object],
      if (cuenta.estaHabilitadaParaOperarConPlanN) "S" else "N",
      null,
      op.datos_medio_pago.get.id_plan.orNull,
      null,
      op.datos_site.get.site_id.get,
      op.datos_medio_pago.get.medio_de_pago.get.asInstanceOf[Object],
      cuenta.idBackend,
      cuenta.idProtocolo.asInstanceOf[Object],
      op.datos_medio_pago.get.nombre_en_tarjeta.orNull,
      op.datos_titular.flatMap(_.tipo_doc).getOrElse(null).asInstanceOf[Object],
      op.datos_titular.flatMap(_.nro_doc).map(_ take 20).orNull,
      op.datos_titular.flatMap(_.email_cliente).getOrElse(""),
      op.datos_medio_pago.get.nro_tarjeta.get.takeRight(4), //Últimos 4 números
      nrotarjetaencriptado,
      encryptionKeyIndex.asInstanceOf[Object],
      vencimiento.get,
      estarjetanacional,
      null,
      null,
      null,
      esautorizadaendospasos,
      op.retries.getOrElse(0).asInstanceOf[Object],
      "N",
      new Timestamp(System.currentTimeMillis()),
      op.datos_medio_pago.flatMap(_.nro_ticket).orNull,
      op.datos_medio_pago.flatMap(_.nro_trace).orNull,
      null,
      null,
      "",
      "",
      null,
      op.datos_site.get.url_dinamica.orNull,
      null,
      null,
      "N",
      op.datos_medio_pago.flatMap(_.nro_terminal).orNull,
      distribuida.orNull,
      idTransaccion.asInstanceOf[Object])

    idTransaccion.toLong

  }


  def updateMainTransaccion(chargeId: Long, op: OperationResource, site: Site, distribuida: Option[String], estados: List[Int], oer: OperationExecutionResponse, respuesta: Option[TransactionResponse]) = {
    loadMDC(siteId = Some(op.siteId),
      transactionId = Some(op.id),
      merchantTransactionId = op.nro_operacion,
      referer = op.datos_site.flatMap(_.referer),
      paymentId = op.charge_id.map(_.toString))

    val idTransaccion = {
      evalWithJdbcTemplate { jdbcT =>
        jdbcT.queryForObject(
          """select transaccion_id from transaccion_operacion_xref where charge_id = ? """.stripMargin,
          Array(chargeId.asInstanceOf[Object]), classOf[String])
      }
    }

    evalTxWithJdbcTemplate { implicit jdbcTemplate =>

      if (op.datos_offline.isDefined && respuesta.flatMap(_.barcode).isDefined) {
        insertIfNotExistOfflinePayment(op, respuesta.flatMap(_.barcode).getOrElse(""), idTransaccion)
      }

      val motivoAdicional = respuesta.flatMap(_.motivoAdicional).orNull
      insertIfNotExistDatosAdicionales(op, motivoAdicional, idTransaccion)

      logger.info("Update Transaccion - spstransac")
      val updatedOer = updateTransaccion(chargeId, op, site, distribuida, estados, oer, respuesta, idTransaccion)

      distribuida match {
        case Some("F") => {
          logger.debug("Update Transaccion xref - transaccion_operacion_xref")
          updateTransaccionXref(chargeId, updatedOer)
        }
        case None => {
          logger.debug("Update Transaccion xref - transaccion_operacion_xref")
          updateTransaccionXref(chargeId, updatedOer)
        }
        case _ => {}
      }
    } // jdbcTemplate
  }

  private def updateFirstHashDate(op: OperationResource, site: Site)(implicit jdbcTemplate: JdbcTemplate) = {
    val sql = "UPDATE spssites SET fechausohash = ? WHERE idsite = ?"
    val date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(op.creation_date.get)
    jdbcTemplate.setDataSource(db.dataSource)
    jdbcTemplate.update(sql, date.asInstanceOf[Object], site.id.asInstanceOf[Object])
  }


  private def insertAgroInstallmentData(installmentData: InstallmentData, periodicity: Int, idTransaccion: Long) = {
    var map = new util.HashMap[String, Any]()
    map.put("idtransaccion", idTransaccion)
    map.put("idcuota", installmentData.id)
    map.put("fecha", installmentData.date)
    map.put("importe", installmentData.amount.toDouble/100)
    map.put("periodicidad", periodicity)

    val insertStatement = new SimpleJdbcInsert(db.dataSource).withTableName("cuotasagro")
    insertStatement.execute(map)
  }

  def updateSubTransaccion(chargeId: Long, op: OperationResource, site: Site, distribuida: Option[String], estados: List[Int], oer: OperationExecutionResponse, respuesta: Option[TransactionResponse]) = {
    loadMDC(siteId = Some(op.siteId),
      transactionId = Some(op.id),
      merchantTransactionId = op.nro_operacion,
      referer = op.datos_site.flatMap(_.referer),
      paymentId = op.charge_id.map(_.toString))

    val idTransaccion = {
      evalWithJdbcTemplate { jdbcTemplate =>
        jdbcTemplate.queryForObject(
          """select transaccion_id from subpayment_transaccion_operacion_xref where subpayment_id = ? """.stripMargin,
          Array(chargeId.asInstanceOf[Object]), classOf[String])
      }
    }

    logger.debug("Update Sub Transaccion - spstransac")
    evalTxWithJdbcTemplate { implicit jdbcTemplate =>
      updateTransaccion(chargeId, op, site, distribuida, estados, oer, respuesta, idTransaccion)
    }

  }

  def updateTransaccion(chargeId: Long, op: OperationResource, site: Site, distribuida: Option[String], estados: List[Int], oer: OperationExecutionResponse, respuesta: Option[TransactionResponse], idTransaccion: String)(implicit jdbcTemplate: JdbcTemplate) = {
    val op = oer.operationResource.get // TODO
    val idProtocolo = paymentMethodService.getProtocolId(op)

    var sqlUpd = """UPDATE spstransac SET idestado = ?, idmotivo = ?, fecha = ? """
    var types = Array(Types.VARCHAR, Types.VARCHAR, Types.VARCHAR)
    var params = ArrayBuffer[Object]()
    params.+=(Integer.valueOf(estados.last).asInstanceOf[Object],
      Integer.valueOf(respuesta.map(tr => getMotivo(tr.idMotivo)).getOrElse(-1)).asInstanceOf[Object],
      new Timestamp(System.currentTimeMillis()))
    respuesta.map(_ => {
      sqlUpd ++= """, codaut = ?, resultadovalidaciondomicilio = ?, terminal = ?, idopmediopago = ?, nroticket = ?, nrotrace = ? """
      types ++= Array(Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR)
      params.+=(respuesta.flatMap(_.cod_aut).orNull,
        respuesta.flatMap(_.validacion_domicilio).orNull,
        respuesta.flatMap(_.terminal).orNull,
        respuesta.map(_.idOperacionMedioPago).orNull,
        respuesta.flatMap(_.nro_ticket).orNull,
        respuesta.flatMap(_.nro_trace).orNull
      )
    })
    sqlUpd ++= """WHERE idtransaccion = ?"""
    types ++= Array(Types.VARCHAR)
    params.+=(idTransaccion)

    logger.debug(s"$sqlUpd :: idestado=${Integer.valueOf(estados.last)} :: idTransaccion=$idTransaccion")

    jdbcTemplate.update(sqlUpd, params.toArray, types)

    estados.foreach { estado =>
      TransactionState.apply(estado) match {
        case RechazadaDatosInvalidos() | Rechazada() => insertEstadoHistorico(Some(idTransaccion.toLong), None, idProtocolo, estado, None, Some(System.currentTimeMillis()), respuesta.map(_.idMotivo))
        case other => insertEstadoHistorico(Some(idTransaccion.toLong), None, idProtocolo, estado, None, Some(System.currentTimeMillis()), None)
      }
    }

    val op3 = op.copy(idTransaccion = Some(idTransaccion.toString()), datos_medio_pago = Some(op.datos_medio_pago.get.copy(cod_autorizacion = Some(oer.authorizationCode), id_operacion_medio_pago = respuesta.map(_.idOperacionMedioPago))))
    oer.copy(operationResource = Some(op3))
  }

  def updateSimpleState(chargeId: Long, transactionState: TransactionState) = {
    logger.info(s"Event recieved - update tx to state ${transactionState.id} for chargeId $chargeId")
    val idTransaccion = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForObject(
        """select transaccion_id from transaccion_operacion_xref where charge_id = ? """.stripMargin,
        Array(chargeId.asInstanceOf[Object]), classOf[String])
    }

    val sqlUpd = "UPDATE spstransac SET idestado = ? WHERE idtransaccion = ?"
    logger.debug(s"$sqlUpd :: idestado=${transactionState.id} :: idTransaccion=$idTransaccion")

    evalTxWithJdbcTemplate { implicit jdbcTemplate =>
      jdbcTemplate.update(sqlUpd,
        Integer.valueOf(transactionState.id),
        idTransaccion)
      insertEstadoHistorico(Some(idTransaccion.toLong), None, 21, transactionState.id, None, Some(System.currentTimeMillis()), None)
    }
  }

  def insertSubTransaction(idTransaccion: Number, idSubTransaccion: Number, porcentaje: Double, importe: scala.math.BigDecimal)(implicit jdbcTemplate: JdbcTemplate) = {

    val params: Map[String, Any] = Map(
      "idtransaccion" -> idTransaccion,
      "idsubtransaccion" -> idSubTransaccion,
      "porcentaje" -> porcentaje,
      "importe" -> importe
    )

    val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("spstransac_subtransac")
    insertStatement.execute(params.asJava)
  }
  
   private def insertIfNotExistDomicilioTitular(op: OperationResource, idTransaccion: Number)(implicit jdbcTemplate: JdbcTemplate) = {
    val calle = StringUtil.safeString(op.datos_titular.flatMap(_.calle).orNull, 100, Some(logger))
    val nro   = op.datos_titular.flatMap(_.nro_puerta).map(_.toString).getOrElse(null)

    if(op.retries.getOrElse(0) > 0){
      val sql = """UPDATE domiciliotitular SET calle=?, nro=? WHERE idtransaccion=?"""

      jdbcTemplate.update(sql, calle.asInstanceOf[Object], nro.asInstanceOf[Object], idTransaccion.asInstanceOf[Object])
    }else{     
      var map = new util.HashMap[String, Any]()
      map.put("calle", calle)
      map.put("nro", nro)
      map.put("idtransaccion", idTransaccion)

      val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("domiciliotitular")
      insertStatement.execute(map)
    }
  }
   
  private def insertIfNotExistNomEstablecimiento(op: OperationResource, idTransaccion: Number)(implicit jdbcTemplate: JdbcTemplate) = {
	  val nomestablec = StringUtil.safeString(op.datos_medio_pago.flatMap(_.establishment_name).getOrElse(""), 25, Some(logger))

    if(op.retries.getOrElse(0) > 0){
      updateEstablishmentName(idTransaccion, nomestablec)
    }else{
      var map = new util.HashMap[String, Any]()
      map.put("idtransaccion", idTransaccion)
      map.put("nomestablec", nomestablec)
  
      val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("nomestablecimiento")
      insertStatement.execute(map)
    }
  }
  
  //Los reintentos para nomestablecimiento, puede que en el primer intento no haya hecho el insert
  private def updateEstablishmentName(idtransaccion: Number, nomestablec: String)(implicit jdbcTemplate: JdbcTemplate) = {    
      val sql = """INSERT INTO nomestablecimiento (idtransaccion, nomestablec)
              VALUES( ?, ?)
              ON DUPLICATE KEY UPDATE idtransaccion = VALUES(idtransaccion), 
              nomestablec = VALUES(nomestablec)"""
      jdbcTemplate.update(sql, idtransaccion.asInstanceOf[Object], nomestablec.asInstanceOf[Object]) 
  }
  
  private def insertIfNotExistAgregador(op: OperationResource, idTransaccion: Number)(implicit jdbcTemplate: JdbcTemplate) = {
    val aindicador = StringUtil.safeString(op.aggregate_data.flatMap(_.indicator).orNull, 1, Some(logger))
    val adocumento = StringUtil.safeString(op.aggregate_data.flatMap(_.identification_number).orNull, 11, Some(logger))
    val afactpagar = StringUtil.safeString(op.aggregate_data.flatMap(_.bill_to_pay).getOrElse(""), 12, Some(logger))
    val afactdevol = StringUtil.safeString(op.aggregate_data.flatMap(_.bill_to_refund).getOrElse(""), 12, Some(logger))
    val anombrecom = StringUtil.safeString(op.aggregate_data.flatMap(_.merchant_name).getOrElse(""), 100, Some(logger))
    val adomiciliocomercio = StringUtil.safeString(op.aggregate_data.flatMap(_.street).getOrElse(""), 20, Some(logger))
    val anropuerta = StringUtil.safeString(op.aggregate_data.flatMap(_.number).getOrElse(""), 6, Some(logger))
    val acodpostal = StringUtil.safeString(op.aggregate_data.flatMap(_.postal_code).getOrElse(""), 8, Some(logger))
    val arubro = StringUtil.safeString(op.aggregate_data.flatMap(_.category).getOrElse(""), 5, Some(logger))
    val acodcanal = StringUtil.safeString(op.aggregate_data.flatMap(_.channel).getOrElse(""), 3, Some(logger))
    val acodgeografico = StringUtil.safeString(op.aggregate_data.flatMap(_.geographic_code).getOrElse(""), 5, Some(logger))
    val aciudad = StringUtil.safeString(op.aggregate_data.flatMap(_.city).getOrElse(""), 20, Some(logger))
    val aproducto = "" // No se usa mas
    val aidcomercio = StringUtil.safeString(op.aggregate_data.flatMap(_.merchant_id).getOrElse(""), 17, Some(logger))
    val aprovincia = StringUtil.safeString(op.aggregate_data.flatMap(_.province).getOrElse(""), 1, Some(logger))
    val apais = StringUtil.safeString(op.aggregate_data.flatMap(_.country).getOrElse(""), 3, Some(logger))
    val aemailcomercio = StringUtil.safeString(op.aggregate_data.flatMap(_.merchant_email).getOrElse(""), 40, Some(logger))
    val atelefonocomercio = StringUtil.safeString(op.aggregate_data.flatMap(_.merchant_phone).getOrElse(""), 20, Some(logger))

    if(op.retries.getOrElse(0) > 0){
        updateAggregator(idTransaccion, aindicador, adocumento, afactpagar, afactdevol, anombrecom, 
            adomiciliocomercio, anropuerta, acodpostal, arubro, acodcanal, acodgeografico, aciudad, 
            aproducto, aidcomercio, aprovincia, apais, aemailcomercio, atelefonocomercio)
    }else{     
        var map = new util.HashMap[String, Any]()
        map.put("idtransaccion", idTransaccion)
        map.put("aindicador", aindicador) 
        map.put("adocumento", adocumento)
        map.put("afactpagar", afactpagar)
        map.put("afactdevol", afactdevol)
        map.put("anombrecom", anombrecom)
        map.put("adomiciliocomercio", adomiciliocomercio)
        map.put("anropuerta", anropuerta)
        map.put("acodpostal", acodpostal)
        map.put("arubro", arubro)
        map.put("acodcanal", acodcanal) 
        map.put("acodgeografico", acodgeografico)
        map.put("aciudad", aciudad)
        map.put("aproducto", "") // No se usa mas
        map.put("aidcomercio", aidcomercio)
        map.put("aprovincia", aprovincia)
        map.put("apais", apais)
        map.put("aemailcomercio", aemailcomercio) 
        map.put("atelefonocomercio", atelefonocomercio)
      val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("spstransac_agregador")
      insertStatement.execute(map)
    }
  }
  
  //Los reintentos para spstransac_agregador, puede que en el primer intento no haya hecho el insert
  private def updateAggregator(idtransaccion: Number, aindicador: String, adocumento: String, afactpagar: String, afactdevol: String,  
      anombrecom: String, adomiciliocomercio: String, 
      anropuerta: String, acodpostal: String, arubro: String, acodcanal: String, acodgeografico: String,
      aciudad: String, aproducto: String, aidcomercio: String, aprovincia: String,
      apais: String, aemailcomercio: String, atelefonocomercio: String)(implicit jdbcTemplate: JdbcTemplate) = {    
    
      val sql = """INSERT INTO spstransac_agregador (idtransaccion, aindicador, adocumento, afactpagar, afactdevol, anombrecom, 
        adomiciliocomercio, anropuerta, acodpostal, arubro, acodcanal, acodgeografico, aciudad, aproducto, aidcomercio,
        aprovincia, apais, aemailcomercio, atelefonocomercio)
              VALUES( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
              ON DUPLICATE KEY UPDATE idtransaccion = VALUES(idtransaccion), 
              aindicador = VALUES(aindicador),
              adocumento = VALUES(adocumento),
              afactpagar = VALUES(afactpagar),
              afactdevol = VALUES(afactdevol),
              anombrecom = VALUES(anombrecom),
              adomiciliocomercio = VALUES(adomiciliocomercio),
              anropuerta = VALUES(anropuerta),
              acodpostal = VALUES(acodpostal),
              arubro = VALUES(arubro),
              acodcanal = VALUES(acodcanal),
              acodgeografico = VALUES(acodgeografico),
              aciudad = VALUES(aciudad),
              aproducto = VALUES(aproducto),
              aidcomercio = VALUES(aidcomercio),
              aprovincia = VALUES(aprovincia),
              apais = VALUES(apais),
              aemailcomercio = VALUES(aemailcomercio),
              atelefonocomercio = VALUES(atelefonocomercio)"""
      jdbcTemplate.update(sql,
          idtransaccion.asInstanceOf[Object],
          aindicador.asInstanceOf[Object],
          adocumento.asInstanceOf[Object],
          afactpagar.asInstanceOf[Object],
          afactdevol.asInstanceOf[Object],
          anombrecom.asInstanceOf[Object],
          adomiciliocomercio.asInstanceOf[Object],
          anropuerta.asInstanceOf[Object],
          acodpostal.asInstanceOf[Object],
          arubro.asInstanceOf[Object],
          acodcanal.asInstanceOf[Object],
          acodgeografico.asInstanceOf[Object],
          aciudad.asInstanceOf[Object],
          aproducto.asInstanceOf[Object],
          aidcomercio.asInstanceOf[Object],
          aprovincia.asInstanceOf[Object],
          apais.asInstanceOf[Object],
          aemailcomercio.asInstanceOf[Object],
          atelefonocomercio.asInstanceOf[Object])
  }

  private def insertIfNotExistDatosAdicionales(op: OperationResource, motivoAdicional: String, idTransaccion: String)(implicit jdbcTemplate: JdbcTemplate) = {
    val motivoadicional = StringUtil.safeString(motivoAdicional, 200, Some(logger))
    
    if(op.retries.getOrElse(0) > 0){
      updateAdditionalReason(idTransaccion, motivoadicional)
    }else{     
      var map = new util.HashMap[String, Any]()
      map.put("idtransaccion", idTransaccion)
      map.put("motivoadicional", motivoadicional)
  
      val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("spstransac_motivoadicional")
      insertStatement.execute(map)
    }
  }
  
  //Los reintentos para motivoadicional, puede que en el primer intento no haya hecho el insert
  private def updateAdditionalReason(idtransaccion: String, motivoadicional: String)(implicit jdbcTemplate: JdbcTemplate) = {    
      val sql = """INSERT INTO spstransac_motivoadicional (idtransaccion, motivoadicional)
              VALUES( ?, ?)
              ON DUPLICATE KEY UPDATE idtransaccion = VALUES(idtransaccion), 
              motivoadicional = VALUES(motivoadicional)"""
      jdbcTemplate.update(sql, idtransaccion.asInstanceOf[Object], motivoadicional.asInstanceOf[Object]) 
  }
  
  private def insertIfNotExistOfflinePayment(op: OperationResource, barCode: String, idTransaccion: String)(implicit jdbcTemplate: JdbcTemplate) = {
    val barcode = StringUtil.safeString(barCode, 100, Some(logger))

    if(op.retries.getOrElse(0) > 0){
      updateOfflinePayment(idTransaccion, barcode)
    }else{     
      var map = new util.HashMap[String, Any]()
      map.put("idtransaccion", idTransaccion)
      map.put("codigo", barcode)
      val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("pagooffline")
      insertStatement.execute(map)
    }
  }
  
  //Los reintentos para pagooffline, puede que en el primer intento no haya hecho el insert
  private def updateOfflinePayment(idtransaccion: String, barcode: String)(implicit jdbcTemplate: JdbcTemplate) = {    
      val sql = """INSERT INTO pagooffline (idtransaccion, barcode)
              VALUES( ?, ?)
              ON DUPLICATE KEY UPDATE idtransaccion = VALUES(idtransaccion), 
              barcode = VALUES(barcode)"""
      jdbcTemplate.update(sql, idtransaccion.asInstanceOf[Object], barcode.asInstanceOf[Object]) 
  }
  
  //TODO:revisar jdbc
  private def insertDatosBsa(op: OperationResource, idTransaccion: Number)(implicit jdbcTemplate: JdbcTemplate) = {

    val sql = "INSERT INTO spstransac_bsa (idtransaccion, public_token, issue_date, public_request_key, volatile_encrypted_data, " +
      "flag_tokenization, flag_security_code, flag_selector_key, flag_pei, private_token, aditionalcrypt, hexsum, counter) " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    var map = new util.HashMap[String, Any]()
    map.put("idtransaccion", idTransaccion)
    map.put("public_token", op.datos_bsa.flatMap(_.public_token).orNull)
    map.put("issue_date", op.datos_bsa.flatMap(_.issue_date).orNull)
    map.put("public_request_key", op.datos_bsa.flatMap(_.public_request_key).orNull)
    map.put("volatile_encrypted_data", op.datos_bsa.flatMap(_.volatile_encrypted_data).orNull)
    map.put("flag_tokenization", op.datos_bsa.flatMap(_.flag_tokenization).orNull)
    map.put("flag_security_code", op.datos_bsa.flatMap(_.flag_security_code).orNull)
    map.put("flag_selector_key", op.datos_bsa.flatMap(_.flag_selector_key).orNull)
    map.put("flag_pei", op.datos_bsa.flatMap(_.flag_pei).orNull)
    map.put("private_token", op.datos_bsa.flatMap(_.private_token).orNull)
    map.put("aditionalcrypt", op.datos_bsa.flatMap(_.aditionalcrypt).orNull)
    map.put("hexsum", op.datos_bsa.flatMap(_.hexsum).orNull)
    map.put("counter", op.datos_bsa.flatMap(_.counter).orNull)

    val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("spstransac_bsa")
    insertStatement.execute(map)
  }

  private def insertDatosSPV(op: OperationResource, idTransaccion: Number)(implicit jdbcTemplate: JdbcTemplate) = {

    val sql = "INSERT INTO datosspv (idtransaccion, codcuota, idenspv, cantcuotasspv) VALUES (?, ?, ?, ?)"

    var map = new util.HashMap[String, Any]()
    map.put("idtransaccion", idTransaccion)
    map.put("codcuota", op.datos_spv.flatMap(_.installment.code).orNull)
    map.put("idenspv", op.datos_spv.flatMap(_.identificator).orNull)
    map.put("cantcuotasspv", op.datos_spv.flatMap(_.installment.quantity).orNull)

    val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("datosspv")
    insertStatement.execute(map)
  }

  private def insertDatosGDS(op: OperationResource, idTransaccion: Number)(implicit jdbcTemplate: JdbcTemplate) = {

    val sql = "INSERT INTO spstransac_agrupador (idtransaccion, idsitemerchant, idsitelocator, iatacode) VALUES (?, ?, ?, ?)"

    var map = new util.HashMap[String, Any]()
    map.put("idtransaccion", idTransaccion)
    map.put("idsitemerchant", op.datos_gds.flatMap(_.id_merchant).orNull)
    map.put("idsitelocator", op.datos_gds.map(_.nro_location).orNull)
    map.put("iatacode", op.datos_gds.map(_.iata_code).orNull)

    val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("spstransac_agrupador")
    insertStatement.execute(map)
  }

  def doInsertEstadoHistorico(oidTransaccion: Option[Long], chargeId: Option[Long], protocolId: Int, state: Int, distribuida: Option[String], changeStateDate: Option[Long], reasonCode: Option[Int]) = {
    logger.debug(s"doInsertEstadoHistorico txId: $oidTransaccion state: $state changeStateDate: $changeStateDate")
    evalTxWithJdbcTemplate { implicit jdbcTemplate =>
      insertEstadoHistorico(oidTransaccion, chargeId, protocolId, state, distribuida, changeStateDate, reasonCode)
    }
  }

  def insertEstadoHistorico(oidTransaccion: Option[Long], chargeId: Option[Long], protocolId: Int, state: Int, distribuida: Option[String], changeStateDate: Option[Long], reasonCode: Option[Int])(implicit jdbcTemplate: JdbcTemplate) = {
    val transactionId = oidTransaccion.getOrElse {
      val cId = chargeId.getOrElse {
        logger.error("cId not existed")
        throw new Exception // TODO)
      }

      distribuida match {
        case Some("F") | None => {
          evalWithJdbcTemplate { jdbcT =>
            jdbcT.queryForObject(
              """select transaccion_id from transaccion_operacion_xref where charge_id = ? """.stripMargin,
              Array(cId.asInstanceOf[Object]), classOf[Long])
          }
        }
        case _ => {
          evalWithJdbcTemplate { jdbcT =>
            jdbcT.queryForObject(
              """select transaccion_id from subpayment_transaccion_operacion_xref where subpayment_id = ?""".stripMargin,
              Array(cId.asInstanceOf[Object]), classOf[Long])
          }
        }
      }
    }

    loadMDC(transactionId = Some(transactionId.toString))
    logger.debug(s"Insert state ${state} - spsestadoshist")
    val mapa: Map[String, Any] = Map(
      "idestado" -> state,
      "idmotivo" -> reasonCode.getOrElse(-1),
      "fecha" -> new Timestamp(changeStateDate.getOrElse(System.currentTimeMillis())),
      "idprotocolo" -> protocolId,
      "idtipooperacion" -> 0,
      "idtransaccion" -> transactionId.toString)

    val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("spsestadoshist")
    insertStatement.execute(mapa.asJava)
  }

  
  def insertTransCybersource(resource: OperationResource, csr: CyberSourceResponse) = {

    loadMDC(siteId = Some(resource.siteId),
      transactionId = Some(resource.id),
      merchantTransactionId = resource.nro_operacion,
      referer = resource.datos_site.flatMap(_.referer),
      paymentId = resource.charge_id.map(_.toString))

    val chargeId = resource.charge_id.getOrElse {
      logger.error("charge_id not existed")
      throw new Exception // TODO)
    }

    val txId = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForObject(
        """select transaccion_id from transaccion_operacion_xref where charge_id = ? """.stripMargin,
        Array(chargeId.asInstanceOf[Object]), classOf[Long])
    }

    val status = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForObject(
        """select idestado from spstransac where idtransaccion = ?""".stripMargin, Array(txId.asInstanceOf[Object]), classOf[String])
    }

    def colores = Map("green" -> "VERDE", "yellow" -> "AMARILLO", "red" -> "ROJO", "blue" -> "AZUL", "black" -> "NEGRO")

    var map = new util.HashMap[String, Any]()
    val idtransaccion = txId
    val resultadocs = colores.get(csr.decision.toString).get
    val reasoncode = csr.reason_code

    val estadofinaltransaccion = status
    val requestid = csr.request_id.getOrElse(null)

    val pendienteanulacion = "N"

    val reviewnotificacionrequerida = "N"
    val reviewnotificacionintentos = 0
    // map.put("reviewnotificaciontimestamp", csr.reason_code)


    evalTxWithJdbcTemplate { implicit jdbcTemplate =>
      //Si existe lo remuevo. Reutilizacion de operacion
      if(resource.retries.getOrElse(0) > 0){
        updateTransCybersource(idtransaccion, resultadocs, reasoncode, estadofinaltransaccion, 
            requestid, pendienteanulacion, reviewnotificacionrequerida, reviewnotificacionintentos)
      }else{
        var map = new util.HashMap[String, Any]()
        map.put("idtransaccion", idtransaccion)
        map.put("resultadocs", resultadocs)
        map.put("reasoncode", reasoncode)
    
        map.put("estadofinaltransaccion", estadofinaltransaccion)
        map.put("requestid", requestid)
    
        map.put("pendienteanulacion", pendienteanulacion)
    
        map.put("reviewnotificacionrequerida",reviewnotificacionrequerida)
        map.put("reviewnotificacionintentos", reviewnotificacionintentos)
        // map.put("reviewnotificaciontimestamp", csr.reason_code)
        logger.debug("Insert Transaccion Cybersource - spstransac_cybersource")
        val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("spstransac_cybersource")
        insertStatement.execute(map)
      }

    }

  }

  //Los reintentos con cs, puede que en el primer intento no haya hecho el insert
  private def updateTransCybersource(idtransaccion: Long, resultadocs: String, reasoncode: Int,
      estadofinaltransaccion: String, requestid: String, pendienteanulacion: String,
      reviewnotificacionrequerida: String, reviewnotificacionintentos: Int)(implicit jdbcTemplate: JdbcTemplate) = {    
      val sql = """INSERT INTO spstransac_cybersource (idtransaccion, resultadocs, reasoncode, estadofinaltransaccion, 
              requestid, pendienteanulacion, reviewnotificacionrequerida, reviewnotificacionintentos)
              VALUES( ?, ?, ?, ?, ?, ?, ?, ?)
              ON DUPLICATE KEY UPDATE idtransaccion = VALUES(idtransaccion), 
              resultadocs = VALUES(resultadocs), 
              reasoncode = VALUES(reasoncode), 
              estadofinaltransaccion = VALUES(estadofinaltransaccion), 
              requestid = VALUES(requestid), 
              pendienteanulacion = VALUES(pendienteanulacion), 
              reviewnotificacionrequerida = VALUES(reviewnotificacionrequerida), 
              reviewnotificacionintentos = VALUES(reviewnotificacionintentos)"""
      jdbcTemplate.update(sql, idtransaccion.asInstanceOf[Object], 
          resultadocs.asInstanceOf[Object],
          reasoncode.asInstanceOf[Object],
          estadofinaltransaccion.asInstanceOf[Object],
          requestid.asInstanceOf[Object],
          pendienteanulacion.asInstanceOf[Object],
          reviewnotificacionrequerida.asInstanceOf[Object],
          reviewnotificacionintentos.asInstanceOf[Object]
          )  
  }

  def updateTransCybersource(oer: OperationExecutionResponse, csr: CyberSourceResponse, annulmentPending: Boolean) = {
    val resource = oer.operationResource.getOrElse(throw new Exception("operationResource no encontrado"))
    loadMDC(siteId = Some(resource.siteId),
      transactionId = Some(resource.id),
      merchantTransactionId = resource.nro_operacion,
      referer = resource.datos_site.flatMap(_.referer),
      paymentId = resource.charge_id.map(_.toString))

    val chargeId = resource.charge_id.getOrElse {
      logger.error("charge_id not existed")
      throw new Exception("chargeId no encontrado")
    }

    val txId = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForObject(
        """select transaccion_id from transaccion_operacion_xref where charge_id = ? """.stripMargin,
        Array(chargeId.asInstanceOf[Object]), classOf[Long])
    }

    val status = oer.status

    def colores = Map("green" -> "VERDE", "yellow" -> "AMARILLO", "red" -> "ROJO", "blue" -> "AZUL", "black" -> "NEGRO")

    val colorDecision = colores.get(csr.decision.toString).get

    val aPending = if (annulmentPending) "S" else "N"
    logger.debug("Update Transaccion Cybersource - spstransac_cybersource")
    val sql = """UPDATE spstransac_cybersource SET resultadocs = ?, estadofinaltransaccion = ?, pendienteanulacion = ? WHERE idtransaccion = ?"""
    evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.setDataSource(db.dataSource)
      jdbcTemplate.update(sql, colorDecision.asInstanceOf[Object], status.asInstanceOf[Object], aPending.asInstanceOf[Object], txId.asInstanceOf[Object])
    }
  }

  def insertTransaccionOperacionXref(idTransaccion: Number, nro_operacion: String, chargeId: Long, operation: OperationExecutionResponse)(implicit jdbcTemplate: JdbcTemplate) = {

    var map = new util.HashMap[String, Any]()
    map.put("transaccion_id", idTransaccion)
    map.put("operation_id", StringUtil.safeString(nro_operacion, 45, Some(logger)))
    map.put("charge_id", chargeId)
    map.put("reused", false)

    val operationFixed = operation.copy(operationResource = operation.operationResource.map(opRes =>
      opRes.copy(datos_medio_pago = opRes.datos_medio_pago.map(datosMP =>
        datosMP.copy(
          nro_tarjeta = datosMP.nro_tarjeta.map(nro => encryptionService.encriptarBase64(nro)),
          security_code = None
        )
      ), datos_banda_tarjeta = opRes.datos_banda_tarjeta.map(datos =>
        datos.copy(None, None, datos.input_mode)))
    ))

    val data = Json.toJson(operationFixed)

    map.put("operation_data", data.toString) // TODO A priori encriptar. Luego reemplazar en el modelo nuevo
    val insertStatement = new SimpleJdbcInsert(jdbcTemplate).withTableName("transaccion_operacion_xref")
    insertStatement.execute(map)
  }

  private def updateTransaccionXref(operationData: OperationData, status: TransactionState)(implicit jdbcTemplate: JdbcTemplate): Unit = {
    val operationExecutionResponse = retrieveCharge(operationData.site.id, operationData.chargeId)

    val oer = operationExecutionResponse.getOrElse {
      logger.error("operationExecutionResponse not existed")
      throw new Exception // TODO)
    }
    val oer2 = oer.copy(status = status.id, operationResource = oer.operationResource.map(or =>
      or.copy(monto = operationData.resource.monto,
        sub_transactions = operationData.resource.sub_transactions,
        confirmed = operationData.resource.confirmed)))
    updateTransaccionXref(operationData.chargeId, oer2)
  }

  /**
    * TODO Estaria bueno no hacer esto, sino guardar el historial de eventos
    * De cualquier manera esto es solo para la interoperabilidad
    */
  def updateTransaccionXref(chargeId: Long, operation: OperationExecutionResponse)(implicit jdbcTemplate: JdbcTemplate): Unit = {
    val sql = "UPDATE transaccion_operacion_xref SET operation_data = ? WHERE charge_id = ?"

    /* Se eliminan los datos de la banda para evitar que se persistan
     * Tambien se encriptan nro_tarjeta y se elimina security_code
     * La encriptacion usada es Base64, ya que en el json creado con anterioridad, el campo era String y no Array[Byte] //TODO: Refactorizar en algun momento.
     */
    val operationFixed = operation.copy(operationResource = operation.operationResource.map(opRes =>
      opRes.copy(datos_medio_pago = opRes.datos_medio_pago.map(datosMP =>
        datosMP.copy(
          nro_tarjeta = datosMP.nro_tarjeta.map(nro => encryptionService.encriptarBase64(nro)),
          security_code = None
        )
      ), datos_banda_tarjeta = opRes.datos_banda_tarjeta.map(datos =>
        datos.copy(None, None, datos.input_mode)))
    ))
    
    val data = Json.toJson(operationFixed)
    jdbcTemplate.update(sql, data.toString.asInstanceOf[Object], chargeId.asInstanceOf[Object])
  }

  def updateCSResponseInXref(chargeId: Long, operation: OperationExecutionResponse): Unit = {


    val jsonString = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForMap(
        """select CAST(tox.operation_data as char(10000)) operation_data, t.idestado
             from transaccion_operacion_xref tox
             join spstransac t on t.idtransaccion = tox.transaccion_id
             where
               tox.charge_id = ?""",
        chargeId.toString)
    }

    val oerToStore = convertToOERFromJson(jsonString) match {
      case Success(oer) => oer.copy(cardErrorCode = operation.cardErrorCode, operationResource = oer.operationResource.map(_.copy(fraud_detection = operation.operationResource.flatMap(_.fraud_detection))))
      case Failure(e) => throw e
    }

    val sql = "UPDATE transaccion_operacion_xref SET operation_data = ? WHERE charge_id = ?"
    val data = Json.toJson(oerToStore)
    evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.setDataSource(db.dataSource)
      jdbcTemplate.update(sql, data.toString.asInstanceOf[Object], chargeId.asInstanceOf[Object])
    }
  }

  def retrieveCharge(siteId: String, chargeId: Long): Try[OperationExecutionResponse] = Try {

    val jsonString = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForMap(
        """
            select CAST(tox.operation_data as char(10000)) operation_data, t.idestado
               from transaccion_operacion_xref tox
               join spstransac t on t.idtransaccion = tox.transaccion_id
               where
                 tox.charge_id = ?
                 and (t.idsite=? or t.idsite in (select merchant_id from site_merchant where site_id = ? ))

                                 """, chargeId.toString, siteId, siteId)
    }

    convertToOERFromJson(jsonString).map(eor =>
      eor.copy(operationResource = eor.operationResource.map(opr =>
        opr.copy(datos_medio_pago = opr.datos_medio_pago.map(datMP =>
          datMP.copy(
            nro_tarjeta = datMP.nro_tarjeta.map(nro =>
              if (nro.length > 20) encryptionService.desencriptarBase64(nro) else nro
            ),
            security_code = None
          )
        ))
      ))
    )
  }.flatten

  def retrievePaymentMethods(siteId: String): List[PaymentMethod] = {
    val jsonString = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForList(
        """
          |SELECT DISTINCT
          |  mpt.idmediopago,
          |  mp.descri
          |FROM spsmedpagotienda mpt
          |  INNER JOIN
          |  spsmediopago mp
          |    ON mpt.idmediopago = mp.idmediopago
          |WHERE idsite = ? AND habilitado = "S";
        """.stripMargin, siteId)
    }

    jsonString.listIterator.asScala.toList.map(convertMapToPaymentMethod)
  }

  private def convertMapToPaymentMethod(jsonMap: java.util.Map[String, Object]): PaymentMethod = {
    PaymentMethod(jsonMap.get("idmediopago").toString.toInt, jsonMap.get("descri").toString)
  }

  def retrieveSubPaymentState(subpayment_id: Long): String = {

    val status = evalWithJdbcTemplate { jdbcTemplate =>
      val sql = """select idestado from spstransac st, subpayment_transaccion_operacion_xref stox where st.idtransaccion = stox.transaccion_id and stox.subpayment_id = ?"""
      jdbcTemplate.queryForObject(
        sql.stripMargin, Array(subpayment_id.asInstanceOf[Object]), classOf[String])
    }

    status
  }

  def retrieveTransState(txId: String): String = {
    val jsonString = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForObject(
        """select idestado from spstransac where idtransaccion = ? order by idtransaccion desc limit 1 """.stripMargin, Array(txId.asInstanceOf[Object]), classOf[String])
    }
    jsonString
  }

  def retrieveSubPaymentIdOnCancelRefund(refundId: Long) = {
    val jsonString = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForObject(
        """select subpayment_id from refunds_transaccion_operacion_xref where refund_id = ? """.stripMargin, Array(refundId.asInstanceOf[Object]), classOf[String])
    }
    jsonString
  }

  def listCharges(siteId: String, offset: Int, pageSize: Int, siteOperationId: Option[String], merchantId: Option[String], csYellow: Option[Boolean], dateFrom :Option[String], dateTo :Option[String]): List[OperationExecutionResponse] = {

    var params: Array[Object] = Array(siteId, siteId)
    var types = Array(Types.VARCHAR, Types.VARCHAR)

    var sql =
      """select CAST(o.operation_data as char(10000)) operation_data, t.idestado, cs.resultadocs from transaccion_operacion_xref o, spstransac t
                    LEFT JOIN spstransac_cybersource cs on t.idtransaccion = cs.idtransaccion
                  where o.transaccion_id = t.idtransaccion
                  and (t.idsite=? or t.idsite in (select merchant_id from site_merchant where site_id = ? ))
                  and o.reused is false
                  and operation_data is not null """

    siteOperationId.foreach { siteOperationId =>
      sql = sql + """and idtransaccionsite = ? """
      params = params :+ siteOperationId
      types = types :+ Types.VARCHAR
    }
    merchantId.foreach { merchantId =>
      sql = sql + """and t.idsite = ? """
      params = params :+ merchantId
      types = types :+ Types.VARCHAR
    }
    csYellow.foreach { csYellow =>
      if (csYellow) {
        sql = sql + """and cs.resultadocs like ? """
        params = params :+ "AMARILLO"
        types = types :+ Types.VARCHAR
      }
    }
    dateFrom.foreach { dateFrom =>
      sql = sql + """and t.fechainicio >= STR_TO_DATE(?, '%Y-%m-%d %T') """
      params = params :+ dateFrom + "00:00:00"
      types = types :+ Types.VARCHAR
    }
    dateTo.foreach { dateTo =>
      sql = sql + """and t.fechainicio <= STR_TO_DATE(?, '%Y-%m-%d %T') """
      params = params :+ dateTo + "23:59:59"
      types = types :+ Types.VARCHAR
    }

    sql = sql + """order by o.transaccion_id LIMIT ?, ?"""
    params = params :+ offset.asInstanceOf[Object] :+ pageSize.asInstanceOf[Object]
    types = types :+ Types.INTEGER :+ Types.INTEGER

    val jsonStrings = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForList(sql, params, types)
    }.asScala.toList

    val converted = jsonStrings map convertToOERFromJson

    // No atrapamos el error, que rompa
    val operations = converted.map {
      _.get
    }
    // Consulta de lotes
    val result = operations.map(oer => oer.copy(operationResource = oer.operationResource.map(updateLotes(_))))
    result

  }

  def updateLotes(or: OperationResource): OperationResource = {

    val sql =
      """SELECT tr.subpayment_id as spid, o.idoperacion as ope, o.lote as lote
    				FROM operacion o  
    				RIGHT OUTER JOIN opertransac ot  
    				ON o.idoperacion = ot.idoperacion AND  o.idtipooperacion = ot.idtipooperacion AND  o.idtipooperacion = 2
    				RIGHT OUTER JOIN (SELECT subpayment_id, transaccion_id 
    				FROM subpayment_transaccion_operacion_xref 
    				WHERE operation_id = ?
    				UNION SELECT 0, ?) tr 
    				ON ot.idtransaccion = tr.transaccion_id;"""

    val params: Array[Object] = Array(or.nro_operacion.get, or.idTransaccion.get)
    val types = Array(Types.VARCHAR, Types.VARCHAR)

    val queryResult = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForList(sql, params, types)
    }.asScala.toList


    val rows = queryResult.map(row => (row.get("spid"), row.get("ope"), row.get("lote")))

    val decryptedCreditCard = or.datos_medio_pago.flatMap(dmp =>
      dmp.nro_tarjeta.flatMap { encrypt =>
        try {
          Option(encryptionService.desencriptarBase64(encrypt))
        }catch{
          case _:Exception => {
            logger.warn(s"credit card not encrypted! or.nroOperacion: ${or.nro_operacion}")
            Option(encrypt)
          }
        }
      }
    )

    or.copy(
      datos_medio_pago = or.datos_medio_pago.flatMap(dmp => Some(dmp.copy(nro_tarjeta = decryptedCreditCard))),
      sub_transactions = or.sub_transactions.map(st => st.copy(lot = findLastLote(st.subpayment_id.get, rows))),
      lote = findLastLote(0L, rows)
    )
  }

  //Searches lote with max operation id for given subpaymentid inside a tuples list (subpaymentid, operationid, lote)
  private def findLastLote(subpaymentId: Long, rows: List[(Object, Object, Object)]): Option[String] = {

    val operations = rows.filter(_._1.toString.equals(subpaymentId.toString))
    val lastLote = operations.maxBy(row => row._2 match {
      case null => 0L
      case x => x.toString.toLong
    })
    lastLote match {
      case null => None
      case (sp, o, null) => None
      case (sp, o, lote) => Some(lote.toString)
    }
  }

  def updateNroTrace(nrosTraceSite: NrosTraceSite) = {
    //INSERT INTO numerostrace (idsite, idbackend, nroterminal, nroticket, nrotrace, nrobatch)
    //VALUES (p_idsite,p_idbackend,p_nroterminal, LAST_INSERT_ID(1),LAST_INSERT_ID(1),LAST_INSERT_ID(1))
    //ON DUPLICATE KEY UPDATE nrotrace=LAST_INSERT_ID(IF(nrotrace=999999,1,nrotrace+1));

    val sql = "UPDATE numerostrace SET nrotrace = IF(nrotrace=999999,1,?) WHERE idsite = ? and idbackend = ? and nroterminal = ?"
    evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.setDataSource(db.dataSource)
      jdbcTemplate.update(sql, nrosTraceSite.nroTrace.asInstanceOf[Object],
        nrosTraceSite.idSite.asInstanceOf[Object],
        nrosTraceSite.idBackend.asInstanceOf[Object],
        nrosTraceSite.nroTerminal.asInstanceOf[Object])
    }
  }

  def updateNroBatch(nrosTraceSite: NrosTraceSite) = {
    //INSERT INTO numerostrace (idsite, idbackend, nroterminal, nroticket, nrotrace, nrobatch)
    //VALUES (p_idsite,p_idbackend,p_nroterminal, LAST_INSERT_ID(1),LAST_INSERT_ID(1),LAST_INSERT_ID(1))
    //ON DUPLICATE KEY UPDATE nrobatch=LAST_INSERT_ID(IF(nrobatch=999,1,nrobatch+1));

    val sql = "UPDATE numerostrace SET nrobatch = IF(nrobatch=999,1,?) WHERE idsite = ? and idbackend = ? and nroterminal = ?"
    evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.setDataSource(db.dataSource)
      jdbcTemplate.update(sql, nrosTraceSite.nroBatch.asInstanceOf[Object],
        nrosTraceSite.idSite.asInstanceOf[Object],
        nrosTraceSite.idBackend.asInstanceOf[Object],
        nrosTraceSite.nroTerminal.asInstanceOf[Object])
    }
  }

  def updateNroTicket(nrosTraceSite: NrosTraceSite) = {
    //INSERT INTO numerostrace (idsite, idbackend, nroterminal, nroticket, nrotrace, nrobatch)
    //VALUES (p_idsite,p_idbackend,p_nroterminal, LAST_INSERT_ID(1),LAST_INSERT_ID(1),LAST_INSERT_ID(1))
    //ON DUPLICATE KEY UPDATE nroticket=LAST_INSERT_ID(IF(nroticket=9999,1,nroticket+1));
    val sql = "UPDATE numerostrace SET nroticket = IF(nroticket=9999,1,?) WHERE idsite = ? and idbackend = ? and nroterminal = ?"
    evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.setDataSource(db.dataSource)
      jdbcTemplate.update(sql, nrosTraceSite.nroTicket.asInstanceOf[Object],
        nrosTraceSite.idSite.asInstanceOf[Object],
        nrosTraceSite.idBackend.asInstanceOf[Object],
        nrosTraceSite.nroTerminal.asInstanceOf[Object])
    }
  }

  def convertToOERFromJson(jsonMap: java.util.Map[String, Object]): Try[OperationExecutionResponse] = Try {
    val status = jsonMap.get("idestado").toString.toInt
    val jsonString = jsonMap.get("operation_data").toString

    Json.parse(jsonString).validate[OperationExecutionResponse] fold(
      errors => {
        logger.error("convert error", errors)
        throw new Exception // TODO
      },
      operation => {
        val opResponse = status match {
          case 6 => operation.copy(status = 6)
          case 8 => operation.copy(status = 8)
          case 10 => operation.copy(status = 10)
          case 12 => operation.copy(status = 12)
          case _ => operation
        }
        val subTransactions = opResponse.operationResource.map(or => or.sub_transactions.map(subTransaction => {
          val subpaymentsState = getSubpaymentState(opResponse.operationResource.get.charge_id.get)
          val subpaymentState = subpaymentsState.find(spState => spState.subpaymentId == subTransaction.subpayment_id.get).get
          subpaymentState.status match {
            case 6 => subTransaction.copy(status = Some(6))
            case 8 => subTransaction.copy(status = Some(8))
            case 10 => subTransaction.copy(status = Some(10))
            case 12 => subTransaction.copy(status = Some(12))
            case _ => subTransaction
          }
        }))
        subTransactions.map(sTransactions =>
          opResponse.copy(operationResource = Some(updateLotes(opResponse.operationResource.get.copy(sub_transactions = sTransactions)))))
          .getOrElse(opResponse)
      }
    )

  }

  def getSubpaymentState(chargeId: Long) = {
    val sql =
      """select subtrans.subpayment_id,
                    spstrans.idestado
                    from subpayment_transaccion_operacion_xref subtrans,
                    transaccion_operacion_xref trans,
                    spstransac spstrans
                  where subtrans.operation_id = trans.operation_id
                    and spstrans.idtransaccion = subtrans.transaccion_id
                    and trans.charge_id = ?"""
    val jsonMap = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForList(sql, chargeId.toString())
    }.asScala.toList
    jsonMap.map(subPayment => SubpaymentState(subpaymentId = subPayment.get("subpayment_id").toString.toLong, status = subPayment.get("idestado").toString.toLong))
  }

  private def convertFromJsonOERefundR(jsonMap: java.util.Map[String, Object]): Try[OperationExecutionRefundResponse] = Try {
    OperationExecutionRefundResponse(
      id = jsonMap.get("refund_id").toString.toLong,
      date_created = jsonMap.get("fecha").toString,
      amount = if (jsonMap.get("monto") != null) (new BigDecimal(jsonMap.get("monto").toString).multiply(new BigDecimal(100))).setScale(0).toString.toLong else 0, //0 se muestra en las operaciones hechas en legacy
      status = TransactionState.operationApply(jsonMap.get("e_estatus").toString.toInt).toString(),
      date_canceled = None,
      operation = jsonMap.get("o_estatus").toString
    )
  }

  private def convert2OERefundR(oerr: OperationExecutionRefundResponse, oerrCancel: Option[OperationExecutionRefundResponse]): OperationExecutionRefundResponse = {
    oerrCancel.map(oCancel => {
      oerr.copy(date_canceled = Some(oCancel.date_created))
    }).getOrElse(oerr)
  }

  def listPaymentRefunds(siteId: String, chargeId: Long): List[OperationExecutionRefundResponse] = {
    val sql =
      """select o.monto, o.fecha, ro_xref.refund_id, top.descri o_estatus, est.idestado e_estatus, ro_xref.cancel_id
                   from transaccion_operacion_xref o_xref, 
                   spstransac t, 
                   refunds_transaccion_operacion_xref ro_xref, 
                   operacion o,
                   tipooperacion top,
                   spsestado est
                   where o_xref.transaccion_id = t.idtransaccion and 
                   o_xref.charge_id = ro_xref.charge_id and 
                   ro_xref.operation_id = o.idoperacion and
                   o.idtipooperacion = top.idtipooperacion and
                   o.idtipooperacion NOT IN (6, 7) and
                   o.idestado = est.idestado and
             		   (t.idsite = ? or t.idsite in (select merchant_id from site_merchant where site_id = ? )) and
                   o_xref.charge_id = ? 
                   order by o.fecha DESC"""
    val jsonMap = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForList(sql, siteId, siteId, chargeId.toString())
    }.asScala.toList


    jsonMap.map { jsonM =>
      val refund = convertFromJsonOERefundR(jsonM).get
      val cancelId = if (jsonM.get("cancel_id") == null) None else Some(jsonM.get("cancel_id").toString)
      convert2OERefundR(refund, getCancelRefund(cancelId, siteId, chargeId))
    }

  }

  def listSubpaymentsRefunds(siteId: String, chargeId: Long): List[OperationExecutionRefundSubpaymentResponse] = {

    val oer = retrieveCharge(siteId, chargeId)

    val subpayments = oer match {
      case Success(x) => x.subPayments.getOrElse(List())
      case Failure(e) => List()
    }

    subpayments.map(subpayment =>
      OperationExecutionRefundSubpaymentResponse(id = subpayment.subpayment_id.getOrElse(0L),
        history = listSubpaymentRefunds(subpayment.subpayment_id.getOrElse(0L), siteId, chargeId)))
  }

  private def getSubpaymentRefunds(subpaymentId: Long, siteId: String, chargeId: Long): OperationExecutionRefundSubpaymentResponse = {
    OperationExecutionRefundSubpaymentResponse(id = subpaymentId, history = listSubpaymentRefunds(subpaymentId, siteId, chargeId))
  }

  private def listSubpaymentRefunds(subpaymentId: Long, siteId: String, chargeId: Long): List[OperationExecutionRefundResponse] = {
    val sql =
      """select o.monto, o.fecha, ro_xref.refund_id, top.descri o_estatus, est.idestado e_estatus
                   from transaccion_operacion_xref o_xref, 
                   spstransac t, 
                   refunds_transaccion_operacion_xref ro_xref, 
                   operacion o,
                   tipooperacion top,
                   spsestado est
                   where o_xref.transaccion_id = t.idtransaccion and 
                   o_xref.charge_id = ro_xref.charge_id and 
                   ro_xref.operation_id = o.idoperacion and
                   o.idtipooperacion = top.idtipooperacion and
                   o.idtipooperacion NOT IN (6, 7) and
                   o.idestado = est.idestado and
                   (t.idsite = ? or t.idsite in (select merchant_id from site_merchant where site_id = ? )) and
                   o_xref.charge_id = ? and
                   ro_xref.subpayment_id = ?
                   order by o.fecha DESC"""
    val jsonMap = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForList(sql, siteId, siteId, chargeId.toString(), subpaymentId.toString())
    }.asScala.toList

    jsonMap.map { jsonM =>
      val refund = convertFromJsonOERefundR(jsonM).get
      val ocancelId = if (jsonM.get("cancel_id") == null) None else Some(jsonM.get("cancel_id").toString)
      convert2OERefundR(refund, getCancelRefund(ocancelId, siteId, chargeId))
    }
  }

  private def getCancelRefund(ocancelId: Option[String], siteId: String, chargeId: Long): Option[OperationExecutionRefundResponse] = {
    ocancelId match {
      case Some(cancelId) => {
        val sql =
          """select o.monto, o.fecha, ro_xref.refund_id, top.descri o_estatus, est.idestado e_estatus, ro_xref.cancel_id
              						 from transaccion_operacion_xref o_xref, 
              					   spstransac t, 
              					   refunds_transaccion_operacion_xref ro_xref, 
              					   operacion o,
              					   tipooperacion top,
              					   spsestado est 
                           where o_xref.transaccion_id = t.idtransaccion and 
                           o_xref.charge_id = ro_xref.charge_id and 
                           ro_xref.operation_id = o.idoperacion and
                           o.idtipooperacion = top.idtipooperacion and
                           o.idestado = est.idestado and
                           ro_xref.charge_id = ? and
                           ro_xref.refund_id = ? and
                           ro_xref.operation_id = o.idoperacion and
                           o.idtipooperacion IN (6, 7) """
        try {

          val jsonMap = evalWithJdbcTemplate { jdbcTemplate =>
            jdbcTemplate.queryForMap(sql, chargeId.toString(), cancelId)
          }
          val converted = convertFromJsonOERefundR(jsonMap)
          Some(converted.get)
        } catch {
          case ioe: EmptyResultDataAccessException => None
          case e: Exception => throw e
        }
      }
      case None => None
    }
  }

  def retrieveRefund(siteId: String, chargeId: Long, id: Long): Try[OperationExecutionRefundResponse] = {
    val sql =
      """select o.monto, o.fecha, ro_xref.refund_id, top.descri o_estatus, est.idestado e_estatus
                   from transaccion_operacion_xref o_xref, 
                   spstransac t, 
                   refunds_transaccion_operacion_xref ro_xref, 
                   operacion o,
                   tipooperacion top,
                   spsestado est
                   where o_xref.transaccion_id = t.idtransaccion and 
                   o_xref.charge_id = ro_xref.charge_id and 
                   ro_xref.operation_id = o.idoperacion and
                   o.idtipooperacion = top.idtipooperacion and
                   o.idtipooperacion NOT IN (6, 7) and
                   o.idestado = est.idestado and
                   (t.idsite = ? or t.idsite in (select merchant_id from site_merchant where site_id = ? )) and
                   o_xref.charge_id = ? and
                   ro_xref.refund_id = ? and
                   ro_xref.cancel_id IS null"""
    try {
      val jsonMap = evalWithJdbcTemplate { jdbcTemplate =>
        jdbcTemplate.queryForMap(sql, siteId, siteId, chargeId.toString(), id.toString())
      }

      val converted = convertFromJsonOERefundR(jsonMap)
      converted
    } catch {
      case ioe: EmptyResultDataAccessException => Failure(ioe)
      case e: Exception => throw e
    }
  }

  def retrieveCharge(csId: String): Try[OperationExecutionResponse] = Try {

    val jsonString = evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForMap(
        """
            select CAST(tox.operation_data as char(10000)) operation_data, t.idestado from transaccion_operacion_xref tox 
              join spstransac t on t.idtransaccion = tox.transaccion_id
              join spstransac_cybersource cs on tox.transaccion_id = cs.idtransaccion
              where cs.requestid = ? """, csId)
    }

    convertToOERFromJson(jsonString)

  }.flatten

  def retrieveSiteIdFromChargeId(chargeId: Long): String = {
    evalWithJdbcTemplate { jdbcTemplate =>
      jdbcTemplate.queryForObject(
        """select idsite from spstransac t
             join transaccion_operacion_xref tox on t.idtransaccion = tox.transaccion_id
             where tox.charge_id = ? """.stripMargin,
        Array(chargeId.asInstanceOf[Object]), classOf[String])
    }
  }

  def insertDistributedTx(chargeId: Long, oerInstallmentsFixed: OperationExecutionResponse, parent: InsertTx, subtxs: List[DistributedTxElement], estadoFinal: TransactionState) = {

    evalTxWithJdbcTemplate { implicit jdbcTemplate =>

      val parentId = doInsertTransaccionBasico(chargeId, oerInstallmentsFixed.operationResource.get, parent.site, parent.distribuida, TransactionFSM.estadosPara(estadoFinal).map(_.id), oerInstallmentsFixed)

      subtxs.foreach { child =>
        val subTxId = doInsertTransaccionBasico(chargeId, child.operation, child.site, Some("C"), TransactionFSM.estadosPara(estadoFinal).map(_.id), oerInstallmentsFixed)
        insertSubTransaction(parentId, subTxId, child.percent.getOrElse(0d), child.monto)
      }
    }
  }

  /**
    *
    * Busca la transacciones simples rechazadas por Cybersource.
    *
    * @return
    */
  def findTransactionsRejectedByCS(): List[OperationExecutionResponse] = {
    val sql =
      """select CAST(o.operation_data as char(10000)) operation_data, t.idestado, cs.resultadocs
         from transaccion_operacion_xref o, spstransac t
            INNER JOIN spstransac_cybersource cs on t.idtransaccion = cs.idtransaccion
            where o.transaccion_id = t.idtransaccion
            and o.reused is false
            and o.operation_data is not null
            and t.idestado = 4
            and cs.estadofinaltransaccion = 48
            and (t.distribuida IS NULL or t.distribuida = '')
            order by o.transaccion_id"""

    //Estado = Autorizado (4) | ResultadoFinal: PendienteAnulacion (48)

    val jsonStrings = evalWithJdbcTemplate { implicit jdbcTemplate =>
      jdbcTemplate.queryForList(sql)
    }.asScala.toList

    val converted = jsonStrings map convertToOERFromJson

    val operations = converted.map {
      _.get
    }
    operations
  }

  /**
    * Busca todas las transacciones distribuidas padre en estado a reversar.
    *
    * @return
    */
  def findDistributedTransactionsPartiallyRejected() = {
    val sql =
      """select CAST(o.operation_data as char(10000)) operation_data, t.idestado
        from transaccion_operacion_xref o, spstransac t
        where o.transaccion_id = t.idtransaccion
        and o.reused is false
        and operation_data is not null
        and t.idestado = 15
        and t.distribuida = 'F'
        order by o.transaccion_id"""

    //Estado = A Reversar (15)

    val jsonStrings = evalWithJdbcTemplate { implicit jdbcTemplate =>
      jdbcTemplate.queryForList(sql)
    }.asScala.toList

    val converted = jsonStrings map convertToOERFromJson

    val operations = converted.map {
      _.get
    }
    operations
  }

  /**
    * Busca todas las transacciones distribuidas padre en estado Autorizado, ResultadoFinal CS = Pendiente Anulacion.
    *
    * @return
    */
  def findDistributedTransactionsRejectedByCS(): List[OperationExecutionResponse] = {
    val sql =
      """select CAST(o.operation_data as char(10000)) operation_data, t.idestado, cs.resultadocs
         from transaccion_operacion_xref o, spstransac t
            INNER JOIN spstransac_cybersource cs on t.idtransaccion = cs.idtransaccion
            where o.transaccion_id = t.idtransaccion
            and o.reused is false
            and o.operation_data is not null
            and t.idestado = 4
            and cs.estadofinaltransaccion = 48
            and t.distribuida = 'F'
            order by o.transaccion_id"""

    //Estado = Autorizado (4) | ResultadoFinal: PendienteAnulacion (48)

    val jsonStrings = evalWithJdbcTemplate { implicit jdbcTemplate =>
      jdbcTemplate.queryForList(sql)
    }.asScala.toList

    val converted = jsonStrings map convertToOERFromJson

    val operations = converted.map {
      _.get
    }
    operations
  }
  
 /* def updateReverse(operationData: OperationData, distribuida: Option[String], oidTransaccion: Option[String], state:TransactionState) = {
    val chargeId = operationData.chargeId
    evalTxWithJdbcTemplate { implicit jdbcTemplate =>
      distribuida match {
        case Some("F") | None => {
          val idTransaccion = oidTransaccion.getOrElse {
            evalWithJdbcTemplate { jdbcT =>
              jdbcT.queryForObject(
                """select transaccion_id from transaccion_operacion_xref where charge_id = ? """.stripMargin,
                Array(chargeId.asInstanceOf[Object]), classOf[String])
            }
          }
        	updateTransaccionXref(operationData, state)          
        	updateReverseState(idTransaccion, operationData, distribuida, state)
        	insertEstadoHistorico(Some(idTransaccion.toLong), Some(chargeId), operationData.cuenta.idProtocolo, state.id, distribuida, None)
        } 
        case _ => {
          val idTransaccion = oidTransaccion.getOrElse {
            evalWithJdbcTemplate { jdbcT =>
              jdbcT.queryForObject(
                """select transaccion_id from subpayment_transaccion_operacion_xref where subpayment_id = ? """.stripMargin,
                Array(chargeId.asInstanceOf[Object]), classOf[String])
            }
          }
        	updateReverseState(idTransaccion, operationData, distribuida, state)
        	insertEstadoHistorico(Some(idTransaccion.toLong), Some(chargeId), operationData.cuenta.idProtocolo, state.id, distribuida, None)
        }
      }
    }
  }
  
  private def updateReverseState(idTransaccion: String, operationData: OperationData, distribuida: Option[String], state:TransactionState)(implicit jdbcTemplate: JdbcTemplate): Unit = {
    logger.info(s"Event recieved - update tx to state ${state.id} for idTransaccion ${idTransaccion}")

    val sqlUpd = "UPDATE spstransac SET idestado = ?, fechaoriginal = ? WHERE idtransaccion = ?"
    logger.debug(s"$sqlUpd :: idestado=${state.id} :: idTransaccion=$idTransaccion") 
    val lastUpdate = operationData.resource.last_update.getOrElse(throw new RuntimeException("No se definio fecha de creacion de la operacion"))
    evalTxWithJdbcTemplate { implicit jdbcTemplate =>      
      jdbcTemplate.update(sqlUpd,
        Integer.valueOf(Rechazada().id),
        lastUpdate,
        idTransaccion)
    }       
  }*/

}

case class SubpaymentState(subpaymentId: Long, status: Long)

