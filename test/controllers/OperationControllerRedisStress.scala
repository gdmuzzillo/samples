package controllers

import com.decidir.coretx.api.OperationJsonFormats._
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.domain.OperationResourceRepository
import com.decidir.coretx.utils.JedisPoolProvider
import play.api.Configuration
import com.decidir.encrypt.EncryptionService
import com.decidir.encrypt.EncryptionRepository

object OperationControllerRedisStress extends App {

  val operationString = """{"transactionId":"%d","nro_operacion":"%d","monto":100,"cuotas":1,"cuotas_dist":"01","datos_titular":{"email_cliente":"asdssssss@asd.com","tipo_doc":1,"nro_doc":"3333311333","calle":"asdasdasdasdasdasdasdas","nro_puerta":123,"fecha_nacimiento":"01011911"},"datos_medio_pago":{"medio_de_pago":1,"nro_tarjeta":"4929641395150171","nombre_en_tarjeta":"pepito","vencimiento":"2211","cod_seguridad":"1234"},"datos_site":{"site_id":"00040407","url_dinamica":"http://catalina.decidir.net/mostrarParametros.jsp","param_sitio":"","referer":"http://localhost:9001/test/TestCompra-Dist.html"}}"""
  val jedisPoolProvider = new JedisPoolProvider(Configuration.empty)
  val encripcionService = new EncryptionService(new EncryptionRepository(jedisPoolProvider), Configuration.from(Map("sps.encryption.key" -> "b8c2ca4a7baed8e334dc49c01c7ea22d016f83bf66b288333a163233ed46565e")) )
  val operationRepo = new OperationResourceRepository(jedisPoolProvider, Configuration.empty, encripcionService)
 
  
  for(i <- 1 to 1000000) {

    val json = Json.parse(operationString.format(i, i))
    val operation = json.validate[OperationResource].get
    operationRepo.store(operation, Integer.MAX_VALUE)
    if(i % 100 == 0) print(".")    
    if(i % 10000 == 0) println
  }
  println
  
}