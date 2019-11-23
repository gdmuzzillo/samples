package services.replication


import javax.inject.Singleton
import legacy.decidir.sps.domain.LegacyDao
import controllers.utils.OperationJsonFormat._
import com.decidir.events.EntityUpdatedEvent
import decidir.sps.core.{TipoActividad => TipoActividadLegacy}
import decidir.sps.core.{Site => SiteLegacy}
import com.decidir.coretx.domain._
import play.api.libs.json.Json
import play.Logger
import decidir.sps.core.{MedioPago => MedioPagoLegacy}
import decidir.sps.core.{Moneda => MonedaLegacy}
import scala.util.Try
import scala.collection.JavaConverters._
import javax.inject.Inject
import legacy.decidir.sps.domain.DBSPSProvider
import services.KafkaClient
import com.decidir.coretx.api._
import com.decidir.coretx.api.WebJsonFormats._
import com.decidir.events.EntityUpdatedEventJsonFormats._
import scala.collection.JavaConversions._
import com.decidir.coretx.api.OperationJsonFormats._
import scala.util.Success
import scala.util.Failure
import play.api.Configuration


/**
 * @author martinpaoletta
 * Nota: este servicio va del lado del replicador
 */

trait ReplicationNotificationService {
  def replicate(entityUpdatedEvent: EntityUpdatedEvent)
}

@Singleton
class DirectReplication @Inject() (legacyReplicationClient: LegacyReplicationClient) extends ReplicationNotificationService {
// Por ahora va directo, a futuro se podra desacoplar via kafka (y tener LegacyReplicationService en una app separada)
  def replicate(entityUpdatedEvent: EntityUpdatedEvent) = legacyReplicationClient.entityUpdated(entityUpdatedEvent)
}


