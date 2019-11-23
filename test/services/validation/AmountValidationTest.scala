package services.validation

import com.decidir.coretx.api._
import com.decidir.coretx.domain.Site
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.validations.operation.AmountValidation

class AmountValidationTest extends PlaySpec with MockitoSugar {

    "AmountValidation" should {
      "validate successfully" in {
        val amount = 1000
        implicit val operationResource = OperationResource(monto = Some(amount))
        implicit val site = mock[Site]

        try {
          AmountValidation.validate
        } catch {
          case e:Exception => fail(e)
        }
        assert(operationResource.monto.contains(amount))
      }

      "validate SuccessFully when amount is None" in {
        implicit val operationResource = OperationResource(monto = None)
        implicit val site = mock[Site]
        try {
          AmountValidation.validate
        } catch {
          case e:Exception => fail(e)
        }
        assert(operationResource.monto.isEmpty)
      }

      "Throw Exception when amount is minor to 0" in {
        implicit val operationResource = OperationResource(monto = Some(-100))
        implicit val site = mock[Site]
        try {
          AmountValidation.validate
        } catch {
          case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
            assert(typeName == ErrorMessage.INVALID_AMOUNT)
            assert(param == ErrorMessage.AMOUNT)
            assert(operationResource.monto.nonEmpty && operationResource.monto.contains(-100))
          }
          case _ => fail()
        }
      }

      "Throw Exception when amount is equal 0" in {
        implicit val operationResource = OperationResource(monto = Some(0))
        implicit val site = mock[Site]
        try {
          AmountValidation.validate
        } catch {
          case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
            assert(typeName == ErrorMessage.INVALID_AMOUNT)
            assert(param == ErrorMessage.AMOUNT)
            assert(operationResource.monto.nonEmpty && operationResource.monto.contains(0))
          }
          case _ => fail()
        }
      }
    }

}
