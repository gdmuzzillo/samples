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
class CyberSourceVerticalDigitalGoods @Inject() (context: ExecutionContext) extends CyberSourceVerticalCommons with ProductMapping {
  
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
    
    val dgtd = fdd.digital_goods_transaction_data.get //TODO
    
    val itemsXml = mapItems(dgtd.items)
    val commons = mapVerticalCommons(site, vertical, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated)
    
    val mdd =     
      <urn:merchantDefinedData>
        {commons.map(dataCustomerInSite => dataCustomerInSite)}
				{dgtd.delivery_type.map(deliveryType => <urn:mddField id="32">{deliveryType}</urn:mddField>).getOrElse(Nil)}
				{fdd.csmdds.map(csmdds => csmdds.map(csmdd => <urn:mddField id={csmdd.code.toString}>{csmdd.description}</urn:mddField>)).getOrElse(Nil)}
  		</urn:merchantDefinedData> 
				
    (mdd, itemsXml)
  }

}