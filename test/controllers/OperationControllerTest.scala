package controllers

import com.decidir.coretx.api.ErrorMessage
import org.scalatestplus.play._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, Json, _}
import play.api.test.FakeRequest
import play.api.test.Helpers._

/**/class OperationControllerTest {

} /* extends PlaySpec with OneAppPerSuite {

  implicit override lazy val app: Application = {
    new GuiceApplicationBuilder()
      //Create an inMemory db mock
      .configure(inMemoryDatabase())
      .build()
  }

  def fixture = new {
    val tx = Json.obj(
      "id" -> "",
      "nro_operacion" -> String.valueOf(System.currentTimeMillis),
      "monto" -> 1000,
      "cuotas" -> 1,
      "sub_transactions" -> Json.arr(),
      "datos_site" -> Json.obj("site_id" -> "00290815", "referer" -> "referer", "origin_site_id" -> "00290815"),
      "datos_medio_pago" -> Json.obj("medio_de_pago" -> 1, "nro_tarjeta" -> "4507990000004905", "expiration_month" -> "10", "expiration_year" -> "20", "security_code" -> "123", "nombre_en_tarjeta" -> "Juan"),
      "datos_titular" -> Json.obj("tipo_doc" -> 1, "nro_doc" -> "20123456", "ip" -> "192.168.1.1"))

    val txDist = Json.obj(
      "id" -> "",
      "nro_operacion" -> String.valueOf(System.currentTimeMillis),
      "monto" -> 1000,
      "sub_transactions" -> Json.arr(Json.obj("site_id" -> "00300815", "amount" -> 800, "installments" -> 1, "referer" -> "referer", "origin_site_id" -> "00290815"),
        Json.obj("site_id" -> "00310815", "amount" -> 200, "installments" -> 1)),
      "datos_site" -> Json.obj("site_id" -> "00290815", "referer" -> "referer", "id_modalidad" -> "S", "origin_site_id" -> "00290815"),
      "datos_medio_pago" -> Json.obj("medio_de_pago" -> 1, "nro_tarjeta" -> "4507990000004905", "vencimiento" -> "1020", "cod_seguridad" -> "123", "nombre_en_tarjeta" -> "Juan"),
      "datos_titular" -> Json.obj("tipo_doc" -> 1, "nro_doc" -> "20123456", "ip" -> "192.168.1.1"))

  }

  "POST /tx" should {
    "return 200 in single tx" in {
      val result = route(app, FakeRequest(POST, "/tx").withBody(fixture.tx)).get
      result mustBe (OK)
      val responseNode = Json.parse(contentAsString(result))
      val txId = (responseNode \ "id").as[String]

      assert(txId.nonEmpty)
    }

    "return 200 in distributed tx" in {
      val result = route(app, FakeRequest(POST, "/tx").withBody(fixture.txDist)).get
      status(result) mustBe OK
      val responseNode = Json.parse(contentAsString(result))
      val txId = (responseNode \ "id").as[String]

      assert(txId.nonEmpty)
    }

    "validate nro_operacion" in {

      val jsonTransformer = (__ \ 'nro_operacion).json.prune
      val body = fixture.tx.transform(jsonTransformer).get

      val result = route(app, FakeRequest(POST, "/tx").withBody(body)).get
      status(result) mustBe BAD_REQUEST

      val responseNode = Json.parse(contentAsString(result))
      (responseNode \ "error_type").as[String] mustBe "invalid_request_error"

      val validationErrors = (responseNode \ "validation_errors").as[JsArray]
      (validationErrors.head \ "code").as[String] mustBe ErrorMessage.PARAM_REQUIRED
      (validationErrors.head \ "param").as[String] mustBe "site_transaction_id"
    }

    "validate datos_site.site_id" in {

      val jsonTransformer = (__ \ 'datos_site).json.prune
      val body = fixture.tx.transform(jsonTransformer).get

      val result = route(app, FakeRequest(POST, "/tx").withBody(body)).get
      status(result) mustBe BAD_REQUEST

      val responseNode = Json.parse(contentAsString(result))
      (responseNode \ "error_type").as[String] mustBe "invalid_request_error"

      val validationErrors = (responseNode \ "validation_errors").as[JsArray]
      (validationErrors.head \ "code").as[String] mustBe ErrorMessage.PARAM_REQUIRED
      (validationErrors.head \ "param").as[String] mustBe ErrorMessage.DATA_SITE_SITE_ID
    }

    "validate invalid monto" in {
      val body = fixture.tx.as[JsObject] ++ Json.obj("monto" -> -1)

      val result = route(app, FakeRequest(POST, "/tx").withBody(body)).get
      status(result) mustBe BAD_REQUEST

      val responseNode = Json.parse(contentAsString(result))
      (responseNode \ "error_type").as[String] mustBe "invalid_request_error"

      val validationErrors = (responseNode \ "validation_errors").as[JsArray]
      (validationErrors.head \ "code").as[String] mustBe "invalid_amount"
    }

    "validate invalid monto equal 0" in {
      val body = fixture.tx.as[JsObject] ++ Json.obj("monto" -> 0)

      val result = route(app, FakeRequest(POST, "/tx").withBody(body)).get
      status(result) mustBe BAD_REQUEST

      val responseNode = Json.parse(contentAsString(result))
      (responseNode \ "error_type").as[String] mustBe "invalid_request_error"

      val validationErrors = (responseNode \ "validation_errors").as[JsArray]
      (validationErrors.head \ "code").as[String] mustBe "invalid_amount"
    }

    "validate datos medios pago" in {

      val jsonTransformer = (__ \ 'datos_medio_pago).json.prune
      val body = fixture.tx.transform(jsonTransformer).get

      val result = route(app, FakeRequest(POST, "/tx").withBody(body)).get
      status(result) mustBe BAD_REQUEST

      val responseNode = Json.parse(contentAsString(result))
      (responseNode \ "error_type").as[String] mustBe "invalid_request_error"

      val validationErrors = (responseNode \ "validation_errors").as[JsArray]
      (validationErrors.head \ "code").as[String] mustBe "param_required"
    }

    "validate sub_site in distributed payment" in {

      val subSite1 = (fixture.txDist \ "sub_transactions" \ 0).as[JsObject] ++ Json.obj("site_id" -> "00040407")
      val subSite2 = (fixture.txDist \ "sub_transactions" \ 1).as[JsObject]
      val body = fixture.txDist ++ Json.obj("sub_transactions" -> Json.arr(subSite1, subSite2))

      val result = route(app, FakeRequest(POST, "/tx").withBody(body)).get
      status(result) mustBe BAD_REQUEST

      val responseNode = Json.parse(contentAsString(result))
      (responseNode \ "error_type").as[String] mustBe "invalid_request_error"

      val validationErrors = (responseNode \ "validation_errors").as[JsArray]
      (validationErrors.head \ "code").as[String] mustBe "invalid_param"
      (validationErrors.head \ "param").as[String] mustBe "sub_payments.site_id"
    }
  }

}*/
