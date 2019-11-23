package services.cybersource

import javax.inject.Inject

import com.decidir.coretx.domain.{ConfigurationRepository, DecidirConfiguration}
import play.api.Configuration
import services.ConfigurationService

import scala.util.{Failure, Success, Try}
import controllers.MDCHelperTrait

/**
  * Created by ivalek on 4/26/18.
  */
class CSConfiguration @Inject()(config: Configuration, configurationRepository: ConfigurationRepository) extends MDCHelperTrait{

  private val default_timeoutMillis = (config.getLong("sps.cybersource.timeoutMillis").getOrElse(10000l))//millis
  private val default_url = config.getString("sps.cybersource.url").getOrElse(throw new Exception("No se configuro sps.cybersource.url"))
  private val default_retries = 3
  private val max_retries = 3
  private val min_retries = 0

  private def default(param: CSConfigParam) = {
    val value = param match {
      case CSConfigParam.Retries() => Some(default_retries)
      case CSConfigParam.URL() => Some(default_url)
      case CSConfigParam.Timeout() => Some(default_timeoutMillis)
      case _ => None
    }
    value.map(_.toString)
  }

  def get(param: CSConfigParam): Option[String] = {
    configurationRepository.get(param.name) match {
      case Success(Some(value)) => validate(param, value).map(conf => conf.value)
      case Success(None) => default(param)
      case Failure(error) => {
        val value = default(param)
        logger.error(s"Error al obtener clave para ${param.name}. Utilizando default = $value",error)
        value
      }
    }
  }

  def set(param: CSConfigParam, value: String): Try[Boolean] = {
    validate(param, value) match {
      case Some(config) => configurationRepository.set(config)
      case None => {
        logger.warn(s"{ param: ${param.name}, value: ${value} } is not valid")
        Success(false)
      }
    }
  }

  private def validate(param: CSConfigParam, value: String): Option[DecidirConfiguration] = {
    val validValue =  param match {
      case CSConfigParam.Retries() => value.toInt match {
        case min if (min_retries > min) => Some(min_retries)
        case max if (max_retries < max) => Some(max_retries)
        case valid => Some(valid)
      }
      case CSConfigParam.URL() => Some(default_url)
      case CSConfigParam.Timeout() => Some(default_timeoutMillis)
      case _ => None
    }
    validValue.map(v => DecidirConfiguration(param.name, v.toString))
  }


}



trait ConfigurationParam {
  override def toString = name
  val name : String

}

trait CSConfigParam extends ConfigurationParam

object CSConfigParam {

  val timeout = "cs.timeout"
  val url = "cs.url"
  val retries = "cs.retries"

  def create(name: String): Option[CSConfigParam]= {
    name match {
      case `timeout` => Some(Timeout())
      case `url` => Some(URL())
      case `retries` => Some(Retries())
      case _ => None
    }

  }
  case class Timeout() extends CSConfigParam {
    val name = timeout
  }
  case class URL() extends CSConfigParam {
    val name = url
  }
  case class Retries() extends CSConfigParam {
    val name = retries
  }

}
