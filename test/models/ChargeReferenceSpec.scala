/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

import helpers.Constants
import org.scalacheck.Gen
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{EitherValues, OptionValues}
import play.api.libs.json.{JsString, Json}
import play.api.mvc.PathBindable

class ChargeReferenceSpec extends AnyWordSpec with Matchers with EitherValues with OptionValues with Constants {

  "ChargeReference" must {
    "be bound from a url path" in {

      val chargeReference = ChargeReference(chargeReferenceNumber)

      val result = implicitly[PathBindable[ChargeReference]]
        .bind("chargeReference", "XHPR1234567890")

      result.value mustEqual chargeReference
    }

    "unbind" in {

      val result = implicitly[PathBindable[ChargeReference]]
        .unbind("chargeReference", ChargeReference(chargeReferenceNumber))

      result mustEqual "XHPR1234567890"
    }

    "deserialise" in {

      val chargeReference = ChargeReference(chargeReferenceNumber)

      JsString(chargeReference.toString).as[ChargeReference] mustEqual chargeReference
    }

    "serialise" in {

      val chargeReference = ChargeReference(chargeReferenceNumber)

      Json.toJson(chargeReference) mustEqual JsString(chargeReference.toString)
    }

    "generate a modulo 23 check character" in {

      ChargeReference(1).checkCharacter mustEqual 'Y'
      ChargeReference(2).checkCharacter mustEqual 'Q'
      ChargeReference(3).checkCharacter mustEqual 'S'
      ChargeReference(4).checkCharacter mustEqual 'Z'
      ChargeReference(5).checkCharacter mustEqual 'W'
      ChargeReference(6).checkCharacter mustEqual 'B'
      ChargeReference(7).checkCharacter mustEqual 'D'
      ChargeReference(8).checkCharacter mustEqual 'F'
      ChargeReference(9).checkCharacter mustEqual 'H'
      ChargeReference(10).checkCharacter mustEqual 'P'
      ChargeReference(30).checkCharacter mustEqual 'V'
      ChargeReference(50).checkCharacter mustEqual 'E'
      ChargeReference(70).checkCharacter mustEqual 'K'
      ChargeReference(80).checkCharacter mustEqual 'N'
      ChargeReference(14).checkCharacter mustEqual 'A'
      ChargeReference(15).checkCharacter mustEqual 'C'
      ChargeReference(17).checkCharacter mustEqual 'G'
      ChargeReference(18).checkCharacter mustEqual 'X'
      ChargeReference(27).checkCharacter mustEqual 'J'
      ChargeReference(28).checkCharacter mustEqual 'L'
      ChargeReference(37).checkCharacter mustEqual 'M'
      ChargeReference(47).checkCharacter mustEqual 'P'
      ChargeReference(48).checkCharacter mustEqual 'R'
      ChargeReference(49).checkCharacter mustEqual 'T'
      ChargeReference(58).checkCharacter mustEqual 'Z'
    }

    "fail to build from an input that is not 14 characters" in {
      ChargeReference("1234567890123")   must not be defined
      ChargeReference("123456789012345") must not be defined
    }

    "fail to build from an input that doesn't start with X" in {
      ChargeReference("1234567890123") must not be defined
    }

    "fail to build from an input that does not have P as the third character" in {
      ChargeReference("XAXR1234567890") must not be defined
    }

    "fail to build from an input that does not have R as the fourth character" in {
      ChargeReference("XAPX1234567890") must not be defined
    }

    "fail to build from an input that does not have ten digits as characters 5 to 14" in {
      ChargeReference("XAPRX000000001") must not be defined
    }

    "fail to build from an input that does not have the correct check character as the second character" in {
      ChargeReference("XAPR0000000001") must not be defined
    }

    "build from a valid input" in {

      ChargeReference("XYPR0000000001").value mustEqual ChargeReference(1)
    }

    "convert to a string in the correct format" in {
      ChargeReference(1).toString mustEqual "XYPR0000000001"
    }

    "treat .apply and .toString as dual" in {

      Gen.choose(0, Int.MaxValue).map(ChargeReference(_))
    }

    "fail to build from inputs with invalid check characters" in {
      val remainingCharactersLength = 12

      for {
        chargeReference       <- Gen.choose(0, Int.MaxValue).map(ChargeReference(_).toString)
        invalidCheckCharacter <- Gen.alphaUpperChar suchThat (_ != chargeReference(1))
      } yield s"${chargeReference(0)}$invalidCheckCharacter${chargeReference.takeRight(remainingCharactersLength)}"
    }
  }
}
