package services.cybersource

import javax.inject.Singleton
import javax.inject.Inject

import scala.concurrent.ExecutionContext
import com.decidir.coretx.domain.DatosMedioPago
import com.decidir.coretx.domain.MedioDePago
import com.decidir.coretx.domain.Site
import com.decidir.coretx.api._

import scala.xml.NodeSeq

@Singleton
class CyberSourceVerticalRetailTP @Inject() (context: ExecutionContext) extends CyberSourceVerticalCommons with ProductMapping {

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

    /**
      * Se eliminan momentaneamente las validaciones de TP para CS
      */
    //val rttpd: RetailTPTransactionData = fdd.retailtp_transaction_data.getOrElse(throw ErrorFactory.missingDataException(List("retailtp_transaction_data")))
    val rttpd: RetailTPTransactionData = fdd.retailtp_transaction_data.getOrElse(RetailTPTransactionData())

    val itemsXml = mapItems(rttpd.items.getOrElse(Nil))
    val commons = mapVerticalCommons(site, vertical, medioDePago, cuotas, datosTitular, authorized, fdd, addressValidated)

    val mdd =
      <urn:merchantDefinedData>
        {commons.map(dataCustomerInSite => dataCustomerInSite)}
        {rttpd.days_to_delivery.map(dd => <urn:mddField id="12">{dd}</urn:mddField>).getOrElse(Nil)}
        {rttpd.tax_voucher_required.map(tvr => <urn:mddField id="14">{tvr}</urn:mddField>).getOrElse(Nil)}
        {rttpd.customer_loyality_number.map(cln => <urn:mddField id="15">{cln}</urn:mddField>).getOrElse(Nil)}
        {rttpd.coupon_code.map(cc => <urn:mddField id="16">{cc}</urn:mddField>).getOrElse(Nil)}
        {rttpd.account.flatMap(ac => ac.id.map(ai => <urn:mddField id="80">{ai}</urn:mddField>)).getOrElse(Nil)}
        {rttpd.account.flatMap(ac => ac.name.map(an => <urn:mddField id="81">{an}</urn:mddField>)).getOrElse(Nil)}
        {rttpd.account.flatMap(ac => ac.category.map(ac => <urn:mddField id="82">{ac}</urn:mddField>)).getOrElse(Nil)}
        {rttpd.account.flatMap(ac => ac.antiquity.map(aa => <urn:mddField id="83">{aa}</urn:mddField>)).getOrElse(Nil)}
        {rttpd.account.flatMap(ac => ac.`type`.map(at => <urn:mddField id="84">{at}</urn:mddField>)).getOrElse(Nil)}
        {rttpd.double_factor_tp.map(ttp => <urn:mddField id="85">{ttp}</urn:mddField>).getOrElse(Nil)}
        {rttpd.wallet_account.flatMap(wa => wa.id.map( wid => <urn:mddField id="86">{wid}</urn:mddField>)).getOrElse(Nil)}
        {rttpd.wallet_account.flatMap(wa => wa.antiquity.map( wan => <urn:mddField id="87">{wan}</urn:mddField>)).getOrElse(Nil)}
        {rttpd.enroled_card_quantity.map(eca => <urn:mddField id="88">{eca}</urn:mddField>).getOrElse(Nil)}
        {rttpd.payment_method_risk_level.map(pmrl => <urn:mddField id="89">{pmrl}</urn:mddField>).getOrElse(Nil)}
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