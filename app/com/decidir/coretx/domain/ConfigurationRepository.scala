package com.decidir.coretx.domain

import javax.inject.Inject

import com.decidir.coretx.utils.{JedisPoolProvider, JedisUtils}
import play.api.libs.json.Json
import redis.clients.jedis.Jedis
import redis.clients.util.Pool

import scala.util.{Success, Try}

/**
  * Created by ivalek on 4/26/18.
  */
class ConfigurationRepository @Inject() (jedisPoolProvider: JedisPoolProvider) extends JedisUtils {

  val key = "configuration:decidir"
  override def jedisPool: Pool[Jedis] = jedisPoolProvider.get

  def retrieveAll = Try {
    doWithRedis { redis =>
      redis.hgetAll(key)
    }
  }

  def get(id: String) = Try {
    doWithRedis { redis =>
      redis.hget(key, id)
    }
  }.map(value => Option(value))

  def set(conf: DecidirConfiguration) = Try {
    doWithRedis { redis =>
      redis.hset(key, conf.id, conf.value)
    }
  }.map(res => res != 0)

  def remove(id: String) = Try {
    doWithRedis { redis =>
      redis.hdel(key, id)
    }
  }.map(res => res != 0)
}

object DecidirConfiguration {
  implicit val decidirConfigurationReads = Json.reads[DecidirConfiguration]
  implicit val decidirConfigurationWrites = Json.writes[DecidirConfiguration]
}
case class DecidirConfiguration(id: String, value: String)
