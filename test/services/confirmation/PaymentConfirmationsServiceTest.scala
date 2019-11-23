package services.confirmation

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import com.decidir.coretx.api.{ApiException, ErrorFactory, _}
import com.decidir.coretx.domain.{OperationFSM, _}
import com.decidir.protocol.api._
import org.mockito.Matchers.{any, eq => equ}
import org.mockito.Mockito.{doNothing, when}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import services.converters.OperationResourceConverter
import services.payments._
import services.protocol.ProtocolService

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import com.decidir.util.RefundsLockRepository


class PaymentConfirmationsServiceTest extends PlaySpec with MockitoSugar {

  private val sdf = new SimpleDateFormat("EEE MMM dd hh:mm:ss z yyyy", Locale.ENGLISH)

  /**
   * Payment Confirm Request Operation Resource
   */
  private val REQUEST_OPERATION_RESOURCE: OperationResource =
    OperationResource(
      id = "",
      charge_id = Some(4394921),
      monto = Some(20000),
      datos_site = Some(DatosSiteResource(
          site_id = Some("28465555"),
          origin_site_id = Some("28465555"))))

  private val SITE_ID: String = REQUEST_OPERATION_RESOURCE.siteId

  private val CHARGE_ID: Long = REQUEST_OPERATION_RESOURCE.charge_id.getOrElse(0)

  /**
   * Pre-Payment Operation Response
   */
  private val PAYMENT_OPERATION_RESOURCE: OperationResource = OperationResource(
      id = "53bda39d-5072-4b3c-8f5c-5d7e374bdfa1",
      nro_operacion = Some("automation regresion 1523045736"),
      None,
      charge_id = Some(4394921),
      None,
      monto = Some(20000),
      original_amount = Some(20000),
      cuotas = Some(1),
      sub_transactions = List(),
      datos_titular = Some(DatosTitularResource(
        tipo_doc = Some(1),
        nro_doc = Some("23968498"),
        ip = Some("0:0:0:0:0:0:0:1")
      )),
      datos_medio_pago = Some(DatosMedioPagoResource(
        medio_de_pago = Some(1),
        card_brand = Some("Visa"),
        id_moneda = Some(1),
        marca_tarjeta = Some(4),
        nro_tarjeta = Some("4509790112684851"),
        nombre_en_tarjeta = Some("Jorge Perez"),
        expiration_month = Some("07"),
        expiration_year = Some("19"),
        bin_for_validation = Some("450979"),
        nro_trace = Some("603"),
        cod_autorizacion = Some("171543"),
        nro_devolucion = None,
        last_four_digits = Some("4851"),
        card_number_length = Some(16),
        id_operacion_medio_pago = Some("      000603"),
        nro_terminal = Some("45879859"),
        nro_ticket = Some("594"),
        motivo = Some("APROBADA  (authno)"),
        establishment_name = Some("prueba soft")
      )),
      datos_site = Some(DatosSiteResource(
        site_id = Some(SITE_ID),
        url_dinamica = None,
        param_sitio = None,
        use_url_origen = None,
        url_origen = None,
        referer = None,
        id_modalidad = Some("N"),
        None,
        enviarResumenOnLine = Some("B"),
        usaUrlDinamica = Some(true),
        urlPost = Some("http://192.168.74.24:10005"),
        mandarMailAUsuario = Some(false),
        mail = Some("jorge.perez@mail.com"),
        replyMail = Some("jorge.perez@mail.com"),
        None,
        origin_site_id = Some(SITE_ID)
      )),
      creation_date = Some(sdf.parse("Fri Apr 06 17:15:36 ART 2018")),
      last_update = Some(sdf.parse("Fri Apr 06 17:15:36 ART 2018")),
      ttl_seconds = Some(0),
      idTransaccion = Some("46291310"),
      fraud_detection = Some(FraudDetectionData(
        device_unique_id = Some("12345"))),
      retries = Some(0),
      origin = Some(Requester(app = Some("RESTTX")))
    )

  private val CUENTA: Cuenta = Cuenta(
    idMedioPago = "1",
    idProtocolo = 7,
    idBackend = "6",
    nroId = "26139568",
    estaHabilitadaParaOperarConPlanN = false,
    habilitado = true,
    numeroDeTerminal = "45879859",
    utilizaAutenticacionExterna = false,
    autorizaEnDosPasos = true,
    autoriza2PLimiteInferior = 50,
    autoriza2PLimiteSuperior = 100,
    planCuotas = "0",
    pasoAutenticacionExterna = true,
    pasoAutenticacionExternaSinServicio = true,
    formatoNroTarjetaVisible = "",
    password = "",
    pagoDiferidoHabilitado = false,
    aceptaSoloNacional = false,
    tipoPlantilla = "1",
    nroIdDestinatario = "")

