package com.decidir.coretx.domain

import java.util.Date
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.api.DatosTitularResource
import com.decidir.coretx.api.DatosMedioPagoResource
import com.decidir.coretx.api.DatosSiteResource
import decidir.sps.core.Comprador
import scala.beans.BeanProperty
import monocle.macros.GenLens
import com.decidir.coretx.api.DatosTitularResource
import com.decidir.coretx.api.DatosSiteResource
import decidir.sps.core.Protocolos
import services.PaymentMethodService
import play.Logger

object OperationData {
  
  private val opDataLens = GenLens[OperationData]
  
  private val opResourceLens = GenLens[OperationResource]
  private val montoInOpResource = opResourceLens(_.monto)
  private val cuotasInOpResource = opResourceLens(_.cuotas)
  private val chargeIdInOpResource = opResourceLens(_.charge_id)
  private val resourceInOpData = opDataLens(_.resource)
  private val montoInOpData = resourceInOpData composeLens montoInOpResource
  private val cuotasInOpData = resourceInOpData composeLens cuotasInOpResource  
  private var chargeIdInOpData = resourceInOpData composeLens chargeIdInOpResource
  
  //TODO: hacerlo andar :)
//  private val dmpResourceLens = GenLens[DatosMedioPagoResource]
//  private val terminalInDmpResource = dmpResourceLens(_.nro_terminal)
//  private val traceInDmpResource = dmpResourceLens(_.nro_trace)
//  private val ticketInDmpResource = dmpResourceLens(_.nro_ticket)
//  
//  private val dmpInOpResource = GenLens[OperationResource](_.datos_medio_pago)
//  private val terminalInOnResource = dmpInOpResource composeLens terminalInDmpResource
//  private val traceInOnResource = dmpInOpResource composeLens traceInDmpResource
//  private val ticketInOnResource = dmpInOpResource composeLens ticketInDmpResource
//  private val terminalInOpData = resourceInOpData composeLens terminalInOnResource
//  private val traceInOpData = resourceInOpData composeLens traceInOnResource
//  private val ticketInOpData = resourceInOpData composeLens ticketInOnResource
  
  private val siteInOpData = opDataLens(_.site) 
}

/**
 * protocolId: puede ser distinto para MedioDePago.id = 42
 */
