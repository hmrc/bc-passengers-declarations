/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{JsString, Reads, Writes}
import play.api.mvc.PathBindable

import scala.collection.Map

final case class ChargeReference(value: Int) {

  override def toString: String = s"X${checkCharacter}PR$paddedValue"

  private def paddedValue = f"$value%010d"

  val checkCharacter: Char = {

    val weights = Seq(9, 10, 11, 12, 13, 8, 7, 6, 5, 4, 3, 2)

    val equivalentValues = Seq(48, 50) ++ paddedValue.map(_.asDigit)

    val remainder = weights
      .zip(equivalentValues)
      .map { case (a, b) =>
        a * b
      }
      .sum % 23

    ChargeReference.checkCharacterMap(remainder)
  }
}

object ChargeReference {

  private val ChargeReferenceFormat = """^X([A-Z])PR(\d{10})$""".r

  def apply(input: String): Option[ChargeReference] = input match {
    case ChargeReferenceFormat(checkChar, digits) =>
      val chargeReference = ChargeReference(digits.toInt)

      if (chargeReference.checkCharacter.toString == checkChar)
        Some(chargeReference)
      else
        None
    case _                                        =>
      None
  }

  implicit lazy val reads: Reads[ChargeReference] = {

    import play.api.libs.json._

    __.read[String].map(ChargeReference.apply).flatMap {
      case Some(c) => Reads(_ => JsSuccess(c))
      case None    => Reads(_ => JsError("Invalid charge reference"))
    }
  }

  implicit lazy val writes: Writes[ChargeReference] =
    Writes { chargeReference =>
      JsString(chargeReference.toString)
    }

  implicit def pathBindable: PathBindable[ChargeReference] = new PathBindable[ChargeReference] {

    override def bind(key: String, value: String): Either[String, ChargeReference] =
      ChargeReference.apply(value).toRight("Invalid charge reference")

    override def unbind(key: String, value: ChargeReference): String =
      value.toString
  }

  private val checkCharacterMap = Map(
    0  -> 'A',
    1  -> 'B',
    2  -> 'C',
    3  -> 'D',
    4  -> 'E',
    5  -> 'F',
    6  -> 'G',
    7  -> 'H',
    8  -> 'X',
    9  -> 'J',
    10 -> 'K',
    11 -> 'L',
    12 -> 'M',
    13 -> 'N',
    14 -> 'Y',
    15 -> 'P',
    16 -> 'Q',
    17 -> 'R',
    18 -> 'S',
    19 -> 'T',
    20 -> 'Z',
    21 -> 'V',
    22 -> 'W'
  )
}
