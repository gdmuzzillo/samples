package services.validations

import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import controllers.MDCHelperTrait
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.ErrorMessage
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.domain.Site

@Singleton
class InstallmentsValidator @Inject() (context: ExecutionContext)  extends MDCHelperTrait {
  implicit val ec = context




  def validateInstallments(op : OperationResource, site: Site) = {
    op.datos_site.foreach(datosSite => datosSite.id_modalidad match {
      case Some("S") => {
    	  logger.debug("Distrubuted payment")
    	  site.montoPorcent match {
    	    case "M" => {//Distribuida por monto
    	      op.cuotas.foreach(installment => validateInstallment(installment))//Validacion que sera removida a futuro. Solo para q se muestre en en SAC (No sirve)
    	      
      	    val installments = op.sub_transactions.flatMap(_.installments)
        	  if (installments.isEmpty) {
        	    validateInstallment(op.cuotas.getOrElse({
                  logger.error("required installments, distributed by amount")
                  throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_INSTALLMENTS)
                })
              )
        	  } else {
        	    installments.foreach(installment => validateInstallmentWithIndex(installment, installments.indexOf(installment)))
        	  }
      	    if (installments.length != op.sub_transactions.length) {
              logger.error("required installments, some subpayment dont have set installment")
              throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_INSTALLMENTS)
            }
      	  }
    	    case _ => { //Distribuida por porcentaje
            validateInstallment(op.cuotas.getOrElse({
                logger.error("required installments, distributed by percent")
                throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_INSTALLMENTS)
      	      })
            )
    	    }
    	  }
      }
      case _ => {
        op.cuotas match {
          case None => {
            logger.error("required installments, single payment")
            throw ErrorFactory.validationException(ErrorMessage.PARAM_REQUIRED, ErrorMessage.DATA_INSTALLMENTS)
          }
          case Some(installment) => validateInstallment(installment)
        }
      }
    })
  }
  
  /* Validacion para los formularios de Webtx
   * */
  def validateInstallmentsAmount(op : OperationResource, site: Site) = {
    op.datos_site.foreach(datosSite => datosSite.id_modalidad match {
      case Some("S") => {
    	  logger.debug("Distrubuted payment")
    	  op.cuotas.foreach(installment => validateInstallment(installment))
    	  site.montoPorcent match {
    	    case "M" => op.sub_transactions.foreach(st => st.installments.foreach(installment => validateInstallment(installment)))
    	    case _ =>
    	  }
      }
      case _ => {
        op.cuotas match {
          case Some(installment) => {
        	  logger.debug("Single payment")
            validateInstallment(installment)
          }
          case None =>
        }
      }
    })
  }
  
  
  private def validateInstallment(installment : Int) = {
    if (installment < 1 || installment > 99) {
      logger.error(s"invalid installments: ${installment}")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_INSTALLMENTS)
    }
  }

  def validateInstallmentWithIndex(installment: Int, index: Int) = {
    if (installment < 1 || installment > 99) {
      logger.error(s"In sub_payments.[${index}]. Invalid installments: ${installment}")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, s"sub_payments.[${index}]." + ErrorMessage.DATA_INSTALLMENTS)
    }
  }

 def validateInstallmentType(installment : Int, installentExpected: Int) = {
    if (installment != installentExpected) {
      logger.error(s"invalid installments: ${installment} - installments expected: ${installentExpected}")
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, ErrorMessage.DATA_INSTALLMENTS)
    }
  }
}