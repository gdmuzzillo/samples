package com.decidir.coretx.domain

import java.util.Date
import com.decidir.core.MedioPago
import javax.inject.Singleton
import com.decidir.coretx.utils.JedisPoolProvider
import com.decidir.coretx.utils.JedisUtils
import javax.inject.Inject
import scala.collection.JavaConverters._
import com.decidir.coretx.utils.ToMap
import com.decidir.coretx.utils.ApiSupport
import com.google.common.base.Splitter
import play.Logger
import scala.util.Try
import play.api.libs.json.Json
import com.decidir.coretx.api.{EncryptedForm, ErrorFactory}
import scala.util.Success
import controllers.utils.OperationJsonFormat._
import com.decidir.encripcion.Encriptador
import com.decidir.encripcion.EncriptadorDES
import com.decidir.encripcion.EncriptadorTDES
import scala.util.Failure
import decidir.sps.util.Utilities
@Singleton
class SiteRepository @Inject() (jedisPoolProvider: JedisPoolProvider) extends JedisUtils {

  val logger = Logger.underlying()
  logger.info("Configuracion pool Jedis: " + jedisPoolProvider.showConf)
  private def siteTransactionsKey(siteId: String) = s"sites:${siteId}:txs"
  private def siteTransactionDetailKey(siteId: String) = s"sites:${siteId}:tx"
  
  val jedisPool = jedisPoolProvider.get
  val entityPrefix = "sites:"
  def entityKey(id: Any) = entityPrefix + id
  def allKey = entityPrefix + "all"
  def encryptionKey(id: Any) = entityKey(id) + ":encryptedForm"  //TODO: la replicacion de este dato esta en WEBTX, agregarlo en core cuando quede deprecado webtx
  def encryptionTypeAllKey = entityPrefix + "all:encryptedType:" //TODO: la replicacion de este dato esta en WEBTX, agregarlo en core cuando quede deprecado webtx
  
  def exists(id: String) = {
    evalWithRedis { _.exists(entityKey(id)) }
  }
  
  /**
   * TODO Pasar a TX (doWithRedisTx)
   */
  def store(entity: Site) = {
    
    try {
    
    val opMap = ApiSupport.toMap(entity)
    val siteKey = entityKey(entity.id)
    storeMapInRedis(siteKey, opMap)
    
    doWithRedis {redis => 
      val cuentas = entity.cuentas
      val cuentasByNdx = (0 to cuentas.size) zip cuentas
      val cuentasKey = siteKey + ":cuentas:"
      cuentasByNdx.foreach {ndxCuenta => 
        val (ndx, cuenta) = ndxCuenta
        val cuentaKey = cuentasKey + ndx
        storeMapInRedis(cuentaKey, ApiSupport.toMap(cuenta))
      }
      redis.set(cuentasKey + "size", cuentas.size.toString)

      val subSites = entity.subSites
      val subSitesByNdx = (0 to subSites.size) zip subSites
      val subSitesKey = siteKey + ":subsites:"
      subSitesByNdx.foreach { ndxSubSite =>
        val (ndx, subSite) = ndxSubSite
        val subSiteKey = subSitesKey + ndx
        storeMapInRedis(subSiteKey, subSite.toMap)
      }
      
      //TODO: momentaneamente la encriptacion se esta guardando en WebTx. A futuro sera replicado aqui.
      
      redis.set(subSitesKey + "size", subSites.size.toString)
      entity.rangos.foreach { rangos => 
        val rangosKey = rangosTarjetasKey(entity.id, rangos.idMarcaTarjeta)
        redis.del(rangosKey)
        rangos.rangos.foreach { rango =>
          redis.lpush(rangosKey, s"${rango.e1}-${rango.e2}")
        }
      }
      
      val merchants = entity.merchants
      val merchantsByNdx = (0 to merchants.size) zip merchants
      val merchantsKey = siteKey + ":merchant:"
      merchantsByNdx.foreach { ndxMerchant =>
        val (ndx, merchant) = ndxMerchant
        val merchantKey = merchantsKey + ndx
        redis.del(merchantKey)
        redis.sadd(merchantKey, merchant)
      }
      redis.set(merchantsKey + "size", merchants.size.toString)
      
      redis.sadd(allKey, entity.id.toString)
    }
    
    } catch {
      case t: Throwable => {
        logger.error(s"Error while store in redis with this site: $entity")
        logger.error(s"Cause: $t")
      }
    }
    
  }
  
