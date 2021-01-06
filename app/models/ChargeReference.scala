/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package models

import play.api.libs.json.{JsString, Reads, Writes}
import play.api.mvc.PathBindable

final case class ChargeReference(value: Int) {

  override def toString: String = s"X${checkCharacter}PR$paddedValue"

  private def paddedValue = f"$value%010d"

  val checkCharacter: Char = {

    val weights = Seq(9, 10, 11, 12, 13, 8, 7, 6, 5, 4, 3, 2)

    val equivalentValues = Seq(48, 50) ++ paddedValue.map(_.asDigit)

    val remainder = weights.zip(equivalentValues).map {
      case (a, b) => a * b
    }.sum % 23

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
    case _ =>
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
    Writes {
      chargeReference =>
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
