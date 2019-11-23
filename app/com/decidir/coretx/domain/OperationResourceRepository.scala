package com.decidir.coretx.domain

import java.util.Date

import scala.BigDecimal
import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import org.slf4j.LoggerFactory
import com.decidir.coretx.api._
import com.decidir.coretx.utils.ApiSupport
import com.decidir.coretx.utils.JedisPoolProvider
import com.decidir.coretx.utils.JedisUtils
import javax.inject.Inject
import javax.inject.Singleton

import play.api.Configuration
import redis.clients.jedis.Transaction
import com.google.common.base.CharMatcher

import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.decidir.encrypt.EncryptionService

import scala.collection.mutable.ArrayBuffer
import services.payments.DistributedTxElement
import play.api.libs.json.Json

import scala.collection.mutable.HashMap
import java.text.SimpleDateFormat
import java.util.TimeZone

import com.decidir.coretx.domain.OperationResourceRepository.agroDataFromMap


object OperationResourceRepository {
  
  def fromMap(map: Map[String, String]) = {
    
      OperationResource(id = map.getOrElse("id", ""),
        nro_operacion = map.get("nro_operacion"),
        user_id = map.get("user_id"),
        fechavto_cuota_1 = map.get("fechavto_cuota_1"),
        monto = map.get("monto").map(_.toLong),
        original_amount = map.get("original_amount").map(_.toLong),
        sub_transactions = Nil,
        cuotas = map.get("cuotas").map(_.toInt),
        datos_titular = ApiSupport.selectPrefixedNoneIfEmpty("datos_titular", map).map(datosTitularFromMap),
        datos_medio_pago = ApiSupport.selectPrefixedNoneIfEmpty("datos_medio_pago", map).map(datosMedioPagoFromMap),
        datos_site = ApiSupport.selectPrefixedNoneIfEmpty("datos_site", map).map(datosSiteFromMap),
        creation_date = map.get("creation_date").map(cd => new Date(cd.toLong)),
        last_update = map.get("last_update").map(lu => new Date(lu.toLong)),
        ttl_seconds = map.get("ttl_seconds").map(_.toInt),
        fraud_detection = ApiSupport.selectPrefixedNoneIfEmpty("fraud_detection", map).map(fraudDetectionFromMap),
        used = map.get("used").map(_.toBoolean),
        retries = map.get("retries").map(_.toInt),
        aggregate_data = ApiSupport.selectPrefixedNoneIfEmpty("aggregate_data", map).map(aggregateDataFromMap),
        origin = ApiSupport.selectPrefixedNoneIfEmpty("origin", map).map(requesterFromMap),
        ticket_request = ApiSupport.selectPrefixedNoneIfEmpty("ticket_request", map).map(ticketRequestFromMap),
        datos_offline = ApiSupport.selectPrefixedNoneIfEmpty("datos_offline", map).map(datosOfflineFromMap),
        datos_banda_tarjeta = ApiSupport.selectPrefixedNoneIfEmpty("datos_banda_tarjeta", map).map(datosBandaTarjetaFromMap),
        datos_bsa = ApiSupport.selectPrefixedNoneIfEmpty("datos_bsa", map).map(datosBsaFromMap),
        datos_gds = ApiSupport.selectPrefixedNoneIfEmpty("datos_gds", map).map(datosGDSFromMap),
        agro_data = ApiSupport.selectPrefixedNoneIfEmpty("agro_data", map).map(agroDataFromMap),
        datos_spv = ApiSupport.selectPrefixedNoneIfEmpty("datos_spv", map).map(datosSPVFromMap)
      )
  }

  def ticketRequestFromMap(map: Map[String, String]) = {
    TicketRequest(
      cuit = CuitRequest(
        authorizing = map.get("cuit.authorizing"),
        taxpayer = map.get("cuit.taxpayer"),
        user = map.get("cuit.user"),
        owner = map.get("cuit.owner")),
      cp = CPRequest(
        payment_entity = map.get("cp.payment_entity"),
        payer_bank = map.get("cp.payer_bank"),
        control_code = map("cp.control_code"),
        payment_format = map.get("cp.payment_format").map(_.toInt).get,
        branch_office_type = map.get("cp.branch_office_type").map(_.toInt)),
      vep = VEPRequest(
        number = map.get("vep.number"),
        advance_fee = map.get("vep.advance_fee").map(_.toInt),
        fiscal_period = map.get("vep.fiscal_period"), //YYYYMM
        concept = map.get("vep.concept"),
        sub_concept = map.get("vep.sub_concept"),
        establishment = map.get("vep.establishment"),
        payment_description_extract = map.get("vep.payment_description_extract"),
        payment_description = map.get("vep.payment_description"),
        payment_type = map.get("vep.payment_type"),
        expiration_date = map.get("vep.expiration_date").map(dt => new Date(dt.toLong)),
        creation_date = map.get("vep.creation_date").map(dt => new Date(dt.toLong)),
        due_date = map.get("vep.due_date").map(dt => new Date(dt.toLong)),
        collection_agency = map.get("vep.collection_agency"),
        description_concept = map.get("vep.description_concept"),
        subconcept_description = map.get("vep.subconcept_description"),
        owner_transaction_id = map.get("vep.owner_transaction_id"),
        field_description = map.get("vep.field_description"),
        content_description = map.get("vep.content_description"),
        tax_description = map.get("vep.tax_description"),
        taxes = taxesFromMap(ApiSupport.selectPrefixed("vep.taxes", map)),
        form = map("vep.form"),
        content = map("vep.content"),
        field_id = map.get("vep.field_id").map(_.toInt).get,
        field_type = map("vep.field_type"))
    )
  }

  def taxesFromMap(map: Map[String, String]): List[TaxesRequest] = {
    val count = map.getOrElse("size", "0").toInt
    (0 to count - 1).map {ndx =>
      val itemMap = ApiSupport.selectPrefixed(ndx.toString, map)
      TaxesRequest(
        id = itemMap.get("id").map(_.toInt).get,
        amount = itemMap.get("amount").map(_.toDouble).get)
    }.toList
  }

