package services.payments

import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import javax.inject.Inject
import controllers.MDCHelperTrait
import scala.concurrent.Future
import scala.util.Try
import com.decidir.coretx.api.OperationExecutionResponse
import services.OperacionService
import com.decidir.coretx.domain.OperationResourceRepository
import scala.util.Failure
import scala.util.Success
import com.decidir.coretx.api.ApiException
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.ErrorMessage
import services.bins.BinsService
import com.decidir.coretx.api.Rechazada
import com.decidir.coretx.api.RechazadaDatosInvalidos
import com.decidir.coretx.api.Ingresada
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.domain.InvalidCard
import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.domain.SiteRepository
import com.decidir.protocol.api.TransactionResponse
import com.decidir.coretx.domain._
import com.decidir.coretx.api.DatosMedioPagoResource
import com.decidir.coretx.api.RebotadaPorGrupo
import com.decidir.coretx.api.SubTransaction

@Singleton
class PaymentsService @Inject() (implicit executionContext: ExecutionContext,
    operacionService: OperacionService,
    operationRepository: OperationResourceRepository,
    paymentProcessor: PaymentsProcessor,
    binsService: BinsService,
    siteRepository: SiteRepository,
    legacyTxService: LegacyTransactionServiceClient) extends MDCHelperTrait {
  
  
    def process(txId: String): Future[Try[OperationExecutionResponse]] = {
    try {
      val opData = operacionService.validateOperacion(operacionService.validateProcess(txId))
      operacionService.validateTwoSteps(opData)
      //No existe en redis, se la inserta
      operationRepository.flagAsUsed(opData.resource)
      operationRepository.storeOperacionAProcesar(opData.resource)
      val operationData = setChargeId(buildOperationData(opData))
      binsService.validateBin(operationData.resource) match {
        case (None, Success(_)) => {
          //Se realiza el pago
          paymentProcessor.pay(operationData).flatMap {
            case Failure(fail) => {
              logger.error("process: Throwable", fail)
              Future.failed(fail)
            }
            case Success(oer) => {
              logger.debug("process Success")
              Future(Success(oer))
            }
          }
        }
        case (Some(siteId), Failure(fail)) => {
          logger.warn("process bin: Throwable", fail)
          Future(Success(operationExecutionResponseBinFailure(siteId, operationData)))
        }
      }
    }
    catch {
      case e: ApiException => {
        logger.error("process: ApiException", e)
        Future(Failure(e))
      }
      case e: Throwable => {
        logger.error("process: Throwable", e)
        Future(ErrorFactory.uncategorizedFailure(e))
      }
    }
  }   
    
  private def operationExecutionResponseBinFailure(siteId: String, operationData: OperationData) = {
    val opData = operationData.copy(resource = operationData.resource.copy(
        sub_transactions = operationData.datosSite.id_modalidad match {
        	case Some("S") => operationData.resource.sub_transactions.map(subT => 
        	  subT.copy(status = if(subT.site_id == siteId) Some(RechazadaDatosInvalidos().id) else Some(RebotadaPorGrupo().id)))
        	case _ => List()
        })
    )
    val oer = OperationExecutionResponse(
            status = RechazadaDatosInvalidos().id, 
            authorizationCode = "", 
            cardErrorCode = Some(InvalidCard(10005)),
            authorized = false,
            validacion_domicilio = None,
            operationResource = Some(opData.resource),
            subPayments = None)
    val reazonTransactionResponse = getReazonTransactionResponse(oer)        
            
    opData.datosSite.id_modalidad match {
      case Some("S") => {
        logger.debug("Distrubuted payment")
        val parentInsert = InsertTx(opData.chargeId, opData.site, Some("F"), Ingresada(), oer)
        val distributedTxElement = oer.operationResource.map(_.sub_transactions.map(subT => DistributedTxElement(
            siteRepository.retrieve(subT.site_id).get, 
            subT.installments.get, 
            None, 
            subT.amount, 
            if(subT.site_id == siteId) Some(reazonTransactionResponse) else None, 
            opData.resource.copy(charge_id = subT.subpayment_id)))).getOrElse(List())
        legacyTxService.insert(InsertDistributedTx(opData.chargeId, parentInsert, distributedTxElement, Ingresada()))
        
        val parentUpdate = UpdateTx(opData.chargeId, opData.site, Some("F"), RechazadaDatosInvalidos(), oer, None)
        legacyTxService.update(UpdateDistributedTx(opData.chargeId, parentUpdate, distributedTxElement, RechazadaDatosInvalidos()))
      }
      case _ => {
        logger.debug("Single payment")
        legacyTxService.insert(InsertTx(opData.chargeId, opData.site, None, Ingresada(), oer))
        legacyTxService.update(UpdateTx(opData.chargeId, opData.site, None, RechazadaDatosInvalidos(), oer, Some(reazonTransactionResponse)))
      }
    }
    
    oer
  }
    
  private def getReazonTransactionResponse(oer: OperationExecutionResponse) = {
    TransactionResponse(statusCode = oer.status, idMotivo = oer.cardErrorCode.map(_.code).getOrElse(-1), 
        terminal = None, nro_trace = None, nro_ticket = None, cod_aut = None, validacion_domicilio = None, cardErrorCode = None, 
        site_id = oer.operationResource.get.siteId, idOperacionMedioPago = "", motivoAdicional = None)
  }
  
  private def buildOperationData(oData: OperationData): OperationData = {
    val datosSite = oData.resource.datos_site.getOrElse(throw new Exception("Error grave, intentando obtener datosSites"))
    val opdata = oData.copy(resource = oData.resource.copy(original_amount = oData.resource.monto))
    datosSite.id_modalidad match {
      case Some("S") => { //Distrubuted
        if (opdata.site.montoPorcent == "M") {
          prepareDistributedByAmount(opdata)
        }
        else {
          prepareDistributedByPercentage(opdata)
        }
      } 
      case _ => opdata
    }
  }
  
  //Set all keys
  private def setChargeId(opdata: OperationData): OperationData = {
    opdata.copy(resource = opdata.resource.copy(
        charge_id = Some(operationRepository.newChargeId), 
        sub_transactions = opdata.datosSite.id_modalidad match {
        	case Some("S") => opdata.resource.sub_transactions.map(subT => subT.copy(subpayment_id = Some(operationRepository.newSubpaymentId)))
        	case _ => List()
        })
    )
  }
  
  private def prepareDistributedByAmount(opdata: OperationData): OperationData = {
    opdata.copy(resource = opdata.resource.copy(sub_transactions = opdata.resource.sub_transactions.map { subTx =>
      val installment = subTx.installments.getOrElse(retriveInstallments(opdata.resource.cuotas))
      subTx.copy(original_amount = Some(subTx.amount),installments = Some(installment))
    }.toList))
  }
  
  private def prepareDistributedByPercentage(opdata: OperationData): OperationData = {
    val totalAmount = opdata.resource.monto.getOrElse(throw new Exception("Error grave, intentando obtener amount"))
    val cuotasParent = retriveInstallments(opdata.resource.cuotas)

    val subSites = siteRepository.findSubSitesBySite(opdata.site)
    val subTransactions = subSites.map { subSite =>
      val amount = ((totalAmount * subSite.porcentaje)/100).toLong
      SubTransaction(site_id= subSite.idSite, amount = amount, original_amount = Some(amount),installments = Some(cuotasParent), nro_trace = None, subpayment_id = None, status = None)
    }
    
    val amountSubpayments = subTransactions.map{_.amount}.sum
    val totalPercents = subSites.map{_.porcentaje}.sum

    val subTransactionsFixed = if (totalPercents < 100){
    	// Resto al site padre
      val fatherPercent = 100D - totalPercents
      val amount = totalAmount - amountSubpayments
      SubTransaction(site_id= opdata.site.id, amount = amount, original_amount = Some(amount), installments = Some(cuotasParent), nro_trace = None, subpayment_id = None, status = None) :: subTransactions
    } else if (totalAmount != amountSubpayments) {
        //Asigno el resto al hijo con major porcentaje
        val subTransactionToFixed = subTransactions.maxBy(_.amount)
        subTransactions.map{ subTransaction => 
          if (subTransaction.site_id == subTransactionToFixed.site_id) {
            val amount = subTransactionToFixed.amount + (totalAmount - amountSubpayments)
            SubTransaction(site_id= opdata.site.id, amount = amount, original_amount = Some(amount), installments = Some(cuotasParent), nro_trace = None, subpayment_id = None, status = None)
          }
          else subTransaction
        }
    } else subTransactions
    
    opdata.copy(resource = opdata.resource.copy(sub_transactions = subTransactionsFixed))
  }
  
  private def retriveInstallments(installment: Option[Int]) = {
    installment.getOrElse(throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "installments"))
  }
  
}