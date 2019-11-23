package services.cybersource

import org.scalatest.FunSuite
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec

class CybersourceConverterTest extends PlaySpec with MockitoSugar {

  val context = scala.concurrent.ExecutionContext.global
  val service = new CybersourceConverter(context)

  "normalize ipv6 successfully" in {
    val ipv6 = "123A:45cd:0067:89::EF"
    val expected = "123a:45cd:67:89:0:0:0:ef"

    val result = service.normalizeIP(ipv6)
    assert(result == expected)
  }

}
