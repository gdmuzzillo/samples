package com.decidir.coretx.utils

import org.slf4j.Logger

/**
  * Created by gustavo on 11/8/17.
  */
object StringUtil {

  /**
    * Metodo que chequea si una cadena excede determinado tamaño y de ser así lo trunca.
    *
    * @param value
    * @param length
    * @param logger
    * @return
    */
  def safeString(value: String, length: Int, logger: Option[Logger] = None): String = {
    if (value != null && value.nonEmpty && value.length > length) {
      logger.map(log => {
        log.info(s"Campo ${value} fue truncado por exceder el maximo de ${length} caracteres")
      })
      value.substring(0, length)
    } else
      value
  }

}
