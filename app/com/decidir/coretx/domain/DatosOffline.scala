package com.decidir.coretx.domain

case class DatosOffline (val nroOperacion: String,
                         val nroTienda: String,
                         val medioPago: String,
                         val nroDoc: String,
                         val monto: String,
                         val recargo: String,
                         val fechaVto: String,
                         val fechaVto2: String,
                         val codp1: String,
                         val codp2: String,
                         val codp3: String,
                         val codp4: String,
                         val cliente: String){

  def getParametro(param:String): String = {

    param.toUpperCase() match  {
      case "NROOPERACION" => nroOperacion
      case "NROTIENDA" => nroTienda
      case "NRODOC" => nroDoc
      case "MONTO" => monto
      case "RECARGO" => recargo
      case "FECHAVTO" => fechaVto
      case "FECHAVTO2" => fechaVto2
      case "COD_P1" => codp1
      case "COD_P2" => codp2
      case "COD_P3" => codp3
      case "COD_P4" => codp4
      case "CLIENTE" => cliente
    }
  }

}