  def datosOfflineFromMap(map: Map[String, String]) = {
    OfflinePayment(
      cod_p1 = map.get("cod_p1"),
      cod_p2 = map.get("cod_p2"),
      cod_p3 = map.get("cod_p3"),
      cod_p4 = map.get("cod_p4"),
      recargo = map.get("recargo"),
      cliente = map.get("cliente"),
      fechavto = map.get("fechavto"),
      barcode = map.get("barcode"),
      fechavto2 = map.get("fechavto2")
    )
  }

  def agroDataFromMap(map: Map[String, String]) = {
    AgroData(
      token = map.get("token").get,
      token_type = map.get("token_type").get,
      days_agreement = map.get("days_agreement").map(_.toInt),
      installments = Nil //Se recupera luego, dado que se almacena en operations:id:installments:*
    )
  }

  def datosBandaTarjetaFromMap(map: Map[String, String]) = {
    DatosBandaTarjeta(
      card_track_1 = map.get("card_track_1"),
      card_track_2 = map.get("card_track_2"),
      input_mode = map.get("input_mode").getOrElse("")
    )
  }

  def datosBsaFromMap(map: Map[String, String]) = {
    Bsa(
      volatile_encrypted_data = map.get("volatile_encrypted_data"),
      public_request_key = map.get("public_request_key"),
      public_token = map.get("public_token"),
      private_token = map.get("private_token"),
      ip_address = map.get("ip_address"),
      issue_date = map.get("issue_date"),
      aditionalcrypt = map.get("aditionalcrypt"),
      hexsum = map.get("hexsum"),
      counter = map.get("counter"),
      security_code = map.get("security_code"),
      card_expiration = map.get("card_expiration"),
      flag_security_code = map.get("flag_security_code"),
      flag_tokenization = map.get("flag_tokenization"),
      flag_selector_key = map.get("flag_selector_key"),
      flag_pei = map.get("flag_pei"))
  }

  def datosGDSFromMap(map: Map[String, String]) = {
    GDSResource(
      iata_code = map.get("iata_code").get,
      nro_location = map.get("nro_location").get,
      id_merchant = map.get("id_merchant"))
  }

  def billingDataFromMap(map: Map[String, String]) = {
    BillingData(
        city = map.get("city"), 
        country = map.get("country"),
        customer_id = map.get("customer_id"),
        email = map.get("email"),
        first_name = map.get("first_name"),
        last_name = map.get("last_name"),
        phone_number = map.get("phone_number"),
        postal_code = map.get("postal_code"),
        state = map.get("state"),
        street1 = map.get("street1"),
        street2 = map.get("street2")
        )
  }
  
  def shipingDataFromMap(map: Map[String, String]) = {
    ShipingData(
        city = map.get("city"), 
        country = map.get("country"),
        email = map.get("email"),
        first_name = map.get("first_name"),
        last_name = map.get("last_name"),
        phone_number = map.get("phone_number"),
        postal_code = map.get("postal_code"),
        state = map.get("state"),
        street1 = map.get("street1"),
        street2 = map.get("street2"))
  }
  
  def purchaseTotalsFromMap(map: Map[String, String]) = {
    PurchaseTotals(
      currency = map.get("currency"),
      amount = map.get("amount").map(_.toLong))
  }

  def customerInSiteFromMap(map: Map[String, String]) = {
    CustomerInSite(
        days_in_site = map.get("days_in_site").map(_.toInt),
        is_guest = map.get("is_guest").map(_.toBoolean),
        password = map.get("password"),
        num_of_transactions = map.get("num_of_transactions").map(_.toInt),
        cellphone_number = map.get("cellphone_number"),
        date_of_birth = map.get("date_of_birth"),
        street = map.get("street"))
  }

  def departureDateFromMap(map: Map[String, String]) = {
    DepartureDate(
      departure_time = map.get("departure_time").map(dt => new Date(dt.toLong)),
      departure_zone= map.get("departure_zone"))
  }
  
  def decisionManagerTravelFromMap(map: Map[String, String]) = {
    DecisionManagerTravel(
      complete_route = map.get("complete_route"),
      journey_type = map.get("journey_type"),
      departure_date = ApiSupport.selectPrefixedNoneIfEmpty("departure_date", map).map(departureDateFromMap))
  }
  
  def itemsFromMap(map: Map[String, String]): List[Item] = {
    val count = map.getOrElse("size", "0").toInt
    (0 to count - 1).map {ndx =>
      val itemMap = ApiSupport.selectPrefixed(ndx.toString, map)
      Item(
          code = itemMap.get("code"),
          description = itemMap.get("description"),
          name = itemMap.get("name"),
          sku = itemMap.get("sku"),
          total_amount = itemMap.get("total_amount").map(_.toLong),
          quantity = itemMap.get("quantity").map(_.toInt),
          unit_price = itemMap.get("unit_price").map(_.toLong)
      )
    }.toList
  }
  
  def passengersFromMap(map: Map[String, String]): List[Passenger] = {
    val count = map.getOrElse("size", "0").toInt
    (0 to count - 1).map {ndx =>
      val itemMap = ApiSupport.selectPrefixed(ndx.toString, map)
      Passenger(
          email = itemMap.get("email"),
          first_name = itemMap.get("first_name"),
          passport_id = itemMap.get("passport_id"),
          last_name = itemMap.get("last_name"),
          phone = itemMap.get("phone"),
          passenger_status = itemMap.get("passenger_status"),
          passenger_type = itemMap.get("passenger_type")
      )
    }.toList
  }

  def AccountFromMap(map: Map[String, String]) = {
    Account(
      id = map.get("id"),
      name = map.get("name"),
      category = map.get("category").map(_.toInt),
      antiquity = map.get("antiquity").map(_.toInt),
      `type` = map.get("type"))
  }

  def WalletAccountFromMap(map: Map[String, String]) = {
    WalletAccount(
      id = map.get("id"),
      antiquity = map.get("antiquity").map(_.toInt))
  }

  def ticketingTransactionDataFromMap(map: Map[String, String]) = {
    TicketingTransactionData(
        days_to_event = map.get("days_to_event").map(_.toInt),
        delivery_type = map.get("delivery_type"),
        items = itemsFromMap(ApiSupport.selectPrefixed("items", map)))
  }