@Singleton
class LegacyReplicationService @Inject() (dbspsProvider: DBSPSProvider, 
              legacyDao: LegacyDao, 
              replicationNotificationService: ReplicationNotificationService,
              infoFiltrosRepository: InfoFiltrosRepository,
              kafkaClient: KafkaClient,
              configuration: Configuration) {
  
  val batchMaxSize = configuration.getInt("replication.batch.maxSize").getOrElse(100)
  val sleepTimeBatch = configuration.getInt("replication.batch.delay").getOrElse(800) //milliseconds
  
  val dbsps = dbspsProvider.dbsps
  val dbsac = dbspsProvider.dbsac
  val logger = Logger.underlying()
  
  
  def replicate(entidad: Entidad): Unit = entidad match {
    
    case Entidad("IdComercioLocation", None) => replicateAllIdComercioLocation
    case Entidad("IdComercioLocation", Some(id)) => replicateIdComercioLocation(id)
    
    case Entidad("TipoActividad", None) => replicateAllTipoActividad
    case Entidad("TipoActividad", Some(id)) => replicateTipoActividad(id)

    case Entidad("Site"|"site", None) => replicateAllSites
    case Entidad("Site"|"site", Some(id)) => replicateSite(id)
    
    case Entidad("MedioPago", None) => replicateAllMedioPago
    case Entidad("MedioPago", Some(id)) => replicateMedioPago(id)
    
    case Entidad("Moneda", None) => replicateAllMoneda
    case Entidad("Moneda", Some(id)) => replicateMoneda(id)
    
    case Entidad("MarcaTarjeta", None) => replicateAllMarcaTarjeta
    case Entidad("MarcaTarjeta", Some(id)) => replicateMarcaTarjeta(id)
    
    case Entidad("InfoFiltros", None) => replicateAllInfoFiltros
    case Entidad("InfoFiltros", Some(siteId)) => replicateInfoFiltrosXSite(siteId)

    case Entidad("Terminal", None) => replicateAllTerminales
    case Entidad("Terminal", Some(siteId)) => replicateTerminalesXSite(siteId)

    case Entidad("TipoDocumento", None) => replicateAllTipoDoc
    
    case Entidad("Motivo", None) => replicateAllMotivos
    
    case Entidad("Encripcion", None) => replicateTablaEncripcion
    
    case Entidad("IdentificadorDePagos", None) => replicateIdentificadorDePagos
    
    case Entidad("BinFilter", None) => replicateAllBinFilter
    
    case Entidad("TransactionsStatus", Some(siteId)) => replicateTransactionsStatus(siteId)

    case Entidad("Notifications", None) => replicateAllNotifications
    case Entidad("Notifications", Some(siteId)) => replicateNotificationsBySiteId(siteId)

    case Entidad("Banco", None) => replicateAllBanco
    case Entidad("Banco", Some(bankId)) => replicateBanco(bankId)

    
    case Entidad(other, _) => logger.error("No se replicar " + other)
    
  }  
  
  private def replicateIdentificadorDePagos() = {
    val chargeId = legacyDao.findIdentificadorDePagos()
    replicationNotificationService.replicate(EntityUpdatedEvent("IdentificadorDePagos", List(chargeId.toString)))
  }
  
  private def replicateTablaEncripcion() = {
    val lista = dbsps.obtenerTablaEncripcion().asScala
    val actual = lista.last
    val json = actual.toJson().toString
    logger.info(s"Replicando ${lista.size} encripciones.")
    replicationNotificationService.replicate(EntityUpdatedEvent("Encripcion", List(json)))
  }

  private def replicateAllTipoDoc() = {
    val tiposDocumentos = legacyDao.getAllTipoDocumento()
    val jsonSList = tiposDocumentos.map(Json.toJson(_).toString)

    logger.info(s"Replicando ${tiposDocumentos.size} tipos de docs.")
    replicationNotificationService.replicate(EntityUpdatedEvent("TipoDocumento", jsonSList))

  }
  //FIXME aplicar replicacion por lotes y revisar como guarda los datos el consumer (hay demasiadas keys en redis)
  private def replicateAllMotivos() = {
    // idMotivo: Int, descripcion: String, descripcion_display: String, idMotivoTarjeta: String, infoAdicional: String
    val motivos = dbsps.getAllMotivos
    logger.info(s"Replicando ${motivos.size} motivos.")
    motivos.asScala.toStream.
      map(ml => Motivo(ml.getId, ml.getIdProtocolo, ml.getIdTipoOperacion(), ml.getDescripcion, ml.getDescripcion_display)).
      map(Json.toJson(_)).
      foreach(json => replicationNotificationService.replicate(EntityUpdatedEvent("Motivo", List(json.toString))))
  }
  
  private def replicateAllIdComercioLocation() = {
    val ids = legacyDao.findAllComercioLocationIds()
    ids foreach replicateIdComercioLocation
  }
  
  private def replicateIdComercioLocation(id: String) = {
    
    val nroid = dbsac.getIdComercioLocation(id)
    
    val pair = Pair(id, nroid)
    
    val jsonS = Json.toJson(pair).toString
    
    replicationNotificationService.replicate(EntityUpdatedEvent("IdComercioLocation", List(jsonS)))
    
  }

  private def replicateAllNotifications() = try {
    logger.info("replicateAllNotifications")
    legacyDao.getAllSiteIds().foreach(replicateNotificationsBySiteId)
  }catch {
    case t:Throwable => logger.error("Error replicando las Notifications", t)
  }

  private def replicateNotificationsBySiteId(siteId: String) = {
    if(isValidSiteId(siteId)) {
      val notifications = legacyDao.findAllNotificationsBySiteId(siteId)
      if(notifications.isEmpty) {
        logger.warn("No hay notificaciones para replicar para site " + siteId)
      } else {
        val notificationsList = SitesNotificationsList(list = notifications)
        logger.info(s"replicando ${notifications.size} notificaciones para site $siteId")
        val jsonS = Json.toJson(notificationsList).toString
        val eventSiteWeb = EntityUpdatedEvent("Notifications", List(jsonS))
        kafkaClient.send(Json.toJson(eventSiteWeb).toString, "replication-webtx-topic")
      }
    }
  }


  private def replicateAllTerminales = try {
    logger.info("Replicando Terminales...")
    legacyDao.getAllSiteIds().foreach(replicateTerminalesXSite)
  }
  catch {
    case t: Throwable => {
      logger.error("Error replicando todas las terminales", t)
    }
  }
  
  private def replicateTerminalesXSite(siteId: String) = try {
    if(isValidSiteId(siteId)) {
      val terminales = legacyDao.findAllTerminalesSitio(siteId)
      val terminalsQuantity = terminales.foldRight(0)((tr, res) => tr.terminales.size + res)

      if(terminales.isEmpty) {
        logger.warn("No hay terminales para replicar para site " + siteId)
      }
      else {
        logger.info(s"Replicando ${terminalsQuantity} terminales con siteId: $siteId")
        val jsonS = Json.toJson(terminales).toString
        replicationNotificationService.replicate(EntityUpdatedEvent("Terminal", List(jsonS)))
      }

      val nrostrace = legacyDao.findAllNrosTraceSitio(siteId)
      if(nrostrace.isEmpty) {
        logger.warn("No hay nros de trace/ticket/batch para site " + siteId)
      }
      else {
        logger.info(s"Replicando ${nrostrace.size} traces con siteId: $siteId")
        val jsonS = Json.toJson(nrostrace).toString
        replicationNotificationService.replicate(EntityUpdatedEvent("NrosTrace", List(jsonS)))
      }
    }
  }
  catch {
    case t: Throwable => {
      logger.error("Error replicando terminales del site " + siteId , t)
    }
  }
  
  private def replicateAllInfoFiltros = Try{
    
    legacyDao.getAllSiteIds().foreach(replicateInfoFiltrosXSite)
    
  }
  
  private def replicateInfoFiltrosXSite(siteId: String) = Try{
    if(isValidSiteId(siteId)) {
      val idsMarcaTarjetaForSite = legacyDao.findAllMarcaTarjetasForSite(siteId)
      logger.info(s"Encontradas ${idsMarcaTarjetaForSite.size} marcas de tarjetas para site: $siteId")

      val binesXMarcaTarjeta = idsMarcaTarjetaForSite.map(idMarcaTarjeta =>
        (idMarcaTarjeta, legacyDao.findAllBinesForSiteAndTarjeta(siteId, idMarcaTarjeta))).toMap

      binesXMarcaTarjeta.foreach { tuple =>
        logger.info(s"Para site $siteId y marca de tarjeta ${tuple._1} se encontraron ${tuple._2.size} bines.")
      }


      val infoFiltros = idsMarcaTarjetaForSite.
        map(dbsps.getMarcaTarjeta(_)).
        map(dbsac.getInfoFiltros(siteId, _)).
        map(iff => InfoFiltros(siteId, iff.getMarcaTarjeta.getId, iff.getFiltraXBin, iff.getListaNegra,
          binesXMarcaTarjeta.getOrElse(iff.getMarcaTarjeta.getId, Nil)))

      infoFiltrosRepository.cleanData(siteId)
      val jsonListS = infoFiltros map {iff =>
        Json.toJson(iff).toString
      }
      replicationNotificationService.replicate(EntityUpdatedEvent("InfoFiltros", jsonListS))
    }
  }
  
  
  private def noneIfNullOrEmpty(string: String) = if(string == null || string.trim().isEmpty()) None else Some(string)
  
//  def replicateAllInfoReglas = {
//    legacyDao.getAllSiteIds().foreach(replicateInfoReglas)
//  }

//  def replicateInfoReglas(idSite: String): Unit  = {
//    val reglasSiteList = dbsac.getInfoReglasProtocolo(idSite).asScala.toList.map(InfoReglas.fromLegacy)
//    if(!reglasSiteList.isEmpty) {
//      val reglasSite = InfoReglasList(idSite, reglasSiteList)
//      import com.decidir.coretx.domain.InfoReglasJsonFormats._
//      val json = Json.toJson(reglasSite)
//      replicationNotificationService ! EntityUpdatedEvent(reglasSite.getClass().getSimpleName, json.toString)  
//    }
//  }
  
  def replicateAllMarcaTarjeta = {
    try {
      legacyDao.getAllMarcaTarjetaIds.foreach(id => replicateMarcaTarjeta(id.toString))
    } 
    catch {
      case e: Exception => {
        logger.error("Error replicateAllMarcaTarjeta", e)
      }
    }    

  }
  
  
  def replicateMarcaTarjeta(id: String) = {
    try {
      val idmt = id.toInt
      val mtl = dbsps.getMarcaTarjeta(idmt)
      val rangosNacionales = dbsps.getRangosTarjetaNacional(new Integer(idmt)).asScala.toList.map(array => Pair(array(0), array(1)))
      val mt = MarcaTarjeta(mtl.getId, 
                            mtl.getDescri, 
                            mtl.getCodAlfaNum, 
                            noneIfNullOrEmpty(mtl.getUrlServicio), 
                            noneIfNullOrEmpty(mtl.getSufijoPlantilla), 
                            noneIfNullOrEmpty(mtl.getVerificaBin), 
                            rangosNacionales)
      val jsonS = Json.toJson(mt).toString
      replicationNotificationService.replicate(EntityUpdatedEvent(mt.getClass().getSimpleName, List(jsonS)))    
       marcaTarjetaToSoapTx(mt)
    }
    catch {
      case e: Exception => {
        logger.error("Error replicando tarjeta con id " + id, e)
      }
    }
  }
  
  private def marcaTarjetaToSoapTx(marcaTarjeta: MarcaTarjeta) = {
    logger.info(s"""Replicating to SoapTx MarcaTarjeta: ${marcaTarjeta.id} - ${marcaTarjeta.codAlfaNum}""")
    val jsonS = Json.toJson(marcaTarjeta).toString
    val eventmarcaTarjetaSoap= EntityUpdatedEvent("MarcaTarjeta", List(jsonS)) 
    kafkaClient.send(Json.toJson(eventmarcaTarjetaSoap).toString, "replication-soaptx-topic")
  }
  
  def replicateAllMoneda = {
    try {
      logger.info(s"Replicando todas las monedas" )
      dbsps.getMonedas().asScala.foreach{e => Try{replicateMoneda(e)} }
    } 
    catch {
      case e: Exception => {
        logger.error("Error replicateAllMoneda", e)
      }
    }    

  }
  
  def replicateMoneda(id: String): Unit = {
    try {
      val monedas = dbsps.getMonedas
      logger.info(s"Replicando ${monedas.size} monedas.")
      monedas.asScala.filter(_.getIdMoneda == id).foreach(replicateMoneda)
    } 
    catch {
      case e: Exception => {
        logger.error("Error replicateMoneda", e)
      }
    }    

  }
  
  private def replicateMoneda(m: MonedaLegacy): Unit = {
      val moneda = Moneda(m.getIdMoneda, m.getDescripcion, m.getSimbolo, m.getcodigoIsoAlfaNum, m.getCodigoIsoNum)
      val jsonS = Json.toJson(moneda).toString
      replicationNotificationService.replicate(EntityUpdatedEvent(moneda.getClass().getSimpleName, List(jsonS)))
  }  
  
  def replicateAllMedioPago = {
    logger.info(s"Replicando todos los medios de pago" )
    try {
      val paymentMethods = dbsps.getMediosPago().asScala.toList
      logger.info(s"Replicando ${paymentMethods.size} medios de pagos.")
      paymentMethods.foreach{pm => replicateMedioPago(pm) }
      paymentMethodToWebTx(paymentMethods)
    } 
    catch {
      case e: Exception => {
        logger.error("Error replicateAllMedioPago", e)
      }
    }    
  }
  
  private def paymentMethodToWebTx(paymentMethods: List[MedioPagoLegacy]) = {
    val paymentMethodsWeb = PaymentMethodsWeb( list = paymentMethods.map(pm => PaymentMethodWeb(id = pm.getIdMedioPago, protocol = pm.getProtocol, template_suffix = if(pm.getTemplateSuffix != null) pm.getTemplateSuffix else "")))
    val jsonS = Json.toJson(paymentMethodsWeb).toString
    val eventPaymentMethodsWeb = EntityUpdatedEvent("PaymentMethodsWeb", List(jsonS)) 
    kafkaClient.send(Json.toJson(eventPaymentMethodsWeb).toString, "replication-webtx-topic")
  }
  
  def replicateMedioPago(id: String): Unit = {
    try {
      val paymentMethod = dbsps.getMediosPago().asScala.filter(_.getIdMedioPago == id).toList
      paymentMethod.foreach(mp => replicateMedioPago(mp))
      paymentMethodToWebTx(paymentMethod)
    } 
    catch {
      case e: Exception => {
        logger.error("Error replicateMedioPago", e)
      }
    }    
    
  }
  
  private def legacy2new(m: MedioPagoLegacy) = {
      val moneda = m.getMoneda
      val idMoneda = if(moneda != null) Some(moneda.getIdMoneda) else None
      val marcaTarjeta = m.getMarcaTarjeta
      val idMarcaTarjeta = if(marcaTarjeta != null) Some(marcaTarjeta.getId.toInt) else None
      val cardBrand = if(marcaTarjeta != null) marcaTarjeta.getDescri else ""
      
    
      MedioDePago(m.getIdMedioPago, m.getDescripcion, idMoneda, idMarcaTarjeta, cardBrand, m.getLimite,
          m.getBackend, m.getProtocol, 
          CardBrandOperations(m.getAnnulment, 
              m.getAnnulmentPreApproved, 
              m.getRefundPartialBeforeClose, 
              m.getRefundPartialBeforeCloseAnnulment,
              m.getRefundPartialAfterClose, 
              m.getRefundPartialAfterCloseAnnulment,
              m.getRefund, 
              m.getRefundAnnulment,
              m.getTwoSteps),
          m.getBinRegex,
          m.getBlackList,
          m.getWhiteList,
          m.getValidateLuhn,
          m.getCyberSourceApiField,
          m.getTokenized,
          m.esAgro().equalsIgnoreCase("S"))
  }
  
  def replicateMedioPago(m: MedioPagoLegacy): Unit = {
      val medioPago = legacy2new(m)
      val jsonS = Json.toJson(medioPago).toString
      replicationNotificationService.replicate(EntityUpdatedEvent(medioPago.getClass().getSimpleName, List(jsonS)))
      replicateAllBines
      replicateBinFilter(m)
  }
  
  private def replicateAllBines() = {
      try {
        val bins = Bins(dbsps.getAllBins.asScala.toList)
        logger.info(s"Replicando ${bins.list.size} bines.")
        val jsonS = Json.toJson(bins).toString
        replicationNotificationService.replicate(EntityUpdatedEvent(bins.getClass().getSimpleName, List(jsonS)))
      } 
      catch {
        case e: Exception => {
          logger.error("Error replicateMedioPago, replicando bines de todos los medios de pago", e)
        }
      } 
  }
  
  
  //Nueva replicación de sites batch
  //////////////////////////////////////////////////////////////////////
  def replicateAllSites() = {
    //esto se hacia una vez por cada site, ahora solo al comenzar la repliacion
    replicateEncriptedFormList
    Try {
      legacyDao.getAllSiteIds
    } match {
      case Failure(e) => logger.error("Error al consultar site Ids", e)
      case Success(ids) => replicateAsBatchOfSites(ids.toList, Set())
    }
  }
  def replicateSite(id: String) = {
    replicateAsBatchOfSites(List(id), Set())
  }
  
  def replicateAsBatchOfSites(siteIds: List[String], replicated: Set[String]): Unit = {
    logger.info(s"Replicando ${siteIds.size} sites.")
    //Se filtran los que ya han sido replicados
    val splitted = siteIds.filterNot{ 
      id => val exists = replicated.contains(id) 
            if(exists) logger.warn(s"Site $id ya replicado")
            exists
    }.splitAt(batchMaxSize)
    val sites = getSitesLegacy(splitted._1)
    
    replicateToCoreTxAsBatch(sites)
    replicateToSoapTxAsBatch(sites)
    replicateToWebTxAsBatch(sites)
    replicateToFormsAsBatch(sites)
    
    //obtiene los merchants de cada site replicado
    val merchantIds = sites.map( dbsps.getMerchants(_).asScala.toList).flatten
    //se agregan los merchants al remanente para una siguiente corrida
    merchantIds ++ splitted._2 match {
      case Nil => Logger.info("Sites replication finished")
      case moreSites => {
        Thread.sleep(sleepTimeBatch)
        replicateAsBatchOfSites(moreSites, replicated ++ splitted._1 )
      }
    }
    
  }

  /**
   * SiteIds Validations
   */

  private def logInvalidSiteIds(invalidSiteIds: List[String]): Unit = {
    if(invalidSiteIds.nonEmpty) {
      logger
        .warn(s"Number of empty sites omitted from replication: ${invalidSiteIds.size}.")
    }
  }

  private def validateListOfSiteIds(siteIds: List[String]): List[String] = {
    logInvalidSiteIds(siteIds.filterNot(sId => sId.trim.nonEmpty))
    siteIds.filter(sId => sId.trim.nonEmpty)
  }

  private def isValidSiteId(siteId: String): Boolean = {
    val isValid = siteId.trim.nonEmpty
    if(!isValid) logger.warn("Ommited invalid siteId.")
    isValid
  }

  def getSitesLegacy(siteIds: List[String]): List[SiteLegacy] = {
      val sites = dbsps.obtenerSitesDecidir(validateListOfSiteIds(siteIds)).asScala
      sites.foreach { site =>
        site.getCuentas.addAll(dbsps.getCuentas(site))
      }
      sites.toList
  }
  
  def replicateToCoreTxAsBatch(sites: List[SiteLegacy]) = {
    val batchSites = sites.map { site =>
      convertSiteLegacy2Site(site) match {
        case Failure(e) => {
          logger.error(s"Conversion error siteId: ${site.getIdSite}",e)
          None
        }
        case Success(s) => Option(s)
      }
    }
    val jsonListS = batchSites.filter(_.isDefined).map { Json.toJson(_).toString }
    replicationNotificationService.replicate(EntityUpdatedEvent("Site", jsonListS))
  }
    
  private def replicateToSoapTxAsBatch(sites: List[SiteLegacy]) = {
    
    val authKeys = sites.map { site => AuthKey(key = Option(site.getPublikKey) , siteId = site.getIdSite) }
    val jsonListS = authKeys map { Json.toJson(_).toString } 
    val eventSoap = EntityUpdatedEvent("AuthKey", jsonListS)
    kafkaClient.send(Json.toJson(eventSoap).toString, "replication-soaptx-topic")
  }

  private def replicateToWebTxAsBatch(sites: List[SiteLegacy]) = {
    val batchSites = sites map convertSiteLegacy2SiteWeb
    val jsonListS = batchSites map { Json.toJson(_).toString }
    val eventSiteWeb = EntityUpdatedEvent("Site", jsonListS)              
    kafkaClient.send(Json.toJson(eventSiteWeb).toString, "replication-webtx-topic")
    
  }
  private def convertSiteLegacy2SiteWeb(siteLegacy: SiteLegacy) = {
      val encryptedForm = if (!siteLegacy.encripta) None else Some(EncryptedForm(siteLegacy.getTipoEncripcion, siteLegacy.getPublikKey))            
      val hashKey = if(!siteLegacy.getFlagclavehash) None else Some(siteLegacy.getClavehash)
      val templateTypeMap = for {
        cuenta <- siteLegacy.getCuentas
      } yield (cuenta.getIdMedioPago -> cuenta.getTipoPlantilla)

      SiteWeb(id = siteLegacy.getIdSite,
                    amountPorcent = if(siteLegacy.getMontoPorcent.equals("")) None else Some(siteLegacy.getMontoPorcent),
                    encryptedForm = encryptedForm,
                    hashKey = hashKey,
                    templateTypeMap = templateTypeMap.toMap)
  }

   private def replicateEncriptedFormList() = {      
      val encryptedLegacy = dbsps.obtenerEncriptacionDefaultSiteDecidir()
      val encrypted = EncryptedFormList(list = encryptedLegacy.toList.map( encr => EncryptedForm(encryptionType = encr.getType, publicKey = encr.getPublicKey)))
      val jsonS = Json.toJson(encrypted).toString
      val eventEncrypted = EntityUpdatedEvent("Encrypted", List(jsonS)) 
      kafkaClient.send(Json.toJson(eventEncrypted).toString, "replication-webtx-topic")
  }
  
  def replicateToFormsAsBatch(sites: List[SiteLegacy]) = {
    val entitiesList = sites.map { site =>
      val siteTemplateList = dbsps.getSiteTemplates(site).asScala.toList
      
      SiteForms(
        id = site.getIdSite ,
        ttlForm = site.getTimeoutCompra,
        templates = siteTemplateList.map { st => 
          Template(
            id = st.getTemplate_id,
            state = st.getState,
            alias = Option(st.getAlias),
            signed = st.isSigned,
            template = Option(st.getTemplate)
          ) 
        }
      )
    }
    val jsonSList = entitiesList.map(Json.toJson(_).toString)
    val eventSiteForms = EntityUpdatedEvent("SiteForms", jsonSList) 
    kafkaClient.send(Json.toJson(eventSiteForms).toString, "replication-forms-topic")

    replicateSitePaymentTypes()
  }

  private def replicateSitePaymentTypes() = {

    val sitePaymentTypes = dbsps.getSitePaymentTypes.asScala.toList
    val entitiesList = sitePaymentTypes.map { item =>
      SitePaymentType(item.getSiteId, item.getPaymentType)
    }
    val jsonList = entitiesList.map(Json.toJson(_).toString)

    val eventSitePaymentTypes = EntityUpdatedEvent("SitePaymentTypes", jsonList)
    kafkaClient.send(Json.toJson(eventSitePaymentTypes).toString, "replication-forms-topic")
  }
  
  def convertSiteLegacy2Site(siteLegacy: SiteLegacy): Try[Site] = Try {
    val id = siteLegacy.getIdSite
    val url = legacyDao.retrieveSiteUrl(id).getOrElse("")

    def ein(string: String) = if(string == null) "" else string
    
    val cuentas = siteLegacy.getCuentas.map { cl => 
      
      Cuenta(cl.getIdMedioPago,
             cl.getProtocoloId, 
             ein(cl.getBackEndId),
             ein(cl.getNroId),
             cl.permiteOperarConPlanN, 
             cl.estaHabilitada(), 
             ein(cl.getNumeroDeTerminal),
             cl.utilizaAutenticacionExterna, 
             cl.autorizaEnDosPasos, 
             cl.getPorcentajeinferior,
             cl.getPorcentajesuperior,
             ein(cl.getPlanCuotas),
             cl.pasaAutenticacionExterna, 
             cl.pasaVBVSinServicio, 
             ein(cl.getFormatoNroTarjetaVisible),
             ein(cl.getPassword), 
             cl.pagoDiferidoHabilitado, 
             cl.getAceptaSoloNacional, 
             ein(cl.getTipoPlantilla), 
             ein(cl.getNroIdDestinatario))
    }.toList
    
    val subSites = dbsps.getSubSites(siteLegacy).asScala.map { is =>
      InfoSite(is.getIdSite, is.getPorcentaje)
    }.toList
    
    val rangosPermitidosTarjeta = 
      dbsps.getRangosPermitidosTarjeta(id).asScala.
        map(array => (array(0).toInt, array(1), array(2))).toList.
        groupBy(_._1).map(kv => RangosPermitidosTarjeta(id, kv._1, kv._2.map(t => Pair(t._2, t._3)))).toList
          //RangosPermitidosTarjeta(id, array(0).toInt, array(1), array(2)))
        
    val csConf = 
      if(siteLegacy.getFlagCS != "N") 
        Some(CyberSourceConfiguration(
          enabled = siteLegacy.getFlagCS != "N" && siteLegacy.getFlagCS != "B" && siteLegacy.getFlagCS != "T",
          withAutoReversals = siteLegacy.getFlagCS == "A",
          vertical = siteLegacy.getRubro.getNombre_rubro,
          mid = siteLegacy.getMid,
          securityKey = siteLegacy.getSecurityKey, 
          continueOnBlack = siteLegacy.autorizaSeguirAnteErrorValidacionCybersource(), 
          withAutoReversalsOnBlue = siteLegacy.getCsreversiontimeout == "S"))
      else 
        None

    val postbackConf = PostbackConf(
      usaUrlDinamica = siteLegacy.getUsaUrlDinamica,
      urlPost = siteLegacy.getURLPost)

    val mailConfiguration = MailConfiguration(
      mandarMailAUsuario = siteLegacy.getMandarMailAUsuario,
      mandarMailASite = siteLegacy.getMandarMailASite,
      mail = siteLegacy.getMail,
      replyMail = siteLegacy.getReplyMail)

    val hashConfiguration = HashConfiguration(
      useHash = siteLegacy.getFlagclavehash,
      firstHashDate = if(siteLegacy.getFechaUsoHash != null) Some(siteLegacy.getFechaUsoHash) else None
    )
    
    val merchantIds = dbsps.getMerchants(siteLegacy).asScala.toList
    val description = Option(siteLegacy.getDescripcionSite).orElse(None)
    val habilitado = siteLegacy.getEstadoTienda.toInt match {
      case 1 => true;
      case other => false;
    }
    
    val encrypt = Encrypt(if (!siteLegacy.encripta) None else Some(Encryption(siteLegacy.getTipoEncripcion, siteLegacy.getPublikKey)), siteLegacy.getRetornaTarjetaEnc)

    logger.info("Creating site")
    Site(id = siteLegacy.getIdSite,
        description = description,
        habilitado = habilitado,
        url = url,
        timeoutCompra = siteLegacy.getTimeoutCompra,
        validaRangoNroTarjeta = siteLegacy.getValidaRangoNroTarjeta,
        transaccionesDistribuidas = ein(siteLegacy.getTransaccionesDistribuidas),
        montoPorcent = siteLegacy.getMontoPorcent,
        reutilizaTransaccion = siteLegacy.reutilizaTransaccion(),
        enviarResumenOnLine = siteLegacy.getEnviarResuOnLine.toString,
        ppb = postbackConf,
        validaOrigen = siteLegacy.getValidaOrigen,
        mailConfiguration = mailConfiguration,
        agregador = siteLegacy.getAgregador,
        cuentas = cuentas,
        rangos = rangosPermitidosTarjeta,
        mensajeria = siteLegacy.getMensajeria,
        subSites = subSites,
        cyberSourceConfiguration = csConf,
        merchants = merchantIds,
        parentSiteId = Option(siteLegacy.getParentSiteId).orElse(None),
        hashConfiguration = hashConfiguration,
        encrypt = encrypt,
        mensajeriaMPOS = Some(siteLegacy.getMensajeriaMPOS),
        isTokenized = siteLegacy.getIsTokenized,
        timeToLive = siteLegacy.getTimeToLive)
  }
  
  ////////////////////////////////////////////////////////////////////
  
  def replicateAllTipoActividad() = {
    try {
      val tiposActividad = legacyDao.getAllTipoActividad
      logger.info(s"Replicando ${tiposActividad.size} tipos de actividad.")
      tiposActividad.foreach {e => Try{replicate(e)} }
    } 
    catch {
      case e: Exception => {
        logger.error("Error replicando actividades", e)
      }
    }
  }
  
  def replicateTipoActividad(id: String) = {
    try {
      replicate(legacyDao.retrieveTipoActividad(id.toInt))
    } 
    catch {
      case e: Exception => {
        logger.error("Error replicateTipoActividad", e)
      }
    }    
  }
  
  private def ein(str: String) = if(str == null) "" else str

  private def replicate(entidad: TipoActividadLegacy): Unit = {
    val tipoActividad = TipoActividad(entidad.getIdTipoActividad, entidad.getDescripcion)    
    val jsonS = Json.toJson(tipoActividad).toString
    replicationNotificationService.replicate(EntityUpdatedEvent(tipoActividad.getClass().getSimpleName, List(jsonS)))
  }  
 
  def replicateAllBinFilter() = {
    logger.info(s"Replicando todos los filtros de bines de los medios de pago" )
    try {
       dbsps.getMediosPago().asScala.foreach{medioPago => Try{replicateBinFilter(medioPago)} }
    } 
    catch {
      case e: Exception => {
        logger.error("Error replicateAllMedioPago", e)
      }
    } 
  }

  def replicateAllBanco() = {
    logger.info(s"Replicando todos los bancos" )
    try {
      val bancos = dbsps.getAllBancos()

      logger.info(s"Replicando ${bancos.size} bancos.")
      bancos.asScala.foreach { banco =>
        val jsonS = Json.toJson(banco).toString
        replicationNotificationService.replicate(EntityUpdatedEvent("Banco", List(jsonS)))
      }
    }
    catch {
      case e: Exception => {
        logger.error("Error replicateAllBanco", e)
      }
    }
  }

  def replicateBanco(id: String) = {
    try {
      val bank = dbsps.getBanco(id)
      val jsonS = Json.toJson(bank).toString
      replicationNotificationService.replicate(EntityUpdatedEvent("Banco", List(jsonS)))
    }
    catch {
      case e: Exception => {
        logger.error("Error replicateBanco", e)
      }
    }
  }
  
  private def replicateBinFilter(medioPago: MedioPagoLegacy): Unit = {
      val bines = legacyDao.retrieveBinesFilter(medioPago.getIdMedioPago)
      val limitsMedioPago = LimitBinMedioPago(idmediopago = medioPago.getIdMedioPago, 
                                       white = bines
                                        .filter {!_.blacklist}
                                        .map(lmp => LimitPair(min = lmp.limiteinferior, max = lmp.limitesuperior)),
                                       black = bines
                                        .filter {_.blacklist}
                                        .map(lmp => LimitPair(min = lmp.limiteinferior, max = lmp.limitesuperior)),
                                       validate = medioPago.getValidabines)
      val jsonS = Json.toJson(limitsMedioPago).toString
      replicationNotificationService.replicate(EntityUpdatedEvent("BinFilter", List(jsonS)))
  }
  
  def replicateTransactionsStatus(siteId: String) = {
    if(isValidSiteId(siteId)) {
      logger.info(s"Replicando todos los estados de los legacy transactions para el site: $siteId")
      try {
        val transactionStatus = TransactionsStatus(
          siteId = siteId,
          transactions = legacyDao.retrieveTransactionsStatus(siteId))
        logger.info(s"Replicando ${transactionStatus.transactions.size} transacciones para site: $siteId")
        val jsonS = Json.toJson(transactionStatus).toString
        replicationNotificationService.replicate(EntityUpdatedEvent("TransactionsStatus", List(jsonS)))
      }
      catch {
        case e: Exception => {
          logger.error("Error replicateTransactionsStatus", e)
        }
      }
    }
  }
  
  
}

case class Entidad(entity_name: String, id: Option[String])