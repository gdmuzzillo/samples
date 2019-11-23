package services.cybersource

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext

import com.decidir.coretx.api.BillingData
import com.decidir.coretx.api.DecisionManagerTravel
import com.decidir.coretx.api.ErrorFactory
import com.decidir.coretx.api.TravelTransactionData
import com.decidir.coretx.api.ValidationError
import com.decidir.coretx.domain.OperationData

import javax.inject.Inject
import com.decidir.coretx.api.DepartureDate

class VerticalTravelValidator @Inject() (implicit context: ExecutionContext) extends CyberSourceValidator {
  
  implicit val ec = context
  
  protected def validate(operationData: OperationData, cyberSourceResponses: ArrayBuffer[ValidationError]) {
    val fdd = operationData.resource.fraud_detection.getOrElse(throw ErrorFactory.missingDataException(List("fraud_detection")))
 		val billTo: BillingData = fdd.bill_to.getOrElse(throw ErrorFactory.missingDataException(List("bill_to")))
    val ttd = fdd.travel_transaction_data.getOrElse(throw ErrorFactory.missingDataException(List("travel_transaction_data")))
    val decision_manager_travel = ttd.decision_manager_travel.getOrElse(throw ErrorFactory.missingDataException(List("decision_manager_travel")))
    val departure_date = decision_manager_travel.departure_date.getOrElse(throw ErrorFactory.missingDataException(List("departure_date")))
    
    billToValidate(billTo, cyberSourceResponses)
    validate(ttd, decision_manager_travel, departure_date, cyberSourceResponses)
    fdd.csmdds.map(csmdds => validate(csmdds, List(17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27), cyberSourceResponses))
  }
  
  protected def billToValidate(billTo: BillingData, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (billTo.customer_id.getOrElse("").isEmpty) addErrorCode(10103, cyberSourceResponses)
  }
  
  private def validate(ttd: TravelTransactionData, decision_manager_travel: DecisionManagerTravel, departure_date: DepartureDate, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    travelRequiredValidate(ttd, decision_manager_travel, departure_date, cyberSourceResponses)
    travelFormatValidate(ttd, decision_manager_travel, departure_date, cyberSourceResponses)
  }

  private def travelRequiredValidate(ttd: TravelTransactionData, decision_manager_travel: DecisionManagerTravel, departure_date: DepartureDate, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    if (decision_manager_travel.complete_route.getOrElse("").isEmpty) addErrorCode(10170, cyberSourceResponses)
    if (decision_manager_travel.journey_type.getOrElse("").isEmpty) addErrorCode(10171, cyberSourceResponses)
    if (departure_date.departure_time.isEmpty) addErrorCode(10172, cyberSourceResponses)
    if (ttd.airline_number_of_passengers.isEmpty) addErrorCode(10180, cyberSourceResponses)
    if (ttd.reservation_code.getOrElse("").isEmpty) addErrorCode(10517, cyberSourceResponses)
    if (ttd.third_party_booking.isEmpty) addErrorCode(10518, cyberSourceResponses)
  }
  
  private def travelFormatValidate(ttd: TravelTransactionData, decision_manager_travel: DecisionManagerTravel, departure_date: DepartureDate, cyberSourceResponses: ArrayBuffer[ValidationError]) = {
    decision_manager_travel.complete_route.map(deliveryType => sizeValidate(deliveryType, 255, "decision_manager_travel.complete_route", cyberSourceResponses))
    decision_manager_travel.journey_type.map(journeyType => sizeValidate(journeyType, 255, "decision_manager_travel.journey_type", cyberSourceResponses))
    departure_date.departure_zone.map(departureZone => sizeValidate(departureZone, 255, "departure_date", cyberSourceResponses))
    ttd.reservation_code.map(reservationCode => sizeValidate(reservationCode, 255, "reservation_code", cyberSourceResponses))
    ttd.departure_city.map(departureCity => sizeValidate(departureCity, 255, "departure_city", cyberSourceResponses))
    ttd.final_destination_city.map(finalDestinationCity => sizeValidate(finalDestinationCity, 255, "final_destination_city", cyberSourceResponses))
    ttd.frequent_flier_number.map(frequentFlierNumber => sizeValidate(frequentFlierNumber, 255, "frequent_flier_number", cyberSourceResponses))
    ttd.class_of_service.map(classOfService => sizeValidate(classOfService, 255, "class_of_service", cyberSourceResponses))
//    ttd.day_of_week_of_flight.map(dayOfWeekOfFlight => sizeValidate(dayOfWeekOfFlight.toString, 255, 0, cyberSourceResponses))
//    ttd.week_of_year_of_flight.map(weekOfYearOfFlight => sizeValidate(weekOfYearOfFlight.toString, 255, 0, cyberSourceResponses))
    ttd.airline_code.map(airlineCode => sizeValidate(airlineCode, 255, "airline_code", cyberSourceResponses))
    ttd.code_share.map(codeShare => sizeValidate(codeShare, 255, "code_share", cyberSourceResponses))
    ttd.airline_number_of_passengers.map(airlineNumberOfPassengers => sizeValidate(airlineNumberOfPassengers.toString, 255, "airline_number_of_passengers", cyberSourceResponses))
  }
    
}