package services.validation

import com.decidir.coretx.api._
import com.decidir.coretx.domain.Site
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.validations.operation.OperationNumberValidation

class OperationNumberValidationTest extends PlaySpec with MockitoSugar {

  "OperationNumberValidation" should {
    "Validate successFully" in {
      val nroOperacion = "1234"
      implicit val op = OperationResource(nro_operacion = Some(nroOperacion))
      implicit val site = mock[Site]

      try {
        OperationNumberValidation.validate
      } catch {
        case e: Exception => fail(e)
      }
      assert(op.nro_operacion.contains(nroOperacion))
    }

    "Throw Exception when NroOperacion is None" in {
      implicit val op = OperationResource(nro_operacion = None)
      implicit val site = mock[Site]

      try {
        OperationNumberValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.PARAM_REQUIRED)
          assert(param == "site_transaction_id")
          assert(op.nro_operacion.isEmpty)
        }
        case _ => fail()
      }
    }

    "Throw Exception when NroOperacion length is invalid" in {
      val nroOperacion = "12345678901234567890123456789012345678901234567890"
      implicit val op = OperationResource(nro_operacion = Some(nroOperacion))
      implicit val site = mock[Site]

      try {
        OperationNumberValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == "site_transaction_id")
          assert(op.nro_operacion.nonEmpty && op.nro_operacion.contains(nroOperacion))
        }
        case _ => fail()
      }
    }
  }
}
