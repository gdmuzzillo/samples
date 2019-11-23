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

@Singleton
class CyberSourceVerticalRetail @Inject() (context: ExecutionContext) extends CyberSourceVerticalCommons with ProductMapping {
  
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
    
    val rtd = fdd.retail_transaction_data.get //TODO
    
    val itemsXml = mapItems(rtd.items)
    val commons = mapVerticalCommons(site, vertical, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated)
    
    val mdd =     
      <urn:merchantDefinedData>
        {commons.map(dataCustomerInSite => dataCustomerInSite)}
        {rtd.days_to_delivery.map(dd => <urn:mddField id="12">{dd}</urn:mddField>).getOrElse(Nil)}
        {rtd.tax_voucher_required.map(tvr => <urn:mddField id="14">{tvr}</urn:mddField>).getOrElse(Nil)}
        {rtd.customer_loyality_number.map(cln => <urn:mddField id="15">{cln}</urn:mddField>).getOrElse(Nil)}
        {rtd.coupon_code.map(cc => <urn:mddField id="16">{cc}</urn:mddField>).getOrElse(Nil)}
				{fdd.csmdds.map(csmdds => csmdds.map(csmdd => <urn:mddField id={csmdd.code.toString}>{csmdd.description}</urn:mddField>)).getOrElse(Nil)}
  		</urn:merchantDefinedData> 
				
    (mdd, itemsXml)
  }
  
  def mapShipTo(shipingTo: ShipingData) = { 
      <urn:shipTo>
        {shipingTo.first_name.map(firstName => <urn:firstName>{firstName}</urn:firstName>).getOrElse(Nil)}
        {shipingTo.last_name.map(lastName => <urn:lastName>{lastName}</urn:lastName>).getOrElse(Nil)}
        {shipingTo.street1.map(street1 => <urn:street1>{street1}</urn:street1>).getOrElse(Nil)}
        {shipingTo.street2.map(street2 => <urn:street2>{street2}</urn:street2>).getOrElse(Nil)}
        {shipingTo.city.map(city => <urn:city>{city}</urn:city>).getOrElse(Nil)}
        {shipingTo.state.map(state => <urn:state>{state}</urn:state>).getOrElse(Nil)}
        {shipingTo.postal_code.map(postalCode => <urn:postalCode>{postalCode}</urn:postalCode>).getOrElse(Nil)}
        {shipingTo.country.map(country => <urn:country>{country}</urn:country>).getOrElse(Nil)}
        {shipingTo.phone_number.map(phoneNumber => <urn:phoneNumber>{phoneNumber}</urn:phoneNumber>).getOrElse(Nil)}
        {shipingTo.email.map(email => <urn:email>{email}</urn:email>).getOrElse(Nil)}
      </urn:shipTo>  
  }

}