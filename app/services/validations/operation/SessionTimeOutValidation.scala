package services.validations.operation

import java.util.Date

import com.decidir.coretx.api.{ApiException, ErrorFactory, OperationResource}
import com.decidir.coretx.domain.Site

object SessionTimeOutValidation extends Validation {

  override def validate(implicit op: OperationResource, site: Site) = {
    if(op.origin.isDefined && op.origin.get.app.contains("WEBTX")){
      val actual = new Date().getTime
      val elapsedTime =  actual - op.creation_date.map(_.getTime).getOrElse(0l)
      val timeOut =  if(site.timeoutCompra > 0) site.timeoutCompra * 1000 else 1800000 //30 minutos por default
      if(elapsedTime > timeOut){
        logger.error(s"Session expired - Elapsed time >>> $elapsedTime")
        throw ApiException(ErrorFactory.notFoundError("OperationResource", op.nro_operacion.getOrElse("")))
      }
    }
  }
}
