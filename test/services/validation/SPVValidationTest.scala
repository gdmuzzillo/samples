package services.validation

import com.decidir.coretx.api._
import com.decidir.coretx.domain.Site
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.validations.operation.SPVValidation
import org.mockito.Mockito.when

class SPVValidationTest extends PlaySpec with MockitoSugar {
  "SPVValidation" should {
    "Validate successFully" in {
      implicit val op = OperationResource(datos_spv = None)
      implicit val site = mock[Site]

      try {
        SPVValidation.validate
      } catch {
        case e: Exception => fail(e)
      }
      assert(op.datos_spv.isEmpty)
    }

    "throws error when spv client id is wrong" in {
      val spv = mock[DatosSPV]
      implicit val op = OperationResource(datos_spv = Some(spv))
      implicit val site = mock[Site]

      when(spv.client_id) thenReturn Some("idclient1234567890123145")

      try {
        SPVValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_SPV_CLIENT_ID)
        }
        case _ => fail()
      }
      assert(op.datos_spv.nonEmpty)
    }

    "throws error when SPV installment_code is invalid" in {
      val spv = mock[DatosSPV]
      val installmentSPV = mock[InstallmentSPV]
      implicit val op = OperationResource(datos_spv = Some(spv))
      implicit val site = mock[Site]

      when(spv.client_id) thenReturn None
      when(spv.installment) thenReturn installmentSPV
      when(installmentSPV.code) thenReturn Some("code14")

      try {
        SPVValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_SPV_INSTALLMENT_CODE)
        }
        case _ => fail()
      }
      assert(op.datos_spv.nonEmpty)
    }

    "throws error when SPV identificator is invalid" in {
      val spv = mock[DatosSPV]
      val installmentSPV = mock[InstallmentSPV]
      implicit val op = OperationResource(datos_spv = Some(spv))
      implicit val site = mock[Site]

      when(spv.client_id) thenReturn None
      when(spv.installment) thenReturn installmentSPV
      when(installmentSPV.code) thenReturn None
      when(spv.identificator) thenReturn Some("IDENTICADOR")

      try {
        SPVValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_SPV_IDENTIFICATOR)
        }
        case _ => fail()
      }
      assert(op.datos_spv.nonEmpty)
    }

    "throws error when SPV installment_quantity is invalid" in {
      val spv = mock[DatosSPV]
      val installmentSPV = mock[InstallmentSPV]
      implicit val op = OperationResource(datos_spv = Some(spv))
      implicit val site = mock[Site]

      when(spv.client_id) thenReturn None
      when(spv.installment) thenReturn installmentSPV
      when(installmentSPV.code) thenReturn None
      when(spv.identificator) thenReturn None
      when(installmentSPV.quantity) thenReturn Some("123")

      try {
        SPVValidation.validate
      } catch {
        case ApiException(InvalidRequestError(List(ValidationError(typeName, param)))) => {
          assert(typeName == ErrorMessage.INVALID_PARAM)
          assert(param == ErrorMessage.DATA_SPV_INSTALLMENT_QUANTITY)
        }
        case _ => fail()
      }
      assert(op.datos_spv.nonEmpty)
    }
  }
}
