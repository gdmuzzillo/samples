package services.validations

import javax.inject.Singleton
import javax.inject.Inject

import scala.concurrent.ExecutionContext
import com.decidir.coretx.api.{ErrorFactory, ErrorMessage, OperationResource, VEPRequest}
import com.decidir.coretx.domain.TipoDocumentoRepository
import controllers.utils.CuitValidator

@Singleton
class AfipValidate @Inject() (context: ExecutionContext,
    tipoDocumentoRepository: TipoDocumentoRepository){
  
  implicit val ec = context   
  
  def validate(operation: OperationResource, vep: VEPRequest): OperationResource = {
    if(!operation.datos_titular.flatMap(_.tipo_doc).contains(tipoDocumentoRepository.retrieve(9).map(_.id).getOrElse(0)))
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "tipo_doc")

    if(operation.datos_titular.flatMap(_.tipo_doc).contains(9) && (
      operation.datos_titular.flatMap(_.nro_doc).isEmpty ||
      !CuitValidator.validaDigitoVerificador(operation.datos_titular.flatMap(_.nro_doc).get)))
//      !operation.datos_titular.flatMap(_.nro_doc.map(_.matches("^\\d{11}$"))).getOrElse(false))
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "nro_doc no es un CUIL valido")

    if(operation.datos_medio_pago.flatMap(_.establishment_name).isEmpty ||
      operation.datos_medio_pago.flatMap(_.establishment_name.map(_.length)).getOrElse(0) > 20)
      throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "leyenda_de_pago")

    operation
  }
  
}