  def digitalGoodsTransactionDataFromMap(map: Map[String, String]) = {
    DigitalGoodsTransactionData(
        delivery_type = map.get("delivery_type"),
        items = itemsFromMap(ApiSupport.selectPrefixed("items", map)))
  }
  
  def retailTransactionDataFromMap(map: Map[String, String]) = {
    RetailTransactionData(
        ship_to = ApiSupport.selectPrefixedNoneIfEmpty("ship_to", map).map(shipingDataFromMap),
        days_to_delivery = map.get("days_to_delivery"),
        tax_voucher_required = map.get("tax_voucher_required").map(tvr => tvr.toString.toBoolean),
        customer_loyality_number = map.get("customer_loyality_number"),
        coupon_code = map.get("coupon_code"),
        items = itemsFromMap(ApiSupport.selectPrefixed("items", map)))
  }

  def retailTPTransactionDataFromMap(map: Map[String, String]) = {
    RetailTPTransactionData(
      account = ApiSupport.selectPrefixedNoneIfEmpty("account", map).map(AccountFromMap),
      wallet_account = ApiSupport.selectPrefixedNoneIfEmpty("wallet_account", map).map(WalletAccountFromMap),
      double_factor_tp = map.get("double_factor_tp").map(_.toInt),
      enroled_card_quantity = map.get("enroled_card_quantity").map(_.toInt),
      payment_method_risk_level = map.get("payment_method_risk_level").map(_.toInt),
      ship_to = ApiSupport.selectPrefixedNoneIfEmpty("ship_to", map).map(shipingDataFromMap),
      days_to_delivery = map.get("days_to_delivery"),
      tax_voucher_required = map.get("tax_voucher_required").map(tvr => tvr.toString.toBoolean),
      customer_loyality_number = map.get("customer_loyality_number"),
      coupon_code = map.get("coupon_code"),
      items = Some(itemsFromMap(ApiSupport.selectPrefixed("items", map))))
  }

  def travelTransactionDataFromMap(map: Map[String, String]) = {
    TravelTransactionData(
        reservation_code = map.get("reservation_code"),
        third_party_booking = map.get("third_party_booking").map(tpv => tpv.toString.toBoolean),
        departure_city = map.get("departure_city"),
        final_destination_city = map.get("final_destination_city"),
        international_flight = map.get("international_flight").map(inf => inf.toString.toBoolean),
        frequent_flier_number = map.get("frequent_flier_number"),
        class_of_service = map.get("class_of_service"),
        day_of_week_of_flight = map.get("day_of_week_of_flight").map(_.toInt),
        week_of_year_of_flight = map.get("week_of_year_of_flight").map(_.toInt),
        airline_code = map.get("airline_code"),
        code_share = map.get("code_share"),
        decision_manager_travel = ApiSupport.selectPrefixedNoneIfEmpty("decision_manager_travel", map).map(decisionManagerTravelFromMap),
        passengers = passengersFromMap(ApiSupport.selectPrefixed("passengers", map)),
        airline_number_of_passengers = map.get("airline_number_of_passengers").map(_.toInt))
  }

  def servicesTransactionDataFromMap(map: Map[String, String]) = {
    ServicesTransactionData(
      service_type = map.get("service_type"),
      reference_payment_service1 = map.get("reference_payment_service1"),
      reference_payment_service2 = map.get("reference_payment_service2"),
      reference_payment_service3 = map.get("reference_payment_service3"),
      items = itemsFromMap(ApiSupport.selectPrefixed("items", map)))
  }

  def copyPasteCardDataFromMap(map: Map[String, String]) = {
    CopyPasteCardData(
        card_number = map.get("card_number").map(_.toBoolean),
        security_code = map.get("security_code").map(_.toBoolean))
  }
  
  def cybersourceResponseFromMap(map: Map[String, String]): CyberSourceResponse = {
    CyberSourceResponse(
        decision = FraudDetectionDecision.fromMap(ApiSupport.selectPrefixed("decision", map)),
        reason_code = map("reason_code").toInt,
        request_id = map.get("request_id"),
        description = map("description"),
        details = ApiSupport.selectPrefixedNoneIfEmpty("details", map).flatMap(ErrorType.fromMap(_))
    )
  }

  def aggregateDataFromMap(map: Map[String, String]) = {
    Aggregator(
      indicator = map.get("indicator"),
      identification_number = map.get("identification_number"),
      bill_to_pay = map.get("bill_to_pay"),
      bill_to_refund = map.get("bill_to_refund"),
      merchant_name = map.get("merchant_name"),
      street = map.get("street"),
      number = map.get("number"),
      postal_code = map.get("postal_code"),
      category = map.get("category"),
      channel = map.get("channel"),
      geographic_code = map.get("geographic_code"),
      city = map.get("city"),
      merchant_id = map.get("merchant_id"),
      province = map.get("province"),
      country = map.get("country"),
      merchant_email = map.get("merchant_email"),
      merchant_phone = map.get("merchant_phone")
    )
  }
  
  def requesterFromMap(map: Map[String, String]) = {
    Requester(app = map.get("app"),
      useHash = map.get("useHash").map(_.toBoolean),
      encryptionType = map.get("encryptionType"),
      isEncrypted = map.get("isEncrypted").map(_.toBoolean))
  }

  def csmddsFromMap(map: Map[String, String]): Option[List[Csmdd]] = {
    val count = map.getOrElse("size", "0").toInt
    val csmdds = (0 to count - 1).map {ndx =>
      val csmddMap = ApiSupport.selectPrefixed(ndx.toString, map)
      Csmdd(
          code = csmddMap.get("code").map(_.toInt).get,
          description = csmddMap.get("description").get
      )
    }.toList
    Option(csmdds).filter(_.nonEmpty)
  }
  
