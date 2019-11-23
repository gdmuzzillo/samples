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

trait WSServiceTest {

  val wsClient = {
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
    new AhcWSClient(ahcConfig)  
  }  
  
}