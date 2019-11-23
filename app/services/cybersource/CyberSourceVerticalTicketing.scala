package services.cybersource

import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import com.decidir.coretx.domain.CyberSourceConfiguration
import com.decidir.coretx.domain.DatosMedioPago
import com.decidir.coretx.domain.MedioDePago
import com.decidir.coretx.domain.Site
import com.decidir.coretx.api.DatosTitularResource
import scala.xml.NodeSeq
import com.decidir.coretx.api.FraudDetectionData
import com.decidir.coretx.api.CustomerInSite

@Singleton
class CyberSourceVerticalTicketing @Inject() (context: ExecutionContext) extends CyberSourceVerticalCommons with ProductMapping {
  
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
    
    val ttd = fdd.ticketing_transaction_data.get //TODO
    
    val itemsXml = mapItems(ttd.items)
    val commons = mapVerticalCommons(site, vertical, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated)
    
    val mdd =     
      <urn:merchantDefinedData>
        {commons.map(dataCustomerInSite => dataCustomerInSite)}
        {ttd.days_to_event.map(daysToEvent => <urn:mddField id="33">{daysToEvent}</urn:mddField>).getOrElse(Nil)}			
				{ttd.delivery_type.map(deliveryType => <urn:mddField id="34">{deliveryType}</urn:mddField>).getOrElse(Nil)}
				{fdd.csmdds.map(csmdds => csmdds.map(csmdd => <urn:mddField id={csmdd.code.toString}>{csmdd.description}</urn:mddField>)).getOrElse(Nil)}
  		</urn:merchantDefinedData> 
				
    (mdd, itemsXml)
  }

}