  def fraudDetectionFromMap(map: Map[String, String]) = {
    FraudDetectionData(
        bill_to = ApiSupport.selectPrefixedNoneIfEmpty("bill_to", map).map(billingDataFromMap),
        purchase_totals = ApiSupport.selectPrefixedNoneIfEmpty("purchase_totals", map).map(purchaseTotalsFromMap),
        channel = map.get("channel"),
        dispatch_method = map.get("dispatch_method"),
        customer_in_site = ApiSupport.selectPrefixedNoneIfEmpty("customer_in_site", map).map(customerInSiteFromMap),
        copy_paste_card_data = ApiSupport.selectPrefixedNoneIfEmpty("copy_paste_card_data", map).map(copyPasteCardDataFromMap),
        send_to_cs = map.get("send_to_cs").map(_.toBoolean),
        device_unique_id = map.get("device_unique_id"),
        ticketing_transaction_data = ApiSupport.selectPrefixedNoneIfEmpty("ticketing_transaction_data", map).map(ticketingTransactionDataFromMap),
        retail_transaction_data = ApiSupport.selectPrefixedNoneIfEmpty("retail_transaction_data", map).map(retailTransactionDataFromMap),
        digital_goods_transaction_data = ApiSupport.selectPrefixedNoneIfEmpty("digital_goods_transaction_data", map).map(digitalGoodsTransactionDataFromMap),
        travel_transaction_data = ApiSupport.selectPrefixedNoneIfEmpty("travel_transaction_data", map).map(travelTransactionDataFromMap),
        retailtp_transaction_data = ApiSupport.selectPrefixedNoneIfEmpty("retailtp_transaction_data", map).map(retailTPTransactionDataFromMap),
        services_transaction_data = ApiSupport.selectPrefixedNoneIfEmpty("services_transaction_data", map).map(servicesTransactionDataFromMap),
        status = ApiSupport.selectPrefixedNoneIfEmpty("status", map).map(cybersourceResponseFromMap),
        csmdds = csmddsFromMap(ApiSupport.selectPrefixed("csmdds", map)))
  }
  
  def subTransactionFromMap(map: Map[String, String]) = {
    SubTransaction(
        site_id = map.get("site_id").get, 
        amount = map.get("amount").map(_.toLong).get,
        original_amount = map.get("original_amount").map(_.toLong),
        installments = map.get("installments").map(_.toInt),
        nro_trace = map.get("nro_Trace"),
        subpayment_id = map.get("subpayment_id").map(_.toLong),
        status = map.get("status").map(_.toInt))
  }

  def agroInstallmentDataFromMap(map: Map[String, String]) = {
    InstallmentData(
      id = map.get("id").map(_.toInt).get,
      date = map.get("date").map(dt => new Date(dt.toLong)).get,
      amount = map.get("amount").map(_.toLong).get)
  }

  def datosTitularFromMap(map: Map[String, String]) = {
      DatosTitularResource(
                 email_cliente = map.get("email_cliente"),
                 tipo_doc = map.get("tipo_doc").map(_.toInt), 
                 nro_doc = map.get("nro_doc"),
                 calle = map.get("calle"),
                 nro_puerta = map.get("nro_puerta").map(_.toInt),
                 fecha_nacimiento = map.get("fecha_nacimiento"),
                 sexo_titular = map.get("sexo_titular"),
                 ip = map.get("ip"))
  }  
  
  def datosMedioPagoFromMap(map: Map[String, String]) = {
    DatosMedioPagoResource(
      medio_de_pago = map.get("medio_de_pago").map(_.toInt),
      id_moneda = map.get("id_moneda").map(_.toInt),
      marca_tarjeta = map.get("marca_tarjeta").map(_.toInt),
      nro_tarjeta = map.get("nro_tarjeta"),
      card_number_encrypted = map.get("card_number_encrypted"),
      nombre_en_tarjeta = map.get("nombre_en_tarjeta"),
      expiration_month = map.get("expiration_month"),
      expiration_year = map.get("expiration_year"),
      security_code = map.get("security_code"),
      last_four_digits = map.get("last_four_digits"),
      bin_for_validation = map.get("bin_for_validation"),
      card_number_length = map.get("card_number_length").map(_.toInt),
      id_plan = map.get("id_plan"),
      establishment_name = map.get("establishment_name"))
  }   
  
  
  def datosSiteFromMap(map: Map[String, String]) = {
    DatosSiteResource(
      site_id = map.get("site_id"),
      url_dinamica = map.get("url_dinamica"),
      param_sitio = map.get("param_sitio"),
      use_url_origen = map.get("use_url_origen").map(par => "true".equals(par.toLowerCase())),
      url_origen = map.get("url_origen"),
      referer = map.get("referer"),
      id_modalidad = map.get("id_modalidad"),
      enviarResumenOnLine = map.get("enviarResumenOnLine"),
      usaUrlDinamica = map.get("usaUrlDinamica").map(_.toBoolean),
      urlPost = map.get("urlPost"),
      mandarMailAUsuario = map.get("mandarMailAUsuario").map(_.toBoolean),
      mail = map.get("mail"),
      replyMail = map.get("replyMail"),
      banco = map.get("banco"),
      origin_site_id = map.get("origin_site_id"))
  }

  def installmentSPVFromMap(map: Map[String, String]) = {
    InstallmentSPV(
      code = map.get("code"),
      quantity = map.get("quantity")
    )
  }

  def datosSPVFromMap(map: Map[String, String]) = {
    DatosSPV(
      client_id = map.get("client_id"),
      identificator = map.get("identificator"),
      installment = installmentSPVFromMap(ApiSupport.selectPrefixed("installment", map))
    )
  }
  
    
}

/**
 * TODO Implementar TTL en las operaciones, para evitar llenar redis de claves espureas.
 * Ojo, dado que tambien se guarda en sets, habria que usar PUB/SUB para ser notificado de 
 * las expiraciones y remover la clave de los lugares donde esta indexada. O implementar un 
 * garbage collector periodico.
 */
@Singleton
class OperationResourceRepository @Inject() (jedisPoolProvider: JedisPoolProvider, configuration: Configuration, encryptionService: EncryptionService) extends JedisUtils {

  val logger = LoggerFactory.getLogger(getClass)
  logger.info("Configuracion pool Jedis: " + jedisPoolProvider.showConf)
  
  val jedisPool = jedisPoolProvider.get
  val operationPrefix = "operations:"
  val digitsMatcher = CharMatcher.digit()
  