  private val SITE: Site = Site(
    id = "28464383",
    description  = Some("Ticketek"),
    habilitado = true,
    url = "http://testportal.passto.com.ar;https://sps.decidir.com;https://192.168.135.55;http://localhost:19001;http://marathon-lb.infrastructure.marathon.mesos:10001",
    agregador = "N",
    timeoutCompra = 20000,
    validaRangoNroTarjeta = false,
    transaccionesDistribuidas = "S",
    montoPorcent = "P",
    reutilizaTransaccion = true,
    enviarResumenOnLine = "B",
    ppb = PostbackConf(
      usaUrlDinamica = true,
      urlPost = "http://192.168.74.24:10005"
    ),
    validaOrigen = true,
    mailConfiguration = MailConfiguration(
      mandarMailAUsuario = false,
      mandarMailASite = true,
      mail = "jorge.perez@mail.com",
      replyMail = "jorge.perez@mail.com"
    ),
    cuentas = List(CUENTA),
    rangos = List(),
    mensajeria = "",
    subSites = List(
      InfoSite(idSite = "04052021", porcentaje = 30.0F),
      InfoSite(idSite = "04052020", porcentaje = 30.0F),
      InfoSite(idSite = "04052019",porcentaje = 30.0F)
    ),
    cyberSourceConfiguration = None,
    merchants = List(),
    parentSiteId = None,
    hashConfiguration = HashConfiguration(useHash = false, firstHashDate = None),
    encrypt = Encrypt(encyption = None, cardNumberEnc = true),
    mensajeriaMPOS = Some("N"),
    isTokenized = false,
    timeToLive = 0)

  private val CARD_BRAND_OPERATIONS: CardBrandOperations = CardBrandOperations(
    annulment = true,
    annulment_pre_approved = true,
    refundPartialBeforeClose = true,
    refundPartialBeforeCloseAnnulment = true,
    refundPartialAfterClose = true,
    refundPartialAfterCloseAnnulment = true,
    refund = true,
    refundAnnulment = true,
    twoSteps = true
  )

  private val MEDIO_DE_PAGO: MedioDePago = MedioDePago(
    id = "1",
    descripcion = "Visa",
    idMoneda = Some("1"),
    idMarcaTarjeta = Some(4),
    cardBrand = "Visa",
    limite = 0.0,
    backend = 6,
    protocol = 7,
    operations = CARD_BRAND_OPERATIONS,
    bin_regex = "^4[0-9]{2}(?:[0-9]{3})?$",
    hasBlackList = true,
    hasWhiteList = false,
    validateLuhn = true,
    cyberSource = true,
    tokenized = true,
    isAgro = false
  )

  private val MARCA_TARJETA: MarcaTarjeta =  MarcaTarjeta(
    id = 4,
    descripcion = "Visa",
    codAlfaNum = "VISA",
    urlServicio = None,
    sufijoPlantilla = Some("_visaex"),
    verificaBin = None,
    rangosNacionales = List(Pair("4509790000000000","4509809999999999"))
  )

  private val MONEDA: Moneda = Moneda(
    id = "1",
    descripcion = "Pesos",
    simbolo = "$",
    idMonedaIsoAlfa = "ARA",
    idMonedaIsoNum = "032"
  )

  private val OPERATION_DATA: OperationData = OperationData(
    resource = PAYMENT_OPERATION_RESOURCE,
    site = SITE,
    medioDePago = MEDIO_DE_PAGO,
    marcaTarjeta = MARCA_TARJETA,
    moneda = MONEDA)

  private val POSTBACK_HASH  = Map(
    "emailcomprador" -> "",
    "validanropuerta" -> "NO",
    "estadoentrega" -> "",
    "ciudadentrega" -> "",
    "validatipodoc" -> "SI",
    "codautorizacion" -> "171543",
    "validafechanac" -> "NO",
    "validanrodoc" -> "SI",
    "paisentrega" -> "",
    "validaciondomicilio" -> "VTE0011",
    "nrodoc" -> "23968498",
    "nroticket" -> "594",
    "tarjeta" -> "Visa",
    "direccionentrega" -> "",
    "nombreentrega" -> "",
    "sitecontracargo" -> "",
    "motivoadicional" -> "",
    "zipentrega" -> "",
    "resultado" -> "APROBADA",
    "noperacion" -> "automation regresion 1523045736",
    "titular" -> "Jorge Perez",
    "fechacontracargo" -> "",
    "paramsitio" -> "",
    "telefonocomprador" -> "",
    "cuotas" -> "1",
    "motivocontracargo" -> "",
    "fechaentrega" -> "",
    "moneda" -> "Pesos",
    "idmotivo" -> "0",
    "pedido" -> "",
    "tipodocdescri" -> "DNI",
    "mensajeentrega" -> "",
    "fechahora" -> "06/04/2018 05:15:36",
    "barrioentrega" -> "",
    "motivo" -> "APROBADA  (authno)",
    "resultadoautenticacionvbv" -> "0",
    "codigopedido" -> "",
    "nrotarjetavisible" -> "4851",
    "paymentid" -> "4394921",
    "monto" -> "200,00",
    "tipodoc" -> "1")

