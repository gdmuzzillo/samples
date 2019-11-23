package services.validations.operation

import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.domain.Site
import org.slf4j.LoggerFactory

trait Validation {

  val logger = LoggerFactory.getLogger("Validation")

  def validate(implicit op: OperationResource, site: Site)

}