  def store(transactions: TransactionsStatus) = {
    
    try {
    
    doWithRedis {redis => 
        transactions.transactions.foreach(transaction => {
          redis.sadd(siteTransactionsKey(transactions.siteId), transaction.siteTransactionId)
          import scala.collection.JavaConversions._
          redis.hmset(siteTransactionDetailKey(transactions.siteId), Map(s"${transaction.siteTransactionId}:status" -> transaction.status.toString))
          }
        )
    }
    
    } catch {
      case t: Throwable => t.printStackTrace()
    }
    
  }
  
  private def rangosTarjetasKey(siteId: String, idMarcaTarjeta: Int) = s"${entityKey(siteId)}:${idMarcaTarjeta}:rangospermitidostarjeta"
  
  def retrieve(id: Any): Option[Site] = {
    val siteKey = entityKey(id)
    val map = evalWithRedis { _.hgetAll(siteKey) }.asScala.toMap
    if(map.isEmpty) None
    else {
      val site = Site.fromMap(map)
      // cuentas
      val cuentasKey = siteKey + ":cuentas:"
      val cantCuentas = evalWithRedis { _.get(cuentasKey + "size") }.toInt
      val cuentas = (0 to cantCuentas - 1).map{ndx =>
        val cuentaKey = cuentasKey + ndx
        val cuentaMap = evalWithRedis { _.hgetAll(cuentaKey) }.asScala.toMap
        Cuenta.fromMap(cuentaMap)
      }.toList
      
      // subsites
      val subsitesKey = siteKey + ":subsites:"
      val cantSubsites = evalWithRedis { _.get(subsitesKey + "size") }.toInt
      val subsites = (0 to cantSubsites - 1).map{ndx =>
        val subsiteKey = subsitesKey + ndx
        val subsitesMap = evalWithRedis { _.hgetAll(subsiteKey) }.asScala.toMap
        InfoSite.fromMap(subsitesMap)
      }.toList
      
      // encrypted
      val encyption = getEncrypted(id.toString) match {
        case Success(enc) => enc
        case Failure(error) => {
          logger.error(s"Error de configuracion para la encriptacion del site: ${id}", error)
          None
        }
      }
      
    	Some(site.copy(cuentas = cuentas, subSites = subsites, encrypt = site.encrypt.copy(encyption = encyption)))
    }
  }
  
  private def getEncrypted(siteId: String): Try[Option[Encryption]] = Try {
    val map = evalWithRedis{_.hgetAll(encryptionKey(siteId))}.asScala.toMap
    if(map.isEmpty) {
      logger.debug(s"No se encuentran replicados los datos de encriptacion para el site: ${siteId}")
      None
    }
    else {
      val encryted = Encryption(map.get("encryptionType").getOrElse(throw new Exception(s"No se ha configurado el tipo de encriptacion para el site: ${siteId}")), 
        map.get("publicKey").getOrElse(throw new Exception(s"No se ha configurado la clave pública para el site: ${siteId}")))
      val enc: Encriptador = encryted.`type` match {
        case "DES" => new EncriptadorDES()
        case "TDES" => new EncriptadorTDES()
        case other => throw new Exception(s"El site: ${siteId} posee un tipo de encriptacion: ${other} - desconocida")
      }
     Some(encryted)
    }
  }
  
  def getEncryptor(site: Site): Try[Option[Encriptador]] = Try {
    if(site.encrypt.cardNumberEnc){
      site.encrypt.encyption.map(encyption => {
        val enc: Encriptador = encyption.`type` match {
          case "DES" => new EncriptadorDES()
          case "TDES" => new EncriptadorTDES()
          case other => throw new Exception(s"El site: ${site.id} posee un tipo de encriptacion: ${other} - desconocida")
        }
        val key = encyption.publicKey.getBytes
        enc.makeKey(key)
        enc
      }) 
    } else {
      logger.warn(s"El sitio ${site.id} no opera con numero de tarjeta encriptada")
      None
    }

  }

