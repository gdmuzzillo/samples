package services.cybersource

import com.decidir.coretx.api.CustomerInSite
import com.decidir.coretx.domain.DatosMedioPago
import com.decidir.coretx.domain.MedioDePago
import com.decidir.coretx.domain.Site
import com.decidir.coretx.api.FraudDetectionData
import scala.collection.mutable.ArrayBuffer
import scala.xml.Elem
import com.decidir.coretx.api.DatosTitularResource
import com.decidir.coretx.api.CopyPasteCardData

trait CyberSourceVerticalCommons {
  
  protected def mapVerticalCommons(site: Site, 
     vertical: String, 
     medioDePago: MedioDePago, 
     cuotas : Integer, 
     datosTitular: DatosTitularResource, 
     authorized: Option[Boolean],
     fdd: FraudDetectionData,
     addressValidated: Option[String]) = {
    
    val dataCommons = ArrayBuffer[Elem](
        <urn:field1>{site.id}</urn:field1>,
				<urn:field2>{site.description.getOrElse("")}</urn:field2>,	
        <urn:field3>{vertical}</urn:field3>,
        <urn:field4>{medioDePago.descripcion}</urn:field4>,
        <urn:field5>{cuotas.toString}</urn:field5>
    )
    fdd.channel.map(channel => dataCommons += <urn:field6>{channel}</urn:field6>)
    dataCommons ++= mapCustomerInSite(fdd.customer_in_site, fdd.dispatch_method)
    authorized.map(auth => if(!auth) {dataCommons += <urn:mddField id="40">{"N"}</urn:mddField>})
    fdd.copy_paste_card_data.map(copyPasteCardData  => dataCommons ++= mapFraudDetectionForCopyPaste(copyPasteCardData, authorized))
    
    if (medioDePago.id.equals("1")) {
      dataCommons ++= mapVisaTransaction(datosTitular, addressValidated)
    }
    
    dataCommons.toList
  }
  
  private def mapCustomerInSite(customerInSite: Option[CustomerInSite],  dispatchMethod: Option[String]) = {
    List (
      customerInSite.flatMap(_.days_in_site.map(daysInSite => <urn:field7>{daysInSite.toString}</urn:field7>)),
      customerInSite.flatMap(_.is_guest.map(isGuest => <urn:field8>{if(isGuest) {"Y"} else {"N"}}</urn:field8>)),
      customerInSite.flatMap(_.password.map(password => <urn:field9>{password}</urn:field9>)),
      customerInSite.flatMap(_.num_of_transactions.map(nt => <urn:field10>{nt.toString}</urn:field10>)),
      customerInSite.flatMap(_.cellphone_number.map(cn => <urn:field11>{cn}</urn:field11>)),
      dispatchMethod.map(dm => <urn:field13>{dm}</urn:field13>),
      customerInSite.flatMap(_.street.map(street => <urn:mddField id="37">{street}</urn:mddField>)),
      customerInSite.flatMap(_.date_of_birth.map(dateOfBirth => <urn:mddField id="38">{dateOfBirth}</urn:mddField>))
    ).flatten
  }
  
  private def mapVisaTransaction(datosTitular: DatosTitularResource, addressValidated: Option[String]) = {
    List (
  		{datosTitular.tipo_doc.map(td => <urn:mddField id="35">{td}</urn:mddField>)},
      {datosTitular.nro_doc.map(nd => <urn:mddField id="36">{nd}</urn:mddField>)},
			{addressValidated.map(av => <urn:mddField id="39">{av}</urn:mddField>)}    
    ).flatten
  }
  
  private def mapFraudDetectionForCopyPaste(cpCardData: CopyPasteCardData, authorized: Option[Boolean]) = {
    List (
      {authorized.map(auth => <urn:mddField id="40">{if(!auth) "N"}</urn:mddField>)},
      {cpCardData.card_number.map(cardNumber => <urn:mddField id="41">{if(cardNumber) {"S"} else {"N"}}</urn:mddField>)},
      {cpCardData.security_code.map(securityCode => <urn:mddField id="42">{if(securityCode) {"S"} else {"N"}}</urn:mddField>)}
    ).flatten
  }
  
}