  val operationTTLSeconds = configuration.getInt("sps.coretx.operation.ttlseconds").getOrElse(900)
  
  private def operationKey(txId: String) = operationPrefix + txId
  private def siteTransactionDetailKey(siteId: String) = s"sites:${siteId}:tx"
  private def siteOperationsKey(siteId:String) = s"operationBeenExecuted:${siteId}"
  private def medioPagoKey(mpId: Int) = s"mediospago:${mpId}"
  private val sequentialTransactionIdKey = "transactionId"
  private val sequentialRefundIdKey = "refundId"
  private val sequentialSubpaymentIdKey = "subpaymentId"
  private val sequentialPaymentConfirmIdKey = "paymentConfirmId"
  private val fsmStateField = "fsm_state"
  private val fsmKeyPrefix = "fsm:"
  private val fsmTimestampKey = "fsm:timestamp"
  
  
  def exists(txId: String) = evalWithRedis { _.exists(operationPrefix + txId) }
  
  private def toMap(operation: OperationResource): Map[String, String] = {
    var opMap = ApiSupport.toMap(operation)

    val ticketingItems = getItems(operation, 
        "fraud_detection.ticketing_transaction_data.items.", 
        (operation.fraud_detection.flatMap(_.ticketing_transaction_data).map { _.items }))
    opMap = opMap ++ ticketingItems

    val retailItems = getItems(operation, 
        "fraud_detection.retail_transaction_data.items.", 
        (operation.fraud_detection.flatMap(_.retail_transaction_data).map { _.items }))
    opMap = opMap ++ retailItems

    val retailTPItems = getItems(operation,
      "fraud_detection.retailtp_transaction_data.items.",
      (operation.fraud_detection.flatMap(_.retailtp_transaction_data).flatMap(_.items)))
    opMap = opMap ++ retailTPItems

    val digitalGoodsItems = getItems(operation, 
        "fraud_detection.digital_goods_transaction_data.items.", 
        (operation.fraud_detection.flatMap(_.digital_goods_transaction_data).map { _.items }))
    opMap = opMap ++ digitalGoodsItems

    val servicesItems = getItems(operation,
      "fraud_detection.services_transaction_data.items.",
      (operation.fraud_detection.flatMap(_.services_transaction_data).map { _.items }))
    opMap = opMap ++ servicesItems

    val travelPassengers = getPassengers(operation,
        "fraud_detection.travel_transaction_data.passengers.", 
        (operation.fraud_detection.flatMap(_.travel_transaction_data).map { _.passengers }))
    opMap = opMap ++ travelPassengers


    val csmdds = getCsmdds(operation, 
        "fraud_detection.csmdds.", 
        (operation.fraud_detection.flatMap(_.csmdds)))
    opMap = opMap ++ csmdds

    val ticketRequestTaxes = getTicketRequestTaxes(operation,
        "ticket_request.vep.taxes.",
        (operation.ticket_request.map(_.vep.taxes)))
    opMap = opMap ++ ticketRequestTaxes
    
    opMap
  }

  private def getTicketRequestTaxes(operation: OperationResource, prefix: String, oTaxes: Option[List[TaxesRequest]]) = {
    val mapItems = oTaxes match {
      case Some(taxes) => {
        val taxesMap = taxes.zipWithIndex.foldLeft(Map[String, String]()){(map, pair) =>
          val (item, ndx) = pair
          map ++ ApiSupport.prefixWith(prefix + ndx.toString, ApiSupport.toMap(item))
        }
        Map(prefix + "size" -> taxes.size.toString) ++ taxesMap
      }
      case None => Map()
    }
    mapItems
  }

  private def getItems(operation: OperationResource, prefix: String, oItems: Option[List[Item]]) = {
    val mapItems = oItems match {
      case Some(items) => {
        val itemsMap = items.zipWithIndex.foldLeft(Map[String, String]()){(map, pair) =>
          val (item, ndx) = pair
          map ++ ApiSupport.prefixWith(prefix + ndx.toString, ApiSupport.toMap(item))
        }
        Map(prefix + "size" -> items.size.toString) ++ itemsMap
      }
      case None => Map()
    }
    mapItems
  }
  
   private def getPassengers(operation: OperationResource, prefix: String, oItems: Option[List[Passenger]]) = {
    val mapItems = oItems match {
      case Some(items) => {
        val itemsMap = items.zipWithIndex.foldLeft(Map[String, String]()){(map, pair) =>
          val (item, ndx) = pair
          map ++ ApiSupport.prefixWith(prefix + ndx.toString, ApiSupport.toMap(item))
        }
        Map(prefix + "size" -> items.size.toString) ++ itemsMap
      }
      case None => Map()
    }
    mapItems
  }
  
  private def getCsmdds(operation: OperationResource, prefix: String, oCsmdds: Option[List[Csmdd]]) = {
    val mapCsmdds = oCsmdds match {
      case Some(csmdds) => {
        val itemsMap = csmdds.zipWithIndex.foldLeft(Map[String, String]()){(map, pair) =>
          val (item, ndx) = pair
          map ++ ApiSupport.prefixWith(prefix + ndx.toString, ApiSupport.toMap(item))
        }
        Map(prefix + "size" -> csmdds.size.toString) ++ itemsMap
      }
      case None => Map()
    }
    mapCsmdds
  }
  