  def getEncryptor(siteId: String, tipoEnc: Option[String] = None): Try[Encriptador] = Try {
    val map = evalWithRedis{_.hgetAll(encryptionKey(siteId))}.asScala.toMap
    if(map.isEmpty) {
      throw new Exception("No se encuentran replicados los datos del encriptador del sites")
    }
    else {
      val encrytedForm = fromMap(map)
      if(tipoEnc.isDefined && tipoEnc.get != encrytedForm.encryptionType){
        logger.error("Encryption type not found")
        throw new Exception("El tipo de encriptación no coincide con el configurado")
      }

      val enc: Encriptador = encrytedForm.encryptionType match {
        case "DES" => new EncriptadorDES()
        case "TDES" => new EncriptadorTDES()
        case _ => {
          Logger.error("El comercio no posee encriptación")
          throw new Exception("El comercio no posee encriptación")
        }
      }
      val key = encrytedForm.publicKey.getBytes
      enc.makeKey(key)
      enc
    }
  }

  def fromMap(map: Map[String, String]) = {
    import com.decidir.coretx.api.WebJsonFormats._
    EncryptedForm(map.get("encryptionType").getOrElse(throw new Exception("No se ha configurado el tipo de encriptacion")),
      map.get("publicKey").getOrElse(throw new Exception("No se ha configurado la clave pública")))
  }

  def getDefaultEncryptor(encryptionType: String): Try[Encriptador] = Try {
    val publicKey = evalWithRedis{_.get(encryptionTypeAllKey+encryptionType)}
    if(publicKey == null) {
      throw new Exception("No se encuentran replicados los datos del encriptador por default")
    }
    else {
      val enc: Encriptador = encryptionType match {
        case "DES" => new EncriptadorDES()
        case "TDES" => new EncriptadorTDES()
        case _ => {
          Logger.error("El comercio no posee encriptación")
          throw new Exception("El comercio no posee encriptación")
        }
      }
      val key = publicKey.getBytes
      enc.makeKey(key)
      enc
    }

  }

  def retrieveAll: List[Site] = {
    evalWithRedis { _.smembers(allKey) }.asScala.toList.map(key => retrieve(key)).flatten
  }
  
  def findSubSitesBySite(site: Site): List[InfoSite] = {
    val siteKey = entityKey(site.id)
    
    val subSitesKey = siteKey + ":subsites:"
      val cantSubSites = evalWithRedis { _.get(subSitesKey + "size") }.toInt
      val subSites = (0 to cantSubSites - 1).map{ndx =>
        val subSiteKey = subSitesKey + ndx
        val subSiteMap = evalWithRedis { _.hgetAll(subSiteKey) }.asScala.toMap
        InfoSite.fromMap(subSiteMap)
      }.toList
      
      subSites
  }
  
  val dashSplitter = Splitter.on('-')
  
  def validarRangoNroTarjeta(idSite: String, idMarcaTarjeta: Int, nroTarjeta: String) = {
    
    val rangos = evalWithRedis { _.lrange(rangosTarjetasKey(idSite, idMarcaTarjeta), 0, -1) }.
                    asScala.toList.map(dashSplitter.split(_).asScala.toSeq).map(seq => (seq(0), seq(1)))
    
    rangos.find(r => r._1 <= nroTarjeta && r._2 >= nroTarjeta).isDefined
  }
  
}

object CyberSourceConfiguration {
  def fromMap(map: Map[String, String]) = {
    CyberSourceConfiguration(
        enabled = map.get("enabled").map(_.toBoolean).getOrElse(false),
//        modelo = map.get("modelo").map(_.toInt).getOrElse(2),
        withAutoReversals = map.get("withAutoReversals").map(_.toBoolean).getOrElse(false),
        vertical = map.getOrElse("vertical", ""), 
        mid = map.getOrElse("mid",""), 
        securityKey = map.getOrElse("securityKey",""), 
        continueOnBlack = map.get("continueOnBlack").map(_.toBoolean).getOrElse(false),
        withAutoReversalsOnBlue = map.get("withAutoReversalsOnBlue").map(_.toBoolean).getOrElse(false))
  }
}

object MailConfiguration {
  def fromMap(map: Map[String, String]) = {
    MailConfiguration(
      map("mandarMailAUsuario").toBoolean,
      map("mandarMailASite").toBoolean,
      map("mail"),
      map("replyMail"))
  }
}

