package com.decidir.coretx.domain

import decidir.sps.core.Protocolos
import org.scalatest.{FlatSpec, Matchers}

class MotivoTest extends FlatSpec with Matchers{

  it should "return Visa Motivo when receive a visa protocol" in {
    val visa = Protocolos.codigoProtocoloVisa
    val motivo = Motivo.getMotivoErrorDefaultParaProtocolo(visa, 11)
    motivo.idProtocolo shouldEqual visa
    motivo.id shouldEqual Motivo.ID_MOTIVO
  }

  it should "return Mastercard Motivo when receive a mastercard protocol" in {
    val mastercard = Protocolos.codigoProtocoloMastercard
    val motivo = Motivo.getMotivoErrorDefaultParaProtocolo(mastercard, 11)
    motivo.idProtocolo shouldEqual mastercard
    motivo.id shouldEqual Motivo.ID_MOTIVO
  }

  it should "return Default Motivo object when receive a undefined protocol" in {
    val undefinedProtocol = Protocolos.codigoProtocoloIndefinido
    val motivo = Motivo.getMotivoErrorDefaultParaProtocolo(undefinedProtocol, 11)
    motivo.idProtocolo shouldEqual undefinedProtocol
    motivo.id shouldEqual Motivo.ID_MOTIVO
  }

}