case class OperationData(val resource: OperationResource, 
                         val site: Site, 
                         val medioDePago: MedioDePago, 
                         val marcaTarjeta: MarcaTarjeta,
                         val moneda: Moneda) {

  def removeCvv = this.copy(resource = OperationResource.removeCvv(this.resource))

  val logger = Logger.underlying()
  
  lazy val chargeId = resource.charge_id.getOrElse(throw new Exception("No se definio charge_id"))
  def buildComprador(): Comprador = {
    null // TODO
  }
  
  def nroOperacionSite = resource.nro_operacion.getOrElse(throw new Exception("No se definio nro_operacion"))
  
  def cuenta: Cuenta = site.cuentas.find { c => (c.idMedioPago == medioDePago.id && 
    c.idProtocolo == protocolId &&
    c.idBackend == backenId.toString)} getOrElse(throw new Exception(s"No hay una cuenta asociada al sitio ${site.id} y medio de pago ${medioDePago.id}: $medioDePago"))
  
/**
 * protocolId: puede ser distinto para MedioDePago.id = 42
 */
  def protocolId = {
    resource.datos_medio_pago.map{_.medio_de_pago.map{ _ match {
      case 42 => { resource.datos_medio_pago.flatMap(_.nro_tarjeta).map(cardNumber => {
          if(cardNumber.take(1) == "5")
            Protocolos.codigoProtocoloMastercard
          else
            Protocolos.codigoProtocoloVisa}).getOrElse{
              logger.error("Undefined ProtocoloId: OperationResource.datos_medio_pago.medio_de_pago not found")
              throw new Exception(s"undefined ProtocoloId: OperationResource.datos_medio_pago.nro_tarjeta not found, in operation: ${resource}")}
      }
      case medioDePagoId => medioDePago.protocol
    }}.getOrElse{
      logger.error("Undefined ProtocoloId: OperationResource.datos_medio_pago.medio_de_pago not found")
      throw new Exception(s"undefined ProtocoloId: OperationResource.datos_medio_pago.medio_de_pago not found, in operation: ${resource}")}
    }.getOrElse{
      logger.error("Undefined ProtocoloId: OperationResource.datos_medio_pago not found")
      throw new Exception(s"undefined ProtocoloId: OperationResource.datos_medio_pago not found, in operation: ${resource}")}
  } 

/**
 * backenId: puede ser distinto para MedioDePago.id = 42
 */
  def backenId = {
    resource.datos_medio_pago.map{_.medio_de_pago.map{ _ match {
      case 42 => { 
        protocolId match {
            case Protocolos.codigoProtocoloVisa => 6
            case Protocolos.codigoProtocoloMastercard => 7
            case other => {
              logger.error(s"Undefined backenId: OperationResource.datos_medio_pago.medio_de_pago not found, protocolId: ${other}")
              throw new Exception(s"undefined backenId: OperationResource.datos_medio_pago.medio_de_pago not found, protocolId: ${other}, in operation: ${resource}")
            }
          }
      }
      case medioDePagoId => medioDePago.backend
    }}.getOrElse{
      logger.error("Undefined backenId: OperationResource.datos_medio_pago.medio_de_pago not found")
      throw new Exception(s"undefined backenId: OperationResource.datos_medio_pago.medio_de_pago not found, in operation: ${resource}")}
    }.getOrElse{
      logger.error("Undefined backenId: OperationResource.datos_medio_pago not found")
      throw new Exception(s"undefined backenId: OperationResource.datos_medio_pago not found, in operation: ${resource}")}
  }    
    
  lazy val esNacional = marcaTarjeta.esNacional(resource.datos_medio_pago.flatMap(_.nro_tarjeta))
  
  lazy val nroTarjeta = resource.datos_medio_pago.flatMap(_.nro_tarjeta).getOrElse("")
  lazy val nroTarjetaVisible = nroTarjeta.takeRight(4)
  
  def codigo_iso_num = moneda.idMonedaIsoNum
  
  def getMoneda = moneda
  
  def replaceMonto(monto: Long): OperationData = {
    OperationData.montoInOpData.set(Some(monto))(this)
  }
  
  def replaceCuotas(cuotas: Option[Int]): OperationData = {
    OperationData.cuotasInOpData.set(cuotas)(this)
  }
  
  def replaceChargeId(subpaymentId:Option[Long]):OperationData = {
    OperationData.chargeIdInOpData.set(subpaymentId)(this)
  }
  
//  def replaceTerminal(terminal: Option[String]): OperationData = {
//    OperationData.terminalInOpData.set(terminal)(this)
//  }
//  
//  def replaceTicket(ticket: Option[String]): OperationData = {
//    OperationData.ticketInOpData.set(ticket)(this)
//  }
//  
//  def replaceTrace(trace: Option[String]): OperationData = {
//    OperationData.traceInOpData.set(trace)(this)
//  }
  
  def remplaceSite(site: Site): OperationData = {
    OperationData.siteInOpData.set(site)(this)
  }
  
  lazy val datosTitular = resource.datos_titular.getOrElse(throw new Exception("No se definio datosTitular"))
  
  lazy val datosSite = resource.datos_site.getOrElse(DatosSiteResource())
  
  lazy val cuotas = resource.cuotas.getOrElse(1)
  
  lazy val datosMedioPago = resource.datos_medio_pago match {
    
    case Some(DatosMedioPagoResource(
                Some(medio_de_pago),
                _,
                Some(id_moneda),
                Some(marca_tarjeta),
                Some(nro_tarjeta),
                card_number_encrypted,
                Some(nombre_en_tarjeta),
                Some(expiration_month),
                Some(expiration_year),
                security_code,
                bin_for_validation,
                nro_trace,
                cod_autorizacion,
                nro_devolucion,
                last_four_digits,
                card_number_length,
                id_operacion_medio_pago,nro_terminal,
                nro_ticket,
                motivo,
                motivo_adicional,
                id_plan,
                establishment_name)) =>
                  DatosMedioPago(
                    medio_de_pago,
                    id_moneda,
                    marca_tarjeta,
                    nro_tarjeta,
                    nombre_en_tarjeta,
                    expiration_month,
                    expiration_year,
                    security_code
                  )
// Pago Offline
    case Some(DatosMedioPagoResource(
    Some(medio_de_pago),
    _,
    Some(id_moneda),
    Some(marca_tarjeta),
    None,
    None,
    nombre_en_tarjeta,
    None,
    None,
    security_code,
    bin_for_validation,
    nro_trace,
    cod_autorizacion,
    nro_devolucion,
    last_four_digits,
    card_number_length,
    id_operacion_medio_pago,nro_terminal,
    nro_ticket,
    motivo,
    motivo_adicional,
    id_plan,
    establishment_name)) =>
      DatosMedioPago(
        medio_de_pago,
        id_moneda,
        marca_tarjeta,
        null,
        nombre_en_tarjeta.getOrElse(""),
        null,
        null,
        security_code
      )

    case _ => throw new Exception("Datos de medio de pago no definido")
  }
    
}


case class DatosMedioPago(
              medio_de_pago: Int,
              id_moneda: Int, 
              marca_tarjeta: Int,
              nro_tarjeta: String,
              nombre_en_tarjeta: String,
              expiration_month: String,
              expiration_year: String, 
              security_code: Option[String]) {
  
  def expiration = expiration_year + expiration_month 
  
}


