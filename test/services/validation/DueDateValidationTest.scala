package services.validation

import com.decidir.coretx.api._
import com.decidir.coretx.domain.{OfflinePayment, Site}
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.validations.operation.{DueDateValidation, HashValidation}

class DueDateValidationTest extends PlaySpec with MockitoSugar {

  "DueDateValidation" should {
    "Validate successFully when offline is empty" in {
      implicit val site = mock[Site]
      implicit val op = OperationResource(datos_offline = None)

      try {
        DueDateValidation.validate
      } catch {
        case e: Exception => fail(e)
      }
      assert(op.datos_offline.isEmpty)
    }
    "Validate successFully when due date is correct" in {
      implicit val site = mock[Site]
      val datosOffline = mock[OfflinePayment]
      val datosMedioPago = mock[DatosMedioPagoResource]
      implicit val op = OperationResource(datos_offline = Some(datosOffline), datos_medio_pago = Some(datosMedioPago))

      when(datosOffline.fechavto) thenReturn Some("290319 000000")
      when(datosMedioPago.medio_de_pago) thenReturn Some(41)

      try {
        DueDateValidation.validate
      } catch {
        case e: Exception => fail(e)
      }
      assert(op.datos_offline.nonEmpty)
    }

    "throws Error when due date is wrong format" in {
      implicit val site = mock[Site]
      val datosOffline = mock[OfflinePayment]
      val datosMedioPago = mock[DatosMedioPagoResource]
      implicit val op = OperationResource(datos_offline = Some(datosOffline), datos_medio_pago = Some(datosMedioPago))

      when(datosOffline.fechavto) thenReturn Some("29/03/2019")
      when(datosMedioPago.medio_de_pago) thenReturn Some(41)

      try {
        DueDateValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == "Invalid format")
          assert(param == "invoice_expiration")
        }
      }
      assert(op.datos_offline.nonEmpty)
    }
  }
}
