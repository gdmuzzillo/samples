package services.validations.operation

import java.text.SimpleDateFormat
import javax.inject.{Inject, Singleton}

import com.decidir.coretx.api._
import com.decidir.coretx.api.DatosMedioPagoResource
import com.decidir.coretx.domain._
import controllers.utils.FullCreditCardValidator
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, Days, Months}
import services.PaymentMethodService
import services.bins.BinsService
import services.validations.InstallmentsValidator

@Singleton
class PaymentMethodValidation @Inject() (operationRepository: OperationResourceRepository,
                                         medioDePagoRepository: MedioDePagoRepository,
                                         paymentMethodService: PaymentMethodService,
                                         monedaRepository: MonedaRepository,
                                         fullCreditCardValidator: FullCreditCardValidator,
                                         binsService: BinsService,
                                         marcaTarjetaRepository: MarcaTarjetaRepository,
                                         installmentsValidator: InstallmentsValidator) extends Validation {

  override def validate(implicit operation: OperationResource, site: Site) = {

    val isMPOS = site.mensajeriaMPOS.getOrElse("N").equalsIgnoreCase("S")

    if (!isMPOS) {
      operation.datos_medio_pago.getOrElse {
        logger.error("medioDePago None")
        throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, "datos_medio_pago")
      }

      //Validacion de medio de pago
      operation.datos_medio_pago match {
        case Some(DatosMedioPagoResource(Some(medioDePagoId), _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)) => {

          if (!medioDePagoRepository.exists(medioDePagoId.toString)) {
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_METHOD_ID)
          }

          if (medioDePagoId != 42 &&
            site.cuentas.exists(_.idMedioPago == medioDePagoId.toString) &&
            !site.cuentas.find(_.idMedioPago == medioDePagoId.toString).get.habilitado) {
            logger.warn("payment_method_id not enabled")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_METHOD_ID)
          }
          if (operation.cuotas.isDefined && medioDePagoId == 31 && operation.cuotas.get != 1) {
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_INSTALLMENTS)
          }
          if (medioDePagoId == 45) {

            if (operation.fechavto_cuota_1.isEmpty)
              throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_FIRST_INSTALLMENT_EXPIRATION)
            if (operation.cuotas.isEmpty)
              throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_INSTALLMENTS)
            if (operation.cuotas.get > 11)
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_INSTALLMENTS)

            val formatter = DateTimeFormat.forPattern("ddMMyy")
            val dateFirstExprirationInstallment = try {
              DateTime.parse(operation.fechavto_cuota_1.get, formatter)
            } catch {
              case e: Throwable => {
                throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_FIRST_INSTALLMENT_EXPIRATION)
              }
            }

            val now = DateTime.now
            val monthsDiff = Months.monthsBetween(now, dateFirstExprirationInstallment).getMonths()
            val daysDiff = Days.daysBetween(now, dateFirstExprirationInstallment).getDays()
            //La compra debe estar pagada antes de cumplir los 11 meses
            if (monthsDiff > (11 - operation.cuotas.get) || daysDiff < 0)
              throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_FIRST_INSTALLMENT_EXPIRATION)
          }
        }
        case _ => logger.debug("Payment Method Validation success.")
      }


      operation.datos_medio_pago match {
        case Some(DatosMedioPagoResource(Some(medioDePagoId), _, _, _, Some(nroTarjeta: String), _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)) => {

          val oMedioDePago = medioDePagoRepository.retrieve(medioDePagoId)
          val medioDePago = oMedioDePago.getOrElse {
            logger.error("invalid payment_method_id, medioDePago not existed")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_METHOD_ID)
          }
          val carBrandId = medioDePago.idMarcaTarjeta.getOrElse {
            logger.error("Undefined idMarcaTarjeta")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_ID_MARCA_TARJETA)
          }
          val protocolId = paymentMethodService.getProtocolId(operation)
          val backendId = paymentMethodService.getBackenId(operation)
          validateSitePaymentMeans(site, medioDePagoId.toString, protocolId, backendId, ErrorMessage.DATA_METHOD_ID)

          validateNacional(site, medioDePagoId.toString, protocolId, backendId, carBrandId, nroTarjeta)
          val isBsaEncryptedCard = operation.datos_bsa match {
            case Some(datosBsa) => {
              datosBsa.flag_tokenization.contains("1")
            }
            case other => false
          }
          if (!isBsaEncryptedCard) {
            validateLuhn(nroTarjeta, medioDePago)
          }
        }
        case _ => logger.debug("Payment Method - Marca Tarjeta Validation success.")
      }

      operation.datos_medio_pago match {
        case Some(DatosMedioPagoResource(_, _, _, _, Some(nroTarjeta: String), _, _, _, _, _, None, _, _, _, _, _, _, _, _, _, _, _, _)) => {
          val bin = operationRepository.decrypted(nroTarjeta).take(6)
          binsService.validateBin(bin, None)
        }

        case Some(DatosMedioPagoResource(Some(medioDePagoId), _, _, _, Some(nroTarjeta), _, _, _, _, _, Some(binParaValidar: String), _, _, _, _, _, _, _, _, _, _, _, _)) => {
          val oMedioDePago = medioDePagoRepository.retrieve(medioDePagoId)
          val medioDePago = oMedioDePago.getOrElse {
            logger.error("invalid payment_method_id, medioDePago not existed")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_METHOD_ID)
          }
          binsService.validateBin(binParaValidar, oMedioDePago)
        }
        case _ => logger.debug("Payment Method - Tarj Number Validation success.")
      }

      val decrypted = operationRepository.retrieveDecrypted(operation.id) match {
        case Some(dOp: OperationResource) => {
          dOp
        }
        case None => {
          operation
        }
      }

      operation.datos_medio_pago match {
        case Some(DatosMedioPagoResource(_, _, _, _, _, _, _, _, _, _, Some(binParaValidar), _, _, _, _, _, _, _, _, _, _, _, _)) => {
          val oBinGuardado = decrypted.datos_medio_pago.flatMap(_.nro_tarjeta.map(_.take(6)))
          if (oBinGuardado != Some(binParaValidar)) {
            logger.warn(s"Error de validacion de bin ($binParaValidar vs $oBinGuardado)")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_BIN)
          }
        }
        case _ => logger.debug("Payment Method - Bin Validation success.")
      }

      operation.datos_medio_pago match {
        case Some(DatosMedioPagoResource(_, _, _, Some(idMarcaTarjeta), _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)) => {
          if (!marcaTarjetaRepository.exists(idMarcaTarjeta.toString)) {
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_ID_MARCA_TARJETA)
          }

          //Si es débito no puede tener mas de 1 cuota
          if (operation.cuotas.isDefined && idMarcaTarjeta == 16) {
            operation.datos_site.map(datosSite => datosSite.id_modalidad match {
              case Some("S") => {
                logger.debug("Distrubuted payment")
                operation.sub_transactions.foreach(st => st.installments.map(installment => installmentsValidator.validateInstallmentType(installment, 1)))
              }
              case _ => {
                operation.cuotas match {
                  case None => throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_INSTALLMENTS)
                  case Some(installment) => {
                    logger.debug("Single payment")
                    installmentsValidator.validateInstallmentType(installment, 1)
                  }
                }
              }
            })
          }

        }
        case _ => logger.debug("Payment Method - Marca Tarjeta Validation success.")
      }

      operation.datos_gds match {
        case Some(GDSResource(nro_location, _, iata_code)) => {
          if (nro_location.length > 8) {
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "nro_location")
          }

          if (iata_code.length > 10) {
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "iata_code")
          }
        }
        case _ => logger.debug("Payment Method - GDS Validation success.")
      }

      operation.datos_medio_pago match {
        case Some(DatosMedioPagoResource(_, _, Some(idMoneda), _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)) => {
          if (!monedaRepository.exists(idMoneda.toString)) {
            logger.error("IdMoneda Don't exists.")
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_ID_MONEDA)
          }
        }
        case _ => logger.debug("Payment Method - id Moneda Validation success.")
      }


      operation.datos_medio_pago match {
        case Some(DatosMedioPagoResource(_, _, _, _, Some(nroTarjeta: String), _, _, _, _, _, Some(binParaValidar: String), _, _, _, _, _, _, _, _, _, _, _, _)) => {
          if (binParaValidar != nroTarjeta.take(binParaValidar.length())) {
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_NRO_TARJETA)
          }
        }
        case _ => logger.debug("Payment Method - Nro Tarjeta Validation success.")
      }

      operation.datos_medio_pago match {
        case Some(DatosMedioPagoResource(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, Some(establishment_name: String))) => {
          if (establishment_name.length > 25)
            throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_ESTABLISHMENT_NAME)
        }
        case _ => logger.debug("Payment Method - Establishment Validation success.")
      }

      operation.datos_medio_pago match {
        case Some(DatosMedioPagoResource(_, _, _, _, _, _, _, Some(expMonth), Some(expYear), _, _, _, _, _, _, _, _, _, _, _, _, _, _)) => {
          if (!expMonth.matches("0[1-9]|1[0-2]")) throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_CARD_EXPIRATION_MONTH)
          val month = try {
            expMonth.toInt
          }
          catch {
            case e: Exception => throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_CARD_EXPIRATION_MONTH)
          }

          if (!expYear.matches("[0-9]{2}")) throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_CARD_EXPIRATION_YEAR)
          val year = try {
            expYear.toInt
          }
          catch {
            case e: Exception => throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_CARD_EXPIRATION_YEAR)
          }

          val dateFormat = new SimpleDateFormat("MM/yyyy")
          val currentDate = dateFormat.parse(DateTime.now.getMonthOfYear + "/" + DateTime.now.year().get)
          val expirationDate = dateFormat.parse(expMonth + "/" + "20" + expYear)
          if (expirationDate.before(currentDate)) {
            throw ErrorFactory.validationException("CardData", "expired card")
          }
        }
        case _ => logger.debug("Payment Method - Expire Date Validation success.")
      }
    }
  }

  private def validateLuhn(nroTarjeta: String, oMedioDePago: MedioDePago) = {
    if (oMedioDePago.validateLuhn && !fullCreditCardValidator.isValid(nroTarjeta)) {
      logger.error(s"invalid Luhn of cardNumber: ${nroTarjeta}")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_CARD_NUMBER)
    }
  }

  private def validateSitePaymentMeans(site: Site, medioPagoId: String, protocoloId: Int, backendId: Int, errorMessage: String) = {
    site.cuenta(medioPagoId, protocoloId, backendId).map(cuenta =>
      if (!cuenta.habilitado) {
        logger.error(s"El site ${site.id} tiene deshabilitado el medio de pago $medioPagoId")
        throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, errorMessage)
      }
    ).getOrElse {
      logger.warn(s"El site ${site.id} no tiene configurado el medio de pago: ${medioPagoId}, protocolo: ${protocoloId}, backend ${backendId}")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, errorMessage)
    }
  }

  private def validateNacional(site: Site, medioPagoId: String, protocoloId: Int, backendId: Int, marcaTarjetaId: Int, nroTarjeta: String) = {
    val marcaTarjeta = marcaTarjetaRepository.retrieve(marcaTarjetaId).getOrElse(throw new Exception(s"tarjeta no existente marcaTarjetaId: ${marcaTarjetaId}"))
    site.cuenta(medioPagoId, protocoloId, backendId).map(cuenta =>
      if (cuenta.aceptaSoloNacional && !marcaTarjeta.esNacional(Some(nroTarjeta))) {
        throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_METHOD_ID)
      }
    ).getOrElse{
      logger.warn(s"El site ${site.id} no tiene configurado el medio de pago: ${medioPagoId}, protocolo: ${protocoloId}, backend ${backendId}")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_METHOD_ID)
    }
  }
}
