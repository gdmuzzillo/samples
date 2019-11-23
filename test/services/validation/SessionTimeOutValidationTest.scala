package services.validation

import java.util.Date

import com.decidir.coretx.api._
import com.decidir.coretx.domain.{HashConfiguration, Site}
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.validations.operation.SessionTimeOutValidation

class SessionTimeOutValidationTest extends PlaySpec with MockitoSugar {

  "SessionTimeOutValidation" should {
    "validate successfully" in {
      val origin = mock[Requester]

      implicit val op = OperationResource(origin = Some(origin), creation_date = Some(new Date()))
      implicit val site = mock[Site]
      val hash = mock[HashConfiguration]

      when(origin.app) thenReturn Some("WEBTX")
      when(site.timeoutCompra) thenReturn 1800000

      try {
        SessionTimeOutValidation.validate
      } catch {
        case e:Exception => fail()
      }
      assert(op.origin.nonEmpty && op.creation_date.nonEmpty)
    }

    "throws error when session is expired because the timeoutCompra is invalid" in {
      val origin = mock[Requester]

      implicit val op = OperationResource(origin = Some(origin), creation_date = Some(new Date()))
      implicit val site = mock[Site]
      val hash = mock[HashConfiguration]

      when(origin.app) thenReturn Some("WEBTX")
      when(site.timeoutCompra) thenReturn 0

      try {
        SessionTimeOutValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == "OperationResource")
          assert(param == "")
        }
        case _ => fail()
      }
    }
  }
}
