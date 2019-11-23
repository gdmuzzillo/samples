package services.cybersource

import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import com.decidir.coretx.domain.DatosMedioPago
import com.decidir.coretx.domain.MedioDePago
import com.decidir.coretx.domain.Site
import com.decidir.coretx.api.DatosTitularResource
import scala.xml.NodeSeq
import com.decidir.coretx.api.ShipingData
import com.decidir.coretx.api.FraudDetectionData
import com.decidir.coretx.api.DecisionManagerTravel
import com.decidir.coretx.api.DepartureDate
import java.util.TimeZone
import java.text.SimpleDateFormat

@Singleton
class CyberSourceVerticalTravel @Inject() (context: ExecutionContext) extends CyberSourceVerticalCommons with PassengerstMapping {
  
  implicit val ec = context
  
  
  def mapVertical(site: Site, 
     vertical: String, 
     datosMedioDePago: DatosMedioPago, 
     medioDePago: MedioDePago, 
     cuotas : Integer, 
     datosTitular: DatosTitularResource, 
     authorized: Option[Boolean],
     fdd: FraudDetectionData,
     addressValidated: Option[String]): (NodeSeq, NodeSeq) = {
    
    val ttd = fdd.travel_transaction_data.get //TODO
    val dmt = ttd.decision_manager_travel.get
    val passengersXml = mapPassengers(ttd.passengers)
    //TODO resolver campos mdd 41 y 42 para aerolineas
    val commons = mapVerticalCommons(site, vertical, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated)
   
    val data = {    
      <urn:decisionManager>
				<urn:travelData>
          {dmt.departure_date.map(dt => <urn:departureDateTime>{formatDepartureDate(dt)}</urn:departureDateTime>).getOrElse(Nil)}
          {dmt.complete_route.map(cr => <urn:completeRoute>{cr}</urn:completeRoute>).getOrElse(Nil)}
          {dmt.journey_type.map(jt => <urn:journeyType>{jt}</urn:journeyType>).getOrElse(Nil)}
				</urn:travelData>
			</urn:decisionManager>
      <urn:merchantDefinedData>
        {commons.map(dataCustomerInSite => dataCustomerInSite)}
        {ttd.reservation_code.map(dd => <urn:mddField id="17">{dd}</urn:mddField>).getOrElse(Nil)}
        {ttd.third_party_booking.map(tpb => <urn:mddField id="18">{if(tpb) "S" else "N"}</urn:mddField>).getOrElse(Nil)}
        {ttd.departure_city.map(dc => <urn:mddField id="19">{dc}</urn:mddField>).getOrElse(Nil)}
        {ttd.final_destination_city.map(fdc => <urn:mddField id="20">{fdc}</urn:mddField>).getOrElse(Nil)}
        {ttd.international_flight.map(inf => <urn:mddField id="21">{if(inf) "S" else "N"}</urn:mddField>).getOrElse(Nil)}
        {ttd.frequent_flier_number.map(ffn => <urn:mddField id="22">{ffn}</urn:mddField>).getOrElse(Nil)}
        {ttd.class_of_service.map(cos => <urn:mddField id="23">{cos}</urn:mddField>).getOrElse(Nil)}
        {ttd.day_of_week_of_flight.map(dow => <urn:mddField id="24">{dow.toString}</urn:mddField>).getOrElse(Nil)}
        {ttd.week_of_year_of_flight.map(woy => <urn:mddField id="25">{woy.toString}</urn:mddField>).getOrElse(Nil)}
        {ttd.airline_code.map(ac => <urn:mddField id="26">{ac}</urn:mddField>).getOrElse(Nil)}
        {ttd.code_share.map(cs => <urn:mddField id="27">{cs}</urn:mddField>).getOrElse(Nil)}
        {fdd.csmdds.map(csmdds => csmdds.map(csmdd => <urn:mddField id={csmdd.code.toString}>{csmdd.description}</urn:mddField>)).getOrElse(Nil)}
    	</urn:merchantDefinedData>

    }
    
    (data , passengersXml)
  }
  
  def mapAirlineNumberOfPassengers(anop: Int) = {
      <urn:airlineData>
        {<urn:numberOfPassengers>{anop.toString}</urn:numberOfPassengers>}
			</urn:airlineData>
  }
  
  def formatDepartureDate(departureDate: DepartureDate) = {
      val tz = departureDate.departure_zone.getOrElse("GMT");
      val df = new SimpleDateFormat("yyyy-MM-dd HH:mm z");
      df.setTimeZone(TimeZone.getTimeZone(tz));        
      departureDate.departure_time.map(df.format).orNull
  }
}