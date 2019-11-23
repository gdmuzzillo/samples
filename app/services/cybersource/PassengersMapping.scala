package services.cybersource

import com.decidir.coretx.api.Item
import com.decidir.coretx.api.Passenger

trait PassengerstMapping {
    
  protected def mapPassengers(passengers: List[Passenger]) = {
    passengers.zipWithIndex.map(p => mapPassenger(p._1, p._2))
  }
  
  //TODO: revisar unit price
  private def mapPassenger(passenger: Passenger, ndx: Int) = {  
    <urn:item id={ndx.toString}>  
			{<urn:unitPrice>0</urn:unitPrice>}
			{passenger.first_name.map(fn => <urn:passengerFirstName>{fn}</urn:passengerFirstName>).getOrElse(Nil)}
			{passenger.last_name.map(ln => <urn:passengerLastName>{ln}</urn:passengerLastName>).getOrElse(Nil)}
			{passenger.passport_id.map(pid => <urn:passengerID>{pid}</urn:passengerID>).getOrElse(Nil)}
			{passenger.passenger_status.map(ps => <urn:passengerStatus>{ps}</urn:passengerStatus>).getOrElse(Nil)}
			{passenger.passenger_type.map(pt => <urn:passengerType>{pt.toString}</urn:passengerType>).getOrElse(<urn:passengerType>MIL</urn:passengerType>)}
			{passenger.email.map(e => <urn:passengerEmail>{e}</urn:passengerEmail>).getOrElse(Nil)}
			{passenger.phone.map(p => <urn:passengerPhone>{p}</urn:passengerPhone>).getOrElse(Nil)}
		</urn:item>
  }
  
}