object HashConfiguration {
  def fromMap(map: Map[String, String]) = {
    HashConfiguration(
      map("useHash").toBoolean,
      map.get("firstHashDate").map(cd => new Date(cd.toLong))
    )
  }
}

object Encryption {
  def fromMap(map: Map[String, String]) = {
    Encryption(
      map("type"),
      map("publicKey")
    )
  }
}

object Encrypt {
  def fromMap(map: Map[String, String]) = {
    Encrypt(
      ApiSupport.selectPrefixedNoneIfEmpty("encryptedForm", map).map(Encryption.fromMap),
      map("cardNumberEnc").toBoolean
    )
  }
}

// <option value="1">Enterprise</option>
// <option value="2">Agregador</option>
// <option value="3">Jerarquia</option>
case class CyberSourceConfiguration(
    enabled: Boolean, 
//    modelo: Int,
    withAutoReversals: Boolean, 
    vertical: String, 
    mid: String, 
    securityKey: String, 
    continueOnBlack: Boolean,
    withAutoReversalsOnBlue: Boolean)

object PostbackConf {
  def fromMap(map: Map[String, String]) = {
    PostbackConf(
      map("usaUrlDinamica").toBoolean,
      map("urlPost"))
  }
}


object Site {
  
  def fromMap(map: Map[String, String]) = {
    Site(map("id"),
      map.get("description"),
      map("habilitado").toBoolean,
      map("url"),
      map("agregador"),
      map.get("timeoutCompra").getOrElse("0").toInt,
      map("validaRangoNroTarjeta").toBoolean,
      map("transaccionesDistribuidas"),
      map("montoPorcent"),
      map("reutilizaTransaccion").toBoolean,
      map("enviarResumenOnLine"),
      ApiSupport.selectPrefixedNoneIfEmpty("ppb", map).map(PostbackConf.fromMap).get,
      map("validaOrigen").toBoolean,
      ApiSupport.selectPrefixedNoneIfEmpty("mailConfiguration", map).map(MailConfiguration.fromMap).get,
      Nil,
      Nil,
      map("mensajeria"),
      Nil,
      ApiSupport.selectPrefixedNoneIfEmpty("cyberSourceConfiguration", map).map(CyberSourceConfiguration.fromMap),
      Nil,
      map.get("parentSiteId"),
      ApiSupport.selectPrefixedNoneIfEmpty("hashConfiguration", map).map(HashConfiguration.fromMap).get,
      ApiSupport.selectPrefixedNoneIfEmpty("encrypt", map).map(Encrypt.fromMap).get,
      map.get("mensajeriaMPOS"),
      map("isTokenized").toBoolean,
      map("timeToLive").toInt)
  }
  
  def fromJson(jsonString: String): Try[Site] = {
    val json = Json.parse(jsonString) 
    // TODO 
    json.validate[Site].fold(
        errors => ErrorFactory.uncategorizedFailure(new Exception("TODO")), 
        mp => Success(mp)
    )
  }
}


case class Site (id: String,
                 description : Option[String],
                 habilitado: Boolean,
                 url: String,
                 agregador: String,
                 timeoutCompra: Int,
                 validaRangoNroTarjeta: Boolean,
                 transaccionesDistribuidas: String,
                 montoPorcent: String,
                 reutilizaTransaccion: Boolean,
                 enviarResumenOnLine: String,
                 ppb: PostbackConf,
                 validaOrigen: Boolean,
                 mailConfiguration: MailConfiguration,
                 cuentas: List[Cuenta],
                 rangos: List[RangosPermitidosTarjeta],
                 mensajeria: String,
                 subSites: List[InfoSite],
                 cyberSourceConfiguration: Option[CyberSourceConfiguration],
                 merchants: List[String],
                 parentSiteId: Option[String], 
                 hashConfiguration: HashConfiguration,
                 encrypt: Encrypt,
                 mensajeriaMPOS: Option[String],
                 isTokenized: Boolean,
                 timeToLive: Int){
  
  def cuenta(medioPagoId: String, 
      protocoloId: Int,
      backenId: Int): Option[Cuenta] = cuentas.find( cuenta =>
          cuenta.idMedioPago == medioPagoId && 
          cuenta.idProtocolo == protocoloId &&
          cuenta.idBackend == backenId.toString)

  def getNroTarjetaVisible(nroTarjeta: String, medioPagoId: String, protocoloId: Int, backenId: Int): String = {
    val formatoNroTarjetaVisible = this.cuenta(medioPagoId,protocoloId,backenId).fold("")(c => c.formatoNroTarjetaVisible)

    if(formatoNroTarjetaVisible.isEmpty)
      nroTarjeta.takeRight(4)
    else
      formatStringWithMask(nroTarjeta, formatoNroTarjetaVisible)
  }

  private def formatStringWithMask(nroTarjeta: String, formatoNroTarjetaVisible:String): String = {
    if (formatoNroTarjetaVisible.forall(_ == '#'))
      nroTarjeta
    else
      nroTarjeta.take(6) + formatoNroTarjetaVisible.find(_ != '#').getOrElse('X').toString * (nroTarjeta.size - 6 - 4) + nroTarjeta.takeRight(4)
  }
}

