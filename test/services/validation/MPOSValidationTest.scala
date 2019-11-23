package services.validation

import com.decidir.coretx.api._
import com.decidir.coretx.domain.Site
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.validations.operation.MPOSValidation
import org.mockito.Mockito.when

class MPOSValidationTest extends PlaySpec with MockitoSugar {

  "MPOS Validation" should {

    "validate successfully" in {
      val bandaTarjeta = DatosBandaTarjeta(card_track_1 = Some("cardtrack1"), card_track_2 = Some("cardTrack2"), input_mode = "input_mode")
      implicit val operationResource = OperationResource(datos_banda_tarjeta = Some(bandaTarjeta))

      implicit val site = mock[Site]

      when(site.mensajeriaMPOS) thenReturn Some("S")

      try {
        MPOSValidation.validate
      } catch {
        case e:Exception => fail(e)
      }
      assert(operationResource.datos_banda_tarjeta.contains(bandaTarjeta))
    }

    "throws an Exception when Site allows only MPOS transactions" in {
      implicit val operationResource = OperationResource(datos_banda_tarjeta = None)

      implicit val site = mock[Site]

      when(site.mensajeriaMPOS) thenReturn Some("S")

      try {
        MPOSValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_SITE_MPOS_ENABLED)
        }
        case _ => fail()
      }
    }

    "throws Exception when Site does not allow MPOS transactions" in {
      val bandaTarjeta = DatosBandaTarjeta(card_track_1 = Some("cardtrack1"), card_track_2 = Some("cardTrack2"), input_mode = "input_mode")
      implicit val operationResource = OperationResource(datos_banda_tarjeta = Some(bandaTarjeta))

      implicit val site = mock[Site]

      when(site.mensajeriaMPOS) thenReturn Some("N")

      try {
        MPOSValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_SITE_MPOS_DISABLED)
        }
        case _ => fail()
      }
    }
  }

}
