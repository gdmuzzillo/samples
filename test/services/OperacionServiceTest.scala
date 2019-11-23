package services

import java.text.SimpleDateFormat

import com.decidir.coretx.api._
import com.decidir.coretx.domain._
import com.decidir.coretx.utils.JedisPoolProvider
import com.decidir.encripcion.Encriptador
import com.decidir.encrypt.EncryptionService
import controllers.utils.FullCreditCardValidator
import org.mockito.Matchers
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.bins.BinsService
import services.cybersource.CybersourceClient
import services.metrics.MetricsClient
import services.payments.{LegacyTransactionServiceClient, PaymentsProcessor}
import services.protocol.ProtocolService
import services.validations.operation.PaymentMethodValidation
import services.validations.{AfipValidate, AggregatorValidate, InstallmentsValidator, OfflineValidator}

import scala.util.{Failure, Success}

class OperacionServiceTest extends PlaySpec with MockitoSugar {

	val context = scala.concurrent.ExecutionContext.global
  val operationRepository : OperationResourceRepository = mock[OperationResourceRepository]
  val jedisPoolProvider : JedisPoolProvider = mock[JedisPoolProvider]
  val siteRepository : SiteRepository = mock[SiteRepository]
  val medioDePagoRepository : MedioDePagoRepository =  mock[MedioDePagoRepository]
  val monedaRepository : MonedaRepository = mock[MonedaRepository]
  val marcaTarjetaRepository : MarcaTarjetaRepository= mock[MarcaTarjetaRepository]
  val protocolService : ProtocolService = mock[ProtocolService]
  val nroTraceRepository: NroTraceRepository = mock[NroTraceRepository]
  val legacyTxService : LegacyTransactionServiceClient = mock[LegacyTransactionServiceClient]
  val offlineTransactionProcessor : OfflineTransactionProcessor = mock[OfflineTransactionProcessor]
  val cybersourceClient: CybersourceClient = mock[CybersourceClient]
	val motivoRepository: MotivoRepository = mock[MotivoRepository]
  val metricsClient = mock[MetricsClient]
  val paymentsProcessor = mock[PaymentsProcessor]
  val fullCreditCardValidator = mock[FullCreditCardValidator]
	val paymentMethodService = mock[PaymentMethodService]
  val aggregatorValidate = mock[AggregatorValidate]
	val afipValidate = mock[AfipValidate]
  val offlineValidator = mock[OfflineValidator]
	val binsService = mock[BinsService]
	val tipoDocumentoRepository = mock[TipoDocumentoRepository]
	val installmentsValidator = mock[InstallmentsValidator]
  val encryptionService = mock[EncryptionService]
  val paymentMethodValidation = new PaymentMethodValidation(
    operationRepository = operationRepository,
    medioDePagoRepository = medioDePagoRepository,
    paymentMethodService = paymentMethodService,
    monedaRepository = monedaRepository,
    fullCreditCardValidator = fullCreditCardValidator,
    binsService = binsService,
    marcaTarjetaRepository = marcaTarjetaRepository,
    installmentsValidator = installmentsValidator)

  val operacionService = new OperacionService(context,
    operationRepository,
    jedisPoolProvider,
    siteRepository,
    medioDePagoRepository,
    monedaRepository,
    marcaTarjetaRepository,
    protocolService,
    nroTraceRepository,
    legacyTxService,
    cybersourceClient,
    motivoRepository,
    metricsClient,
    paymentsProcessor,
    fullCreditCardValidator,
    tipoDocumentoRepository,
    offlineTransactionProcessor,
    paymentMethodService,
    aggregatorValidate,
    afipValidate,
    offlineValidator,
    binsService,
    installmentsValidator,
    encryptionService,
    paymentMethodValidation)
  
  val sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss")
  
  val SITE_ID1 = "00300815"
  val AMOUNT1 = 800
  val INSTALLMENTS1 = Some(1)
  val SUBPAYMENT_ID1= Some(123l)
  val STATUS=Some(1)
  
  val USER_ID = Some("test_user")
  
  val SITE_ID = "00290815"

  val SITE_ID2 = "00310815"
  val AMOUNT2 = 800
  val INSTALLMENTS2 = Some(2)
  val SUBPAYMENT_ID2= Some(321l)
  
  val CARD_BRAND = Some("VISA")
  val EMAIL_CLIENTE = Some("maxi@gmail.com")
  val TIPO_DOC = Some(1)
  val NRO_DOC = Some("12121212")
  val CALLE = Some("av corrientes")
  val NRO_PUERTA = Some(1)
  val FECHA_NACIMIENTO = Some("12-12-1234")
  
  val MEDIO_DE_PAGO = Some(1)
  val ID_MONEDA = Some(1)
  val MARCA_TARJETA = Some(7)
  val NRO_TARJETA =Some("1234567890123456")
  val NOMBRE_EN_TARJETA = Some("visa")
  val EXPIRATION_MONTH = Some("11")
  val EXPIRATION_YEAR = Some("18")
  val SECURITY_CODE = Some("code")
  val BIN_FOR_VALIDATION = Some("123456")
  val NRO_TRACE = Some("123456")
  val COD_AUTORIZACION = Some("cod_autorizacion")
  val OPERATION_RESOURCE_ID = "id"

  val subTransaction = List()
  val datosTitularResource = DatosTitularResource(EMAIL_CLIENTE, TIPO_DOC, NRO_DOC, CALLE, NRO_PUERTA, FECHA_NACIMIENTO, ip = Some("192.168.1.1"))
  val datosMedioPagoResource = DatosMedioPagoResource(MEDIO_DE_PAGO,
      CARD_BRAND,
      ID_MONEDA, 
      MARCA_TARJETA, 
      NRO_TARJETA, 
      None,
      NOMBRE_EN_TARJETA,
      EXPIRATION_MONTH, 
      EXPIRATION_YEAR, 
      SECURITY_CODE, 
      BIN_FOR_VALIDATION,
      NRO_TRACE,
      COD_AUTORIZACION
      )
  val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID), origin_site_id = Some(SITE_ID))
  