case class PostbackConf(usaUrlDinamica: Boolean,
                        urlPost: String)

case class MailConfiguration(mandarMailAUsuario: Boolean,
                    mandarMailASite: Boolean,
                    mail: String,
                    replyMail: String)

case class HashConfiguration(useHash: Boolean,
                             firstHashDate: Option[Date])

case class Encrypt(encyption: Option[Encryption], cardNumberEnc: Boolean)    

case class Encryption(`type`: String, publicKey: String)

object Cuenta {
  def fromMap(map: Map[String, String]) = {
    Cuenta(idMedioPago = map("idMedioPago"), 
         idProtocolo = map("idProtocolo").toInt, idBackend = map("idBackend"), nroId = map("nroId"),
         estaHabilitadaParaOperarConPlanN = map("estaHabilitadaParaOperarConPlanN").toBoolean, habilitado = map("habilitado").toBoolean,
         numeroDeTerminal = map("numeroDeTerminal"), utilizaAutenticacionExterna = map("utilizaAutenticacionExterna").toBoolean,
         autorizaEnDosPasos = map("autorizaEnDosPasos").toBoolean, autoriza2PLimiteInferior = map("autoriza2PLimiteInferior").toInt, autoriza2PLimiteSuperior = map("autoriza2PLimiteSuperior").toInt,
         planCuotas = map("planCuotas"), pasoAutenticacionExterna = map("pasoAutenticacionExterna").toBoolean,
         pasoAutenticacionExternaSinServicio = map("pasoAutenticacionExternaSinServicio").toBoolean, formatoNroTarjetaVisible = map("formatoNroTarjetaVisible"), 
         password = map("password"), pagoDiferidoHabilitado = map("pagoDiferidoHabilitado").toBoolean, aceptaSoloNacional = map("aceptaSoloNacional").toBoolean, 
         tipoPlantilla = map("tipoPlantilla"), nroIdDestinatario = map("nroIdDestinatario"))
    
  }
}

case class Cuenta(idMedioPago: String, idProtocolo: Int, idBackend: String, nroId: String, 
                  estaHabilitadaParaOperarConPlanN: Boolean, habilitado: Boolean, numeroDeTerminal: String, 
                  utilizaAutenticacionExterna: Boolean, autorizaEnDosPasos: Boolean, autoriza2PLimiteInferior: Int, autoriza2PLimiteSuperior: Int, 
                  planCuotas: String, pasoAutenticacionExterna: Boolean, pasoAutenticacionExternaSinServicio: Boolean, 
                  formatoNroTarjetaVisible: String, password: String, pagoDiferidoHabilitado: Boolean, 
                  val aceptaSoloNacional: Boolean, tipoPlantilla: String, nroIdDestinatario: String){  
}

object InfoSite {
  def fromMap(map: Map[String, String]) = {
    InfoSite(map("idSite"), map("porcentaje").toFloat)
  }
}

case class InfoSite(idSite: String, porcentaje: Float) extends ToMap {
  def toMap = {
    ApiSupport.toMap(this)
  }
}
                  
                  
   
case class RangosPermitidosTarjeta(idSite: String, idMarcaTarjeta: Int, rangos: List[Pair])

case class TransactionStatus(siteTransactionId: String, status: Int)
case class TransactionsStatus(siteId: String, transactions: List[TransactionStatus])


