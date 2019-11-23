package services.validation

import com.decidir.coretx.api._
import com.decidir.coretx.domain.Site
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.validations.operation.IPValidation
import org.mockito.Mockito.when

class IPValidationTest extends PlaySpec with MockitoSugar {
  "IPValidation" should {
    "validate successfully" in {
      val datosTitular = mock[DatosTitularResource]
      implicit val operationResource = OperationResource(datos_titular = Some(datosTitular))
      implicit val site = mock[Site]

      when(datosTitular.ip) thenReturn Some("192.168.1.1")

      try {
        IPValidation.validate
      } catch {
        case e:Exception => fail(e)
      }
      assert(operationResource.datos_titular.contains(datosTitular))
    }

    "throws Error when Ip is empty" in {
      val datosTitular = mock[DatosTitularResource]
      implicit val operationResource = OperationResource(datos_titular = Some(datosTitular))
      implicit val site = mock[Site]

      when(datosTitular.ip) thenReturn None

      try {
        IPValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.PARAM_REQUIRED)
          assert(param == ErrorMessage.IP)
          assert(operationResource.datos_titular.nonEmpty)
        }
        case _ => fail()
      }
    }

    "throws Error when Ip is wrong" in {
      val datosTitular = mock[DatosTitularResource]
      implicit val operationResource = OperationResource(datos_titular = Some(datosTitular))
      implicit val site = mock[Site]

      when(datosTitular.ip) thenReturn Some("192.168.")

      try {
        IPValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.IP)
        }
        case _ => fail()
      }

      when(datosTitular.ip) thenReturn Some("ip")

      try {
        IPValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.IP)
        }
        case _ => fail()
      }
    }
  }
}