//         operationResource = (id: String = "",
//                             nro_operacion: Option[String] = None,
//                             user_id: Option[String] = None,
//                             charge_id: Option[Long] = None,
//                             fechavto_cuota_1: Option[String] = None,
//                             monto: Option[Long] = None,
//                             cuotas: Option[Int] = None,
//                             sub_transactions: List[SubTransaction] = Nil,
//                             datos_titular: Option[DatosTitularResource] = None,
//                             datos_medio_pago: Option[DatosMedioPagoResource] = None,
//                             datos_site: Option[DatosSiteResource] = None,
//                             creation_date: Option[Date] = None,
//                             last_update: Option[Date] = None,
//                             ttl_seconds: Option[Int] = None,
//                             idTransaccion: Option[String] = None,
//                             fraud_detection: Option[FraudDetectionData] = None,
//                             used: Option[Boolean] = None,
//                             retries:Option[Int] = None,
//                             aggregate_data: Option[Aggregator] = None,
//                             origin: Option[Requester] = None)
  

  "createOperation" should {
      "return OperationResource successfully" in {
        val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                               Some("nro_operacion"),
                               USER_ID,
                               Some(1234),
                               Some("fechavto_cuota_1"),
                               Some(1000),
                               Some(1000),
                               Some(1),
                               subTransaction,
                               Some(datosTitularResource),
                               Some(datosMedioPagoResource),
                               Some(datosSiteResource),
                               Some(sdf.parse("08-06-2016 00:00:00")),
                               Some(sdf.parse("09-06-2016 00:00:00")),
                               Some(1),
                               None)

        val site = mock[Site]
    		val pbb = mock[PostbackConf]
  			val mailConfiguration = mock[MailConfiguration]
        val cuentaMock = mock[Cuenta]
        val medioDePago = mock[MedioDePago]
        val medioDePagoId = "1"
        val cuentas = List(cuentaMock)
        val marcaTarjeta = mock[MarcaTarjeta]
        val encrypt = mock[Encriptador]

  			when(site.id) thenReturn SITE_ID
        when(site.ppb) thenReturn pbb
  			when(site.mailConfiguration) thenReturn mailConfiguration
  			when(site.transaccionesDistribuidas) thenReturn "N"
        when(site.habilitado) thenReturn(true)
        when(site.cuentas) thenReturn cuentas
        when(site.cuenta(medioDePagoId, 0,0)) thenReturn Some(cuentaMock)
        when(site.agregador) thenReturn "N"
        when(site.mensajeriaMPOS) thenReturn Some("N")
        when(cuentaMock.idMedioPago) thenReturn medioDePagoId
        when(medioDePago.id) thenReturn medioDePagoId
        when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
        when(medioDePago.bin_regex) thenReturn ".*"
        when(cuentaMock.habilitado) thenReturn true

        when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]

        when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
        when(siteRepository.getEncryptor(site)) thenReturn Success(Some(encrypt))
        when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString)) thenReturn true
        when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
        when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
        when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
        when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true
        when(operationRepository.retrieve(Matchers.any[String])) thenReturn Some(operationResource)
        when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
        when(operationRepository.decrypted(Matchers.any[String])) thenReturn NRO_TARJETA.get

        try {
          operacionService.createOperation(operationResource) match {
            case Success(op) => {
              assert(op.id equals OPERATION_RESOURCE_ID)
              assert(op.datos_site.contains(datosSiteResource))
              assert(op.datos_titular.contains(datosTitularResource))
              assert(op.datos_medio_pago.contains(datosMedioPagoResource))
            }
            case Failure(error) => {
              fail(error)
            }
          }
        }catch{
          case e: Exception => fail(e)
        }
      }
  }

  "createOperation" should {
    "return OperationResource successfully when is shopping credit card" in {
      val medioDePagoId = 23
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("fechavto_cuota_1"),
        Some(1000),
        Some(1000),
        Some(1),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(medioDePagoId))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)

      val site = mock[Site]
      val pbb = mock[PostbackConf]
      val mailConfiguration = mock[MailConfiguration]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]

      val cuentas = List(cuentaMock)
      val marcaTarjeta = mock[MarcaTarjeta]
      val encrypt = mock[Encriptador]

      when(site.id) thenReturn SITE_ID
      when(site.ppb) thenReturn pbb
      when(site.mailConfiguration) thenReturn mailConfiguration
      when(site.transaccionesDistribuidas) thenReturn "N"
      when(site.habilitado) thenReturn true
      when(site.cuentas) thenReturn cuentas
      when(site.cuenta(medioDePagoId.toString, 0,0)) thenReturn Some(cuentaMock)
      when(site.agregador) thenReturn "N"
      when(site.mensajeriaMPOS) thenReturn Some("N")
      when(cuentaMock.idMedioPago) thenReturn medioDePagoId.toString
      when(medioDePago.id) thenReturn medioDePagoId.toString
      when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
      when(medioDePago.bin_regex) thenReturn ".*"
      when(cuentaMock.habilitado) thenReturn true

      when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]

      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(siteRepository.getEncryptor(site)) thenReturn Success(Some(encrypt))
      when(medioDePagoRepository.exists(Matchers.any[String])) thenReturn true
      when(medioDePagoRepository.retrieve(medioDePagoId)) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
      when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
      when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true
      when(operationRepository.retrieve(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.decrypted(Matchers.any[String])) thenReturn NRO_TARJETA.get

      try {
        operacionService.createOperation(operationResource) match {
          case Success(op) => {
            assert(op.id equals OPERATION_RESOURCE_ID)
            assert(op.datos_site.contains(datosSiteResource))
            assert(op.datos_titular.contains(datosTitularResource))
          }
          case Failure(error) => {
            fail(error)
          }
        }
      }catch{
        case e: Exception => fail(e)
      }
    }
  }

  
  "createOperation with validationException nro_operacion None" should{
    "throw ValidationError" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                             None,
                             USER_ID,
                             None,
                             Some("fechavto_cuota_1"),
                             Some(1000),
                             Some(1000),
                             Some(1),
                             subTransaction,
                             Some(datosTitularResource),
                             Some(datosMedioPagoResource),
                             Some(datosSiteResource),
                             Some(sdf.parse("08-06-2016 00:00:00")),
                             Some(sdf.parse("09-06-2016 00:00:00")),
                             Some(1), None)
      val site = mock[Site]
      when(site.parentSiteId) thenReturn Some(SITE_ID)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)

      val result = operacionService.createOperation(operationResource)
      result match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == "param_required")
          assert(param == "site_transaction_id")
        }
      }
    }
  }

  "createOperation with validationException nro_operacion is invalid" should{
    "throw ValidationError" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("12345678901234567890123456789012345678901234567890"),
        USER_ID,
        None,
        Some("fechavto_cuota_1"),
        Some(1000),
        Some(1000),
        Some(1),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1), None)
      val site = mock[Site]
      when(site.parentSiteId) thenReturn Some(SITE_ID)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)

      val result = operacionService.createOperation(operationResource)
      result match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == "site_transaction_id")
        }
      }
    }
  }

  "createOperation with validationException HASH is invalid" should{
    "throw ValidationError" in {
      val site = mock[Site]
      val origin = mock[Requester]
      val hash = mock[HashConfiguration]

      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("1234567890"),
        USER_ID,
        None,
        Some("fechavto_cuota_1"),
        Some(1000),
        Some(1000),
        Some(1),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        origin = Some(origin))

      when(site.validaOrigen) thenReturn false
      when(site.hashConfiguration) thenReturn hash
      when(site.url) thenReturn ""
      when(hash.useHash) thenReturn true
      when(hash.firstHashDate) thenReturn None
      when(origin.app) thenReturn Some("WEBTX")
      when(origin.useHash) thenReturn Some(false)
      when(site.parentSiteId) thenReturn Some(SITE_ID)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)

      val result = operacionService.createOperation(operationResource)
      result match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.PARAM_REQUIRED)
          assert(param == "HASH")
        }
      }
    }
  }
   
  "createOperation with validationException ip invalid" should{
    "throw ValidationError" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                             Some("nro_operacion"),
                             USER_ID,
                             None,
                             Some("fechavto_cuota_1"), 
                             Some(1000),
                             Some(1000),
                             Some(1),
                             subTransaction,
                             Some(datosTitularResource.copy(ip = Some("123.456.78.9"))),
                             Some(datosMedioPagoResource), 
                             Some(datosSiteResource),
                             Some(sdf.parse("08-06-2016 00:00:00")),
                             Some(sdf.parse("09-06-2016 00:00:00")),
                             Some(1), None)
      val site = mock[Site]
      when(site.parentSiteId) thenReturn Some(SITE_ID)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
                             
      val result = operacionService.createOperation(operationResource)
      result match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.IP)
        }
      }
    }
  }  
  
   "createOperation with validationException ip empty" should{
    "throw ValidationError" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                             Some("nro_operacion"),
                             USER_ID,
                             None,
                             Some("fechavto_cuota_1"), 
                             Some(1000),
                             Some(1000),
                             Some(1),
                             subTransaction,
                             Some(datosTitularResource.copy(ip = Some(""))), 
                             Some(datosMedioPagoResource), 
                             Some(datosSiteResource),
                             Some(sdf.parse("08-06-2016 00:00:00")),
                             Some(sdf.parse("09-06-2016 00:00:00")),
                             Some(1), None)
      val site = mock[Site]
      when(site.parentSiteId) thenReturn Some(SITE_ID)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
                             
      val result = operacionService.createOperation(operationResource)
      result match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.IP)
        }
      }
    }
  }  
    
  "createOperation with validationException datos_site None" should{
    "throw ValidationError" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                             Some("nro_operacion"),
                             USER_ID,
                             Some(1234),
                             Some("fechavto_cuota_1"),
                             Some(1000),
                             Some(1000),
                             Some(1),
                             subTransaction,
                             Some(datosTitularResource),
                             Some(datosMedioPagoResource),
                             None,
                             Some(sdf.parse("08-06-2016 00:00:00")),
                             Some(sdf.parse("09-06-2016 00:00:00")),
                             Some(1),
                             None)

      val encrypt = mock[Encriptador]

      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)

      val result = operacionService.createOperation(operationResource)
      result match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.PARAM_REQUIRED)
          assert(param == ErrorMessage.DATA_SITE_SITE_ID)
        }
        case Success(_) => fail()
      }
    }
  }
  
  
  "createOperation with validationException datos_site.site_id None" should{
    "throw ValidationError" in {

      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                             Some("nro_operacion"),
                             USER_ID,
                             Some(1234),
                             Some("fechavto_cuota_1"),
                             Some(1000),
                             Some(1000),
                             Some(1),
                             subTransaction,
                             Some(datosTitularResource),
                             Some(datosMedioPagoResource),
                             Some(DatosSiteResource(site_id = None, origin_site_id = None)),
                             Some(sdf.parse("08-06-2016 00:00:00")),
                             Some(sdf.parse("09-06-2016 00:00:00")),
                             Some(1),
                             None)

      val encrypt = mock[Encriptador]
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.PARAM_REQUIRED)
          assert(param == ErrorMessage.DATA_SITE_SITE_ID)
        }
      }
    }
  }

  "createOperation with validationException when datos_site.sub_site_id is distinct to datos_site.id" should{
    "throw ValidationError" in {

      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("fechavto_cuota_1"),
        Some(1000),
        Some(1000),
        Some(1),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource),
        Some(DatosSiteResource(site_id = Some(SITE_ID), origin_site_id = Some(SITE_ID1))),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)

      //when
      val site = mock[Site]
      val pbb = mock[PostbackConf]
      val mailConfiguration = mock[MailConfiguration]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = "1"
      val cuentas = List(cuentaMock)
      val marcaTarjeta = mock[MarcaTarjeta]
      val encrypt = mock[Encriptador]
      val hash = mock[HashConfiguration]

      when(site.id) thenReturn SITE_ID
      when(site.ppb) thenReturn pbb
      when(site.mailConfiguration) thenReturn mailConfiguration
      when(site.transaccionesDistribuidas) thenReturn "S"
      when(site.montoPorcent) thenReturn "M"
      when(site.habilitado) thenReturn true
      when(site.cuentas) thenReturn cuentas
      when(site.cuenta(medioDePagoId, 0,0)) thenReturn Some(cuentaMock)
      when(site.agregador) thenReturn "N"
      when(site.mensajeriaMPOS) thenReturn Some("N")
      when(site.validaOrigen) thenReturn false
      when(site.hashConfiguration) thenReturn hash
      when(site.url) thenReturn ""
      when(site.parentSiteId) thenReturn None
      when(hash.useHash) thenReturn false
      when(hash.firstHashDate) thenReturn None
      when(cuentaMock.idMedioPago) thenReturn medioDePagoId
      when(medioDePago.id) thenReturn medioDePagoId
      when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
      when(medioDePago.bin_regex) thenReturn ".*"
      when(cuentaMock.habilitado) thenReturn true

      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(siteRepository.findSubSitesBySite(site)) thenReturn List(InfoSite(SITE_ID1,10.0F))
      when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString)) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
      when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
      when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_SITE)
          assert(param == ErrorMessage.DATA_SITE_ORIGIN_SITE_ID)
        }
      }
    }
  }

  "createOperation with validationException datos_site.referer invalid_param" should{
    "throw ValidationError" in {
      //with
      val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID),
                              use_url_origen = Some(true),
                              url_origen = Some("url_origen"),
                              origin_site_id = Some(SITE_ID))

      val appOrigin = Requester(app = Some("WEBTX"))

      val operationResource = OperationResource(id = OPERATION_RESOURCE_ID,
                              nro_operacion = Some("nro_operacion"),
                              user_id =USER_ID,
                              charge_id= Some(1234),
                              fechavto_cuota_1 = Some("fechavto_cuota_1"),
                              monto = Some(1000),
                              cuotas = Some(1),
                              sub_transactions = subTransaction,
                              datos_titular = Some(datosTitularResource),
                              datos_medio_pago = Some(datosMedioPagoResource),
                              datos_site = Some(datosSiteResource),
                              creation_date = Some(sdf.parse("08-06-2016 00:00:00")),
                              last_update = Some(sdf.parse("09-06-2016 00:00:00")),
                              ttl_seconds = Some(1),
                              origin = Some(appOrigin))
      //when
      val site = mock[Site]
      val pbb = mock[PostbackConf]
      val mailConfiguration = mock[MailConfiguration]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = "1"
      val cuentas = List(cuentaMock)
      val marcaTarjeta = mock[MarcaTarjeta]
      val encrypt = mock[Encriptador]
      val hash = mock[HashConfiguration]

      when(site.id) thenReturn SITE_ID
      when(site.ppb) thenReturn pbb
      when(site.mailConfiguration) thenReturn mailConfiguration
      when(site.transaccionesDistribuidas) thenReturn "N"
      when(site.habilitado) thenReturn true
      when(site.cuentas) thenReturn cuentas
      when(site.cuenta(medioDePagoId, 0,0)) thenReturn Some(cuentaMock)
      when(site.agregador) thenReturn "N"
      when(site.mensajeriaMPOS) thenReturn Some("N")
      when(site.validaOrigen) thenReturn true
      when(site.hashConfiguration) thenReturn hash
      when(site.url) thenReturn ""
      when(hash.useHash) thenReturn false
      when(hash.firstHashDate) thenReturn None
      when(cuentaMock.idMedioPago) thenReturn medioDePagoId
      when(medioDePago.id) thenReturn medioDePagoId
      when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
      when(medioDePago.bin_regex) thenReturn ".*"
      when(cuentaMock.habilitado) thenReturn true

      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString)) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
      when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
      when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true

      //then
      val result = operacionService.createOperation(operationResource)
      result match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_SITE_REFERER)
        }
      }
    }
  }

  "createOperation with validationException invalid_amount monto" should{
    "throw ValidationError" in {

      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                             Some("nro_operacion"),
                             USER_ID,
                             Some(1234),
                             Some("fechavto_cuota_1"),
                             Some(-1),
                             Some(-1),
                             Some(1),
                             subTransaction,
                             Some(datosTitularResource),
                             Some(datosMedioPagoResource),
                             Some(datosSiteResource),
                             Some(sdf.parse("08-06-2016 00:00:00")),
                             Some(sdf.parse("09-06-2016 00:00:00")),
                             Some(1),
                             None)


      val result = operacionService.createOperation(operationResource)
      result match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_AMOUNT)
          assert(param == ErrorMessage.AMOUNT)
        }
        case Success(_) => fail
      }
    }
  }

  "createOperation with validationException invalid_payment_type transacciones_distribuidas" should{
    "throw ValidationError" in {
      val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID), id_modalidad = Some("S"), origin_site_id = Some(SITE_ID))
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                             Some("nro_operacion"),
                             USER_ID,
                             Some(1234),
                             Some("fechavto_cuota_1"),
                             Some(1000),
                             Some(1000),
                             Some(1),
                             subTransaction,
                             Some(datosTitularResource),
                             Some(datosMedioPagoResource),
                             Some(datosSiteResource),
                             Some(sdf.parse("08-06-2016 00:00:00")),
                             Some(sdf.parse("09-06-2016 00:00:00")),
                             Some(1),
                             None)

      //when
      val site = mock[Site]
      val pbb = mock[PostbackConf]
      val mailConfiguration = mock[MailConfiguration]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = "1"
      val cuentas = List(cuentaMock)
      val marcaTarjeta = mock[MarcaTarjeta]
      val encrypt = mock[Encriptador]
      val hash = mock[HashConfiguration]

      when(site.id) thenReturn SITE_ID
      when(site.ppb) thenReturn pbb
      when(site.mailConfiguration) thenReturn mailConfiguration
      when(site.transaccionesDistribuidas) thenReturn "N"
      when(site.habilitado) thenReturn true
      when(site.cuentas) thenReturn cuentas
      when(site.cuenta(medioDePagoId, 0,0)) thenReturn Some(cuentaMock)
      when(site.agregador) thenReturn "N"
      when(site.mensajeriaMPOS) thenReturn Some("N")
      when(site.validaOrigen) thenReturn true
      when(site.hashConfiguration) thenReturn hash
      when(site.url) thenReturn ""
      when(hash.useHash) thenReturn false
      when(hash.firstHashDate) thenReturn None
      when(cuentaMock.idMedioPago) thenReturn medioDePagoId
      when(medioDePago.id) thenReturn medioDePagoId
      when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
      when(medioDePago.bin_regex) thenReturn ".*"
      when(cuentaMock.habilitado) thenReturn true

      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString)) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
      when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
      when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PAYMENT_TYPE)
          assert(param == ErrorMessage.DISTRIBUTED_TRANSACTIONS)
        }
        case Success(_) => fail
      }
    }
  }

  "createOperation with validationException invalid_param sub_payments.site_id" should{
    "return ValidationError" in {
      val subTransaction = List(
        SubTransaction(site_id = SITE_ID1, amount = AMOUNT1, original_amount = Some(AMOUNT1), installments = INSTALLMENTS1, subpayment_id = SUBPAYMENT_ID1, status = STATUS, nro_trace = None),
        SubTransaction(site_id = SITE_ID2, amount = AMOUNT2, original_amount = Some(AMOUNT1), installments = INSTALLMENTS2, subpayment_id = SUBPAYMENT_ID2, status = STATUS, nro_trace = None)
      )

      val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID), id_modalidad = Some("S"), origin_site_id = Some(SITE_ID))
      val operationResource = OperationResource(id = OPERATION_RESOURCE_ID,
                             nro_operacion = Some("nro_operacion"),
                             fechavto_cuota_1 = Some("fechavto_cuota_1"),
                             monto = Some(1000),
                             charge_id = Some(1),
                             sub_transactions = subTransaction,
                             datos_titular = Some(datosTitularResource),
                             datos_medio_pago = Some(datosMedioPagoResource),
                             datos_site = Some(datosSiteResource),
                             creation_date = Some(sdf.parse("08-06-2016 00:00:00")),
                             last_update = Some(sdf.parse("09-06-2016 00:00:00")),
                             ttl_seconds = Some(1))

      //when
      val site = mock[Site]
      val pbb = mock[PostbackConf]
      val mailConfiguration = mock[MailConfiguration]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = "1"
      val cuentas = List(cuentaMock)
      val marcaTarjeta = mock[MarcaTarjeta]
      val encrypt = mock[Encriptador]
      val hash = mock[HashConfiguration]

      when(site.id) thenReturn SITE_ID
      when(site.ppb) thenReturn pbb
      when(site.mailConfiguration) thenReturn mailConfiguration
      when(site.transaccionesDistribuidas) thenReturn "S"
      when(site.montoPorcent) thenReturn "M"
      when(site.habilitado) thenReturn true
      when(site.cuentas) thenReturn cuentas
      when(site.cuenta(medioDePagoId, 0,0)) thenReturn Some(cuentaMock)
      when(site.agregador) thenReturn "N"
      when(site.mensajeriaMPOS) thenReturn Some("N")
      when(site.validaOrigen) thenReturn false
      when(site.hashConfiguration) thenReturn hash
      when(site.url) thenReturn ""
      when(hash.useHash) thenReturn false
      when(hash.firstHashDate) thenReturn None
      when(cuentaMock.idMedioPago) thenReturn medioDePagoId
      when(medioDePago.id) thenReturn medioDePagoId
      when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
      when(medioDePago.bin_regex) thenReturn ".*"
      when(cuentaMock.habilitado) thenReturn true

      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(siteRepository.findSubSitesBySite(site)) thenReturn List(InfoSite(SITE_ID1,10.0F))
      when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString)) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
      when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
      when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == "invalid_param")
          assert(param == "sub_payments.site_id")
        }
        case Success(_) => fail
      }
    }
  }

  "createOperation with validationException invalid_param subTx.amount" should{
    "return ValidationError" in {
      val subTransaction = List(
        SubTransaction(site_id = SITE_ID, amount = -1, original_amount = Some(-1), installments = INSTALLMENTS1, subpayment_id = SUBPAYMENT_ID1, status = STATUS, nro_trace = None)
        //SubTransaction(site_id = SITE_ID2, amount = AMOUNT2, installments = INSTALLMENTS2, subpayment_id = SUBPAYMENT_ID2, status = STATUS, nro_trace = None)
      )

      val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID), id_modalidad = Some("S"), origin_site_id = Some(SITE_ID))
      val operationResource = OperationResource(id = OPERATION_RESOURCE_ID,
        nro_operacion = Some("nro_operacion"),
        fechavto_cuota_1 = Some("fechavto_cuota_1"),
        monto = Some(1000),
        charge_id = Some(1),
        sub_transactions = subTransaction,
        datos_titular = Some(datosTitularResource),
        datos_medio_pago = Some(datosMedioPagoResource),
        datos_site = Some(datosSiteResource),
        creation_date = Some(sdf.parse("08-06-2016 00:00:00")),
        last_update = Some(sdf.parse("09-06-2016 00:00:00")),
        ttl_seconds = Some(1))

      //when
      val site = mock[Site]
      val pbb = mock[PostbackConf]
      val mailConfiguration = mock[MailConfiguration]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = "1"
      val cuentas = List(cuentaMock)
      val marcaTarjeta = mock[MarcaTarjeta]
      val encrypt = mock[Encriptador]
      val hash = mock[HashConfiguration]

      when(site.id) thenReturn SITE_ID
      when(site.ppb) thenReturn pbb
      when(site.mailConfiguration) thenReturn mailConfiguration
      when(site.transaccionesDistribuidas) thenReturn "S"
      when(site.montoPorcent) thenReturn "M"
      when(site.habilitado) thenReturn true
      when(site.cuentas) thenReturn cuentas
      when(site.cuenta(medioDePagoId, 0,0)) thenReturn Some(cuentaMock)
      when(site.agregador) thenReturn "N"
      when(site.mensajeriaMPOS) thenReturn Some("N")
      when(site.validaOrigen) thenReturn false
      when(site.hashConfiguration) thenReturn hash
      when(site.url) thenReturn ""
      when(hash.useHash) thenReturn false
      when(hash.firstHashDate) thenReturn None
      when(cuentaMock.idMedioPago) thenReturn medioDePagoId
      when(medioDePago.id) thenReturn medioDePagoId
      when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
      when(medioDePago.bin_regex) thenReturn ".*"
      when(cuentaMock.habilitado) thenReturn true

      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(siteRepository.findSubSitesBySite(site)) thenReturn List(InfoSite(SITE_ID1,10.0F))
      when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString)) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
      when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
      when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName contains ErrorMessage.INVALID_PARAM)
          assert(param contains ErrorMessage.AMOUNT)
        }
        case Success(_) => fail
      }
    }
  }

  "createOperation with validationException invalid_param distinct amount" should {
    "thrown ValidationError" in {
      val subTransaction = List(
        SubTransaction(site_id = SITE_ID, amount = AMOUNT1, original_amount = Some(-1), installments = INSTALLMENTS1, subpayment_id = SUBPAYMENT_ID1, status = STATUS, nro_trace = None)
        //SubTransaction(site_id = SITE_ID2, amount = AMOUNT2, installments = INSTALLMENTS2, subpayment_id = SUBPAYMENT_ID2, status = STATUS, nro_trace = None)
      )

      val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID), id_modalidad = Some("S"), origin_site_id = Some(SITE_ID))
      val operationResource = OperationResource(id = OPERATION_RESOURCE_ID,
        nro_operacion = Some("nro_operacion"),
        fechavto_cuota_1 = Some("fechavto_cuota_1"),
        monto = Some(10000),
        charge_id = Some(1),
        sub_transactions = subTransaction,
        datos_titular = Some(datosTitularResource),
        datos_medio_pago = Some(datosMedioPagoResource),
        datos_site = Some(datosSiteResource),
        creation_date = Some(sdf.parse("08-06-2016 00:00:00")),
        last_update = Some(sdf.parse("09-06-2016 00:00:00")),
        ttl_seconds = Some(1))

      //when
      val site = mock[Site]
      val pbb = mock[PostbackConf]
      val mailConfiguration = mock[MailConfiguration]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = "1"
      val cuentas = List(cuentaMock)
      val marcaTarjeta = mock[MarcaTarjeta]
      val encrypt = mock[Encriptador]
      val hash = mock[HashConfiguration]

      when(site.id) thenReturn SITE_ID
      when(site.ppb) thenReturn pbb
      when(site.mailConfiguration) thenReturn mailConfiguration
      when(site.transaccionesDistribuidas) thenReturn "S"
      when(site.montoPorcent) thenReturn "M"
      when(site.habilitado) thenReturn true
      when(site.cuentas) thenReturn cuentas
      when(site.cuenta(medioDePagoId, 0,0)) thenReturn Some(cuentaMock)
      when(site.agregador) thenReturn "N"
      when(site.mensajeriaMPOS) thenReturn Some("N")
      when(site.validaOrigen) thenReturn false
      when(site.hashConfiguration) thenReturn hash
      when(site.url) thenReturn ""
      when(hash.useHash) thenReturn false
      when(hash.firstHashDate) thenReturn None
      when(cuentaMock.idMedioPago) thenReturn medioDePagoId
      when(medioDePago.id) thenReturn medioDePagoId
      when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
      when(medioDePago.bin_regex) thenReturn ".*"
      when(cuentaMock.habilitado) thenReturn true

      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(siteRepository.findSubSitesBySite(site)) thenReturn List(InfoSite(SITE_ID1,10.0F))
      when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString)) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
      when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
      when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName contains ErrorMessage.INVALID_PARAM)
          assert(param contains ErrorMessage.DIFFERENT_AMOUNTS)
        }
        case Success(_) => fail
      }
    }
  }

  "createOperation with validationException notFoundError on doUpdateOperation" should{
      "throw NotFoundError" in {
         val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                               Some("nro_operacion"),
                               USER_ID,
                               Some(1234),
                               Some("fechavto_cuota_1"),
                               Some(1000),
                               Some(1000),
                               Some(1),
                               subTransaction,
                               Some(datosTitularResource),
                               Some(datosMedioPagoResource),
                               Some(datosSiteResource),
                               Some(sdf.parse("08-06-2016 00:00:00")),
                               Some(sdf.parse("09-06-2016 00:00:00")),
                               Some(1),
                               None)

        //when
        val site = mock[Site]
        val pbb = mock[PostbackConf]
        val mailConfiguration = mock[MailConfiguration]
        val cuentaMock = mock[Cuenta]
        val medioDePago = mock[MedioDePago]
        val medioDePagoId = "1"
        val cuentas = List(cuentaMock)
        val marcaTarjeta = mock[MarcaTarjeta]
        val encrypt = mock[Encriptador]
        val hash = mock[HashConfiguration]

        when(site.id) thenReturn SITE_ID
        when(site.ppb) thenReturn pbb
        when(site.mailConfiguration) thenReturn mailConfiguration
        when(site.transaccionesDistribuidas) thenReturn "S"
        when(site.montoPorcent) thenReturn "M"
        when(site.habilitado) thenReturn true
        when(site.cuentas) thenReturn cuentas
        when(site.cuenta(medioDePagoId, 0,0)) thenReturn Some(cuentaMock)
        when(site.agregador) thenReturn "N"
        when(site.mensajeriaMPOS) thenReturn Some("N")
        when(site.validaOrigen) thenReturn false
        when(site.hashConfiguration) thenReturn hash
        when(site.url) thenReturn ""
        when(hash.useHash) thenReturn false
        when(hash.firstHashDate) thenReturn None
        when(cuentaMock.idMedioPago) thenReturn medioDePagoId
        when(medioDePago.id) thenReturn medioDePagoId
        when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
        when(medioDePago.bin_regex) thenReturn ".*"
        when(cuentaMock.habilitado) thenReturn true

        when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
        when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
        when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn None
        when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
        when(siteRepository.findSubSitesBySite(site)) thenReturn List(InfoSite(SITE_ID1,10.0F))
        when(siteRepository.getEncryptor(site)) thenReturn Success(Some(encrypt))
        when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString)) thenReturn true
        when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
        when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
        when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
        when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true
        when(operationRepository.decrypted(Matchers.any[String])) thenReturn NRO_TARJETA.get

        val result = operacionService.createOperation(operationResource)
        result match {
          case Failure(ApiException(NotFoundError(entity, id))) => {
            assert(entity == "OperationResource")
          }
          case Success(_) => fail
        }
      }
  }
 
  "createOperation with validationException disable site.habilitado" should{
      "throw NotFoundError" in {
        val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID), id_modalidad = Some("N"))
         val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                               Some("nro_operacion"),
                               USER_ID,
                               Some(1234),
                               Some("fechavto_cuota_1"),
                               Some(1000),
                               Some(1000),
                               Some(1),
                               subTransaction,
                               Some(datosTitularResource),
                               Some(datosMedioPagoResource),
                               Some(datosSiteResource),
                               Some(sdf.parse("08-06-2016 00:00:00")),
                               Some(sdf.parse("09-06-2016 00:00:00")),
                               Some(1),
                               None)
        //when
        val site = mock[Site]
        val pbb = mock[PostbackConf]
        val mailConfiguration = mock[MailConfiguration]
        val cuentaMock = mock[Cuenta]
        val medioDePago = mock[MedioDePago]
        val medioDePagoId = "1"
        val cuentas = List(cuentaMock)
        val marcaTarjeta = mock[MarcaTarjeta]
        val encrypt = mock[Encriptador]
        val hash = mock[HashConfiguration]

        when(site.id) thenReturn SITE_ID
        when(site.ppb) thenReturn pbb
        when(site.mailConfiguration) thenReturn mailConfiguration
        when(site.transaccionesDistribuidas) thenReturn "S"
        when(site.montoPorcent) thenReturn "M"
        when(site.habilitado) thenReturn false
        when(site.cuentas) thenReturn cuentas
        when(site.cuenta(medioDePagoId, 0,0)) thenReturn Some(cuentaMock)
        when(site.agregador) thenReturn "N"
        when(site.mensajeriaMPOS) thenReturn Some("N")
        when(site.validaOrigen) thenReturn false
        when(site.hashConfiguration) thenReturn hash
        when(site.url) thenReturn ""
        when(hash.useHash) thenReturn false
        when(hash.firstHashDate) thenReturn None
        when(cuentaMock.idMedioPago) thenReturn medioDePagoId
        when(medioDePago.id) thenReturn medioDePagoId
        when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
        when(medioDePago.bin_regex) thenReturn ".*"
        when(cuentaMock.habilitado) thenReturn true

        when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
        when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
        when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
        when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
        when(siteRepository.findSubSitesBySite(site)) thenReturn List(InfoSite(SITE_ID1,10.0F))
        when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString)) thenReturn true
        when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
        when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
        when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
        when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true

        val result = operacionService.createOperation(operationResource)
        result match {
          case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
            assert(typeName == ErrorMessage.INVALID_PARAM)
            assert(param == ErrorMessage.DATA_SITE_DISABLED)
          }
          case Success(_) => fail
        }
      }
  }

  "createOperation with validateMedioPago" should{
    "throw InvalidException for idMedioPago" in {
       val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                             Some("nro_operacion"),
                             USER_ID,
                             Some(1234),
                             Some("fechavto_cuota_1"),
                             Some(1000),
                             Some(1000),
                             Some(1),
                             subTransaction,
                             Some(datosTitularResource),
                             Some(datosMedioPagoResource),
                             Some(datosSiteResource),
                             Some(sdf.parse("08-06-2016 00:00:00")),
                             Some(sdf.parse("09-06-2016 00:00:00")),
                             Some(1),
                             None)
      val site = mock[Site]
      when(site.mensajeriaMPOS) thenReturn Some("N")
      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn (true)
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn (Some(operationResource))
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString())) thenReturn false

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_METHOD_ID)
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException for idMarcaTarjeta" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
                             Some("nro_operacion"),
                             USER_ID,
                             Some(1234),
                             Some("fechavto_cuota_1"),
                             Some(1000),
                             Some(1000),
                             Some(1),
                             subTransaction,
                             Some(datosTitularResource),
                             Some(datosMedioPagoResource),
                             Some(datosSiteResource),
                             Some(sdf.parse("08-06-2016 00:00:00")),
                             Some(sdf.parse("09-06-2016 00:00:00")),
                             Some(1),
                             None)

      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = "1"
      val cuentas = List(cuentaMock)
      when(site.cuentas) thenReturn cuentas
      when(site.mensajeriaMPOS) thenReturn None
      when(medioDePago.idMarcaTarjeta) thenReturn None

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn (Some(site))
      when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString())) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString())) thenReturn false

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == "idMarcaTarjeta")
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException for Installments" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("fechavto_cuota_1"),
        Some(1000),
        Some(1000),
        Some(2),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(31))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)
      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = 31
      val cuentas = List(cuentaMock)
      when(site.cuentas) thenReturn cuentas
      when(site.mensajeriaMPOS) thenReturn None
      when(medioDePago.idMarcaTarjeta) thenReturn None

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn (Some(site))
      when(medioDePagoRepository.exists(Matchers.any[String])) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString())) thenReturn false

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_INSTALLMENTS)
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException for first installment expiration date" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        None,
        Some(1000),
        Some(1000),
        Some(1),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(45))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)
      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = 45
      val cuentas = List(cuentaMock)
      when(site.cuentas) thenReturn cuentas
      when(site.mensajeriaMPOS) thenReturn None
      when(medioDePago.idMarcaTarjeta) thenReturn None

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn (Some(site))
      when(medioDePagoRepository.exists(Matchers.any[String])) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString())) thenReturn false

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.PARAM_REQUIRED)
          assert(param == ErrorMessage.DATA_FIRST_INSTALLMENT_EXPIRATION)
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException when cuotas is not defined" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("vto_1"),
        Some(1000),
        Some(1000),
        None,
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(45))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)
      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = 45
      val cuentas = List(cuentaMock)
      when(site.cuentas) thenReturn cuentas
      when(site.mensajeriaMPOS) thenReturn None
      when(medioDePago.idMarcaTarjeta) thenReturn None

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn (Some(site))
      when(medioDePagoRepository.exists(Matchers.any[String])) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString())) thenReturn false

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.PARAM_REQUIRED)
          assert(param == ErrorMessage.DATA_INSTALLMENTS)
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException when cuotas > 11" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("vto_1"),
        Some(1000),
        Some(1000),
        Some(12),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(45))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)
      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = 45
      val cuentas = List(cuentaMock)
      when(site.cuentas) thenReturn cuentas
      when(site.mensajeriaMPOS) thenReturn None
      when(medioDePago.idMarcaTarjeta) thenReturn None

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn (Some(site))
      when(medioDePagoRepository.exists(Matchers.any[String])) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString())) thenReturn false

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_INSTALLMENTS)
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException when fecha_vto is invalid" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("vto_1"),
        Some(1000),
        Some(1000),
        Some(10),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(45))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)
      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = 45
      val cuentas = List(cuentaMock)
      when(site.cuentas) thenReturn cuentas
      when(site.mensajeriaMPOS) thenReturn None
      when(medioDePago.idMarcaTarjeta) thenReturn None

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn (Some(site))
      when(medioDePagoRepository.exists(Matchers.any[String])) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString())) thenReturn false

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_FIRST_INSTALLMENT_EXPIRATION)
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException when MedioPago has nroTarjeta and medioPago dont exists" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("vto_1"),
        Some(1000),
        Some(1000),
        Some(10),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(1), nro_tarjeta = Some("1234"))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)

      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = 45
      val cuentas = List(cuentaMock)
      when(site.cuentas) thenReturn cuentas
      when(site.mensajeriaMPOS) thenReturn None
      when(medioDePago.idMarcaTarjeta) thenReturn None

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn (Some(site))
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn None

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_METHOD_ID)
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException when MedioPago - idMarcaTarjeta dont exists" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("vto_1"),
        Some(1000),
        Some(1000),
        Some(10),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(1), nro_tarjeta = Some("1234"))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)

      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = 1
      val cuentas = List(cuentaMock)
      when(site.cuentas) thenReturn cuentas
      when(site.mensajeriaMPOS) thenReturn None
      when(medioDePago.idMarcaTarjeta) thenReturn None

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn (Some(site))
      when(medioDePagoRepository.exists(Matchers.any[String])) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_ID_MARCA_TARJETA)
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException when MedioPago - validateSitePaymentMeans - cuenta is disabled" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("vto_1"),
        Some(1000),
        Some(1000),
        Some(10),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(1), nro_tarjeta = Some("1234"))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)

      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = 1
      val cuentas = List(cuentaMock)
      when(site.cuentas) thenReturn cuentas
      when(site.cuenta("1",0,0)) thenReturn Some(cuentaMock)
      when(site.mensajeriaMPOS) thenReturn None
      when(cuentaMock.habilitado) thenReturn false
      when(medioDePago.idMarcaTarjeta) thenReturn Some(1)

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(medioDePagoRepository.exists(Matchers.any[String])) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_METHOD_ID)
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException when MedioPago - validateSitePaymentMeans - cuenta is None" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("vto_1"),
        Some(1000),
        Some(1000),
        Some(10),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(1), nro_tarjeta = Some("1234"))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)

      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = 1
      val cuentas = List(cuentaMock)
      when(site.cuentas) thenReturn cuentas
      when(site.cuenta("1",0,0)) thenReturn None
      when(site.mensajeriaMPOS) thenReturn None
      when(medioDePago.idMarcaTarjeta) thenReturn Some(1)

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(medioDePagoRepository.exists(Matchers.any[String])) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_METHOD_ID)
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException when MedioPago - validateNacional - cuenta is national but the card brand is not" in {
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("vto_1"),
        Some(1000),
        Some(1000),
        Some(10),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(1), nro_tarjeta = Some("1234"))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None)

      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = 1
      val cuentas = List(cuentaMock)
      val marcaTarjetaId = 1
      val marcaTarjeta = mock[MarcaTarjeta]

      when(site.cuentas) thenReturn cuentas
      when(site.cuenta("1",0,0)) thenReturn Some(cuentaMock)
      when(site.mensajeriaMPOS) thenReturn None
      when(cuentaMock.habilitado) thenReturn true
      when(cuentaMock.aceptaSoloNacional) thenReturn true
      when(medioDePago.idMarcaTarjeta) thenReturn Some(marcaTarjetaId)
      when(marcaTarjeta.esNacional(Matchers.any[Option[String]])) thenReturn false

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(medioDePagoRepository.exists(Matchers.any[String])) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.retrieve(marcaTarjetaId)) thenReturn Some(marcaTarjeta)

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_METHOD_ID)
        }
        case Success(_) => fail
      }
    }

    "throw InvalidException when MedioPago - validateLuhn - invalid card Number" in {
      val datosBsa = mock[Bsa]
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("vto_1"),
        Some(1000),
        Some(1000),
        Some(10),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource.copy(medio_de_pago = Some(1), nro_tarjeta = Some("1234"))),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None,
        datos_bsa = Some(datosBsa))

      val site = mock[Site]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = 1
      val cuentas = List(cuentaMock)
      val marcaTarjetaId = 1
      val marcaTarjeta = mock[MarcaTarjeta]

      when(site.cuentas) thenReturn cuentas
      when(site.cuenta("1",0,0)) thenReturn Some(cuentaMock)
      when(site.mensajeriaMPOS) thenReturn None
      when(cuentaMock.habilitado) thenReturn true
      when(cuentaMock.aceptaSoloNacional) thenReturn true
      when(medioDePago.idMarcaTarjeta) thenReturn Some(marcaTarjetaId)
      when(marcaTarjeta.esNacional(Matchers.any[Option[String]])) thenReturn true
      when(datosBsa.flag_tokenization) thenReturn None
      when(medioDePago.validateLuhn) thenReturn true

      when(operationRepository.exists(OPERATION_RESOURCE_ID)) thenReturn true
      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(medioDePagoRepository.exists(Matchers.any[String])) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.retrieve(marcaTarjetaId)) thenReturn Some(marcaTarjeta)
      when(fullCreditCardValidator.isValid(Matchers.any[String])) thenReturn false

      operacionService.createOperation(operationResource) match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_CARD_NUMBER)
        }
        case Success(_) => fail
      }
    }
  }

  "createOperation with validateMPOS" should {
    "throws an Exception when Site allows only MPOS transactions" in {
      val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID), id_modalidad = Some("N"), origin_site_id = Some(SITE_ID))
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("fechavto_cuota_1"),
        Some(1000),
        Some(1000),
        Some(1),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None,
        datos_banda_tarjeta = None)
      //when
      val site = mock[Site]
      val pbb = mock[PostbackConf]
      val mailConfiguration = mock[MailConfiguration]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = "1"
      val cuentas = List(cuentaMock)
      val marcaTarjeta = mock[MarcaTarjeta]
      val encrypt = mock[Encriptador]
      val hash = mock[HashConfiguration]

      when(site.id) thenReturn SITE_ID
      when(site.ppb) thenReturn pbb
      when(site.mailConfiguration) thenReturn mailConfiguration
      when(site.transaccionesDistribuidas) thenReturn "S"
      when(site.montoPorcent) thenReturn "M"
      when(site.habilitado) thenReturn true
      when(site.cuentas) thenReturn cuentas
      when(site.cuenta(medioDePagoId, 0,0)) thenReturn Some(cuentaMock)
      when(site.agregador) thenReturn "N"
      when(site.mensajeriaMPOS) thenReturn Some("S")
      when(site.validaOrigen) thenReturn false
      when(site.hashConfiguration) thenReturn hash
      when(site.url) thenReturn ""
      when(hash.useHash) thenReturn false
      when(hash.firstHashDate) thenReturn None
      when(cuentaMock.idMedioPago) thenReturn medioDePagoId
      when(medioDePago.id) thenReturn medioDePagoId
      when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
      when(medioDePago.bin_regex) thenReturn ".*"
      when(cuentaMock.habilitado) thenReturn true

      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(siteRepository.findSubSitesBySite(site)) thenReturn List(InfoSite(SITE_ID1,10.0F))
      when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString)) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
      when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
      when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true

      val result = operacionService.createOperation(operationResource)
      result match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_SITE_MPOS_ENABLED)
        }
        case Success(_) => fail
      }
    }

    "throws an Exception when Site does not allow MPOS transactions" in {
      val datosSiteResource = DatosSiteResource(site_id = Some(SITE_ID), id_modalidad = Some("N"), origin_site_id = Some(SITE_ID))
      val operationResource = OperationResource(OPERATION_RESOURCE_ID,
        Some("nro_operacion"),
        USER_ID,
        Some(1234),
        Some("fechavto_cuota_1"),
        Some(1000),
        Some(1000),
        Some(1),
        subTransaction,
        Some(datosTitularResource),
        Some(datosMedioPagoResource),
        Some(datosSiteResource),
        Some(sdf.parse("08-06-2016 00:00:00")),
        Some(sdf.parse("09-06-2016 00:00:00")),
        Some(1),
        None,
        datos_banda_tarjeta = Some(DatosBandaTarjeta(Some("CardTrack1"),Some("CardTrack2"),"input_mode")))
      //when
      val site = mock[Site]
      val pbb = mock[PostbackConf]
      val mailConfiguration = mock[MailConfiguration]
      val cuentaMock = mock[Cuenta]
      val medioDePago = mock[MedioDePago]
      val medioDePagoId = "1"
      val cuentas = List(cuentaMock)
      val marcaTarjeta = mock[MarcaTarjeta]
      val encrypt = mock[Encriptador]
      val hash = mock[HashConfiguration]

      when(site.id) thenReturn SITE_ID
      when(site.ppb) thenReturn pbb
      when(site.mailConfiguration) thenReturn mailConfiguration
      when(site.transaccionesDistribuidas) thenReturn "S"
      when(site.montoPorcent) thenReturn "M"
      when(site.habilitado) thenReturn true
      when(site.cuentas) thenReturn cuentas
      when(site.cuenta(medioDePagoId, 0,0)) thenReturn Some(cuentaMock)
      when(site.agregador) thenReturn "N"
      when(site.mensajeriaMPOS) thenReturn Some("N")
      when(site.validaOrigen) thenReturn false
      when(site.hashConfiguration) thenReturn hash
      when(site.url) thenReturn ""
      when(hash.useHash) thenReturn false
      when(hash.firstHashDate) thenReturn None
      when(cuentaMock.idMedioPago) thenReturn medioDePagoId
      when(medioDePago.id) thenReturn medioDePagoId
      when(medioDePago.idMarcaTarjeta) thenReturn MARCA_TARJETA
      when(medioDePago.bin_regex) thenReturn ".*"
      when(cuentaMock.habilitado) thenReturn true

      when(operationRepository.retrieveDecrypted(Matchers.any[String])) thenReturn Some(operationResource)
      when(encrypt.encriptar(Matchers.any[String])) thenReturn Matchers.any[String]
      when(operationRepository.retrieve(OPERATION_RESOURCE_ID)) thenReturn Some(operationResource)
      when(siteRepository.retrieve(SITE_ID)) thenReturn Some(site)
      when(siteRepository.findSubSitesBySite(site)) thenReturn List(InfoSite(SITE_ID1,10.0F))
      when(medioDePagoRepository.exists(MEDIO_DE_PAGO.get.toString)) thenReturn true
      when(medioDePagoRepository.retrieve(Matchers.any[String])) thenReturn Some(medioDePago)
      when(marcaTarjetaRepository.exists(MARCA_TARJETA.get.toString)) thenReturn true
      when(marcaTarjetaRepository.retrieve(Matchers.any[Int])) thenReturn Some(marcaTarjeta)
      when(monedaRepository.exists(ID_MONEDA.get.toString)) thenReturn true

      val result = operacionService.createOperation(operationResource)
      result match {
        case Failure(ApiException(InvalidRequestError(List(ValidationError(typeName, param))))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_SITE_MPOS_DISABLED)
        }
        case Success(_) => fail
      }
    }
  }
}