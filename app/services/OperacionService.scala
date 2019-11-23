package services

import java.util.{Date, UUID}
import javax.inject.{Inject, Singleton}

import com.decidir.coretx.api.{OperationResource, _}
import com.decidir.coretx.domain._
import com.decidir.coretx.utils.JedisPoolProvider
import com.decidir.encripcion.Encriptador
import com.decidir.encrypt.EncryptionService
import controllers.MDCHelperTrait
import controllers.utils.FullCreditCardValidator
import legacy.decidir.sps.util.AnalizeDns
import services.bins.BinsService
import services.cybersource.CybersourceClient
import services.metrics.MetricsClient
import services.payments.{LegacyTransactionServiceClient, PaymentsProcessor}
import services.protocol.ProtocolService
import services.validations.operation._
import services.validations.{AfipValidate, AggregatorValidate, InstallmentsValidator, OfflineValidator}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

@Singleton
class OperacionService @Inject() (context: ExecutionContext,
                                  operationRepository: OperationResourceRepository,
                                  jedisPoolProvider: JedisPoolProvider,
                                  siteRepository: SiteRepository,
                                  medioDePagoRepository: MedioDePagoRepository,
                                  monedaRepository: MonedaRepository,
                                  marcaTarjetaRepository: MarcaTarjetaRepository,
                                  protocolService: ProtocolService,
                                  nroTraceRepository: NroTraceRepository,
                                  legacyTxService: LegacyTransactionServiceClient,
                                  cybersourceClient: CybersourceClient,
                                  motivoRepository:MotivoRepository,
                                  metrics: MetricsClient,
                                  paymentProcessor: PaymentsProcessor,
                                  fullCreditCardValidator : FullCreditCardValidator,
                                  tipoDocumentoRepository: TipoDocumentoRepository,
                                  offlineTransactionProcessor: OfflineTransactionProcessor,
                                  paymentMethodService: PaymentMethodService,
                                  aggregatorValidate: AggregatorValidate,
                                  afipValidate: AfipValidate,
                                  offlineValidator: OfflineValidator,
                                  binsService: BinsService,
                                  installmentsValidator: InstallmentsValidator,
                                  encryptionService: EncryptionService,
                                  paymentMethodValidation: PaymentMethodValidation) extends MDCHelperTrait {

  implicit val ec = context

  def createOperation(operation: OperationResource): Try[OperationResource] = {

    val ini = System.currentTimeMillis()
    val txId = UUID.randomUUID().toString()
    val step2 = System.currentTimeMillis()
    updateMDC(transactionId = Some(txId))
    logger.debug("OperacionService.createOperation: token generated")
    var op = operation.copy(
      id = txId,
      creation_date = Some(new Date),
      nro_operacion = if(operation.nro_operacion == Some("TOKEN")) Some(txId) else operation.nro_operacion)

    try {
      implicit val operationResource = op
      implicit val currentSite = retrieveSite(op)

      AmountValidation.validate
      OperationNumberValidation.validate
      HashValidation.validate
      IPValidation.validate
      DueDateValidation.validate
      paymentMethodValidation.validate
      MPOSValidation.validate

      if(op.datos_offline.isDefined && op.datos_offline.flatMap(_.cod_p1).isDefined)
        offlineValidator.validate(operation)

      val (ope, site) = validateDatosSite(op)
      op = ope

      installmentsValidator.validateInstallmentsAmount(op, site)

      if(site.agregador == "N")
        op = op.copy(aggregate_data = None)

      Success(doUpdateOperation(op, operationResource => {
        operationRepository.store(operationResource, if (site.reutilizaTransaccion) 5 else 0)
      }))
    } catch {
      case e: Exception => {
        logger.error("Error en OperacionService.createOperation", e)
        Failure(e)
      }
    }
  }

  private def retrieveSite(operation: OperationResource): Site = {
    val siteId = operation.datos_site.flatMap(_.site_id).getOrElse(operationRepository.retrieve(operation.id).flatMap { _.datos_site }.flatMap { _.site_id }.getOrElse(throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE_SITE_ID)))
    retrieveSite(Some(siteId))
  }

  private def retrieveSite(siteId: Option[String]): Site =
    siteId match {
      case None => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE_SITE_ID)
      case Some(id) =>
        siteRepository.retrieve(id).getOrElse(throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SITE_SITE_ID))
    }

  private def validateDatosSite(operation: OperationResource): (OperationResource, Site) = {
    var op = operation
    val referer = extractedReferer(op)

    val datosSite = operation.datos_site.getOrElse{
      logger.error("site required")
      throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE)
    }
    val site = retrieveSite(datosSite.site_id)
    validateDisabledSite(site)

    datosSite match {
      case DatosSiteResource(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, origin_site) => {
        origin_site match {
          case None =>  throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_SITE_ORIGIN_SITE_ID)
          case Some(origin_site_id) => {
            if ( origin_site_id != site.id && !(site.parentSiteId.fold(false)(or_site => (origin_site_id contains or_site) && or_site.nonEmpty)) ) {
              logger.error(s"""No existe merchant ${site.id} para site ${datosSite.origin_site_id.orNull}""")
              throw ErrorFactory.validationException(ErrorMessage.INVALID_SITE, ErrorMessage.DATA_SITE_ORIGIN_SITE_ID)
            }
          }
        }
      }
      case _ => logger.debug("Site origin id Validation success")
    }

    if(site.validaOrigen &&
      operation.origin.flatMap(_.app).getOrElse("") == "WEBTX" &&
      !AnalizeDns.isClient(referer, site, if(datosSite.use_url_origen.getOrElse(false)) datosSite.url_origen.orNull else null)){
      logger.error("La url origen no es valida")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SITE_REFERER)
    }

    datosSite match {
      case DatosSiteResource(_, _, Some(paramSitio), _, _, _, _, _, _, _, _, _, _, _, _, _) => {
        op = operation.copy(datos_site = Some(operation.datos_site.get.copy(param_sitio = Some(paramSitio.replaceAll("[^A-Za-z0-9 _\\-,\\.;:@\\|]", "")))))
      }
      case _ => logger.debug("Site Param Sitio Validation success")
    }

    datosSite match {
      case DatosSiteResource(_, _, _, _, _, _, _, _, None, None, None, None, None, None, _, _) => {
        op = operation.copy(datos_site = Some(operation.datos_site.get.copy(usaUrlDinamica = Some(site.ppb.usaUrlDinamica),
          enviarResumenOnLine = Some(site.enviarResumenOnLine),
          urlPost = Some(site.ppb.urlPost),
          mandarMailAUsuario = Some(site.mailConfiguration.mandarMailAUsuario),
          mail = Some(site.mailConfiguration.mail),
          replyMail = Some(site.mailConfiguration.replyMail))))
      }
      case _ => {}
    }

    datosSite match {
      case DatosSiteResource(_, _, _, _, _, _, Some("S"), _, _, _, _, _, _, _, _, _) => {
        if (site.transaccionesDistribuidas != "S") {
          logger.error("site of type transaction distributed")
          throw ErrorFactory.validationException(ErrorMessage.INVALID_PAYMENT_TYPE, ErrorMessage.DISTRIBUTED_TRANSACTIONS)
        }

        // por monto
        if (site.montoPorcent == "M") {
          val subSites = siteRepository.findSubSitesBySite(site).map(_.idSite).toSet

          // Busco si algun subsite ingresado por el usuario no esta entre los subsite del site y no es el site padre
          val siteNotFound = op.sub_transactions.find(stx => !subSites.contains(stx.site_id) & stx.site_id != site.id)

          siteNotFound.foreach { siteId => throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.SUB_PAYMENTS_SITE_ID) }

          op.sub_transactions.foreach { subTx =>
            // Valida que cada subsite existe como site y este habilitado
            val subSite = siteRepository.retrieve(subTx.site_id).getOrElse {
              logger.error("site Id not retrived")
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SUB_SITE_TX_ID)
            }
            validateDisabledSite(subSite)
            // Valida que el subsite tenga el medio de pago seleccionado
            // TODO ver toString
            if (operation.datos_medio_pago.get.medio_de_pago.isDefined) {
              validateSitePaymentMeans(subSite, operation.datos_medio_pago.get.medio_de_pago.get.toString, paymentMethodService.getProtocolId(operation), paymentMethodService.getBackenId(operation), ErrorMessage.DATA_SUB_SITE_OPERATION_METHOD_ID)
            }
            if (subTx.amount < 0) {
              logger.error("invalid amount")
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.AMOUNT)
            }
          }

          if (operation.monto.get != op.sub_transactions.foldLeft(0L)((r, c) => r + c.amount)) {
            logger.error("different amounts")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DIFFERENT_AMOUNTS)
          }
          // por porcentaje
        } else {
          val subSites = siteRepository.findSubSitesBySite(site).map(_.idSite).toSet
          subSites.foreach { subSiteId =>
            // Valida que cada subsite existe como site y este habilitado
            val subSite = siteRepository.retrieve(subSiteId).getOrElse {
              logger.error("subSite id not retrived, not exist or this disabled")
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SUB_SITE_ID)
            }
            validateDisabledSite(subSite)
            // Valida que el subsite tenga el medio de pago seleecionado
            if (operation.datos_medio_pago.get.medio_de_pago.isDefined) {
              validateSitePaymentMeans(subSite, operation.datos_medio_pago.get.medio_de_pago.get.toString, paymentMethodService.getProtocolId(operation), paymentMethodService.getBackenId(operation), ErrorMessage.DATA_SUB_SITE_OPERATION_METHOD_ID)
            }
          }
        }
      }
      case _ => logger.debug("Site Distributed Validation success")
    }

    (op.copy(ttl_seconds = Some(site.timeToLive)), site)

  }

  private def validateSitePaymentMeans(site: Site, medioPagoId: String, protocoloId: Int, backendId: Int, errorMessage: String) = {
    site.cuenta(medioPagoId, protocoloId, backendId).map(cuenta =>
      if (!cuenta.habilitado) {
        logger.error(s"El site ${site.id} tiene deshabilitado el medio de pago $medioPagoId")
        throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, errorMessage)
      }
    ).getOrElse{
      logger.error(s"El site ${site.id} no tiene configurado el medio de pago: ${medioPagoId}, protocolo: ${protocoloId}, backend ${backendId}")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, errorMessage)
    }
  }

  /**
    * TODO Ojo que pueden cambiar tanto el site como el nro_operacion, pasar la validacion también a este punto
    */
  def updateOperation(operation: OperationResource): Try[OperationResource] = {
    try {
      val txId = operation.id
      if (!operationRepository.exists(txId))
        throw ApiException(ErrorFactory.notFoundError("OperationResource", txId))

      implicit val oper = operation
      implicit val site = retrieveSite(operation)

      DueDateValidation.validate

      operation.datos_site match {
        case Some(DatosSiteResource(_, _, _, _, _, _, _, _, None, None, None, None, None, None, _, _)) => {
          operation.datos_site.map(_.copy(enviarResumenOnLine = Some(site.enviarResumenOnLine), usaUrlDinamica = Some(site.ppb.usaUrlDinamica), urlPost = Some(site.ppb.urlPost), mandarMailAUsuario = Some(site.mailConfiguration.mandarMailAUsuario),
            mail = Some(site.mailConfiguration.mail),
            replyMail = Some(site.mailConfiguration.replyMail)))
        }
        case other => {}
      }

      var op = operation

      if(site.agregador == "N")
        op = operation.copy(aggregate_data = None)

      Success(doUpdateOperation(op, (operationResource: OperationResource) => {
        operationRepository.update(operationResource, if (site.reutilizaTransaccion) 5 else 0)
      }))
    } catch {
      case e: Exception => {
        logger.error("Error en OperacionService.updateOperation", e)
        Failure(e)
      }
    }
  }

  private def getEncryptor(operation: OperationResource):Option[Encriptador] = {
    siteRepository.getEncryptor(retrieveSite(operation)) match {
      case Success(encrypt) => encrypt
      case Failure(error) => {
        logger.error("Retrieve encryptor error", error)
        None
      }
    }
  }
  private def doUpdateOperation(op: OperationResource, persist: OperationResource => Unit)(implicit site: Site): OperationResource = {

    val ini = System.currentTimeMillis

    val txId = op.id

    val operation = validate(op)

    val step2 = System.currentTimeMillis()

    metrics.recordInMillis(operation.id, "coretx", "OperacionService", "doUpdateOperation.validate", step2 - ini)

    val opWithCardData = operation.datos_medio_pago.map{ mp =>
      val cardNumber = mp.nro_tarjeta.map(nt => operationRepository.decrypted(nt))
      operation.copy(
        datos_medio_pago = Some(operation.datos_medio_pago.get.copy(
          last_four_digits = cardNumber.map(_.takeRight(4)),
          card_number_encrypted = cardNumber.flatMap(cn => getEncryptor(operation).map(encryptor => encryptor.encriptar(cn))),
          bin_for_validation = cardNumber.map(_.take(6)),
          card_number_length = cardNumber.map(_.length()))
        ),
        last_update = Some(new Date),
        datos_banda_tarjeta = operation.datos_banda_tarjeta.map(banda => {
          DatosBandaTarjeta(card_track_1 = banda.card_track_1.map(track1 => encryptionService.encriptarBase64(track1)),
            card_track_2 = banda.card_track_2.map(track2 => encryptionService.encriptarBase64(track2)),
            input_mode = banda.input_mode)
        })
      )
    }.getOrElse(operation)

    /**
      * Almacenamiento
      */
    persist(opWithCardData)

    val step3 = System.currentTimeMillis()
    metrics.recordInMillis(operation.id, "coretx", "OperacionService", "store/update", step3 - step2)

    val or = operationRepository.retrieve(txId) match {
      case None     => {
        logger.error("OperationResource not retrieved")
        throw ApiException(ErrorFactory.notFoundError("OperationResource", txId))
      }
      case Some(op) => {
        logger.debug("OperationResource retrieved")
        op
      }
    }

    metrics.recordInMillis(operation.id, "coretx", "OperacionService", "doUpdateOperation.retrieve", System.currentTimeMillis - step3)

    or
  }

  private def extractReferer(operation: OperationResource) =
    operation.datos_site.flatMap(_.referer)

  private def extractedReferer(operation: OperationResource) =
    extractReferer(operation).getOrElse("Referer not sent")

  //  private def validateReferer(operation: OperationResource) = {
  //    extractReferer(operation).getOrElse(throw new BadRequestException("Referer not found"))
  //  }

  //<name_card, regex, idMarcaTarjeta, idMedioPago>
  //  val cardBrandRegex = List(
  //      (List("nativa-visa"), "487017", List(27), List("42")),
  //      (List("nativa-master"), "520053|546553", List(27), List("42")),
  //      (List("anonima"), "421024", List(46), List("61")),
  //      (List("cabal"), "589657", List(12), List("27")),
  //      (List("mastercard"), "^5[1-5][0-9]{4}$", List(1), List("15")),
  //      (List("amex"), "^3[47][0-9]{4}$", List(50), List("65")),
  //      (List("naranja"), "589562", List(9), List("24")),
  //      (List("diners"), "^3(?:0[0-5]|[68][0-9])[0-9]{3}", List(3), List("8")),
  //      (List("nevada"), "504363", List(24), List("39")),
  //      (List("carrefour"), "507858|585274", List(29), List("44")),
  //      (List("shopping"), "^279[0-9]{3}|606488", List(8), List("23")),
  //      (List("dia"), "636897", List(41), List("56")),
  //      (List("mas"), "603493", List(28), List("43")),
  //      (List("visa", "visa-debit"), "^4[0-9]{2}(?:[0-9]{3})?$", List(4,16), List("1", "31")))

  private def validateSite(site: Site, operation: OperationResource): OperationResource = {

    validateDisabledSite(site)

    operation.datos_site match {
      case Some(DatosSiteResource(_, Some(urlDinamica), _, _, _, _, _, _, _, _, _, _, _, _, _, _)) => {
        if(site.ppb.usaUrlDinamica &&
          (urlDinamica == "" || urlDinamica == null))
          throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "URL Dinamica")
      }
      case other => {}
    }

    operation.datos_site match {
      case Some(DatosSiteResource(_, _, _, _, _, _, _, _, None, None, None, None, None, None, _, _)) => {
        operation.datos_site.map(_.copy(enviarResumenOnLine = Some(site.enviarResumenOnLine),
          usaUrlDinamica = Some(site.ppb.usaUrlDinamica),
          urlPost = Some(site.ppb.urlPost),
          mandarMailAUsuario = Some(site.mailConfiguration.mandarMailAUsuario),
          mail = Some(site.mailConfiguration.mail),
          replyMail = Some(site.mailConfiguration.replyMail)))
      }
      case other => {}
    }

    //CUIDADO AL MERGEAR!!!
    //Aca se volvio a eliminar un bloque de codigo que habia sido vuelto a insertar luego de un merge.

    operation
  }

  /**
    Validacion de tarjetas shoppings antiguas
    */
  private def applyDataForShoppingCards(operation: OperationResource): OperationResource = {
    operation.datos_medio_pago match{

      case Some(DatosMedioPagoResource(Some(23), _, _, _, Some(nroTarjeta: String), _, _, _, _, _, _, _, _, _, _, _,_, _, _, _, _, _, _)) => {

        val nroTarjetaDecrypted = operationRepository.decrypted(nroTarjeta)

        nroTarjetaDecrypted.take(3) match {
          case "279" => operation.copy(datos_medio_pago = operation.datos_medio_pago.map(_.copy(nro_tarjeta = Some("589407" concat nroTarjetaDecrypted))))
          case _ => operation
        }
      }
      case _ => operation
    }
  }

  def validateDisabledSite(site: Site) = {
    if (!site.habilitado) {
      logger.error(s"site ${site.id} disabled")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_SITE_DISABLED)
    }
  }

  private def validateMedioPagoPostRetrieve(site: Site, operation: OperationResource, referer: String): OperationResource = {
    //    SE COMENTAN VALIDACIONES. La marca sera la encargada de validar si es correcto o no el request.
    //    TAREA: http://jira.prismamp.com.ar:8080/browse/DECD-2184
    //
    //    site.agregador match{
    //      case "S" => {
    //        if(operation.aggregate_data.isEmpty &&
    //          ( operation.datos_medio_pago.flatMap(_.medio_de_pago).getOrElse(0) == 1 ||
    //            operation.datos_medio_pago.flatMap(_.medio_de_pago).getOrElse(0) == 15))
    //          throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_AGGREGATE_DATE)
    //      }
    //      case other => {}
    //    }

    operation
  }

  private def validate(aOperation: OperationResource)(implicit site: Site) = {

    implicit var operation = aOperation

    //    if(operation.used) {
    //      logger.error("Token (id de operation) ya usado")
    //      throw ErrorFactory.validationException(ErrorMessage.INVALID_TOKEN, ErrorMessage.TOKEN)
    //    }

    //val referer = extractReferer(operation).getOrElse("Referer not sent")

    operation = operation.datos_site.flatMap(_.site_id).map { siteId =>
      validateSite(site, operation)
    }.getOrElse(operation)

    OperationNumberValidation.validate

    //TODO Esta validación fue comentada hace tiempo. Tiene sentido?
    operation = operation.aggregate_data.map {
      agregador => aggregatorValidate.validate(site, operation, agregador)
    }.getOrElse(operation)

//    TODO: Revisar cuando se hace la validacion de afip
//    operation = operation.ticket_request.map {
//      ticket_request => afipValidate.validate(operation, ticket_request.vep)
//    }.getOrElse(operation)

    SPVValidation.validate

    paymentMethodValidation.validate

    //Validacion de tarjetas shoppings antiguas
    operation = applyDataForShoppingCards(operation)

    AmountValidation.validate
    //TODO esta validacion se saltea hasta que este bien definida. por el momento se truncan los datos antes de persisitir
    //    operation.datos_titular.foreach(validateDatosTitular)

    operation

  }

  private def validateSessionTimeout(or: OperationResource, site: Site) = {
    if(or.origin.isDefined && or.origin.get.app.contains("WEBTX")){
      val actual = new Date().getTime
      val elapsedTime =  actual - or.creation_date.map(_.getTime).getOrElse(0l)
      val timeOut =  if(site.timeoutCompra > 0) site.timeoutCompra * 1000 else 1800000 //30 minutos por default
      if(elapsedTime > timeOut){
        logger.error(s"Session expired - Elapsed time >>> $elapsedTime")
        throw ApiException(ErrorFactory.notFoundError("OperationResource", or.nro_operacion.getOrElse("")))
      }
    }
  }

  def validateOperacion(opera: OperationResource): OperationData = {

    implicit var (op, site) = validateDatosSite(opera)

    SessionTimeOutValidation.validate

    op = validateSite(site, op)

    OperationNumberValidation.validate

    paymentMethodValidation.validate
    //TODO esta validacion se saltea hasta que este bien definida. por el momento se truncan los datos antes de persisitir
    //    op.datos_titular.foreach(validateDatosTitular)

    /**
    Validacion de tarjetas shoppings antiguas
      */
    op = applyDataForShoppingCards(op)

    if(op.datos_medio_pago.get.medio_de_pago.get != 41) { // PMC NO TIENE ESTOS CAMPOS
      installmentsValidator.validateInstallments(op, site)
      binsService.validateBin(op)
    }

    //validateMontoExists(op, referer)
    AmountValidation.validate

    // Determinar medio de pago
    val (datosMedioPago, medioPago: MedioDePago) = { // Por algun motivo, si no especifico el tipo esta resolviendo a Any
      val oMedioPago = op.datos_medio_pago match {
        // Si el medio de pago esta seteado pisa la moneda y la tarjeta con la que esta en la base
        case Some(DatosMedioPagoResource(Some(idMedioPago), _, _, _, _, _, _, _, _, _, _, _, _, _, _,_, _, _, _, _, _, _, _)) => {
          medioDePagoRepository.retrieve(idMedioPago)
        }
        case None => {
          logger.error("payment method undefined")
          throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_MEDIO_PAGO) // TODO Ver mensaje excepcion
        }
        case other => {
          logger.error("payment method: " + other)
          throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_MEDIO_PAGO) // TODO Ver mensaje excepcion
        }
      }

      (op.datos_medio_pago.get, oMedioPago.getOrElse("No se definio el medio de pago")) // TODO Ver mensaje excepcion
    }

    val nuevosDatosMedioPago = datosMedioPago.copy(medio_de_pago = Some(medioPago.id.toInt), marca_tarjeta = medioPago.idMarcaTarjeta, id_moneda = medioPago.idMoneda.map(_.toInt))
    op = op.copy(datos_medio_pago = Some(nuevosDatosMedioPago))
    //validateMedioPagoPostRetrieve(site, op, referer)

    // Trunca nombre a 60 caracteres
    val onombre = datosMedioPago.nombre_en_tarjeta.map(_.take(60)) // TODO Evaluar si puede no venir el nombre
    op = onombre.map(nombre => op.copy(datos_medio_pago = op.datos_medio_pago.map(_.copy(nombre_en_tarjeta = Some(nombre))))).getOrElse(op)

    val oemail = op.datos_titular.flatMap { _.email_cliente.map(_.take(80)) }
    op = oemail.map(email => op.copy(datos_titular = op.datos_titular.map(_.copy(email_cliente = Some(email))))).getOrElse(op)

    // Validaciones de Protocolo
    val marcaTarjeta = medioPago.idMarcaTarjeta.flatMap(marcaTarjetaRepository.retrieve(_)).getOrElse{
      logger.error("the card brand not find")
      throw new Exception("No se encontro la marca de tarjeta")
    }

    // TODO Revisar con Rodrigo
    val moneda = op.datos_medio_pago.flatMap(_.id_moneda).flatMap(monedaRepository.retrieve(_)).get

    val opData = OperationData(op, site, medioPago, marcaTarjeta, moneda)

    opData
  }

  //TODO esta validacion se saltea hasta que este bien definida. por el momento se truncan los datos antes de persisitir
  private def validateDatosTitular(dt: DatosTitularResource) = {
    //Validacion nro_doc
    dt.nro_doc.map { nd => if (!nd.matches("^\\d{0,11}$"))
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_TITULAR_NUMERO_DOC) }
  }


  /**
    * Devuelve:
    * - APIException ante errores de validacion de operacion
    * - Left(CybersourceResponse) Ante errores de validacion de CyberSource, a devolver directo
    * - Right(OperationData) Cuando esta todo ok
    *
    * TODO Mejorar esto, no esta bueno que sea tan complicado como se reportan los problemas
    *
    */
  def validateProcess(txId: String): OperationResource = {

    val ini = System.currentTimeMillis

    try {

      var op = operationRepository.retrieveDecrypted(txId) match {
        case None => {
          logger.error("cant retrieve Decrypted of txId")
          throw ApiException(ErrorFactory.notFoundError("OperationResource", txId))
        }
        case Some(op) => {
          updateMDCFromOperation(op)
          op
        }
      }

      if(op.used.getOrElse(false))  {
        logger.error(s"Error validando proceso. Operacion repetida con id ${op.id}")
        throw ErrorFactory.validationException("repeated", ErrorMessage.TOKEN)
      }
      op
    }
    finally {
      val step2 = System.currentTimeMillis()
      metrics.recordInMillis(txId, "coretx", "OperacionService", "process.validateProcess", step2 - ini)
    }
  }

  def validateTwoSteps(opdata: OperationData) = {
    opdata.datosSite.id_modalidad match {
      case Some("S") => {
        logger.debug("Distrubuted payment")
      }
      case _ => {
        logger.debug("Single payment")
        if (opdata.cuenta.autorizaEnDosPasos && !hasTwoSteps(opdata.datosMedioPago.medio_de_pago)) {
          logger.error(s"payment method: ${opdata.datosMedioPago.medio_de_pago} - not suport operation: two steps")
          throw ErrorFactory.validationException("State error, not suport operation: two steps", "operation")
        }
      }
    }

  }

  private def hasTwoSteps(meanPaymentId: Long) = {
    val oMedioDePago = medioDePagoRepository.retrieve(meanPaymentId)
    val medioDePago = oMedioDePago.getOrElse{
      logger.error("Mean payment not existed")
      throw new Exception("RefundStateService medioDePago not existed")
    }
    medioDePago.operations.twoSteps
  }

  def validateOffline(opera: OperationResource): OperationData = {
    //val op = validateNroOperacion(opera)
    implicit val op = opera

    val (ope, site) = validateDatosSite(op)

    implicit val currentSite = site
    OperationNumberValidation.validate
    paymentMethodValidation.validate
    //validateSessionTimeout(ope, site)
    SessionTimeOutValidation.validate

    validateOfflineButSessionTimeout(opera)
  }

  def validateOfflineButSessionTimeout(opera: OperationResource): OperationData = {

    implicit var (op, site) = validateDatosSite(opera)

    op = validateSite(site, op)

    OperationNumberValidation.validate
    paymentMethodValidation.validate
    AmountValidation.validate

    // Determinar medio de pago
    val (datosMedioPago, medioPago: MedioDePago) = { // Por algun motivo, si no especifico el tipo esta resolviendo a Any
      val oMedioPago = op.datos_medio_pago match {
        // Si el medio de pago esta seteado pisa la moneda y la tarjeta con la que esta en la base
        case Some(DatosMedioPagoResource(Some(idMedioPago), _, _, _, _, _, _, _, _, _, _, _, _, _, _,_, _, _, _, _, _, _, _)) => {
          medioDePagoRepository.retrieve(idMedioPago)
        }
        case None => {
          logger.error("payment method undefined")
          throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_MEDIO_PAGO) // TODO Ver mensaje excepcion
        }
        case other => {
          logger.error("payment method: " + other)
          throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_MEDIO_PAGO) // TODO Ver mensaje excepcion
        }
      }

      (op.datos_medio_pago.get, oMedioPago.getOrElse("No se definio el medio de pago")) // TODO Ver mensaje excepcion
    }

    val nuevosDatosMedioPago = datosMedioPago.copy(medio_de_pago = Some(medioPago.id.toInt), marca_tarjeta = medioPago.idMarcaTarjeta, id_moneda = medioPago.idMoneda.map(_.toInt))
    op = op.copy(datos_medio_pago = Some(nuevosDatosMedioPago))
    //validateMedioPagoPostRetrieve(site, op, referer)

    // Trunca nombre a 60 caracteres
    val onombre = datosMedioPago.nombre_en_tarjeta.map(_.take(60)) // TODO Evaluar si puede no venir el nombre
    op = onombre.map(nombre => op.copy(datos_medio_pago = op.datos_medio_pago.map(_.copy(nombre_en_tarjeta = Some(nombre))))).getOrElse(op)

    val oemail = op.datos_titular.flatMap { _.email_cliente.map(_.take(80)) }
    op = oemail.map(email => op.copy(datos_titular = op.datos_titular.map(_.copy(email_cliente = Some(email))))).getOrElse(op)

    // TODO esta hacerlos cuando se guarde
    /* Requerimiento de Banelco */
    //          if (esBanelco()) {
    //            idOperacionSite = idOperacionSite.toUpperCase();
    //          }

    // Validaciones de Protocolo
    val marcaTarjeta = medioPago.idMarcaTarjeta.flatMap(marcaTarjetaRepository.retrieve(_)).getOrElse{
      logger.error("the card brand not find")
      throw new Exception("No se encontro la marca de tarjeta")
    }

    // TODO Revisar con Rodrigo
    val moneda = op.datos_medio_pago.flatMap(_.id_moneda).flatMap(monedaRepository.retrieve(_)).get

    val opData = OperationData(op, site, medioPago, marcaTarjeta, moneda)
    opData.copy(resource = opData.resource.copy(charge_id = Some(operationRepository.newChargeId)))
  }

  def processOffline(txId: String): Try[OperationExecutionResponse] = {
    try {
      val op: OperationResource = validateProcess(txId)
      offlineValidator.validate(op)
      val opData: OperationData = validateOffline(op)
      if(op.origin.flatMap(_.app).getOrElse("").equals("RESTTX"))
        offlineValidator.validate(op)
//      validateTwoSteps(opdata)
      operationRepository.flagAsUsed(opData.resource)

      Success(offlineTransactionProcessor.process(updateInvoiceExpiration(opData)))

    } catch {
      case e: ApiException => {
        logger.error("process: ApiException", e)
        Failure(e)
      }
      case e: Throwable => {
        logger.error("process: Throwable", e)
        ErrorFactory.uncategorizedFailure(e)
      }
    }
  }

  private def updateInvoiceExpiration(opData: OperationData) : OperationData = {
    opData.copy(resource = opData.resource.copy(datos_offline = opData.resource.datos_offline.map(datosOff =>
        if (datosOff.fechavto2.isEmpty)
          datosOff.copy(fechavto2 = datosOff.fechavto)
        else
          datosOff
      ))
    )
  }
}