  /**
   * Retrieve Pre-Payment Operation Execution Response
   */
  private val OPERATION_EXECUTION_RESPONSE: OperationExecutionResponse = OperationExecutionResponse(
      status = 11,
      authorizationCode = "171543",
      authorized = true,
      validacion_domicilio = Some("VTE0011"),
      operationResource = Some(PAYMENT_OPERATION_RESOURCE),
      postbackHash = Some(POSTBACK_HASH),
        subPayments = Some(List()))

  /**
   * Protocol Service Payment Confirm Request
   */
  private val PROTOCOL_RESOURCE: ProtocolResource = ProtocolResource(
    id_operacion = "53bda39d-5072-4b3c-8f5c-5d7e374bdfa1",
    nro_id = Some("26139568"),
    nro_operacion_site = "automation regresion 1523045736",
    utiliza_autenticacion_externa = false,
    pago_diferido_habilitado = false,
    es_autorizada_en_dos_pasos = true,
    monto = 20000,
    codigo_iso_num = Some("032"),
    plan_cuotas = Some("0"),
    referer = "NO_REFERER",
    comprador = Comprador(
      nro_tarj_vis = "4851",
      nro_tarj = "4509790112684851",
      vencimiento_tarjeta = "1907",
      nombre_en_tarjeta = "Jorge Perez",
      calle = None,
      nro_puerta = None,
      email = None,
      nro_doc = Some("23968498"),
      fecha_nacimiento = None,
      tipo_doc = Some(1),
      cuotas = 1,
      cod_seguridad = None,
      nombre_establecimiento = Some("prueba soft"),
      ip = "0:0:0:0:0:0:0:1"
    ),
    gds = None,
    mensajeria = "",
    codigo_alfa_num = "VISA",
    codigo_autorizacion = "171543",
    ids = Ids(
      id_protocolo = 7,
      id_site = SITE_ID,
      id_transaccion = 0,
      id_marcatarjeta = "4",
      id_cliente = 0,
      id_mediopago = "1",
      id_backend = "6",
      id_charge = "4394921",
      id_plan = None,
      id_operacion_medio_pago = Some("      000603"),
      nro_devolucion = None,
      nro_id_destinatario = Some(""),
      banco = None
    ),
    fechas = Fechas(
      fecha_original = sdf.parse("Fri Apr 06 17:15:53 ART 2018"),
      fecha_inicio = sdf.parse("Fri Apr 06 17:15:36 ART 2018"),
      fechavto_cuota_1 = None,
      fecha_pago_diferido = None
    ),
    terminal_y_tickets = Some(TerminalYTickets(
      nro_terminal = "45879859",
      nro_trace = 603,
      nro_ticket = 594,
      nro_ticket_new = None
    )),
    aggregate_data = None,
    sitio_agregador = "N",
    pago_offline = None,
    isMPOS = false,
    extended_data = ProtocolResourceExtension())

  /**
   * Successful Payment confirm operation response.
   */
  private val OPERATION_RESPONSE: Try[OperationResponse] = Success(OperationResponse(
    statusCode = 200,
    idMotivo = 0,
    terminal = Some("45879859"),
    nro_trace = Some("604"),
    nro_ticket = Some("594"),
    cod_aut = Some(""),
    tipoOperacion = 4,
    historicalStatusList = List(
      HistoricalStatus(motivoId = -1, estadoId = 101, fecha = sdf.parse("Fri Apr 06 17:15:53 ART 2018")),
      HistoricalStatus(motivoId = -1, estadoId = 104, fecha = sdf.parse("Fri Apr 06 17:15:53 ART 2018")),
      HistoricalStatus(motivoId = -1, estadoId = 105, fecha = sdf.parse("Fri Apr 06 17:15:53 ART 2018")),
      HistoricalStatus(motivoId = 0, estadoId = 103, fecha = sdf.parse("Fri Apr 06 17:15:53 ART 2018"))
    ),
    site_id = SITE_ID,
    cardErrorCode = None,
    idOperacionMedioPago = "000604",
    motivoAdicional = None
  ))

  private val executionContext: ExecutionContext = ExecutionContext.global

  /**
    * Mock Successful case Services.
    */
  private def mockSuccessfulLegacyTransactionServiceClient(): LegacyTransactionServiceClient = {
    val legacyTransactionServiceClient = mock[LegacyTransactionServiceClient]
    
    doNothing.when(legacyTransactionServiceClient).update(any[UpdateTxOnOperation])
    doNothing.when(legacyTransactionServiceClient).insert(any[InsertTxHistorico])
    
    legacyTransactionServiceClient
  }

