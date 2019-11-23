package services

import com.typesafe.config.ConfigFactory
import play.api._
import play.api.libs.ws._
import play.api.libs.ws.ahc.AhcWSClientConfig
import play.api.libs.ws.ahc.AhcConfigBuilder
import play.api.libs.ws.ahc.AhcWSClient
import org.asynchttpclient.AsyncHttpClientConfig
import java.io.File
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * @author martinpaoletta
 */
object CsCallAsWSTest extends App {

  val url = "https://ics2wstest.ic3.com:443/commerce/1.x/transactionProcessor"

  val xml = """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <soapenv:Header>
              <wsse:Security soapenv:mustUnderstand="1" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">decidir_agregador<wsse:UsernameToken wsu:Id="UsernameToken-1" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                                                                <wsse:Username>decidir_agregador</wsse:Username>
                                                                <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">7INYIZLob1xKu4W4xhE7l8HYRgf13MQhIA8XtuRCxW0L9VNqIHXBJCvWMbHzbbv0n771oi3jMEL27xOtt0uD5fbeBRqR0N66QevdhXAQ+5KFHJvmoBZ3Xgq2jFTltP6flXN6PPlN3X6XYmPuBhJoqlrFfuo7sD24pM0qRbZFeRf1/13ZrYA+K3vNe6ATvyHr6TF2jMiK8RkstPl97AE95l7B2zYqxWpL2cpPh/WAFFReOFkgqnpo2NYqe3ZRfcYFMBTpJtmZVJu5gnEDBY7cxCp8jmLz5V9cphX5FO19KaBPkMvHFvVjGcXp8JOcL162sNo5hcKZNFCFIZ3ZFizVnQ==</wsse:Password>
                                                </wsse:UsernameToken>
                                </wsse:Security>  
                </soapenv:Header>
                <soapenv:Body>
                                <requestMessage xmlns="urn:schemas-cybersource-com:transaction-data-1.120">
                                                <merchantID>decidir_agregador</merchantID>
                                                <merchantReferenceCode>1439935655117</merchantReferenceCode>
                                                <clientLibrary>Java Axis WSS4J</clientLibrary>
                                                <clientLibraryVersion>1.4/1.5.1</clientLibraryVersion>
                                                <clientEnvironment>Windows 7/6.1/Oracle Corporation/1.8.0_51
                                                </clientEnvironment>
                                                <billTo>
                                                                <firstName>John</firstName>
                                                                <lastName>Doe</lastName>
                                                                <street1>1295 Charleston Road</street1>
                                                                <city>Mountain View</city>
                                                                <state>CA</state>
                                                                <postalCode>94043</postalCode>
                                                                <country>US</country>
                                                                <email>null@cybersource.com</email>
                                                                <ipAddress>192.0.0.1</ipAddress>
                                                </billTo>
                                                <purchaseTotals>
                                                                <currency>ARS</currency>
                                                                <grandTotalAmount>1234</grandTotalAmount>
                                                </purchaseTotals>
                                                <card>
                                                                <accountNumber>4111111111111111</accountNumber>
                                                                <expirationMonth>12</expirationMonth>
                                                                <expirationYear>2022</expirationYear>
                                                                <cvNumber>123</cvNumber>
                                                                <cardType>001</cardType>
                                                </card>
                                                <afsService run="true"/>
                                                <deviceFingerprintID>[DEVICEFINGER_ID]</deviceFingerprintID>
                                </requestMessage>
                </soapenv:Body>
</soapenv:Envelope>"""

  val configuration = Configuration.reference ++ Configuration(ConfigFactory.parseString(
    """
    |ws.followRedirects = true
    |play.ws.ssl.loose.acceptAnyCertificate=true
  """.stripMargin))

  // If running in Play, environment should be injected
  val environment = Environment(new File("."), this.getClass.getClassLoader, Mode.Prod)

  val parser = new WSConfigParser(configuration, environment)
  val config = new AhcWSClientConfig(wsClientConfig = parser.parse())
  val builder = new AhcConfigBuilder(config)
  val logging = new AsyncHttpClientConfig.AdditionalChannelInitializer() {
    override def initChannel(channel: io.netty.channel.Channel): Unit = {
      channel.pipeline.addFirst("log", new io.netty.handler.logging.LoggingHandler("debug"))
    }
  }
  val ahcBuilder = builder.configure()
  ahcBuilder.setHttpAdditionalChannelInitializer(logging)
  val ahcConfig = ahcBuilder.build()
  implicit val system = ActorSystem("MyActorSystem")
  implicit val materializer = ActorMaterializer()
  val wsClient = new AhcWSClient(ahcConfig)

  implicit val ec = ExecutionContext.global
  val futureResponse = wsClient.url(url).post(xml)
  val fr = futureResponse.map { wsresponse => 
    println(wsresponse.status)
    println(wsresponse.xml)
    wsresponse.xml
  }
  val r = Await.ready(fr, 1 minutes)
  println(r)
  
  wsClient.close
  
}