  private def doUpdate(operationParameter: OperationResource)(implicit tx: Option[Transaction]) = {

    implicit val ttl: Int = operationParameter.ttl_seconds.filter(_ > 0).getOrElse(operationTTLSeconds)
    logger.info(s"ttl seconds: $ttl")

    val unencriptedOp = operationParameter.copy(last_update = Some(new Date()), ttl_seconds = Some(ttl))
    
    val operation = ensureEncripted(unencriptedOp)
    
    val opMap = toMap(operation)
    
    val subTxsKey = operationKey(operation.id) + ":subTx:"

    val agroInstallmentsKey = operationKey(operation.id) + ":agroInstallments:"

//    doWithRedisTx {redis =>
//      implicit val tx = Some(redis)
      storeMapInRedis(operationKey(operation.id), opMap)
      
      val subTxs = operation.sub_transactions
      val subTxByNdx = (0 to subTxs.size) zip subTxs
      subTxByNdx.foreach { ndxSubTx =>
        val (ndx, subTx) = ndxSubTx
        val subTxKey = subTxsKey + ndx
        storeMapInRedis(subTxKey, ApiSupport.toMap(subTx))
      }
      if (subTxs.nonEmpty) {
        val sizeKey = subTxsKey + "size"
    	  tx.get.set(sizeKey, subTxs.size.toString)
        expireKeyInTx(sizeKey, ttl)
      }

      val agroInstallments = operation.agro_data match {
        case Some(agro_data) => agro_data.installments
        case None => Nil
      }
      val agroInstallmentsNdx = (1 to agroInstallments.size) zip agroInstallments
      agroInstallmentsNdx.foreach { ndxAgroInstallment =>
        val (ndx, agroInstallment) = ndxAgroInstallment
        val agroInstallmentKey = agroInstallmentsKey + ndx
        storeMapInRedis(agroInstallmentKey, ApiSupport.toMap(agroInstallment))
      }
      if (agroInstallments.nonEmpty) {
        val sizeKey = agroInstallmentsKey + "size"
        tx.get.set(sizeKey, agroInstallments.size.toString)
        expireKeyInTx(sizeKey, ttl)
      }

//      val impDist = operation.imp_dist
//      val importeByNdx = (0 to impDist.size) zip impDist
//      val importesKey = operationKey(operation.transactionId) + ":impDist:"
//      importeByNdx.foreach { ndxImporte =>
//        val (ndx, importe) = ndxImporte
//        val importeKey = importesKey + ndx
//        println(s"storeMapInRedis  key: ${importeKey}")
//        storeMapInRedis(importeKey, Map("importe" -> importe.toString))
// /    }
    
//    if (!operation.subTransactions.isEmpty) {
//        doWithRedis { _.expire(subTxsKey + "size", ttl) }
//    }    
  }
  
  private def ensureEncripted(operation: OperationResource): OperationResource = {
    
    val operationEnrypted = operation.datos_medio_pago.map {dmp => 
      val dmpSCe = dmp.security_code.map{ sc => 
        dmp.copy(security_code = Some(encryptionService.encriptarBase64(sc)))  
      }.getOrElse(dmp)
      val dmpNTe = dmpSCe.nro_tarjeta.map{ nt => 
        if(digitsMatcher.matchesAllOf(nt)) { // No está encriptado
          dmpSCe.copy(nro_tarjeta = Some(encryptionService.encriptarBase64(nt)))  
        } else {
          dmpSCe
        }
      }.getOrElse(dmpSCe)
      operation.copy(datos_medio_pago = Some(dmpNTe))
    }.getOrElse(operation)
    operationEnrypted
  }
   
  private def decrypt(operation: OperationResource): OperationResource = {
    
    val operationDecrypt = operation.datos_medio_pago.map {dmp => 
      val dmpSCe = dmp.security_code.map{ sc => 
          dmp.copy(security_code = Some(encryptionService.desencriptarBase64(sc))) 
      }.getOrElse(dmp)
      val dmpNTe = dmpSCe.nro_tarjeta.map{ nt => 
          dmpSCe.copy(nro_tarjeta = Some(encryptionService.desencriptarBase64(nt)))  
      }.getOrElse(dmpSCe)
      operation.copy(datos_medio_pago = Some(dmpNTe),
        datos_banda_tarjeta = operation.datos_banda_tarjeta.map(banda => {
          DatosBandaTarjeta(card_track_1 = banda.card_track_1.map(track1 => encryptionService.desencriptarBase64(track1)),
            card_track_2 = banda.card_track_2.map(track2 => encryptionService.desencriptarBase64(track2)),
            input_mode = banda.input_mode)
        }))
    }.getOrElse(operation)
    operationDecrypt
  }
  
  private def doValidateNroOperacion(operation: OperationResource, allowedRepetitions: Int = 0): Try[(Option[String], Int)] = Try {
    import scala.collection.JavaConverters._
    val nroOperacion = operation.nro_operacion.get
    val siteTxDetailKey = siteTransactionDetailKey(operation.siteId)
    //List(Status, reps, paymentType, fraudDetectionDecision)
    val operationState = evalWithRedis { _.hmget(siteTxDetailKey, s"${nroOperacion}:status", s"${nroOperacion}:reps", s"${nroOperacion}:paymentType", s"${nroOperacion}:fraudDetectionDecision") }.asScala.toArray.map(Option(_))
    if(operationState(0).isDefined) {
      if(allowedRepetitions < 1) {
        logger.error("Site does not support retries")
        throw ErrorFactory.validationException("repeated", "site_transaction_id")
      }
      val count = operationState(1).fold(0)(_.toInt)
      if(count > allowedRepetitions) {
        logger.error(s"Site does not support more retries. Retries: ${count} > ${allowedRepetitions}")
        throw ErrorFactory.validationException("repeated", "site_transaction_id")
      } 
      validatePaymentState(operationState(0),  operationState(3), operation)
      validatePaymentCondition(operationState(2), operation)
      (Some(siteTxDetailKey), count)    
    } else {
      (Some(siteTxDetailKey), 0) 
    }
  }
  
