package services

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.math.BigDecimal
import scala.math.BigDecimal.int2bigDecimal
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.domain.OperationData
import com.decidir.coretx.domain.SiteRepository
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import controllers.MDCHelperTrait
import javax.inject.Inject
import javax.inject.Singleton
import services.protocol.ProtocolService
import services.protocol.Operation2ProtocolConverter
import com.decidir.protocol.api.TransactionResponse
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.ErrorMessage
import com.decidir.coretx.domain.ProcessingError
import com.decidir.coretx.api.OperationExecutionResponse
import com.decidir.coretx.api.OperationExecutionResponse
import com.decidir.coretx.api.CyberSourceResponse
import com.decidir.coretx.api.OperationExecutionResponse
import com.decidir.protocol.api.TransactionResponse
import scala.util.Success
import com.decidir.coretx.domain.DatosMedioPago
import services.payments._
import com.decidir.coretx.api.Subpayment
import com.decidir.coretx.api.SubTransaction
import com.decidir.coretx.api.RebotadaPorGrupo
import com.decidir.coretx.api.AProcesar
import com.decidir.coretx.api.Autorizada
import com.decidir.coretx.api.Rechazada
import controllers.MDCHelperTrait
import com.decidir.coretx.domain.OperationResourceRepository
import com.decidir.coretx.api.ApiException
import com.decidir.protocol.api.OperationResponse
import com.decidir.coretx.api.TransactionState
import scala.collection.mutable.ArrayBuffer
import com.decidir.coretx.domain.RefundSubPaymentOperation
import com.decidir.coretx.domain.RefundSubPaymentOperation
import com.decidir.coretx.domain.ReverseOperationPostPayment
import com.decidir.coretx.domain.CancelOperationPostPayment
import services.refunds.DistributedOperationProcessor
import com.decidir.coretx.api.Rechazada
import com.decidir.coretx.api.RefundSubPaymentResponse
import scala.util.Success
import scala.util.Failure
import scala.util.Success
import scala.util.Success
import com.decidir.coretx.api.Rechazada
import com.decidir.coretx.api.Autorizada
import com.decidir.coretx.api.ARevisar
import com.decidir.coretx.api.TxAnulada
import com.decidir.coretx.api.RefundPaymentResponse
import com.decidir.coretx.domain.Site
import com.decidir.coretx.api.Rechazada
import com.decidir.coretx.api.TxAnulada
import com.decidir.coretx.domain.ProtocolError
import com.decidir.coretx.domain.CardErrorCode

