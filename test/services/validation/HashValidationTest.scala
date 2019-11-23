package services.validation

import com.decidir.coretx.api._
import com.decidir.coretx.domain.{HashConfiguration, Site}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.validations.operation.HashValidation
import org.mockito.Mockito.when

class HashValidationTest  extends PlaySpec with MockitoSugar {

  "HashValidation" should {
    "Validate successFully" in {
      implicit val site = mock[Site]
      val origin = mock[Requester]
      val hash = mock[HashConfiguration]

      when(origin.app) thenReturn Some("NOWEBTX")


      implicit val op = OperationResource(origin = Some(origin))

      try {
        HashValidation.validate
      } catch {
        case e: Exception => fail(e)
      }
      assert(op.origin.contains(origin))
    }

    "Throws Exception when site use Hash and origin don't use it" in {
      implicit val site = mock[Site]
      val origin = mock[Requester]
      val hash = mock[HashConfiguration]

      when(origin.app) thenReturn Some("WEBTX")
      when(origin.useHash) thenReturn Some(false)
      when(site.hashConfiguration) thenReturn hash
      when(hash.useHash) thenReturn true

      implicit val op = OperationResource(origin = Some(origin))

      try {
        HashValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.PARAM_REQUIRED)
          assert(param == "HASH")
        }
        case _ => fail()
      }
    }
  }
}
