package services.cybersource

import java.net.{Inet6Address, InetAddress}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.xml.NodeSeq
import scala.xml.NodeSeq.seqToNodeSeq
import scala.xml.PrettyPrinter
import com.decidir.coretx.api.ApiException
import com.decidir.coretx.api.BillingData
import com.decidir.coretx.api.CyberSourceResponse
import com.decidir.coretx.api.DatosTitularResource
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.FraudDetectionData
import com.decidir.coretx.api.FraudDetectionDecision
import com.decidir.coretx.api.InvalidRequestError
import com.decidir.coretx.api.Item
import com.decidir.coretx.api.PurchaseTotals
import com.decidir.coretx.api.TicketingTransactionData
import com.decidir.coretx.api.ValidationError
import com.decidir.coretx.domain.CyberSourceConfiguration
import com.decidir.coretx.domain.DatosMedioPago
import com.decidir.coretx.domain.MedioDePago
import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.domain.Site
import javax.inject.Inject
import javax.inject.Singleton

import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSResponse
import services.metrics.MetricsClient
import controllers.MDCHelperTrait
import org.apache.commons.lang3.StringUtils
import akka.pattern.Patterns
import java.util.regex.Pattern

import scala.collection.mutable.ArrayBuffer

@Singleton
class CybersourceClient @Inject() (ws: WSClient, 
    config: Configuration, 
    executionContext: ExecutionContext,
    verticalCommonsValidator: VerticalCommonsValidator,
    verticalTicketingValidator: VerticalTicketingValidator, 
    verticalRetailValidator: VerticalRetailValidator,
    verticalRetailTPValidator: VerticalRetailTPValidator,
    verticalDigitalGoodsValidator: VerticalDigitalGoodsValidator,
    verticalTravelValidator: VerticalTravelValidator,
    verticalServicesValidator: VerticalServicesValidator,
    metrics: MetricsClient,
    verticalTicketing: CyberSourceVerticalTicketing,
    verticalRetail: CyberSourceVerticalRetail,
    verticalRetailTP: CyberSourceVerticalRetailTP,
    verticalDigitalGoods: CyberSourceVerticalDigitalGoods,
    verticalTravel: CyberSourceVerticalTravel,
    verticalServices: CyberSourceVerticalServices,
    converter: CybersourceConverter,
    csConfiguration: CSConfiguration) extends MDCHelperTrait {

  implicit val ec = executionContext
                    
  val timeoutMillis = (config.getLong("sps.cybersource.timeoutMillis").getOrElse(10000l)) millis
  val url = config.getString("sps.cybersource.url").getOrElse(throw new Exception("No se configuro sps.cybersource.url"))
  val Ticketing = "Ticketing"
  val Retail = "Retail"
  val DigitalGoods = "Digital Goods"
  val Travel = "Travel"
  val RetailTP = "RetailTP"
  val Services = "Services"
  
  
  def retries = csConfiguration.get(CSConfigParam.Retries()).map(_.toInt).getOrElse(3)
  
  def call(operationData: OperationData, authorized: Option[Boolean], addressValidated: Option[String]): Future[CyberSourceResponse] = {
    try{
      Try{soapMessage(operationData, authorized, addressValidated)} match {
        case Failure(ex: ApiException) => {
          val e = ex.error
          logger.error("Error en creacion de mensaje SOAP para invocar a CyberSource", ex)
      	  Future(CyberSourceResponse(FraudDetectionDecision.black, None, -1, e.message))
        }
        case Failure(ex: Throwable) => {
          logger.error("Error en creacion de mensaje SOAP para invocar a CyberSource", ex)
          Future(CyberSourceResponse(FraudDetectionDecision.black, None, -1, "Internal Server Error"))
        }        
        case Success(xml) => {
          logger.info(s"Cybersource ms: ${new PrettyPrinter(200, 2).format(xml).replace(operationData.nroTarjeta,"X" * operationData.nroTarjeta.size)}")
          val ini = System.currentTimeMillis()
          val fwsresponse = 
          ws.url(url).withRequestTimeout(timeoutMillis) post(xml)
          fwsresponse map { wsresponse => 
            loadMDCFromOperation(operationData.resource)
            val cybersourceTime = System.currentTimeMillis() - ini
            logger.info(s"Cybersource time: $cybersourceTime ms: ${new PrettyPrinter(200, 2).format(wsresponse.xml)}")
            metrics.recordInMillis(operationData.resource.id, "coretx", "cybersource", "call", cybersourceTime)
            wsresponse.status match {
              case 200 => handleResponse(wsresponse)
              case other => {
                logger.error(s"Cybersource result ${other}")
                CyberSourceResponse.fromDecisionAndReason(None, "REJECT ERROR", 150)
              }
            }
          }
        }.recover({case x => 
            logger.error("Error en acceso a CyberSource: ", x)
            CyberSourceResponse.fromDecisionAndReason(None, "REJECT ERROR", 150)
        })
      }
    } 
    catch {
      case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
        logger.error(s"Cybersource result black: ${param}")
        Future(CyberSourceResponse(FraudDetectionDecision.black, None, -1, param))
      }
    }
  }            
  
  def validate(operationData: OperationData): Option[CyberSourceResponse] = {
    val ini = System.currentTimeMillis
    val cyberSourceConf = operationData.site.cyberSourceConfiguration.get
    
    try {
      //TP Para la vertical de TP no se va a realizar ningun tipo de validacion
      var csErrors = cyberSourceConf.vertical match {
        case RetailTP => ArrayBuffer[ValidationError]()
        case other => verticalCommonsValidator.validate(operationData)
      }

      csErrors.++=(cyberSourceConf.vertical match {
        case Ticketing => verticalTicketingValidator.validate(operationData)
        case Retail => verticalRetailValidator.validate(operationData)
        case DigitalGoods => verticalDigitalGoodsValidator.validate(operationData)
        case Travel => verticalTravelValidator.validate(operationData)
        //case RetailTP => verticalRetailTPValidator.validate(operationData)
        case Services => verticalServicesValidator.validate(operationData)
        case other => {
          logger.error(s"validate of vertical ${other} not implemented")
          ArrayBuffer[ValidationError]()
        }
      })
      
      metrics.recordInMillis(ini, operationData.resource.id, "coretx", "cybersource", "validate")
      if(csErrors.isEmpty) None
      else {
        logger.error("Cybersource is Empty, result black")
      	Some(CyberSourceResponse(FraudDetectionDecision.black, None, getReazon(csErrors.toList), "validation", Some(InvalidRequestError(csErrors.toList))))
      }
    } catch {
      case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
        logger.error(s"Cybersource result black: ${param}")
        Some(CyberSourceResponse(FraudDetectionDecision.black, None, -1, "validation", Some(InvalidRequestError(List(ValidationError(typeName, param))))))
      }
    }
    
  }

  private def getReazon(csErrors: List[ValidationError]) = {
    val validationError = csErrors.find { error => {
      try{
        error.code.toInt
        true
      }
      catch {
        case e: Exception => false
      }
    }}
    validationError.map(ve => ve.code.toInt).getOrElse(-1)
  }
  
  private def handleResponse(wsResponse: WSResponse): CyberSourceResponse = {
    
    val xml = wsResponse.xml
    val replyMessage = (xml \ "Body" \ "replyMessage")
    val requestId = (replyMessage \ "requestID").text
    val decision = (replyMessage \ "decision").text
    val reasonCode = ((replyMessage \ "reasonCode").text).toInt
    
    CyberSourceResponse.fromDecisionAndReason(Some(requestId), decision, reasonCode)
  }
  
  def soapMessage(operationData: OperationData, authorized: Option[Boolean], addressValidated: Option[String]) = {

    val datosSite = operationData.datosSite
    val site = operationData.site
    val cyberSourceConf = site.cyberSourceConfiguration.get
    val cuotas = operationData.cuotas
    val datosMedioPago = operationData.datosMedioPago
    val medioDePago = operationData.medioDePago
    val datosTitular = operationData.datosTitular

    val fdd = operationData.resource.fraud_detection.get
    val billToXml = mapBillTo(fdd.bill_to.getOrElse(BillingData()), datosTitular.ip, fdd.channel)
    val purchaseTotalsXml = mapPurchaseTotals(fdd.purchase_totals.getOrElse(PurchaseTotals()))
    val cardType = getCardType(medioDePago.id)
    val cardXml = mapCard(datosMedioPago, medioDePago, cardType)
    val (verticalXml, itemsXml) = mapVertical(site, cyberSourceConf, datosMedioPago, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated)
    val shipTo = cyberSourceConf.vertical match {
      case RetailTP => fdd.retailtp_transaction_data.flatMap {rtd =>
        rtd.ship_to.map(st => verticalRetailTP.mapShipTo(st))
      }
      case _ => fdd.retail_transaction_data.flatMap {rtd =>
        rtd.ship_to.map(st => verticalRetail.mapShipTo(st))
      }
    }
    val airlineData = fdd.travel_transaction_data.flatMap { ttd => ttd.airline_number_of_passengers.map(anop => verticalTravel.mapAirlineNumberOfPassengers(anop)) }
    // Username token sale de la configuracion del site.
    // merchantreferencecode: nro de operacion

    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    	<soapenv:Header>{authenticationHeader(cyberSourceConf)}</soapenv:Header>
      <soapenv:Body>
        <urn:requestMessage xmlns:urn="urn:schemas-cybersource-com:transaction-data-1.101">
        	<urn:merchantID>{cyberSourceConf.mid}</urn:merchantID>
          <urn:merchantReferenceCode>{operationData.nroOperacionSite}</urn:merchantReferenceCode>
          {cardType.map(ct => Nil).getOrElse{
          <urn:invoiceHeader>
            <urn:tenderType>private1</urn:tenderType>
           	</urn:invoiceHeader>}}
					{billToXml}
					{shipTo.map(st => st).getOrElse(Nil)}
          {itemsXml}
					{purchaseTotalsXml}
					{cardXml}
          {verticalXml}
          <urn:afsService run="true"/>
					{airlineData.getOrElse(Nil)}
          {fdd.device_unique_id.map(ui => <urn:deviceFingerprintID>{validateTelephoneSale(fdd.channel, ui)}</urn:deviceFingerprintID>).getOrElse(Nil)}
        </urn:requestMessage>
      </soapenv:Body>
    </soapenv:Envelope>
  }
