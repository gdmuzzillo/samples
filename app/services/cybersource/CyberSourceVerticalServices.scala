package services.cybersource

import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import com.decidir.coretx.domain.DatosMedioPago
import com.decidir.coretx.domain.MedioDePago
import com.decidir.coretx.domain.Site
import com.decidir.coretx.api.DatosTitularResource
import scala.xml.NodeSeq
import com.decidir.coretx.api.FraudDetectionData

@Singleton
class CyberSourceVerticalServices @Inject() (context: ExecutionContext) extends CyberSourceVerticalCommons with ProductMapping {

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

    val std = fdd.services_transaction_data.get

    val itemsXml = mapItems(std.items)
    val commons = mapVerticalCommons(site, vertical, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated)

    val mdd =
      <urn:merchantDefinedData>
        {commons.map(dataCustomerInSite => dataCustomerInSite)}
        {std.service_type.map(st => <urn:mddField id="28">{st}</urn:mddField>).getOrElse(Nil)}
        {std.reference_payment_service1.map(rps1 => <urn:mddField id="29">{rps1}</urn:mddField>).getOrElse(Nil)}
        {std.reference_payment_service2.map(rps2 => <urn:mddField id="30">{rps2}</urn:mddField>).getOrElse(Nil)}
        {std.reference_payment_service3.map(rps3 => <urn:mddField id="31">{rps3}</urn:mddField>).getOrElse(Nil)}
        {fdd.csmdds.map(csmdds => csmdds.map(csmdd => <urn:mddField id={csmdd.code.toString}>{csmdd.description}</urn:mddField>)).getOrElse(Nil)}
      </urn:merchantDefinedData>

    (mdd, itemsXml)
  }

}