  private def mockSuccessfulLegacyOperationServiceClient(): LegacyOperationServiceClient = {
    val legacyOperationServiceClient = mock[LegacyOperationServiceClient]
    
    doNothing.when(legacyOperationServiceClient).insert(any[InsertConfirmationOpx]())
    
    legacyOperationServiceClient
  }
  
  private def mockSuccessfulProtocolService(): ProtocolService = {
    val protocolService = mock[ProtocolService]
    
    when(protocolService.paymentConfirm(any[ProtocolResource])).thenReturn(Future.successful(OPERATION_RESPONSE))
    
    protocolService
  }
  
  private def mockSuccessfulOperationResourceRepository(): OperationResourceRepository = {
    val operationResourceRepository = mock[OperationResourceRepository]

    when(operationResourceRepository.newPaymentConfirmId).thenReturn(1L)
    doNothing.when(operationResourceRepository).
      transitionOperation(equ(OPERATION_DATA.resource.id), any[OperationFSM])

    operationResourceRepository
  }

  private def mockSuccessfulOperationResourceConverter(): OperationResourceConverter = {
    val operationResourceConverter = mock[OperationResourceConverter]

    when(operationResourceConverter
      .operationResource2OperationData(PAYMENT_OPERATION_RESOURCE)).thenReturn(OPERATION_DATA)

    operationResourceConverter
  }

  private def mockSuccessfulTransactionRepository(): TransactionRepository = {
    val transactionRepository = mock[TransactionRepository]

    when(transactionRepository
      .retrieveCharge(
        siteId = SITE_ID,
        chargeId = CHARGE_ID)).thenReturn(Success(OPERATION_EXECUTION_RESPONSE))
    when(transactionRepository
      .retrieveTransState(PAYMENT_OPERATION_RESOURCE.idTransaccion.get)).thenReturn("11")

    transactionRepository
  }

  private def mockCustomAmountTransactionAndConverter(amount: Long):(TransactionRepository, OperationResourceConverter ) = {
    val transactionRepository = mock[TransactionRepository]

    val OER = OPERATION_EXECUTION_RESPONSE.copy(operationResource = Some(PAYMENT_OPERATION_RESOURCE.copy(monto = Some(amount))))

    when(transactionRepository
      .retrieveCharge(siteId = SITE_ID, chargeId = CHARGE_ID)).thenReturn(Success(OER))

    when(transactionRepository
      .retrieveTransState(PAYMENT_OPERATION_RESOURCE.idTransaccion.get)).thenReturn("11")

    val operationResourceConverter = mock[OperationResourceConverter]

    when(operationResourceConverter
      .operationResource2OperationData(OER.operationResource.get)).thenReturn(OPERATION_DATA)

    (transactionRepository, operationResourceConverter)
  }

  private def mockRefundsLockRepository(): RefundsLockRepository = {
    val refundsLockRepository = mock[RefundsLockRepository]

    when(refundsLockRepository
      .isLocked(id = CHARGE_ID.toString)).thenReturn(Success(false))
    when(refundsLockRepository
      .getLock(id = CHARGE_ID.toString)).thenReturn(Success(true))
    when(refundsLockRepository
      .releaseLock(id = CHARGE_ID.toString)).thenReturn(Success(true))
      refundsLockRepository
  }
  private def mockConfiguration(): Configuration = {
     val configuration = mock[Configuration]

    when(configuration.getBoolean("lock.refunds.allowed")).thenReturn(Some(true))
    configuration
  }
 


