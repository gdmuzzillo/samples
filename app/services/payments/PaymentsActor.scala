package services.payments

import akka.actor.Actor
import com.decidir.coretx.domain.OperationData
import services.cybersource.CybersourceClient
import akka.actor.ActorRef
import akka.actor.PoisonPill
import com.decidir.coretx.domain.OperationResourceRepository
import scala.concurrent.Future
import services.metrics.MetricsClient
import scala.util.{Try, Success, Failure}
import com.decidir.protocol.api._
import akka.pattern.pipe
import com.decidir.coretx.domain._
import com.decidir.coretx.api._
import controllers.MDCHelperTrait
import javax.inject.{Singleton, Inject}
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import PaymentProcessStatus._
import services.refunds.RefundService
import com.decidir.coretx.domain.CybersourceError
import com.decidir.coretx.domain.ProcessingError
import java.sql.Timestamp
import services.SingleTransactionProcessor
import services.DistributedTransactionProcessor
import services.tokens.TokensService
import services.protocol.TerminalService
import services.validations.AgroTokenValidator


/**
 * @author martinpaoletta
 */

@Singleton
class PaymentsProcessor @Inject() (
    system: ActorSystem,
    operationRepository: OperationResourceRepository,
    singleTransactionProcessor: SingleTransactionProcessor,
    distributedTransactionProcessor: DistributedTransactionProcessor,    
    cybersourceClient: CybersourceClient, 
    refundService: RefundService,
    siteRepository: SiteRepository,
    metrics: MetricsClient,
    motivoRepository:MotivoRepository,
    tokensService: TokensService,
    notifyPayment: NotifyPayment,
    legacyTxService: LegacyTransactionServiceClient,
    terminalService: TerminalService,
    agroTokenValidator: AgroTokenValidator) {
  
  def pay(op: OperationData): Future[Try[OperationExecutionResponse]] = {
		implicit val timeout = Timeout(90 seconds)
    val worker = system.actorOf(Props(new PaymentsActor(operationRepository, singleTransactionProcessor, distributedTransactionProcessor, cybersourceClient, refundService, siteRepository, metrics, motivoRepository, tokensService, notifyPayment, legacyTxService, terminalService, agroTokenValidator)))
		import system.dispatcher
    (worker ? op).mapTo[Try[OperationExecutionResponse]]
  }
}


