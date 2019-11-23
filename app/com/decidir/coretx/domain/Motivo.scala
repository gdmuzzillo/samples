package com.decidir.coretx.domain

import java.sql.PreparedStatement

import com.decidir.coretx.utils._
import javax.inject.Inject
import javax.inject.Singleton
import com.decidir.protocol.api.TransactionResponse
import decidir.sps.core.Protocolos
import play.api.db.Database
import collection.JavaConverters._
import org.springframework.jdbc.core.simple.SimpleJdbcInsert

import scala.collection.mutable.HashMap

/**
 * @author martinpaoletta
 */

object Motivo {

  val ID_MOTIVO = 9999

  def getMotivoErrorDefaultParaProtocolo(codigoProtocolo: Int, idMotivo: Int): Motivo = {

    def getMotivoErrorDefaultSet = Motivo(99, -1, -1, "Venta n\u00e3o autorizada", "Venta n\u00e3o autorizada")

    def getMotivoErrorDefaultPCuenta = Motivo(99, -1, -1, "Venta no autorizada", "Venta no autorizada")

    def getMotivoErrorDefaultAmex =
      Motivo(ID_MOTIVO, -1, -1, "error desconocido", s"error desconocido motivo:$idMotivo")

    def getMotivoErrorDefaultVisanet =
      Motivo(ID_MOTIVO, codigoProtocolo, -1, "Visa error desconocido", s"error desconocido motivo:$idMotivo")

    def getMotivoErrorDefaultMastercard =
      Motivo(ID_MOTIVO, codigoProtocolo, -1, "Mastercard error desconocido", s"error desconocido motivo:$idMotivo")

    def getMotivoErrorDefault =
      Motivo(ID_MOTIVO, codigoProtocolo, -1, "error desconocido", s"error desconocido idProtocolo:$codigoProtocolo motivo:$idMotivo")

    codigoProtocolo match {
      case Protocolos.codigoProtocoloSet|Protocolos.codigoProtocoloMoset => getMotivoErrorDefaultSet
      case Protocolos.codigoProtocoloPCuenta => getMotivoErrorDefaultPCuenta
      case Protocolos.codigoProtocoloAmex => getMotivoErrorDefaultAmex
      case Protocolos.codigoProtocoloVisa => getMotivoErrorDefaultVisanet
      case Protocolos.codigoProtocoloMastercard => getMotivoErrorDefaultMastercard
      case _ => getMotivoErrorDefault
    }
  }


}


case class Motivo(id: Int, idProtocolo: Int, idTipoOperacion: Int, descripcion: String, descripcion_display: String)
  extends Storeable {

  def toMap = ApiSupport.toMap(this)

}


@Singleton
class MotivoRepository @Inject() (jedisPoolProvider: JedisPoolProvider)
  extends RedisDao[Motivo](jedisPoolProvider,
    (map => Motivo(map("id").toInt, map("idProtocolo").toInt, map("idTipoOperacion").toInt,
      map("descripcion"), map("descripcion_display")))) {

  val entityPrefix = "motivos:"

  def store(motivo: Motivo) = {
    doWithRedis { redis =>
      implicit val tx = Some(redis)
      //      val indexKey = entityPrefix + "idprotocolo:idtipooperacion:idmotivo"
      val indexKey = s"$entityPrefix${motivo.idProtocolo}:${motivo.idTipoOperacion}:${motivo.id}"
      storeMapInRedis(indexKey, motivo.toMap)
    }
  }


  def retrieve(idProtocolo: Int, idTipoOperacion: Int, idMotivo: Int): Option[Motivo] = {

    val indexKey = s"${idProtocolo}:${idTipoOperacion}:${idMotivo}"
    super.retrieve(indexKey) match {
      case None => Some(Motivo.getMotivoErrorDefaultParaProtocolo(idProtocolo, idMotivo))
      case Some(motivo) => Some(motivo)
    }

  }

}
