package com.decidir.coretx.domain

case class ProtocolError(errorCode: String, 
    cardErrorCode: Option[CardErrorCode] = None
    ) extends RuntimeException(s"($errorCode)") //TODO: cambiar runtime
  