//TODO:    METERLO!!        <urn:deviceFingerprintRaw>{"true"}</urn:deviceFingerprintRaw>
 
  private def authenticationHeader(cyberSourceConf: CyberSourceConfiguration) = {
    <wsse:Security soapenv:mustUnderstand="1" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
	    <wsse:UsernameToken wsu:Id="UsernameToken-1" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
      	<wsse:Username>{cyberSourceConf.mid}</wsse:Username>
  	  	<wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">{cyberSourceConf.securityKey}</wsse:Password>
    	</wsse:UsernameToken>
    </wsse:Security>      
  }
  
  private def mapBillTo(billTo: BillingData, ip: Option[String], channel: Option[String]) = {
          <urn:billTo>
            {billTo.first_name.map(fn => <urn:firstName>{fn}</urn:firstName>).getOrElse(Nil)}
            {billTo.last_name.map(ln => <urn:lastName>{ln}</urn:lastName>).getOrElse(Nil)}
            {billTo.street1.map(s1 => <urn:street1>{s1}</urn:street1>).getOrElse(Nil)}
            {billTo.street2.map(st => <urn:street2>{st}</urn:street2>).getOrElse(Nil)}
            {billTo.city.map(city => <urn:city>{city}</urn:city>).getOrElse(Nil)}
            {billTo.state.map(state => <urn:state>{state}</urn:state>).getOrElse(Nil)}
            {billTo.postal_code.map(postalCode => <urn:postalCode>{postalCode}</urn:postalCode>).getOrElse(Nil)}
            {billTo.country.map(country => <urn:country>{country}</urn:country>).getOrElse(Nil)}
            {billTo.phone_number.map(phoneNumber => <urn:phoneNumber>{phoneNumber}</urn:phoneNumber>).getOrElse(Nil)}
            {billTo.email.map(email => <urn:email>{email}</urn:email>).getOrElse(Nil)}
            {ip.map(ip => <urn:ipAddress>{validateTelephoneSale(channel, converter.normalizeIP(ip))}</urn:ipAddress>).getOrElse(Nil)}
            {billTo.customer_id.map(customerId => <urn:customerID>{customerId}</urn:customerID>).getOrElse(Nil)}
          </urn:billTo>    
  }
  
  private def mapPurchaseTotals(purchaseTotals: PurchaseTotals) = {
          <urn:purchaseTotals>
            {purchaseTotals.currency.map(currency => <urn:currency>{currency}</urn:currency>).getOrElse(Nil)}
            {purchaseTotals.amount.map(amount => <urn:grandTotalAmount>{formatAmount(amount)}</urn:grandTotalAmount>).getOrElse(Nil)}
          </urn:purchaseTotals>    
  }
  
  private def mapCard(datosMedioPago: DatosMedioPago, medioDePago: MedioDePago, cardType: Option[String]) = {
    val cardNumber = datosMedioPago.nro_tarjeta
    val expirationMonth = datosMedioPago.expiration_month 
    val expirationYear = datosMedioPago.expiration_year
    
          <urn:card>
            <urn:accountNumber>{cardNumber}</urn:accountNumber>
            <urn:expirationMonth>{expirationMonth}</urn:expirationMonth>
            <urn:expirationYear>{expirationYear.takeRight(2)}</urn:expirationYear>
            {cardType.map(ct => <urn:cardType>{ct}</urn:cardType>).getOrElse(Nil)}
          </urn:card>    
  }
  
  private def getCardType(mpId: String) = {
    mpId match {
      case "1"  => Some("001")
      case "15"|"20" => Some("002")
      case "6"|"65" => Some("003")
      case _ => None
    }
  }
  
  private def mapVertical(site: Site, 
                          cyberSourceConf: CyberSourceConfiguration, 
                          datosMedioDePago: DatosMedioPago, 
                          medioDePago: MedioDePago, 
                          cuotas: Int, 
                          datosTitular: DatosTitularResource, 
                          authorized: Option[Boolean], 
                          fdd: FraudDetectionData,
                          addressValidated: Option[String]) = {
    cyberSourceConf.vertical match {
      case Ticketing => {
        fdd.ticketing_transaction_data.map( ttd => verticalTicketing.mapVertical(site, cyberSourceConf.vertical, datosMedioDePago, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated))
        .getOrElse(throw ErrorFactory.missingDataException(List("fraud detection data of vertical Ticketing"))) 
      }
      case Retail => {
        fdd.retail_transaction_data.map(rtd => verticalRetail.mapVertical(site, cyberSourceConf.vertical, datosMedioDePago, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated))
        .getOrElse(throw ErrorFactory.missingDataException(List("fraud detection data of vertical Retail"))) 
      }
      case DigitalGoods => {
        fdd.digital_goods_transaction_data.map(dgtd => verticalDigitalGoods.mapVertical(site, cyberSourceConf.vertical, datosMedioDePago, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated))
        .getOrElse(throw ErrorFactory.missingDataException(List("fraud detection data of vertical Digital Goods")))
      }
      case Travel => {
        fdd.travel_transaction_data.map(ttd => verticalTravel.mapVertical(site, cyberSourceConf.vertical, datosMedioDePago, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated))
        .getOrElse(throw ErrorFactory.missingDataException(List("fraud detection data of vertical Travel"))) 
      }
      case RetailTP => {
        /**
          * Se anulan momentaneamenta las validaciones de TP para CS
          */
        /*
        fdd.retailtp_transaction_data.map(rtd => verticalRetailTP.mapVertical(site, cyberSourceConf.vertical, datosMedioDePago, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated))
          .getOrElse(throw ErrorFactory.missingDataException(List("fraud detection data of vertical RetailTP")))
        */
        verticalRetailTP.mapVertical(site, cyberSourceConf.vertical, datosMedioDePago, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated)
      }
      case Services => {
        fdd.services_transaction_data.map(rtd => verticalServices.mapVertical(site, cyberSourceConf.vertical, datosMedioDePago, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated))
          .getOrElse(throw ErrorFactory.missingDataException(List("fraud detection data of vertical Services")))
      }
      case other => throw ErrorFactory.missingDataException(List(s"vertical ${other} not implemented"))
    }
  }

  private def formatAmount(amount: Long) = {
    val formatted = "%.2f".format(BigDecimal(amount)/100)
    formatted.replace(',', '.') // Para evitar manejar el Locale
  }

  private def validateTelephoneSale(channel: Option[String], field: String) = {
      channel.map(ch => if(ch == "Telefonica") "" else field).getOrElse(field)
    }
  
}