  def storeOperacionAProcesar(operation: OperationResource) = {
    val modality = operation.datos_site.get.id_modalidad
    updateNroOperacionStatus(operation.nro_operacion.get, operation.siteId, Ingresada(), getPaymentType(modality), None, None)
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
  
  private def validatePaymentState(status: Option[String], fdd: Option[String], operation: OperationResource) = {
    status match {
      case Some(state) => TransactionState.apply(state.toInt) match {
        case Rechazada() | TxAnulada() | FacturaNoGenerada() => {
          fdd match {
            case Some(fraudDetection) => FraudDetectionDecision.fromString(fraudDetection) match {
              case FDRed() | FDBlue() => {
                logger.error(s"Payment already realized, state: ${state} - csr: ${fraudDetection}")
                throw ErrorFactory.validationException("repeated", "site_transaction_id")
              }
              case otherColour => logger.debug(s"Payment already realized, state: ${state} - csr: ${otherColour.toString}")
            }
            case None => logger.debug(s"Payment already realized, state: ${state} - without csr")
          }
        }
        case otherState => {
          logger.error(s"Payment already realized, state: ${otherState.toString}")
          throw ErrorFactory.validationException("repeated", "site_transaction_id")
        }
      }
      case None => logger.debug(s"Payment not yet realized")
    }
  }
  
  private def validatePaymentCondition(paymentType: Option[String], operation: OperationResource) = {
    paymentType match {
      case Some(pType) => {
        operation.datos_site.get.id_modalidad match {
          case Some("S") => if(!pType.equals("S")) throw ErrorFactory.validationException("Previous payment with single modality", "site_transaction_id")
          case _ => if(!pType.equals("N")) throw ErrorFactory.validationException("Previous payment with distributed modality", "site_transaction_id")
        }
      }
      case None => logger.debug(s"Payment not yet realized")
    }
  }
  
  def validatePaymentCountSubpayments(operation: OperationResource, subpayments: List[DistributedTxElement] ) = Try {
    val nroOperacion = operation.nro_operacion.get
    val siteTxDetailKey = siteTransactionDetailKey(operation.siteId)
    import scala.collection.JavaConversions._
    val subpaymentsSaved = (evalWithRedis { _.hmget(siteTxDetailKey, s"${nroOperacion}:countSubpayments") }.get(0))
    val oSubpaymentsSaved = if(subpaymentsSaved == null) None else Some(subpaymentsSaved)
    oSubpaymentsSaved.map(sSaved => {
      val sites = sSaved.split(",").toSet
      val subpaymentsSites = subpayments.map(sp => sp.site.id).toSet
      if(!subpaymentsSites.containsAll(sites) || !sites.containsAll(subpaymentsSites)) {
        throw ErrorFactory.validationException("Previous payment without equal subpayments", "site_transaction_id")
      }
    })
  }
  
  def updateNroOperacionStatus(nroOperation: String, siteId: String, status: TransactionState, paymentType: String,  subTransactions: Option[List[SubTransaction]], fraudDetectionDecision: Option[FraudDetectionDecision]) {
    import scala.collection.JavaConversions._
    val key = siteTransactionDetailKey(siteId)
    doWithRedis { redis => 
      status match {
        case Autorizada() => {
          logger.info(s"nroOperacion: $nroOperation status $Autorizada")
        	redis.hmset(key, Map(s"${nroOperation}:status" -> status.id.toString))
          redis.hdel(key, s"${nroOperation}:paymentType",
              s"${nroOperation}:fraudDetectionDecision",
              //TODO este dato puede eliminarse en este momento. Por ahora las devoluciones no utilizan redis, consultan SQL
              //Este campo contiene los sitios (hijos) en caso de distribuidas. Revisar si se va a utilizar o eliminar 
              //s"${nroOperation}:countSubpayments",
              s"${nroOperation}:reps")
        }
        case Ingresada() => {
          logger.info(s"nroOperacion: $nroOperation status $Ingresada")
          val fieldMap = Map(s"${nroOperation}:status" -> status.id.toString, s"${nroOperation}:paymentType" -> paymentType) ++
            fraudDetectionDecision.fold(Map.empty[String,String]) {
              fdd => Map( s"${nroOperation}:fraudDetectionDecision" -> fdd.toString )
            } ++
            subTransactions.fold(Map.empty[String,String]) {
              stxs => Map( s"${nroOperation}:countSubpayments" -> stxs.map(_.site_id).mkString(",") )
            }
          redis.hmset(key, fieldMap)
        }
        case other => {
          logger.info(s"nroOperacion: $nroOperation status: $other")
          val fieldMap = Map(s"${nroOperation}:status" -> status.id.toString, s"${nroOperation}:paymentType" -> paymentType) ++
            fraudDetectionDecision.fold(Map.empty[String,String]) { 
              fdd => Map( s"${nroOperation}:fraudDetectionDecision" -> fdd.toString ) 
            } ++
            subTransactions.fold(Map.empty[String,String]) {
              stxs => Map( s"${nroOperation}:countSubpayments" -> stxs.map(_.site_id).mkString(",") )
            }
          redis.hmset(key, fieldMap)
          redis.hincrBy(key, s"${nroOperation}:reps", 1)
//TODO eliminar una vez que se haya aprobado el cambio
//        	redis.hmset(key, Map(s"${nroOperation}:paymentType" -> paymentType))
//        	fraudDetectionDecision.map(fdd => redis.hmset(key, Map(s"${nroOperation}:fraudDetectionDecision" -> fdd.toString)))
//        	subTransactions.map(sTransactions => {
//        		val sites = sTransactions.map(subTransaction => subTransaction.site_id)
//        				redis.hmset(key, Map(s"${nroOperation}:countSubpayments" -> sites.mkString(",")))
//        	})
//        	redis.hincrBy(key, s"${nroOperation}:reps", 1)
        }
      }
    }
  }

  private def validateNroOperacion(operation: OperationResource, allowedRepetitions: Int = 0): (Option[String], Int) = {
    
    val nroOperacion = operation.nro_operacion.get
    
    val id = operation.id
    
    if(nroOperacion == id) {
      //No se pasa el numero de operacion. Webtx
      // Si el nro de operacion es igual al id, o bien entro por create token de la API Rest y no hay que validar aun el nro de operacion
      // o van a usar el id que creamos nosotros que es unico.
      (None,0)
    }
    else {

      val oopInRepo = retrieve(operation.id)
      oopInRepo match {

        case None => {
          //Si se pasa el numero de operacion. Webtx
          // Si la operacion no existe en el repo (y el nro de operacion no es igual al id), validarlo.
          doValidateNroOperacion(operation, allowedRepetitions)  match {
            case Success(result) =>  result
            case Failure(e) => throw e
          }
        }

        case Some(opInRepo) => {
          val originSiteId = opInRepo.datos_site.flatMap(_.origin_site_id)
          operation.datos_site.flatMap(_.origin_site_id).filter(_.equals(originSiteId.orNull)) match {
            case Some(originSiteId) => {
            	//Se realiza el pago
            	doValidateNroOperacion(operation, allowedRepetitions)  match {
              	case Success(result) =>  result
              	case Failure(e) => throw e
            	}
            }
          case _ => {
                logger.warn(s"""Invalid origin site""")
                throw ErrorFactory.validationException(ErrorMessage.INVALID_SITE, ErrorMessage.DATA_SITE_ORIGIN_SITE_ID)
              }
          }
        }
      }
    }
  }
  
  
  def flagAsUsed(operation: OperationResource) = {
    doWithRedis { redis => 
      redis.hset(operationKey(operation.id), "used", "true")
    }
  }
  
  
  def store(operation: OperationResource, allowedRepetitions: Int = 0) = {
    
    if(operation.id == null || operation.id.trim.isEmpty()) {
      throw ErrorFactory.missingDataException(List("id"))
    }
    
    if(operation.nro_operacion.isEmpty) {
      throw ErrorFactory.missingDataException(List("site_transaction_id"))
    }
    
    val (siteTxsKey,retries) = validateNroOperacion(operation, allowedRepetitions)
    
    // TODO pensar si está bien pisar la transacción anterior sin mas (y si es de un 3ro?)
    // Obviamente esto es una API interna
    doWithRedisTx {redis =>
      implicit val tx = Some(redis)
      doUpdate(operation.copy(retries = Some(retries)))
    }
  }

  def update(operation: OperationResource, allowedRepetitions: Int = 0) = {
    if(operation.id == null || operation.id.trim.isEmpty())
      throw ErrorFactory.missingDataException(List("id"))
    
    val (siteTxsKey,retries) = validateNroOperacion(operation, allowedRepetitions)
    
    doWithRedisTx {redis =>
      implicit val tx = Some(redis)
      doUpdate(operation.copy(retries = Some(retries)))
    }
  }
  
  def retrieve(txId: String): Option[OperationResource] = {
    val map = evalWithRedis { _.hgetAll(operationKey(txId)) }.asScala.toMap
    if(map.isEmpty) None
    else {
      val op = OperationResourceRepository.fromMap(map)
      
      val subTxsKey = operationKey(txId) + ":subTx:"
      
      val subTxs = Option(evalWithRedis { _.get(subTxsKey + "size") }) match {
        case Some(cantSubTxs) => {
          (0 to cantSubTxs.toInt - 1).map {ndx =>
            val subTxKey = subTxsKey + ndx
            val subTxMap = evalWithRedis { _.hgetAll(subTxKey)}.asScala.toMap
            OperationResourceRepository.subTransactionFromMap(subTxMap)
          }.toList
        }
        case None => Nil
      }

      val agroInstallmentsKey = operationKey(txId) + ":agroInstallments:"

      val agroInstallments = Option(evalWithRedis { _.get(agroInstallmentsKey + "size")}) match {
        case Some(cantInst) => {
          (1 to cantInst.toInt).map {ndx =>
            val iKey = agroInstallmentsKey + ndx
            val iMap = evalWithRedis { _.hgetAll(iKey)}.asScala.toMap
            OperationResourceRepository.agroInstallmentDataFromMap(iMap)
          }.toList
        }
        case None => Nil
      }

      val esMPAgro = op.datos_medio_pago.get.medio_de_pago match {
        case Some(idMP) => evalWithRedis { _.hgetAll(medioPagoKey(idMP))}.asScala.toMap.get("isAgro").map(_.toBoolean).get
        case None => false
      }

      Some(op.copy(sub_transactions = subTxs, agro_data = op.agro_data.map( ad => ad.copy(installments = agroInstallments, payment_method_is_agro = esMPAgro))))
    }
  }
  
  
  def retrieveDecrypted(txId: String): Option[OperationResource] = {
    retrieve(txId).map(op => decrypt(op))
  }
  
  def decrypted(cardNumber: String): String = {
    if (digitsMatcher.matchesAllOf(cardNumber)) {
      cardNumber
    } else {
    	encryptionService.desencriptarBase64(cardNumber)      
    }
  }
  
  
  def addOperationBeenExecuted(siteId:String,refundId:Long) = {
    val operationRedisKey = siteOperationsKey(siteId)
    evalWithRedis { _.sadd(operationRedisKey, refundId.toString()) }
  }
  
  def removeOperationBeenExecuted(siteId:String,refundId:Long) = {
    val operationRedisKey = siteOperationsKey(siteId)
    evalWithRedis { _.srem(operationRedisKey, refundId.toString()) }
  }
  
  def operationBeenExecuted(siteId:String,refundId:Long) = {
    val operationRedisKey = siteOperationsKey(siteId)
    evalWithRedis { _.sismember(operationRedisKey, refundId.toString())}
  }
  
  def newChargeId = evalWithRedis { _.incr(sequentialTransactionIdKey) } // TODO Ver como es el setup inicial
  
  def newRefundId = evalWithRedis { _.incr(sequentialRefundIdKey) } // TODO Ver como es el setup inicial
  
  def newSubpaymentId = evalWithRedis { _.incr(sequentialSubpaymentIdKey) } // TODO Ver como es el setup inicial

  def newPaymentConfirmId = evalWithRedis { _.incr(sequentialPaymentConfirmIdKey) } // TODO Ver como es el setup inicial
  
  /*
   * Se comentan las lineas para dejar de persistir 
   * las keys fsm:* en Redis que no estan siendo utilizadas
   */
  def transitionOperation(operationId: String, state: OperationFSM) = {
    val operationRedisKey = operationKey(operationId)
    val newStateName = state.toString
//    val current = evalWithRedis { _.hget(operationRedisKey, fsmStateField) }
//    val ocurrent = if(current == null) None else Some(current)
    doWithRedisTx { redis =>  
//      ocurrent.foreach(stateSuffix => redis.srem(fsmKeyPrefix + stateSuffix, operationId))
//      redis.sadd(fsmKeyPrefix + newStateName, operationId)
      redis.hset(operationRedisKey, fsmStateField, newStateName)
      redis.hset(operationRedisKey, fsmTimestampKey, System.currentTimeMillis().toString)
    }
  }
  
}
