package services.migration

import com.decidir.coretx.utils.JdbcDaoUtils
import com.google.inject.Inject
import play.api.db.Database
import com.decidir.coretx.utils.JedisUtils
import com.decidir.coretx.utils.JedisPoolProvider
import java.sql.Types
import collection.JavaConverters._
import scala.collection.Map
import com.decidir.coretx.api.OperationExecutionResponse
import com.decidir.coretx.domain.CardErrorCode
import com.decidir.coretx.domain.ExpiredCard
import com.decidir.coretx.domain.InsufficientAmount
import com.decidir.coretx.domain.InvalidCard
import com.decidir.coretx.domain.RequestAuthorizationCard
import com.decidir.coretx.domain.CybersourceError
import com.decidir.coretx.domain.InvalidNumber
import com.decidir.coretx.domain.SecurityCodeError
import com.decidir.coretx.api.OperationResource
import java.util.UUID
import com.decidir.coretx.api.DatosTitularResource
import com.decidir.coretx.api.DatosMedioPagoResource
import com.decidir.coretx.api.DatosSiteResource
import com.decidir.coretx.domain.SiteRepository
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.ErrorMessage
import com.decidir.coretx.domain.Site
import play.api.Configuration
import com.decidir.coretx.domain.MedioDePagoRepository
import org.springframework.jdbc.core.simple.SimpleJdbcInsert
import play.api.libs.json.Json
import com.decidir.coretx.api.OperationJsonFormats._
import java.util
import java.util.HashMap
import java.util.Date
import java.text.DecimalFormat
import com.decidir.encrypt.EncryptionService
import scala.util.Try
import com.decidir.coretx.api.Subpayment
import com.decidir.coretx.api.TransactionState
import com.decidir.coretx.domain.OperationResourceRepository
import org.joda.time.format.DateTimeFormat
import org.springframework.jdbc.core.JdbcTemplate
import com.decidir.coretx.api.SubTransaction
import org.joda.time.DateTime
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.sql.Timestamp
import org.springframework.dao.EmptyResultDataAccessException
import com.decidir.coretx.api.Subpayment
import com.decidir.coretx.api.CyberSourceResponse
import com.decidir.coretx.api.FraudDetectionDecision
import com.decidir.coretx.api.FraudDetectionData

