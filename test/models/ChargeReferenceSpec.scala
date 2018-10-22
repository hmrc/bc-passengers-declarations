package models

import org.scalatest.{EitherValues, FreeSpec, MustMatchers}
import play.api.mvc.PathBindable

class ChargeReferenceSpec extends FreeSpec with MustMatchers with EitherValues {

  "a charge reference" - {

    "must be bound from a url path" in {

      val chargeReference = ChargeReference("1234567890asdf")

      val result = implicitly[PathBindable[ChargeReference]]
        .bind("chargeReference", "1234567890asdf")

      result.right.value mustEqual chargeReference
    }
  }
}