class PaymentsActor(
    operationRepository: OperationResourceRepository,
    singleTransactionProcessor: SingleTransactionProcessor,
    distributedTransactionProcessor: DistributedTransactionProcessor,    
    cybersourceClient: CybersourceClient, 
    refundService: RefundService,
    siteRepository: SiteRepository,
    metrics: MetricsClient,
    motivoRepository: MotivoRepository,
    tokensService: TokensService,
    notifyPayment: NotifyPayment,
    legacyTxService: LegacyTransactionServiceClient,
    terminalService: TerminalService,
    agroTokenValidator: AgroTokenValidator) extends Actor with MDCHelperTrait {
  
  var client: Option[ActorRef] = None
  var ooperationData: Option[OperationData] = None
  var osite: Option[Site] = None
  var ocsconf: Option[CyberSourceConfiguration] = None
  var ocsresponse: Option[CyberSourceResponse] = None
  var ooperationexecutionresponse: Option[Try[OperationExecutionResponse]] = None
  var status: PaymentProcessStatus = Validating()
  var csRetries: Int = 0
  
  val maxRetries = cybersourceClient.retries
  
  import context.dispatcher
  
  
  def receive = {
    
    case op: OperationData => withMDC {
      updateMDCFromOperation(op.resource)
      loadOperationData(op)
      setClient
      logger.debug("Load and initialice payment")
      if(op.resource.agro_data.map(ad => ad.payment_method_is_agro).getOrElse(false)){
        validateAgroPayment pipeTo self
      }else{
        validateAndPay
      }
    }

    // Validación de token agro KO para tipo de token E-commerce
    case ar @ AgroTokenValidatorResponse(result, _) if !result.equals("2") && operationData.resource.agro_data.get.token_type.equalsIgnoreCase("E") => withMDC {
      storeAgroTokenValidationResponse(ar)
      answerStoredTransactionResponse
      //TODO: error token no valido
    }

    // Operación Agro E-commerce sin token
    case ar @ AgroTokenValidatorResponse("-1", _) => withMDC {
      storeAgroTokenValidationResponse(ar)
      answerStoredTransactionResponse
      //TODO: error token requerido
    }

    //Operación Agro con token validado - Pago exitoso, llamar a CS si esta habilitado
    case ar @ AgroTokenValidatorResponse(_,_) => withMDC {
      // Pago fallido.
      //TODO: usar metodo para guardar los datos
      ooperationData = ooperationData.map(od => od.copy(resource = od.resource.copy(agro_data = od.resource.agro_data.map(ad => ad.copy(token_resolution_code = Some(ar.response.toInt))))))
      validateAndPay
    }

    // Pago exitoso, llamar a CS si esta habilitado
    case Success(tr: OperationExecutionResponse) if tr.authorized => withMDC {
      updateMDCFromOperation(tr.operationResource.get)
      logger.debug("Pago exitoso ")

      releaseTerminal
      storeOperationExecutionResponse(tr)
      if(isCSEnabled) {
        ocsresponse.map(blackCSResponse => {
          storeCyberSourceResponse(blackCSResponse)
          answerStoredTransactionResponse
        }).getOrElse{
          if (sendToCs) {
        	  callCS
          } else {
            logger.warn("Not call CyberSource because has set fraud_detection.send_to_cs")
            answerStoredTransactionResponse
          }
        }
      }
      else
        answerStoredTransactionResponse
    }

    // Pago fallido.
    case Success(tr: OperationExecutionResponse) if !tr.authorized => withMDC {
      updateMDCFromOperation(tr.operationResource.get)
      logger.info("Pago rechazado ")

      releaseTerminal
      storeOperationExecutionResponse(tr)
      // TODO hay que notificar a CS igual. Pendiente!!!! hay que notificar la validacion de domicilio a CS
      answerStoredTransactionResponse
    }    
    
    /**
     * Error en llamada a tarjeta.
     * Simple intenta reversar.
     * Distribuida ya intento reversar en otro actor
     */
    case Failure(e) if status == ToCard() => withMDC {
      updateMDCFromOperation(operation)
  	  logger.error(s"Error en llamado a tarjeta", e)
  	  reversePayment(e)
    }
    
    // Pagado, green CS
    case csr @ CyberSourceResponse(FraudDetectionDecision.green, _, _, _, _, _) => withMDC {
      updateMDCFromOperation(operation)
      logger.info("CyberSource result: GREEN")
      storeCyberSourceResponse(csr)
      answerStoredTransactionResponse
    }

    // Pagado, review CS
    case csr @ CyberSourceResponse(FraudDetectionDecision.yellow, _, _, _, _, _) => withMDC {
      updateMDCFromOperation(operation)
      logger.info("CyberSource result: YELLOW")
      storeCyberSourceResponse(csr)
      answerStoredTransactionResponse
    }
    
    // Pago exitoso, red en CS
    case csr @ CyberSourceResponse(FraudDetectionDecision.red, _, _, _, _, _) => withMDC {
      updateMDCFromOperation(operation)
      logger.info("CyberSource result: RED")
      storeCyberSourceResponse(csr)
      if(operationExecutionResponse.authorized && withAutoReversals) {
        logger.info("Reversando operacion por red en CyberSource")
    	  cancelPayment.foreach(u => answerStoredTransactionResponse)
      } else {
        answerStoredTransactionResponse
      }
    }
    
    // Pago exitoso y blue en CS
    case csr @ CyberSourceResponse(FraudDetectionDecision.blue, requestId, reasonCode, description, details, _) => withMDC {
      updateMDCFromOperation(operation)
      logger.info("CyberSource result: BLUE")    
      if (csRetries < maxRetries) {            
    	  csRetries += 1
        logger.warn(s"Retry {$csRetries} call to CyberSource")
        callCS
      } else {
        logger.warn(s"Finished all retries to CyberSource")
        storeCyberSourceResponse(csr)
        if(operationExecutionResponse.authorized && withAutoReversalsOnBlue) {
          logger.info("Reversando operacion por blue en CyberSource")
      	  cancelPayment.foreach(u => answerStoredTransactionResponse)
        } else {
          answerStoredTransactionResponse
        } 
      }
    }
    
    // Pago exitoso y black en CS
    case csr @ CyberSourceResponse(FraudDetectionDecision.black, requestId, reasonCode, description, details, _) => withMDC {
      updateMDCFromOperation(operation)
      logger.info("CyberSource result: BLACK")
      storeCyberSourceResponse(csr)
      if(operationExecutionResponse.authorized && withAutoReversalsOnBlue) {
        logger.info("Reversando operacion por black en CyberSource")
    	  cancelPayment.foreach(u => answerStoredTransactionResponse)
      } else {
        answerStoredTransactionResponse
      }      
    }    
    
  }


  private def validateAgroPayment: Future[AgroTokenValidatorResponse] = {

    val tokenType = operationData.resource.agro_data.map(ad => ad.token_type.toUpperCase).getOrElse("");
    val tokenValue= operationData.resource.agro_data.map(ad => ad.token).getOrElse("");

    tokenType match {
      case "E" if tokenValue.isEmpty => Future(AgroTokenValidatorResponse("-1", Some("E-commerce operation requires a valid token.")))
      case _ => agroTokenValidator.validate(operationData.resource)
    }

  }


  /**
   * Realiza la validacion de CS (si esta habilitado) y paga
   */
  private def validateAndPay = {

   (if(isCSEnabled && sendToCs) {
      logger.debug("previous validation to call CyberSource")
    	cybersourceClient.validate(operationData) 
    }
    else None
   ) match {
       
      case Some(csr) => {
        logger.error(s"Error Black CyberSource validation: ${csr}")
    	  if(continueOnBlack) {
          logger.warn("Do continue payment, with Black CS error")  
          storeCyberSourceBlackResponse(csr)
          pay
        } 
        else {
          logger.warn("Do not continue payment, with Black CS error")
          storeBlackCyberSourceResponse(csr)  
          val reazonTransactionResponse = getCSReazonTransactionResponse(operationExecutionResponse)
          //TODO: hacer update por reazon de cs
          operationData.datosSite.id_modalidad match {
            case Some("S") => {
              logger.debug("Distrubuted payment")
              val parent = InsertTx(operationData.chargeId, operationData.site, Some("F"), Black(), operationExecutionResponse)
              val opResource = operationExecutionResponse.operationResource.get
              val distributedTxElement = operationExecutionResponse.operationResource.get.sub_transactions.map(subT => DistributedTxElement(
                  siteRepository.retrieve(subT.site_id).get, 
                  subT.installments.get,
                  None,
                  subT.amount,
                  None,
                  opResource.copy(charge_id = subT.subpayment_id)))
              legacyTxService.insert(InsertDistributedTx(operationData.chargeId, parent, distributedTxElement, Black()))
            }
            case _ => {
              logger.debug("Single payment")
              legacyTxService.insert(InsertTx(operationData.chargeId, operationData.site, None, Black(), operationExecutionResponse))
            }
          }
    	    storeCyberSourceResponse(csr)
          answer(Success(operationExecutionResponse))
        }
      }
      
      case None => pay
      
    }
  }
  
  private def getCSReazonTransactionResponse(opr: OperationExecutionResponse) = {
    TransactionResponse(statusCode = opr.status, idMotivo = opr.operationResource.flatMap(_.fraud_detection.flatMap(_.status.map(_.reason_code))).getOrElse(-1), 
        terminal = None, nro_trace = None, nro_ticket = None, cod_aut = None, validacion_domicilio = None, cardErrorCode = None, 
        site_id = opr.operationResource.get.siteId, idOperacionMedioPago = "", motivoAdicional = None)
  }
  
  private def pay = {
    transitionTo(toCard)
    terminalService.acquireTerminal(operationData) match {
      case Success(opData) => {
        ooperationData = Some(opData)
        processPayment(opData) pipeTo self
      }
      case Failure(error) => {
        //No consiguio terminales
        val motivo = motivoRepository.retrieve(operationData.protocolId, 0, 12035)
        storeOperationExecutionResponse(OperationExecutionResponse(
            status = Rechazada().id,
            authorizationCode = "",
            cardErrorCode = Some(
              ProcessingError(
                code = motivo.map(_.id).getOrElse(-1),
                descripcion = motivo.map(_.descripcion),
                None)),
            authorized = false,
            validacion_domicilio = None,
            operationResource = ooperationData.map(_.resource),
            subPayments = None))

        operationData.datosSite.id_modalidad match {
          case Some("S") => {
            logger.debug("Distrubuted payment")
            val parent = InsertTx(operationData.chargeId, operationData.site, Some("F"), Ingresada(), operationExecutionResponse)
            val opResource = operationExecutionResponse.operationResource.get
            val distributedTxElement = operationExecutionResponse.operationResource.get.sub_transactions.map(subT => DistributedTxElement(
              siteRepository.retrieve(subT.site_id).get,
              subT.installments.get,
              None,
              subT.amount,
              None,
              opResource.copy(charge_id = subT.subpayment_id)))
            legacyTxService.insert(InsertDistributedTx(operationData.chargeId, parent, distributedTxElement, Ingresada()))
          }
          case _ => {
            logger.debug("Single payment")
            legacyTxService.insert(InsertTx(operationData.chargeId, operationData.site, None, Ingresada(), operationExecutionResponse))
          }
        }

        answerStoredTransactionResponse
      }
    }
  }  
  
  
  /**
   * Ejecuta el pago vía el medio de pago
   */
  private def processPayment(opdata: OperationData): Future[Try[OperationExecutionResponse]] = {
    opdata.datosSite.id_modalidad match {
      case Some("S") => {
        logger.debug("Distrubuted payment")
        distributedTransactionProcessor.processDistributedTx(opdata, ocsresponse)
      }
      case _ => {
        logger.debug("Single payment")
    	  singleTransactionProcessor.processSinglePayment(opdata, ocsresponse)
      }
    }
  }
  
  
  private def callCS = {
    logger.info("Invocando a CyberSource")
    cybersourceClient.call(operationData, Some(operationExecutionResponse.authorized), operationExecutionResponse.validacion_domicilio) pipeTo self
  }
  

  /**
   * Asigna el payment id, guarda la operacion en el actor y guarda el MDC para asegurar este disponible 
   * para las próximas llamadas con withMDC
   */
  private def loadOperationData(op: OperationData) = {
    
    def withChargeId(opdata: OperationData) = {
      val step2 = System.currentTimeMillis()
      val txId = opdata.resource.id
      val chargeId = opdata.chargeId
      val step3 = System.currentTimeMillis()
      metrics.recordInMillis(txId, "coretx", "OperacionService", "process.newChargeId", step3 - step2)
      
      opdata.copy(resource = opdata.resource.copy(charge_id = Some(chargeId)))
    }    
    
    ooperationData = Some(withChargeId(op))
    
    updateMDCFromOperation(op.resource)
    
    osite = Some(op.site)
    ocsconf = osite flatMap { _.cyberSourceConfiguration }
  }
  
  private def reversePayment(faild: Throwable) = {
    operationData.datosSite.id_modalidad match {
      case Some("S") => {
        releaseTerminal
        logger.error(s"Error en validacion de datos de Distribuida", faild)
        answer(Failure(faild))
      }
      case _=> {
        logger.error(s"Error en llamado a tarjeta. Reversando", faild)
        reverseSinglePayment
      }
    }
  }

  private def reverseSinglePayment() = {
    logger.info("Reversando pago")
    transitionTo(toReverse)
    
    refundService.reverse(operationData).map{tor => {
      releaseTerminal
      storeOperationExecutionResponse(singleReversed2OperationExecutionResponse(tor))
      answerStoredTransactionResponse
      }
    }
  }
  
  private def cancelPayment() = {
    logger.info("Anulando pago")
    transitionTo(toCancel)

    refundService.cancelOnCSResponse(operationData).map(rprTor => {
      storeOperationExecutionResponse(canceled2OperationExecutionResponse(rprTor._1, rprTor._2))
      TransactionState.apply(operationExecutionResponse.status) match {
        case ARevisar() => legacyTxService.update(UpdateCS(operationExecutionResponse.copy(status = PendienteDeAnulacion().id), csresponse, true))
        case other => {}
      }
    })
  }
  
  private def singleReversed2OperationExecutionResponse(tor: Try[OperationResponse]): OperationExecutionResponse = {
    tor match {
      case Success(or) => {
        OperationExecutionResponse(
            status = if(or.statusCode == 200) Rechazada().id else ARevisar().id,
            authorizationCode = or.cod_aut.getOrElse(""),
            cardErrorCode = Some(or.cardErrorCode.getOrElse(ProcessingError())), 
            authorized = or.authorized,
            validacion_domicilio = None,
            operationResource = ooperationData.map(_.resource),
            subPayments = None)
      }
      case Failure(faild) => {
        //Falla reversa
        OperationExecutionResponse(
            status = ARevisar().id,
            authorizationCode = "",
            cardErrorCode = Some(ProcessingError()), 
            authorized = false,
            validacion_domicilio = None,
            operationResource = ooperationData.map(_.resource),
            subPayments = None)
      }
    }
  }  


  private def canceled2OperationExecutionResponse(refundPaymentResponse : Option[RefundPaymentResponse], tor: Try[OperationResponse]): OperationExecutionResponse = {
    tor match {
      case Success(or) => {
          val rPaymentResponse = refundPaymentResponse.get
          operationExecutionResponse.copy(authorized = false,
            status = rPaymentResponse.status.id,
            operationResource = operationExecutionResponse.operationResource.map(or => or.copy(sub_transactions = or.sub_transactions
                .map(st => st.copy(status = Some(rPaymentResponse.sub_payments.get.find{sp => sp.id == st.subpayment_id.get}.get.status match {
                  case Rechazada() => Autorizada().id //Rechaza la marca (No se pudo anular)
                  case other => other.id
                })))))
          )
      }
      case Failure(faild) => {
        //Fallo la anulacion del pago luego de llamada a CS
        OperationExecutionResponse(
            status = ARevisar().id,
            authorizationCode = operationExecutionResponse.authorizationCode,
            cardErrorCode = Some(ProcessingError()), 
            authorized = false,
            validacion_domicilio = None,
            operationResource = ooperationData.map(_.resource),
            subPayments = None)
      }
    }
  }  
  

  private def answerStoredTransactionResponse: Unit = {
    answer(toperationExecutionResponse)
  }

  private def sendToken(oer: OperationExecutionResponse) = {
   tokensService.sendToken(operationData).map { token =>
      toperationExecutionResponse.map { oer =>
        oer.copy(operationResource = oer.operationResource.map(_.copy(customer_token = token)))
      }.map(storeOperationExecutionResponse)
      updateNroOperation(oer)
      finalizeClient
    }

  }

  private def answer(toer: Try[OperationExecutionResponse]): Unit = {
    toer match {
      case Success(oer) => {
        TransactionState.apply(oer.status) match {
          case PreAutorizada() | Autorizada() => {
          	notifyPayment.sendPaymentNotifications(oer)
          	sendToken(oer)
          }
          case status => {
              val error = oer.cardErrorCode.map(cec => s" - CardError: ${ cec.toJson.toString}").getOrElse("")              
              logger.warn(s"Payment is not authorized. Status: ${status}${error}")
            updateNroOperation(oer)
            finalizeClient
          }
        }

      }
      case Failure(error) => {
        logger.warn(s"Pay Not tokenizated error: ${error}")
        updateNroOperation(operationData)
        finalizeClient
      }
    }


  }

  def finalizeClient(): Unit = {
    client.getOrElse(throw new Exception("Error grave, no se seteo el cliente")) ! toperationExecutionResponse
    die
  }
  
  private def updateNroOperation(oer: OperationExecutionResponse) = {
    val operation = oer.operationResource.getOrElse(throw new Exception("Error grave, intentando obtener operationResource"))
    val site = operation.datos_site.map(_.site_id
        .getOrElse(throw new Exception("Error grave, intentando obtener site")))
        .getOrElse(throw new Exception("Error grave, intentando obtener datosSiteResource"))
    val nroOperation = operation.nro_operacion.getOrElse(throw new Exception("Error grave, intentando obtener nro_operacion"))
    val modality = operation.datos_site.get.id_modalidad
    operationRepository.updateNroOperacionStatus(nroOperation, site, TransactionState.apply(oer.status), getPaymentType(modality), getCountSubpayments(modality, operation.sub_transactions), ocsresponse.map(_.decision))
  }
  
  private def updateNroOperation(operation: OperationData) = {
    val site = operation.site.id
    val nroOperation = operation.resource.nro_operacion.getOrElse(throw new Exception("Error grave, intentando obtener nro_operacion"))
    val modality = operation.datosSite.id_modalidad
    operationRepository.updateNroOperacionStatus(nroOperation, site, AProcesar(), getPaymentType(modality), None, ocsresponse.map(_.decision))
  }
  
  private def getPaymentType(modality: Option[String]) = {
    modality match {
      case Some("S") => "S" //"Distrubuted"
      case _ => "N" //"Single"
    }
  }
  
  private def getCountSubpayments(modality: Option[String], subTransaction: List[SubTransaction]) = {
    modality match {
      case Some("S") => Some(subTransaction)
      case _ =>  None
    }
  }
  
  private def isCSEnabled = ocsconf.map(_.enabled).getOrElse(false) && operationData.medioDePago.cyberSource

  private def continueOnBlack = ocsconf.map(_.continueOnBlack).getOrElse(true)
  
  private def withAutoReversals = ocsconf.map(_.withAutoReversals).getOrElse(true)
  
  private def withAutoReversalsOnBlue = ocsconf.map(_.withAutoReversalsOnBlue).getOrElse(true)
  
  private def setClient = if(client.isEmpty) client = Some(sender) 
  
  private def die = self ! PoisonPill
  
  private def operationData = ooperationData.getOrElse(throw new Exception("Error grave, no se guardo operationdata"))
  
  private def txId = operationData.resource.id
  
  private def operation = operationData.resource
  
  private def sendToCs = {
		val oCsmdd = operation.fraud_detection.flatMap(_.csmdds.flatMap {_.find { csmdd => csmdd.code == 44 && csmdd.description.equalsIgnoreCase("NOSEGUIR")}})
    operation.fraud_detection.flatMap(_.send_to_cs).map(toCs => toCs).getOrElse(oCsmdd.map(csmdd44 => false).getOrElse(true))
  }
  
  private def storeCyberSourceResponse(csr: CyberSourceResponse) = {
    ocsresponse = Some(csr)
    ooperationexecutionresponse = ooperationexecutionresponse.map(operationexecutionresponse => {
      operationexecutionresponse.map(oer => oer.copy(
        cardErrorCode = csr.decision match {
          case FraudDetectionDecision.green | FraudDetectionDecision.yellow => None
          case other => Some(CybersourceError())
        },
        operationResource = oer.operationResource.map(or => or.copy(fraud_detection = or.fraud_detection.map(fd => fd.copy(status = ocsresponse))))))
    })
    ooperationData = ooperationData.map(od => od.copy(resource = od.resource.copy(fraud_detection = od.resource.fraud_detection.map(fd => fd.copy(status = ocsresponse)))))
    
    legacyTxService.update(UpdateXref(operationExecutionResponse.operationResource.flatMap(_.charge_id).getOrElse(throw new Exception("Error grave, no se guardo charge_id")),operationExecutionResponse))
    legacyTxService.insert(InsertCS(operationData.resource, csr))
    val estados = CybersourceFSM.estadosPara(csresponse.decision, Some(csRetries))
    estados.map(estado => legacyTxService.insert(InsertTxHistorico(None, Some(operationData.chargeId), operationData.cuenta.idProtocolo,estado.id, None, Some(System.currentTimeMillis()), getReazon(estado), operationData.resource.nro_operacion)))
  }
  
  private def getReazon(state: TransactionState):Option[Int] = {
    state match {
      case Black() => ocsresponse.map(_.reason_code)
      case other => None  
    }
  }
  
  private def storeBlackCyberSourceResponse(csr:CyberSourceResponse) = {
    // En este caso no se hizo el pago, error en validacion de CS
    val subTransactions = operation.datos_site.get.id_modalidad match {
      case Some("S") => operation.sub_transactions.map(subT => subT.copy(status = Some(Rechazada().id)))
      case _ => List()
    }
    
    ocsresponse = Some(csr)
    ooperationexecutionresponse = Some(Success(OperationExecutionResponse(
            Rechazada().id,
            "",
            Some(CybersourceError()),
            false,
            None,
            Some(operation.copy(
                sub_transactions = subTransactions,
                fraud_detection = Some(FraudDetectionData(status = ocsresponse)))),
            None,
            None)))
  }
  
  private def storeCyberSourceBlackResponse(csr: CyberSourceResponse) = {
    ocsresponse = Some(csr)
    ooperationData = ooperationData.map(od => od.copy(resource = od.resource.copy(fraud_detection = od.resource.fraud_detection.map(fd => fd.copy(status = ocsresponse)))))
  }
  
  private def storeOperationExecutionResponse(tr: OperationExecutionResponse): Unit = {
    ooperationData = ooperationData.map(od => od.copy(resource = od.resource.copy(datos_medio_pago = tr.operationResource.get.datos_medio_pago, sub_transactions = tr.operationResource.get.sub_transactions)))
    ooperationexecutionresponse = Some(Success(tr))}

  private def storeOperationExecutionResponse(tr: Try[OperationExecutionResponse]): Unit = ooperationexecutionresponse = Some(tr)

  private def toperationExecutionResponse: Try[OperationExecutionResponse] = 
    ooperationexecutionresponse.getOrElse(throw new Exception("Error grave, no se guardo operationexecutionresponse"))

  
  private def operationExecutionResponse: OperationExecutionResponse = 
    toperationExecutionResponse.getOrElse(throw new Exception("Error grave, intentando obtener operationexecutionresponse de una operacion fallida"))
  
  private def csresponse: CyberSourceResponse =
    ocsresponse.getOrElse(throw new Exception("Error grave, no se guardo CyberSourceResponse"))

  private def transitionTo(pps: PaymentProcessStatus) = {
    status = pps
    // TODO Persistencia
//    ???
  }


  private def storeAgroTokenValidationResponse(ar: AgroTokenValidatorResponse) = {

    ooperationexecutionresponse = Some(Success(OperationExecutionResponse(
      RechazadaDatosInvalidos().id,
      "",
      Some(AgroTokenValidationError()),
      false,
      None,
      Some(operation),
      None,
      None)))
  }

  private def releaseTerminal(): Unit = {
    terminalService.releaseTerminal(operationData)
  }

}

sealed trait PaymentProcessStatus
object PaymentProcessStatus {
  val validating = Validating()
  val toCard = ToCard()
  val fromCard = FromCard()
  val toCyberSource = ToCyberSource()
  val toReverse = ToReverse()
  val toCancel = ToCancel()
}
case class Validating() extends PaymentProcessStatus
case class ToCard() extends PaymentProcessStatus
case class FromCard() extends PaymentProcessStatus
case class ToCyberSource() extends PaymentProcessStatus
case class ToReverse() extends PaymentProcessStatus
case class ToCancel() extends PaymentProcessStatus
