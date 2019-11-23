package controllers.utils

import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit
import org.apache.commons.validator.routines.{CodeValidator, CreditCardValidator}
import scala.concurrent.ExecutionContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FullCreditCardValidator  @Inject() (implicit context: ExecutionContext){

    val CABAL_VALIDATOR = new CodeValidator("^(589657\\d{10})$", 16, 16, LuhnCheckDigit.LUHN_CHECK_DIGIT)
    val NATIVA_MC = new CodeValidator("520053|546553", 16, 16, LuhnCheckDigit.LUHN_CHECK_DIGIT)
    val NATIVA_VISA = new CodeValidator("487017", 16, 16, LuhnCheckDigit.LUHN_CHECK_DIGIT)
    val NARANJA_VISA = new CodeValidator("^4[0-9]{2}(?:[0-9]{3})?$", 16, 16, LuhnCheckDigit.LUHN_CHECK_DIGIT)



    val validator: CreditCardValidator = new CreditCardValidator(
      Set(
        CreditCardValidator.AMEX_VALIDATOR,
        CreditCardValidator.VISA_VALIDATOR,
        CreditCardValidator.MASTERCARD_VALIDATOR,
        CreditCardValidator.DINERS_VALIDATOR,
        CABAL_VALIDATOR,
        NATIVA_MC,
        NATIVA_VISA,
        NARANJA_VISA
      ).toArray)

    def isValid (cardNumber: String) = {
      validator.isValid(cardNumber)
    }

}
