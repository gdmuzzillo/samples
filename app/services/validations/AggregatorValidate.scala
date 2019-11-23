package services.validations

import scala.concurrent.ExecutionContext
import javax.inject.Inject
import javax.inject.Singleton
import com.decidir.coretx.api.OperationResource
import com.decidir.coretx.domain._
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.ErrorMessage

@Singleton
class AggregatorValidate @Inject() (context: ExecutionContext,
    medioDePagoRepository: MedioDePagoRepository){
 
  implicit val ec = context 

  def validate(site: Site, operation: OperationResource, aggregator: Aggregator): OperationResource = {
//    SE COMENTAN VALIDACIONES. La marca sera la encargada de validar si es correcto o no el request.
//    TAREA: http://jira.prismamp.com.ar:8080/browse/DECD-2184
//
//    if(site.agregador == "S" && operation.datos_medio_pago.flatMap(_.medio_de_pago).isDefined){
//      val isVisa = medioDePagoRepository.retrieve(operation.datos_medio_pago.get.medio_de_pago.get).map(_.backend).contains(6)
//
//      if(aggregator.indicator.isEmpty)
//        throw ErrorFactory.missingDataException(List("aggregate_data.indicator"))
//
//      aggregator.indicator.get match {
//        case "0" | "1" | "2" => {}
//        case other => throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "aggregate_data.indicator")
//      }
//
//      if(aggregator.identification_number.isEmpty)
//        throw ErrorFactory.missingDataException(List("aggregate_data.identification_number"))
//
//      if(!aggregator.identification_number.get.matches("^[0-9]{11}$"))
//        throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "aggregate_data.identification_number")
//
//      if(isVisa){
//
//        aggregator.getRequiredVisaInfoMap.foreach(el =>
//          if(el._2.isEmpty)
//            throw ErrorFactory.missingDataException(List("aggregate_data." + el._1))
//        )
//
//        if(aggregator.bill_to_pay.isDefined && !aggregator.bill_to_pay.get.matches("^[aA-zZ0-9]{1,12}$"))
//          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "aggregate_data.bill_to_pay")
//
//        if(aggregator.bill_to_refund.isDefined && !aggregator.bill_to_refund.get.matches("^[aA-zZ0-9]{1,12}$"))
//          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "aggregate_data.bill_to_refund")
//
//        if(!aggregator.merchant_name.get.matches("^[aA-zZ0-9\\s\\/]{1,20}$"))
//          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "aggregate_data.merchant_name")
//
//        if(!aggregator.street.get.matches("^[aA-zZ0-9\\s]{1,20}$"))
//          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "aggregate_data.street")
//
//        if(!aggregator.number.get.matches("^[aA-zZ0-9\\s]{1,6}$"))
//          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "aggregate_data.number")
//
//        if(!aggregator.postal_code.get.matches("^[aA-zZ0-9]{1,8}$"))
//          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "aggregate_data.postal_code")
//
//        if(!aggregator.category.get.matches("^[aA-zZ0-9]{1,5}$"))
//          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "aggregate_data.category")
//
//        if(aggregator.channel.isDefined && !aggregator.channel.get.matches("^[aA-zZ0-9]{1,3}$"))
//          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "aggregate_data.channel")
//
//        if(aggregator.geographic_code.isDefined && !aggregator.geographic_code.get.matches("^[aA-zZ0-9]{1,5}$"))
//          throw ErrorFactory.validationException(ErrorMessage.INVALID_PARAM, "aggregate_data.geographic_code")
//
//        operation
//      } else {
//        operation.copy(
//          aggregate_data = Some(Aggregator(
//            indicator = operation.aggregate_data.flatMap(_.indicator),
//            identification_number = operation.aggregate_data.flatMap(_.identification_number)
//          )))
//      }
//    } else
      operation
  }

}