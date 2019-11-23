package com.decidir.coretx.domain

import javax.inject.{Singleton, Inject}

import com.decidir.coretx.utils._
import play.Logger

@Singleton
class TipoDocumentoRepository @Inject() (jedisPoolProvider: JedisPoolProvider)
  extends RedisDao[TipoDocumento](jedisPoolProvider,
    (map => TipoDocumento(map("id").toInt,
      map("description")))) {

  val entityPrefix = "tipodoc:"

}

case class TipoDocumento(id: Int, description: String) extends Storeable {
  def toMap = {
    ApiSupport.toMap(this)
  }
}