@Singleton
class DistributedTransactionProcessor @Inject() (context: ExecutionContext, 
                                      actorSystem: ActorSystem, 
                                      distributedOperationProcessor: DistributedOperationProcessor,
                                      protocolService: ProtocolService, 
                                      siteRepository: SiteRepository, 
                                      legacyTxService: LegacyTransactionServiceClient,
                                      operationRepository: OperationResourceRepository,
                                      transactionProcessor: TransactionProcessor) extends MDCHelperTrait {
  implicit val ec = context


  def processDistributedTx( opdata: OperationData, ocsresponse: Option[CyberSourceResponse]): Future[Try[OperationExecutionResponse]] = {  
    val chargeId = opdata.chargeId

    val requestsAndInserts = prepareDistributed(opdata)
    requestsAndInserts match {
      case Failure(error) => { //TODO: Error q no deberia darse
        logger.error("Error", error)
        Future(Failure(error))
      }
      case Success(rAndInserts) => {
        // Ordenar por monto en forma descendiente
        val sortedRequestsAndInserts = rAndInserts.sortBy(_._1.resource.monto).reverse
    
        val (requests, childrenInserts) = sortedRequestsAndInserts.unzip
        
        operationRepository.validatePaymentCountSubpayments(opdata.resource, childrenInserts) match {
          case Failure(error) => {
             logger.error("Previous payment without equal amount of subpayments")
             Future(Failure(error))
          }
          case Success(oer) => {
            val fakeResponse = transactionProcessor.transactionResponse2OperationExecutionResponse(None, ocsresponse, opdata, Nil, None)
            val parent = InsertTx(chargeId, opdata.site, Some("F"), AProcesar(),fakeResponse)
            val dtx = InsertDistributedTx(chargeId, parent, childrenInserts, AProcesar())
            legacyTxService.insert(dtx)
            
            for {
              dTXResponses <- pay(requests.filterNot(x => x.resource.monto.fold(false)(x => x == 0)))
              fOperationExecutionResponse <- handlerPayment(opdata, dTXResponses, childrenInserts, ocsresponse)
            } yield fOperationExecutionResponse
          }
        }
      }
    }
  }
  
  private def pay(requests: List[OperationData]): Future[List[DTXResponse]] ={
    implicit val timeout = Timeout(60 seconds)
    (actorSystem.actorOf(Props(new DTXHandlerActor(protocolService))) ? DTX(requests)).mapTo[List[DTXResponse]]
  }
  
  private def handlerPayment(opdata: OperationData, dTXResponses: List[DTXResponse], childrenInserts: List[DistributedTxElement], ocsresponse: Option[CyberSourceResponse]): Future[Try[OperationExecutionResponse]] = {
    def t2o(tr: TransactionResponse, subPayments: List[DistributedTxElement], dtxType: Option[String] = None) = transactionProcessor.transactionResponse2OperationExecutionResponse(Some(tr), ocsresponse, opdata, subPayments, None)
    
    val chargeId = opdata.chargeId
    val site = opdata.site
    
    //BUSCAMOS LA RESPUESTA QUE FALLO
    val failureResponse = dTXResponses.find(response => {
      response.response.isFailure || (response.response.map(!_.authorized).getOrElse(false) && response.response.map( tr => !isGroupRejected(tr)).getOrElse(false))
    })

    failureResponse match {

      case None => {
        //CASO FELIZ. NO HAY RESPUESTA CON FAILURE Y/O NO AUTORIZADAS.
      	val allSuccessResponses = dTXResponses.collect{ case DTXResponse(_, Success(response)) => response}
      	val dtxWithResponse = childrenInserts.map { insert => joinDtxWithResponse(allSuccessResponses, insert, None) }
            
  	    val tr = allSuccessResponses.head
        val oer = transactionProcessor.transactionResponse2OperationExecutionResponse(Some(tr), ocsresponse, opdata, dtxWithResponse, None)
        val parent = UpdateTx(chargeId, site, Some("F"), Autorizada(), oer, Some(tr))
    
        val dtx = UpdateDistributedTx(chargeId, parent, dtxWithResponse, Autorizada())
        legacyTxService.update(dtx)
        val toer = t2o(tr, dtxWithResponse, Some(site.montoPorcent))
        Future.successful(Success(toer))

      }
      case Some(DTXResponse(request, Success(TransactionResponse(statusCode, idMotivo, terminal, nro_trace, nro_ticket, cod_aut, validacion_domicilio, cardErrorCode, site_id, idOperacionMedioPago, infoAdicional, barcode)))) => {
        //CASO NO FELIZ. HAY UNA RESPUESTA NO AUTORIZADAS (Distinto de GroupRejected).          
      	val tr = TransactionResponse(statusCode, idMotivo, terminal, nro_trace, nro_ticket, cod_aut, validacion_domicilio, cardErrorCode, site_id, idOperacionMedioPago)
      	updateMDCFromOperation(opdata.resource)
        logger.error(s"Error en pago de subpayment: chargeId of subpayment: ${request.chargeId} - TransactionResponse: ${tr}")
      	processDistributedPostPayment(opdata, dTXResponses, childrenInserts, ocsresponse, Some(tr)) 
      }
      case Some(DTXResponse(request,Failure(ProtocolError(authCode, reason)))) => {
        //CASO NO FELIZ. HAY UNA RESPUESTA CON FAILURE.
        updateMDCFromOperation(opdata.resource)
        logger.error(s"Error enviando transaccion a protocolo: chargeId of subpayment: ${request.chargeId} - authCode: ${authCode} - reason: ${reason}")
        processDistributedPostPayment(opdata, dTXResponses, childrenInserts, ocsresponse, None) 
      }
    }
  }
  
  private def isGroupRejected(tr: TransactionResponse) = {
    //TODO: mejorar esta validacion
    tr.cardErrorCode.map(cec => cec.error_code == "group_rejected").getOrElse(false)
  }
  
  private def getOperationExecutionResponseWithFinalState(oer: OperationExecutionResponse, refundSubPResponse: List[RefundSubPaymentResponse]) = {
    val subTransactions = oer.operationResource.map { or => {
       or.sub_transactions.map(st => {
         refundSubPResponse.find(rspr => rspr.id == st.subpayment_id.get) match {
           case Some(rSubPaymentResponse) => st.copy(status = Some(rSubPaymentResponse.status.id))
           case None => st
         }
       }) 
    }}
    val cec = refundSubPResponse.find(_.card_error_code.isDefined).flatMap(_.card_error_code)
    subTransactions.map(sts => oer.copy(operationResource = oer.operationResource.map(op => op.copy(sub_transactions = sts)), cardErrorCode = cec)).getOrElse(oer)
  }
  
  private def joinDtxWithResponse(responses:List[TransactionResponse], dtxElement:DistributedTxElement, defaultResponse:Option[TransactionResponse]):DistributedTxElement = {
    responses.find { response => dtxElement.site.id == response.site_id } match {
      case Some(response) =>  dtxElement.copy(transactionResponse = Some(response))
      case _ => dtxElement.copy(transactionResponse = defaultResponse)
    }
  }

  //TODO: para q esta esto???
  private def extractedReferer(operation: OperationResource) = operation.datos_site.flatMap(_.referer).getOrElse("Referer not sent")

  private def prepareDistributed(opdata: OperationData): Try[List[(OperationData, DistributedTxElement)]] = Try {
    val referer = extractedReferer(opdata.resource)
    opdata.resource.sub_transactions.map { subTx =>
      val subSite = siteRepository.retrieve(subTx.site_id).getOrElse(throw new Exception(s"Error grave, intentando obtener subSite: ${subTx.site_id}"))
      // UNA TUPLA QUE CONTIENE OPDATA A MANDAR AL MEDIO DE PAGO Y UN CASE CLASS QUE CONTIENE DATOS NECESARIOS PARA HACER INSERT EN BASE DE DATOS.
      var fixedOpData = opdata.copy(resource = opdata.resource.copy(datos_medio_pago = opdata.resource.datos_medio_pago.map(dmp => dmp.copy(nro_terminal=subTx.terminal, nro_trace=subTx.nro_trace, nro_ticket = subTx.nro_ticket))))
      .replaceMonto(subTx.amount)
      .replaceCuotas(subTx.installments)
      .replaceChargeId(subTx.subpayment_id)
      .remplaceSite(subSite)
      (fixedOpData, DistributedTxElement(subSite, subTx.installments.get, None, subTx.amount, None, fixedOpData.resource))
    }.toList
  }
  
  private def processDistributedPostPayment(opDataFather: OperationData, responses:List[DTXResponse], childrenInserts: List[DistributedTxElement], ocsresponse: Option[CyberSourceResponse], transactionResponse: Option[TransactionResponse]): Future[Try[OperationExecutionResponse]] ={
    val allSuccessResponses = responses.collect{ case DTXResponse(_, Success(response)) => {
      if (response.statusCode == 200) {
        Some(response)
      }else None
      }}.flatten
    val failureRequest = responses.collectFirst{ case DTXResponse(request,Failure(ProtocolError(authCode, reason))) => request} //A lo sumo hay uno con error de protocolo
    val refundSubPaymentOperation = ArrayBuffer[RefundSubPaymentOperation]()
    
    failureRequest.map(request => refundSubPaymentOperation += RefundSubPaymentOperation(request.chargeId,
      request.resource.monto.get,
      None,
      ReverseOperationPostPayment()))
      
    val hadToReverse = !failureRequest.isEmpty
      
    val dtxSuccessWithResponse = childrenInserts.map { insert => joinDtxWithResponse(allSuccessResponses, insert, transactionResponse) }.map{dtxe => {
      dtxe.transactionResponse.map(tr => if(tr.statusCode == 200) Some(dtxe) else None).flatten}
    }.flatten
    dtxSuccessWithResponse.foreach { dtxSuccess => refundSubPaymentOperation += RefundSubPaymentOperation(dtxSuccess.chargeId,
      dtxSuccess.monto,
      None,
      CancelOperationPostPayment()) }
    
    val meanPayment = opDataFather.resource.datos_medio_pago.get.medio_de_pago.get
    
    val allResponses = responses.collect{ case DTXResponse(_, Success(response)) => response}
    val dtxWithResponse = childrenInserts.map { insert => joinDtxWithResponse(allResponses, insert, None) }
    val oer = transactionProcessor.transactionResponse2OperationExecutionResponse(transactionResponse, ocsresponse, opDataFather, dtxWithResponse, None)
    val opDataFatherFixed = opDataFather.copy(opDataFather.resource.copy(sub_transactions = oer.operationResource.get.sub_transactions))
    
    val refundSubPaymentOperations = refundSubPaymentOperation.toList 
    if (!refundSubPaymentOperations.isEmpty) {
      distributedOperationProcessor.processDistributedOPx(opDataFatherFixed.chargeId, Rechazada(), opDataFatherFixed, None, meanPayment, Some(refundSubPaymentOperations)).map( pr => pr match {
        case (refundPaymentResponse, Success(OperationResponse(statusCode, _, _, _, _, _, _, _, _, _, _,_)), _) => statusCode match {
          case 200 => {
            //Al menos una retorno Success 200
            Success(updatePostPayment(opDataFather.chargeId, opDataFather.site, oer, Rechazada(), transactionResponse, Some(refundPaymentResponse), dtxWithResponse, hadToReverse))
          }
          case other => {
            //Al menos una retorno Success !=200 (Ninguna 200)
            Success(updatePostPayment(opDataFather.chargeId, opDataFather.site, oer, ARevisar(), transactionResponse, Some(refundPaymentResponse), dtxWithResponse, hadToReverse))
          }          
        }
        case (refundPaymentResponse, Failure(faild), _) => faild match {
          case ProtocolError(authCode, reason) => {
            //Al menos una retorno Failure ProtocolError (Ninguna Success)
            Success(updatePostPayment(opDataFather.chargeId, opDataFather.site, oer, ARevisar(), transactionResponse, Some(refundPaymentResponse), dtxWithResponse, hadToReverse))
          }
          case ApiException(invalidRequestError) => {
            //Peor de los casos
            Success(updatePostPayment(opDataFather.chargeId, opDataFather.site, oer, ARevisar(), transactionResponse, Some(refundPaymentResponse), dtxWithResponse, hadToReverse))
          }
        }        
      })
    } else {
      logger.debug("Has not subpayment to Reverse or Cancel")
      val oerFixed = updatePostPayment(opDataFather.chargeId, opDataFather.site, oer, Rechazada(), transactionResponse, None, dtxWithResponse, hadToReverse)
      Future(Success(oerFixed))
    }

  }
  
  private def updatePostPayment(chargeId: Long, site: Site, oer: OperationExecutionResponse, finalState: TransactionState, 
      transactionResponse: Option[TransactionResponse], refundPaymentResponse: Option[RefundPaymentResponse], dtxWithResponse: List[DistributedTxElement], hadToReverse: Boolean):OperationExecutionResponse = {
    val oerWithStateFinal = (refundPaymentResponse.map( rpr => getOperationExecutionResponseWithFinalState(oer, rpr.sub_payments.get)).getOrElse(oer)).copy(status = finalState.id)
    val stateUpdate = finalState match {
      case ARevisar() => if (hadToReverse) {
        ARevisar()
      } else {
        Autorizada()
      }
      case other => other  
    }
    
    val parent = UpdateTx(chargeId, site, Some("F"), stateUpdate, oerWithStateFinal, transactionResponse)

    val dtx = UpdateDistributedTx(chargeId, parent.enEstado(finalState), dtxWithResponse, stateUpdate)
    legacyTxService.update(dtx)
    oerWithStateFinal.copy(status = finalState.id, authorized = false, cardErrorCode = Some(oerWithStateFinal.cardErrorCode.getOrElse(ProcessingError())))//authorized false para q no le pegue a cs. Tr puede venir autorizada luego de reversa 
  }
  
}