  "PaymentConfirmationsService" should {
    "Confirm Successfully" in {

      /**
       * Initializing Payment Confirmations Service
       */
      val service: PaymentConfirmationsService = new PaymentConfirmationsService(
        executionContext,
        transactionRepository = mockSuccessfulTransactionRepository(),
        protocolService = mockSuccessfulProtocolService(),
        operationResourceConverter = mockSuccessfulOperationResourceConverter(),
        operationRepository = mockSuccessfulOperationResourceRepository(),
        legacyTxService = mockSuccessfulLegacyTransactionServiceClient(),
        legacyOpxService = mockSuccessfulLegacyOperationServiceClient(),
        configuration= mockConfiguration(),
        refundsLockRepository= mockRefundsLockRepository())

      /**
       * Executing Payment Confirm Operation
       */
      val response = Await.result(
        service.confirm(
          siteId = SITE_ID,
          chargeId = CHARGE_ID,
          operationResource = REQUEST_OPERATION_RESOURCE,
          user = None), Duration(3000L, MILLISECONDS))

      response._1 must not be None
      val operationExecutionResponse = response._1.get
      operationExecutionResponse.operationResource must not be None

      /**
       * Replacing expected confirmPaymentResponse's Date with response date.
       */

      println(response)

      val date: Date = operationExecutionResponse.operationResource.get.confirmed.get.date
      operationExecutionResponse.operationResource.get.copy(
        confirmed = Some(operationExecutionResponse.operationResource.get.confirmed.get
          .copy(date = date))) mustBe PAYMENT_OPERATION_RESOURCE
        .copy(confirmed = Some(ConfirmPaymentResponse(1, 20000, date)))
      response._2 mustBe a [Try[OperationResponse]]

      response._2 mustBe OPERATION_RESPONSE

      response._1 mustNot be (None)
      val resultOperationExecResponse = response._1.get
      resultOperationExecResponse.operationResource mustNot be (None)

      /**
       * Validate Result OperationResource
       */

      val resultOperationResource = resultOperationExecResponse.operationResource.get

      resultOperationResource.id mustBe "53bda39d-5072-4b3c-8f5c-5d7e374bdfa1"
      resultOperationResource.nro_operacion mustBe Some("automation regresion 1523045736")
      resultOperationResource.user_id mustBe None
      resultOperationResource.charge_id mustBe Some(4394921)
      resultOperationResource.fechavto_cuota_1 mustBe None
      resultOperationResource.monto mustBe Some(20000)
      resultOperationResource.original_amount mustBe Some(20000)
      resultOperationResource.cuotas mustBe Some(1)
      resultOperationResource.sub_transactions mustBe List()
      resultOperationResource.datos_titular mustNot be (None)

      val resultDatosTitular = resultOperationResource.datos_titular.get

      resultDatosTitular.email_cliente mustBe None
      resultDatosTitular.tipo_doc mustBe Some(1)
      resultDatosTitular.nro_doc mustBe Some("23968498")
      resultDatosTitular.calle mustBe None
      resultDatosTitular.nro_puerta mustBe None
      resultDatosTitular.fecha_nacimiento mustBe None
      resultDatosTitular.sexo_titular mustBe None
      resultDatosTitular.telefono mustBe None
      resultDatosTitular.ip mustBe Some("0:0:0:0:0:0:0:1")

      resultOperationResource.datos_medio_pago mustNot be (None)

      val resultDatosMedioPago = resultOperationResource.datos_medio_pago.get

      resultDatosMedioPago.medio_de_pago mustBe Some(1)
      resultDatosMedioPago.card_brand mustBe Some("Visa")
      resultDatosMedioPago.id_moneda mustBe Some(1)
      resultDatosMedioPago.marca_tarjeta mustBe Some(4)
      resultDatosMedioPago.nro_tarjeta mustBe Some("4509790112684851")
      resultDatosMedioPago.card_number_encrypted mustBe None
      resultDatosMedioPago.nombre_en_tarjeta mustBe Some("Jorge Perez")
      resultDatosMedioPago.expiration_month mustBe Some("07")
      resultDatosMedioPago.expiration_year mustBe Some("19")
      resultDatosMedioPago.security_code mustBe None
      resultDatosMedioPago.bin_for_validation mustBe Some("450979")
      resultDatosMedioPago.nro_trace mustBe Some("603")
      resultDatosMedioPago.cod_autorizacion mustBe Some("171543")
      resultDatosMedioPago.nro_devolucion mustBe None
      resultDatosMedioPago.last_four_digits mustBe Some("4851")
      resultDatosMedioPago.card_number_length mustBe Some(16)
      resultDatosMedioPago.id_operacion_medio_pago mustBe Some("      000603")
      resultDatosMedioPago.nro_terminal mustBe Some("45879859")
      resultDatosMedioPago.nro_ticket mustBe Some("594")
      resultDatosMedioPago.motivo mustBe Some("APROBADA  (authno)")
      resultDatosMedioPago.motivo_adicional mustBe None
      resultDatosMedioPago.id_plan mustBe None
      resultDatosMedioPago.establishment_name mustBe Some("prueba soft")

      resultOperationResource.datos_site mustNot be (None)

      val resultDatosSite = resultOperationResource.datos_site.get

      resultDatosSite.site_id mustBe Some("28465555")
      resultDatosSite.url_dinamica mustBe None
      resultDatosSite.param_sitio mustBe None
      resultDatosSite.use_url_origen mustBe None
      resultDatosSite.url_origen mustBe None
      resultDatosSite.referer mustBe None
      resultDatosSite.id_modalidad mustBe Some("N")
      resultDatosSite.gds mustBe None
      resultDatosSite.enviarResumenOnLine mustBe Some("B")
      resultDatosSite.usaUrlDinamica mustBe Some(true)
      resultDatosSite.urlPost mustBe Some("http://192.168.74.24:10005")
      resultDatosSite.mandarMailAUsuario mustBe Some(false)
      resultDatosSite.mail mustBe Some("jorge.perez@mail.com")
      resultDatosSite.replyMail mustBe Some("jorge.perez@mail.com")
      resultDatosSite.banco mustBe None
      resultDatosSite.origin_site_id mustBe Some("28465555")

      resultOperationResource.creation_date mustBe Some(sdf.parse("Fri Apr 06 17:15:36 ART 2018"))
      resultOperationResource.last_update mustBe Some(sdf.parse("Fri Apr 06 17:15:36 ART 2018"))
      resultOperationResource.ttl_seconds mustBe Some(0)
      resultOperationResource.idTransaccion mustBe Some("46291310")
      resultOperationResource.fraud_detection mustNot be (None)

      resultOperationResource.fraud_detection mustBe an [Option[FraudDetectionData]]
      val resultFraudDetection = resultOperationResource.fraud_detection.get

      resultFraudDetection.bill_to mustBe None
      resultFraudDetection.purchase_totals mustBe None
      resultFraudDetection.channel mustBe None
      resultFraudDetection.dispatch_method mustBe None
      resultFraudDetection.customer_in_site mustBe None
      resultFraudDetection.copy_paste_card_data mustBe None
      resultFraudDetection.send_to_cs mustBe None
      resultFraudDetection.device_unique_id mustBe Some("12345")
      resultFraudDetection.ticketing_transaction_data mustBe None
      resultFraudDetection.retail_transaction_data mustBe None
      resultFraudDetection.digital_goods_transaction_data mustBe None
      resultFraudDetection.travel_transaction_data mustBe None
      resultFraudDetection.retailtp_transaction_data mustBe None
      resultFraudDetection.services_transaction_data mustBe None
      resultFraudDetection.status mustBe None
      resultFraudDetection.csmdds mustBe None

      resultOperationResource.used mustBe None
      resultOperationResource.retries mustBe Some(0)
      resultOperationResource.aggregate_data mustBe None
      resultOperationResource.origin mustBe Some(Requester(
        app = Some("RESTTX"),
        useHash = None,
        encryptionType = None,
        isEncrypted = None)
      )
      resultOperationResource.ticket_request mustBe None
      resultOperationResource.confirmed mustBe an [Option[ConfirmPaymentResponse]]
      resultOperationResource.confirmed mustNot be (None)

      val resultConfirmPaymentResponse = resultOperationResource.confirmed.get
      resultConfirmPaymentResponse.id mustBe 1L
      resultConfirmPaymentResponse.origin_amount mustBe 20000
      resultConfirmPaymentResponse.date mustBe a [Date]

      resultOperationResource.datos_offline mustBe None
      resultOperationResource.datos_banda_tarjeta mustBe None
      resultOperationResource.datos_bsa mustBe None
      resultOperationResource.lote mustBe None
      resultOperationResource.datos_gds mustBe None
      resultOperationResource.agro_data mustBe None
      resultOperationResource.datos_spv mustBe None
      resultOperationResource.customer_token mustBe None

      resultOperationExecResponse.status mustBe 4
      resultOperationExecResponse.cardErrorCode mustBe None
      resultOperationExecResponse.authorized mustBe true
      resultOperationExecResponse.validacion_domicilio mustBe Some("VTE0011")
      resultOperationExecResponse.postbackHash mustBe Some(POSTBACK_HASH)
      resultOperationExecResponse.subPayments mustBe Some(List())

      /**
       * Validate OperationResponse
       */

      response._2 mustBe a [Success[OperationResponse]]

      val resultOperationResponse = response._2.get

      resultOperationResponse.statusCode mustBe 200
      resultOperationResponse.idMotivo mustBe 0
      resultOperationResponse.terminal mustBe Some("45879859")
      resultOperationResponse.nro_trace mustBe Some("604")
      resultOperationResponse.nro_ticket mustBe Some("594")
      resultOperationResponse.cod_aut mustBe Some("")
      resultOperationResponse.tipoOperacion mustBe 4
      resultOperationResponse.historicalStatusList mustBe List(
        HistoricalStatus(-1,
          101,
          sdf.parse("Fri Apr 06 17:15:53 ART 2018")),
        HistoricalStatus(-1,
          104,
          sdf.parse("Fri Apr 06 17:15:53 ART 2018")),
        HistoricalStatus(-1,
          105,
          sdf.parse("Fri Apr 06 17:15:53 ART 2018")),
        HistoricalStatus(0,
          103,
          sdf.parse("Fri Apr 06 17:15:53 ART 2018"))
      )
      resultOperationResponse.site_id mustBe "28465555"
      resultOperationResponse.cardErrorCode mustBe None
      resultOperationResponse.idOperacionMedioPago mustBe "000604"
      resultOperationResponse.motivoAdicional mustBe None
    }

    "Fail retrieving charge" in {
      /**
       * Mock Services
       */
      val legacyTransactionServiceClient = mock[LegacyTransactionServiceClient]
      val legacyOperationServiceClient = mock[LegacyOperationServiceClient]
      val protocolService = mock[ProtocolService]
      val operationResourceRepository = mock[OperationResourceRepository]
      val operationResourceConverter = mock[OperationResourceConverter]
      val transactionRepository = mock[TransactionRepository]

      when(transactionRepository
        .retrieveCharge(
          siteId = SITE_ID,
          chargeId = CHARGE_ID)).thenReturn(Failure(new Exception("Error!")))

      val service: PaymentConfirmationsService = new PaymentConfirmationsService(
        executionContext,
        transactionRepository,
        operationResourceConverter,
        operationResourceRepository,
        protocolService,
        legacyTransactionServiceClient,
        legacyOperationServiceClient,
        configuration= mockConfiguration(),
        refundsLockRepository= mockRefundsLockRepository())

      /**
       * Executing Payment Confirm Operation
       */
      val response = Await.result(
        service.confirm(
          siteId = SITE_ID,
          chargeId = CHARGE_ID,
          operationResource = REQUEST_OPERATION_RESOURCE,
          user = None), Duration(3000L, MILLISECONDS))

      response mustBe (None,Failure(ErrorFactory.notFoundException("confirm payment", CHARGE_ID.toString)))
    }

    "Fail Validating State" in {

      val legacyTransactionServiceClient = mock[LegacyTransactionServiceClient]
      val legacyOperationServiceClient = mock[LegacyOperationServiceClient]
      val protocolService = mock[ProtocolService]
      val operationResourceRepository = mock[OperationResourceRepository]
      val operationResourceConverter = mock[OperationResourceConverter]
      val transactionRepository = mock[TransactionRepository]


      when(transactionRepository
        .retrieveCharge(
          siteId = SITE_ID,
          chargeId = CHARGE_ID)).thenReturn(Success(OPERATION_EXECUTION_RESPONSE))
      when(transactionRepository
        .retrieveTransState(PAYMENT_OPERATION_RESOURCE.idTransaccion.get)).thenReturn("4")

      val service: PaymentConfirmationsService = new PaymentConfirmationsService(
        executionContext,
        transactionRepository,
        operationResourceConverter,
        operationResourceRepository,
        protocolService,
        legacyTransactionServiceClient,
        legacyOperationServiceClient,
        configuration= mockConfiguration(),
        refundsLockRepository= mockRefundsLockRepository())

      /**
       * Executing Payment Confirm Operation
       */
      val response = Await.result(
        service.confirm(
          siteId = SITE_ID,
          chargeId = CHARGE_ID,
          operationResource = REQUEST_OPERATION_RESOURCE,
          user = None), Duration(3000L, MILLISECONDS))

      response mustBe (None, Failure(ErrorFactory.validationException("State error", Autorizada().toString)))
    }

    "Fail with Authorized Superior limit amount exceeded" in {

      /**
       * Mock Services
       */

      val operationResourceRepository = mock[OperationResourceRepository]
      val protocolService = mock[ProtocolService]
      val legacyTransactionServiceClient = mock[LegacyTransactionServiceClient]
      val legacyOperationServiceClient = mock[LegacyOperationServiceClient]

      val repositoryAndConverter = mockCustomAmountTransactionAndConverter(100000)

      val service: PaymentConfirmationsService =
        new PaymentConfirmationsService(
          executionContext,
          repositoryAndConverter._1,
          repositoryAndConverter._2,
          operationResourceRepository,
          protocolService,
          legacyTransactionServiceClient,
          legacyOperationServiceClient,
          configuration= mockConfiguration(),
          refundsLockRepository= mockRefundsLockRepository())
      val caught = intercept [ApiException] {
        Await.result(service.confirm(SITE_ID, CHARGE_ID, REQUEST_OPERATION_RESOURCE, None),
          Duration(3000L, MILLISECONDS))
      }
      caught mustBe ErrorFactory.validationException("amount", s"Amount out of ranges: 50000 - 200000")
    }

    "Fail with Authorized Inferior limit amount exceeded" in {
      val operationResourceRepository = mock[OperationResourceRepository]
      val protocolService = mock[ProtocolService]
      val legacyTransactionServiceClient = mock[LegacyTransactionServiceClient]
      val legacyOperationServiceClient = mock[LegacyOperationServiceClient]

      val repositoryAndConverter = mockCustomAmountTransactionAndConverter(5000)

      val service: PaymentConfirmationsService =
        new PaymentConfirmationsService(
          executionContext,
          repositoryAndConverter._1,
          repositoryAndConverter._2,
          operationResourceRepository,
          protocolService,
          legacyTransactionServiceClient,
          legacyOperationServiceClient,
          configuration= mockConfiguration(),
          refundsLockRepository= mockRefundsLockRepository())
      val caught = intercept [ApiException] {
        Await.result(service.confirm(SITE_ID, CHARGE_ID, REQUEST_OPERATION_RESOURCE, None),
          Duration(3000L, MILLISECONDS))
      }
      caught mustBe ErrorFactory.validationException("amount", s"Amount out of ranges: 2500 - 10000")
    }

    "Fail with wrong Operation Execution Response status" in {

      val transactionRepository = mock[TransactionRepository]
      when(transactionRepository
        .retrieveCharge(
          siteId = SITE_ID,
          chargeId = CHARGE_ID)).thenReturn(Success(OPERATION_EXECUTION_RESPONSE.copy(status = 4)))

      when(transactionRepository
        .retrieveTransState(PAYMENT_OPERATION_RESOURCE.idTransaccion.get)).thenReturn("11")

      val operationResourceRepository = mock[OperationResourceRepository]
      val protocolService = mock[ProtocolService]
      val legacyTransactionServiceClient = mock[LegacyTransactionServiceClient]
      val legacyOperationServiceClient = mock[LegacyOperationServiceClient]

      val service: PaymentConfirmationsService =
        new PaymentConfirmationsService(
          executionContext,
          transactionRepository,
          mockSuccessfulOperationResourceConverter(),
          operationResourceRepository,
          protocolService,
          legacyTransactionServiceClient,
          legacyOperationServiceClient,
          configuration= mockConfiguration(),
          refundsLockRepository= mockRefundsLockRepository())

      val caught = intercept [ApiException] {
        Await.result(service.confirm(SITE_ID, CHARGE_ID, REQUEST_OPERATION_RESOURCE, None),
          Duration(3000L, MILLISECONDS))
      }
      caught mustBe ErrorFactory.validationException("State error", "approved")
    }

    "Fail with protocolCall failure" in {
      /**
       * Initialize Service
       */

      val protocolService = mock[ProtocolService]

      when(protocolService.paymentConfirm(any[ProtocolResource])).thenReturn(Future
        .successful(Failure(ErrorFactory.uncategorizedException(new Error("An error")))))

      val service: PaymentConfirmationsService = new PaymentConfirmationsService(
        executionContext,
        transactionRepository = mockSuccessfulTransactionRepository(),
        protocolService = protocolService,
        operationResourceConverter = mockSuccessfulOperationResourceConverter(),
        operationRepository = mockSuccessfulOperationResourceRepository(),
        legacyTxService = mockSuccessfulLegacyTransactionServiceClient(),
        legacyOpxService = mockSuccessfulLegacyOperationServiceClient(),
        configuration= mockConfiguration(),
        refundsLockRepository= mockRefundsLockRepository())

      /**
        * Executing Payment Confirm Operation
        */
      val response = Await.result(
        service.confirm(
          siteId = SITE_ID,
          chargeId = CHARGE_ID,
          operationResource = REQUEST_OPERATION_RESOURCE,
          user = None), Duration(3000L, MILLISECONDS))
      response mustBe (Some(OPERATION_EXECUTION_RESPONSE.copy(cardErrorCode = Some(ProcessingError()))),
        Failure(ErrorFactory.uncategorizedException(new Error("An error"))))
    }

    "Fail with protocolCall 400 status" in {

      val protocolService = mock[ProtocolService]

      when(protocolService
        .paymentConfirm(any[ProtocolResource])).thenReturn(Future
        .successful(OPERATION_RESPONSE.map(u => u.copy(statusCode = 400))))

      val service: PaymentConfirmationsService = new PaymentConfirmationsService(
        executionContext,
        transactionRepository = mockSuccessfulTransactionRepository(),
        protocolService = protocolService,
        operationResourceConverter = mockSuccessfulOperationResourceConverter(),
        operationRepository = mockSuccessfulOperationResourceRepository(),
        legacyTxService = mockSuccessfulLegacyTransactionServiceClient(),
        legacyOpxService = mockSuccessfulLegacyOperationServiceClient(),
        configuration= mockConfiguration(),
        refundsLockRepository= mockRefundsLockRepository())
      val response = Await.result(
        service.confirm(
          siteId = SITE_ID,
          chargeId = CHARGE_ID,
          operationResource = REQUEST_OPERATION_RESOURCE,
          user = None), Duration(3000L, MILLISECONDS))
      response mustBe (Some(OPERATION_EXECUTION_RESPONSE),
        OPERATION_RESPONSE.map(u => u.copy(statusCode = 400)))
    }
  }
}
