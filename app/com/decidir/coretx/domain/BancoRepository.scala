package com.decidir.coretx.domain

import javax.inject.{Inject, Singleton}

import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.utils.{ApiSupport, JedisPoolProvider, RedisDao, Storeable}
import play.api.libs.json.Json
import com.decidir.coretx.api.OperationJsonFormats._

import scala.util.{Success, Try}


@Singleton
class BancoRepository @Inject() (jedisPoolProvider: JedisPoolProvider)
  extends RedisDao[Banco](jedisPoolProvider,
    (map => Banco(map("id").toInt,
      map("description"),
      map("code")
    ))) {

  val entityPrefix = "bank:"

}

object BancoRepository {

  def fromJson(jsonString: String): Try[Banco] = {
    val json = Json.parse(jsonString)
    // TODO
    json.validate[Banco].fold(errors => ErrorFactory.uncategorizedFailure(new Exception("TODO")), banco => Success(banco))
  }

}