package services

import javax.inject.{Inject, Singleton}

import com.decidir.coretx.domain.{ConfigurationRepository, DecidirConfiguration}
import services.cybersource.{CSConfigParam, CSConfiguration}

import scala.util.Try

/**
  * Created by ivalek on 4/26/18.
  */
@Singleton
class ConfigurationService @Inject() (configurationRepository: ConfigurationRepository,
                                      cSConfiguration: CSConfiguration) {

  def get(id: String): Try[Option[String]] = {
    configurationRepository.get(id)
  }

  def put(conf: DecidirConfiguration): Try[Boolean] = {
    //TODO refactorizar
    CSConfigParam.create(conf.id) match {
      case Some(param) => cSConfiguration.set(param, conf.value)
      case None => configurationRepository.set(conf)
    }

  }

  def remove(id: String): Try[Boolean] = {
    configurationRepository.remove(id)
  }

}