class DecidirMigrator @Inject() (db: Database, jedisPoolProvider: JedisPoolProvider,
                                 siteRepository: SiteRepository,
                                 operationResourceRepository: OperationResourceRepository,
                                 configuration: Configuration,
                                 medioDePagoRepository: MedioDePagoRepository,
                                 encryptionService: EncryptionService
                                 ) extends JdbcDaoUtils(db) {

  val operationTTLSeconds = configuration.getInt("sps.coretx.operation.ttlseconds").getOrElse(300)

  def migrate(site_id: String) = Try {
    val site = siteRepository.retrieve(site_id).getOrElse(throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SITE_SITE_ID))

    txWithJdbcTemplate { jdbcTemplate => {
      updateSingleTransactions(site, jdbcTemplate)
      updateDistributedTransactions(site, jdbcTemplate)
  
      updateAnnulmentOperationsOnXref(site_id, jdbcTemplate)
//      throw new Exception
    }}
  }
  
  private def updateSingleTransactions(site: Site, jdbcTemplate: JdbcTemplate){
    logger.info(s"update single transactions of site = ${site.id}")
    val sql = """select * from spstransac where idsite = ? 
        and idtransaccion not in (select transaccion_id from transaccion_operacion_xref) 
        and idtransaccion < ?
        and distribuida is null """.stripMargin

    val transactions = jdbcTemplate.queryForList(sql, site.id, 0.toString)
        .asScala.toList.map(_.asScala)
    
    transactions.foreach { tx => {
      val oer = buildOperationExecutionResponse(tx, site, Some("single"), Nil, jdbcTemplate)
      val or = oer.operationResource.get
      val transactionId = or.idTransaccion.get.toInt
      val chargeId = or.charge_id.get
      insertNewTxOnXref(transactionId, or.nro_operacion.get, chargeId, oer, jdbcTemplate)
      insertNewOperationOnXref(transactionId, chargeId, None, jdbcTemplate)
    }}
  }
  
  private def updateDistributedTransactions(site: Site, jdbcTemplate: JdbcTemplate){
    logger.info(s"update distributed transactions of site = ${site.id}")
    val sql = """select * from spstransac where idsite = ? 
        and idtransaccion not in (select transaccion_id from transaccion_operacion_xref) 
        and idtransaccion < ?
        and distribuida = 'F' """.stripMargin
        
    val transactions = jdbcTemplate.queryForList(sql, site.id, 0.toString)
         .asScala.toList.map(_.asScala)

    transactions.foreach { tx => {
      val transactionId = tx.get("idtransaccion").get.toString.toInt
      val subpayments = getSubTransactions(transactionId, jdbcTemplate)
      val (subTransactions, listTransactionIds) = subpayments.unzip
      val oer = buildOperationExecutionResponse(tx, site, Some("distributed"), subTransactions, jdbcTemplate)
      val or = oer.operationResource.get
      val chargeId = or.charge_id.get
      val opNumber = or.nro_operacion.get
      insertNewTxOnXref(or.idTransaccion.get.toInt, opNumber, chargeId, oer, jdbcTemplate)
      insertNewOperationOnXref(transactionId.toLong, chargeId, None, jdbcTemplate)
      subpayments.foreach { subpayment => {
        val subTransactionId = subpayment._2
        insertNewSubTxOnSubXref(subTransactionId, opNumber, chargeId, subpayment._1.subpayment_id.get, jdbcTemplate)
        insertNewOperationOnXref(subTransactionId, chargeId, subpayment._1.subpayment_id, jdbcTemplate) }
      }
    }}
  }
  
  private def getSubTransactions(transactionId: Number, jdbcTemplate: JdbcTemplate) = {
    logger.info(s"retrive subtransac of transactionId = ${transactionId}")
    val sql = """select sps.*, sps_sub.idsubtransaccion from spstransac_subtransac sps_sub, spstransac sps where 
            sps_sub.idtransaccion = ?
            and sps_sub.idsubtransaccion = sps.idtransaccion""".stripMargin
        
    val transactions = jdbcTemplate.queryForList(sql,transactionId).asScala.toList.map(_.asScala)
    transactions.map(row => {
      val tx = row.filter(_._2 != null)
      def toString(property: String) = tx.get(property).map(_.toString)
      def toInt(property: String) = toString(property).map(_.toInt)
      def toLong(property: String) = toString(property).map(_.toLong)
      val sddsd = toInt("idestado")
      (SubTransaction(site_id = toString("idsite").get,
    		  amount = getAmount(tx.get("monto").map(_.toString().toDouble.toLong)).getOrElse(0),
    		  original_amount = getAmount(tx.get("montooriginal").map(_.toString().toDouble.toLong)),
          installments = toInt("cantcuotas"),
          nro_trace = toString("nrotrace"),
          subpayment_id = Some(operationResourceRepository.newSubpaymentId),
          status = toInt("idestado"),
          terminal = toString("terminal")  
      ),
       toLong("idsubtransaccion").get)
    })
  }

  private def getOperationsToMigrate(transaction_id: Number, jdbcTemplate: JdbcTemplate) = {
    val sql = """select optx.idoperacion, o.fecha from opertransac optx
                join operacion o on optx.idoperacion = o.idoperacion
                where optx.idtransaccion = ? and (o.idtipooperacion in (1,3,5,6,7)) order by idoperacion asc""".stripMargin
    println(s"$sql    $transaction_id")
    var params: Array[Object] = Array(transaction_id)
    var types = Array(Types.VARCHAR)
    val operations = jdbcTemplate.queryForList(sql, params, types).asScala.toList.map(_.asScala)
    operations
  }

  private def updateAnnulmentOperationsOnXref(siteId: String, jdbcTemplate: JdbcTemplate) = {
    logger.info(s"update annulment operations of site = ${siteId}")
    val sql = """select idoperacion from operacion where 
      idtipooperacion in (6,7) 
      and idoperacion < 0
      and idsite = ? """.stripMargin
    var params: Array[Object] = Array(siteId)
    var types = Array(Types.VARCHAR)
    val operations = jdbcTemplate.queryForList(sql, params, types).asScala.toList.map(_.asScala)

    operations.foreach { operation =>
      
      val idoperacioncompuesta = (operation.get("idoperacion").map(_.toString())).getOrElse(throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "idoperacioncompuesta"))
      logger.info(s"idoperacioncompuesta =  ${idoperacioncompuesta}")
      val cancel_id = jdbcTemplate.queryForObject(
          """select refund_id from refunds_transaccion_operacion_xref where operation_id = ? """.stripMargin,
          Array(idoperacioncompuesta.asInstanceOf[Object]), classOf[String])
      logger.info(s"cancel_id =  ${cancel_id}")
      
      val idOperacion = jdbcTemplate.queryForObject(
          """select idoperacionsimple from operacionesenoperacion where idoperacioncompuesta = ? """.stripMargin,
          Array(idoperacioncompuesta.asInstanceOf[Object]), classOf[String])
      logger.info(s"idoperacion =  ${idOperacion}")
      
      val refund_id = jdbcTemplate.queryForObject(
          """select refund_id from refunds_transaccion_operacion_xref where operation_id = ? """.stripMargin,
          Array(idOperacion.asInstanceOf[Object]), classOf[String])
      val sql = "UPDATE refunds_transaccion_operacion_xref SET cancel_id = ? WHERE refund_id = ?".stripMargin
      
      jdbcTemplate.update(sql, cancel_id.asInstanceOf[Object], refund_id.asInstanceOf[Object])
    }
  }

  private def getTitularData(transactionId: String, jdbcTemplate: JdbcTemplate) = {
    logger.info(s"titular to Transaction: ${transactionId}")
    val sql = """select * from domiciliotitular where idtransaccion = ?""".stripMargin

    var params: Array[Object] = Array(transactionId)
    var types = Array(Types.VARCHAR)
    try {
      val titularData = jdbcTemplate.queryForMap(sql, params, types).asScala
      Some(titularData.filter(_._2 != null))
    } catch {
      case empty: EmptyResultDataAccessException => {
        logger.info(s"titular to Transaction: ${transactionId} without titular")
    	  None
      }
    }
  }

  private def buildOperationExecutionResponse(row: Map[String, Object], site: Site, modality: Option[String], subTransactions: List[SubTransaction], jdbcTemplate: JdbcTemplate) = {

    val tx = row.filter(_._2 != null)
    def toString(property: String) = tx.get(property).map(_.toString)
    def toInt(property: String) = toString(property).map(_.toInt)
    def ein(property: String) = toString(property).getOrElse("")

    val transactionId = toString("idtransaccion").getOrElse(throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "idtransaccion"))
    val idmediopago = toInt("idmediopago")
    //val medioPago = medioDePagoRepository.retrieve(idmediopago.getOrElse(throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "idmediopago")))
    val medioPago = medioDePagoRepository.retrieve(idmediopago.getOrElse(""))
    
    val paramsitio = tx.get("paramsitio").map(pSite => Some(pSite.toString)).getOrElse(None)
    val cardNumberEncripted = tx.get("nrotarjetaencriptado")
    val cardNumber = cardNumberEncripted.map(cne => encryptionService.desencriptarString(cne.asInstanceOf[Array[Byte]]))
    val titularData = getTitularData(transactionId, jdbcTemplate)
    val errorCode = getCardError(toInt("idmotivo").getOrElse(-1), toInt("idprotocolo").getOrElse(-1))
    val operation_Id = toString("idtransaccionsite")
    
    val op = new OperationResource(id = UUID.randomUUID().toString(),
      nro_operacion = operation_Id,
      user_id = None,
      charge_id = Some(operationResourceRepository.newChargeId),
      fechavto_cuota_1 = toString("fechavtocuota1"),
      monto = getAmount(tx.get("monto").map(_.toString().toDouble.toLong)),
      original_amount = getAmount(tx.get("montooriginal").map(_.toString().toDouble.toLong)),
      cuotas = tx.get("cantcuotas").map(_.toString().toInt),
      sub_transactions = subTransactions,
      datos_titular = Some(new DatosTitularResource(
        email_cliente = tx.get("mailusu").map(_.toString()),
        tipo_doc = tx.get("idtipodoc").map(_.toString().toInt),
        nro_doc = tx.get("nrodoc").map(_.toString()),
        calle = titularData.map(td => td.get("calle").map(_.toString)).flatten,
        nro_puerta = titularData.map(td => getPortNumber(td.get("nro").map(_.toString)).getOrElse(None)).flatten,
        fecha_nacimiento = None,
        ip = tx.get("ipcomprador").map(_.toString()))),
      datos_medio_pago = Some(new DatosMedioPagoResource(
        medio_de_pago = medioPago.map(_.id.toInt),
        id_moneda = medioPago.map(_.idMoneda.getOrElse(throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "idmoneda")).toInt), // TODO
        marca_tarjeta = medioPago.map(_.idMarcaTarjeta.getOrElse(throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "idmarcatarjeta"))),
        nro_tarjeta = cardNumberEncripted.map(_.toString),
        nombre_en_tarjeta = toString("titular"),
        expiration_month = toString("venctarj").map(_.takeRight(2)),
        expiration_year = toString("venctarj").map(_.take(2)),
        security_code = Some("4123"),
        bin_for_validation = toString("nrotarj"),
        nro_trace = toString("nrotrace"),
        nro_terminal = toString("terminal"),
        cod_autorizacion = toString("codaut"),
        nro_devolucion = None,
        last_four_digits = cardNumber.map(cn => cn.takeRight(4)),
        card_number_length = cardNumber.map(cn => cn.length),
        id_operacion_medio_pago = toString("idopmediopago"))),
      datos_site = Some(new DatosSiteResource(site_id = Some(site.id),
        url_dinamica = tx.get("urldinamica").map(_.toString()),
        param_sitio = paramsitio,
        use_url_origen = None, //FIXME: No se guarda en ninguna tabla. Esto lo envia el site para validar su url origen
        url_origen = toString("urldinamica"),
        referer = None, //TODO: no se de donde sale
        id_modalidad = modality,
        gds = None,
        enviarResumenOnLine = Some(site.enviarResumenOnLine),
        usaUrlDinamica = Some(site.ppb.usaUrlDinamica),
        urlPost = Some(site.ppb.urlPost),
        mandarMailAUsuario = Some(site.mailConfiguration.mandarMailAUsuario),
        mail = toString("mailusu"),
        replyMail = Some(site.mailConfiguration.replyMail))),
      creation_date = Some(new Date()),
      last_update = Some(new Date()),
      ttl_seconds = Some(operationTTLSeconds),
      idTransaccion = Some(transactionId),
      fraud_detection = getFraudDetection(transactionId, jdbcTemplate).map(fd => FraudDetectionData(status = Some(fd))),
      used = None,
      retries = toInt("intentos"))
    
    new OperationExecutionResponse(
      status = tx("idestado").toString().toInt,
      authorizationCode = ein("codaut"),
      cardErrorCode = errorCode,
      authorized = if (errorCode == None) true else false,
      validacion_domicilio = Some(toString("resultadovalidaciondomicilio").getOrElse("")),
      operationResource = Some(op),
      postbackHash = None,
      subPayments = Some(
        subTransactions.map(st =>
          Subpayment(
            site_id = st.site_id,
            installments = st.installments,
            amount = Some(st.amount),
            nro_trace = st.nro_trace,
            subpayment_id = st.subpayment_id,
            payment_method_operation_id = st.id_operacion_medio_pago,
            status = Some(TransactionState.apply(st.status.get)),
            terminal = st.terminal,
            lote = st.lot
          )
        )
      )
    )
  }

  private def getPortNumber(port: Option[String]) =  Try {
    port.map(p => p.toInt)
  }
  
  private def getAmount(amount: Option[Long]) = {
    amount.map(a => Some(a * 100)).getOrElse(None)
  }

  private def insertNewTxOnXref(transaccionId: Number, nro_operacion: String, charge_id: Long, operation: OperationExecutionResponse, jdbcTemplate: JdbcTemplate) = {
	  logger.info(s"Transaction to migrate: ${transaccionId}")
    val data = Json.toJson(operation).toString
    val sql = "insert into transaccion_operacion_xref (charge_id, transaccion_id, operation_id, operation_data) values (?, ?, ?, ?)".stripMargin
    jdbcTemplate.update(sql, charge_id.asInstanceOf[Object], transaccionId.asInstanceOf[Object], nro_operacion.asInstanceOf[Object], data.asInstanceOf[Object])
    logger.info(s"Transaction to migrate: ${transaccionId} - insert transaccion_operacion_xref")
  }
  
  private def insertNewSubTxOnSubXref(transaccionId: Number, nro_operacion: String, charge_id: Long, subpaymentId: Long, jdbcTemplate: JdbcTemplate) = {
    logger.info(s"Transaction to migrate: ${transaccionId}")
    val sql = "insert into subpayment_transaccion_operacion_xref (subpayment_id, charge_id, transaccion_id, operation_id) values (?, ?, ?, ?)".stripMargin
    jdbcTemplate.update(sql, subpaymentId.asInstanceOf[Object], charge_id.asInstanceOf[Object], transaccionId.asInstanceOf[Object], nro_operacion.asInstanceOf[Object])
    logger.info(s"Transaction to migrate: ${transaccionId} - insert subpayment_transaccion_operacion_xref")
  }

  private def insertNewOperationOnXref(idTransaccion: Number, chargeId: Long, subpaymentId: Option[Long], jdbcTemplate: JdbcTemplate) = {
    getOperationsToMigrate(idTransaccion, jdbcTemplate) map { operation =>
      logger.info(s"Transaction to migrate: ${idTransaccion} - insert refunds_transaccion_operacion_xref")
      val idoperacion = operation.get("idoperacion").map(_.toString.toInt)
      val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
      val date = sdf.parse(operation.get("fecha").map(_.toString).get);
      val datesave = new Timestamp(date.getTime());
      val refundId = operationResourceRepository.newRefundId
      val sql = "insert into refunds_transaccion_operacion_xref (refund_id, operation_id, charge_id, date) values (?, ?, ?, ?)".stripMargin
      jdbcTemplate.update(sql, refundId.asInstanceOf[Object], idoperacion.get.asInstanceOf[Object], chargeId.asInstanceOf[Object], datesave.asInstanceOf[Object])
    }
  }

  private def getCardError(errorCode: Int, protocol: Int): Option[CardErrorCode] = protocol match {
    case 7      => getVisaCardError(errorCode)
    case 8      => getMastercardCardError(errorCode)
    case _      => errorCode match {
      case -1|0 => None
      case _    => Some(InvalidCard(errorCode))
    }
  }

  private def getMastercardCardError(errorCode: Int): Option[CardErrorCode] = errorCode match {
	  case -1|0 => None
	  case 105 => Some(InvalidNumber(errorCode))
	  case 108 => Some(SecurityCodeError(errorCode))
	  case 106 => Some(ExpiredCard(errorCode))
	  case 34|309|313|403|953 => Some(InsufficientAmount(errorCode))
	  case 505|951|954 => Some(RequestAuthorizationCard(errorCode))
	  case 10025 => Some(CybersourceError())
    case _ => Some(InvalidCard(errorCode))
  }
  
  private def getVisaCardError(errorCode: Int): Option[CardErrorCode] = errorCode match {
	  case -1|0|11|85 => None
	  case 14|53|56 => Some(InvalidNumber(errorCode))
	  case 47|55 => Some(SecurityCodeError(errorCode))
	  case 46|49|54 => Some(ExpiredCard(errorCode))
	  case 13|51|61|65 => Some(InsufficientAmount(errorCode))
	  case 1|2|7|76 => Some(RequestAuthorizationCard(errorCode))
	  case 10025 => Some(CybersourceError())
    case _ => Some(InvalidCard(errorCode))
  }  
  
  private def getFraudDetection(transactionId: String, jdbcTemplate: JdbcTemplate): Option[CyberSourceResponse] = {
    logger.info(s"CyberSource to Transaction: ${transactionId}")
    val sql = """select * from spstransac_cybersource where idtransaccion = ?""".stripMargin

    var params: Array[Object] = Array(transactionId)
    var types = Array(Types.VARCHAR)
    try {
      val jsonMap = jdbcTemplate.queryForMap(sql, params, types).asScala
      Some(CyberSourceResponse(FraudDetectionDecision.fromString(jsonMap.get("resultadocs").toString.toLowerCase), 
          None, jsonMap.get("reasoncode").toString.toInt, "Decision Manager processing", None))
    } catch {
      case empty: EmptyResultDataAccessException => {
        logger.warn(s"Transaction: ${transactionId} without CyberSource data")
    	  None
      }
    }
  }

} 